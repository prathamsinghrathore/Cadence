package com.example.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.AppDatabase
import com.example.data.CachedSongEntity
import com.example.data.DownloadedSongEntity
import com.example.data.Song
import com.example.data.remote.NetworkModule
import com.example.data.remote.StreamUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class SongDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Unknown"
        val artist = inputData.getString(KEY_ARTIST) ?: "Unknown Artist"
        val album = inputData.getString(KEY_ALBUM) ?: "Single"
        val duration = inputData.getLong(KEY_DURATION, 0L)
        val artworkUrl = inputData.getString(KEY_ARTWORK_URL).orEmpty()
        val initialStreamUrl = inputData.getString(KEY_STREAM_URL).orEmpty()
        val genre = inputData.getString(KEY_GENRE) ?: "Music"
        val qualityLabel = inputData.getString(KEY_QUALITY_LABEL) ?: "320kbps AAC"
        val fallbackArtworkRes = inputData.getInt(KEY_FALLBACK_ARTWORK_RES, -1).takeIf { it > 0 }

        val songStub = Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            artworkUrl = artworkUrl,
            streamUrl = initialStreamUrl,
            genre = genre,
            audioQualityLabel = qualityLabel,
            fallbackArtworkRes = fallbackArtworkRes
        )

        val resolvedStreamUrl = StreamUrlResolver.resolveStreamUrl(songStub, preferredBitrate = 320)
            ?: StreamUrlResolver.resolveStreamUrl(songStub, preferredBitrate = 160)
            ?: return@withContext Result.failure(
                workDataOf(
                    KEY_SONG_ID to songId,
                    KEY_ERROR to "Unable to resolve download URL for $title"
                )
            )

        val audioDir = File(applicationContext.filesDir, "offline_audio").apply {
            if (!exists()) mkdirs()
        }
        val artworkDir = File(applicationContext.filesDir, "offline_artwork").apply {
            if (!exists()) mkdirs()
        }

        val ext = when {
            resolvedStreamUrl.contains(".mp4", ignoreCase = true) -> "mp4"
            resolvedStreamUrl.contains(".m4a", ignoreCase = true) -> "m4a"
            resolvedStreamUrl.contains(".aac", ignoreCase = true) -> "aac"
            else -> "mp3"
        }
        val tempAudioFile = File(audioDir, "$songId.tmp")
        val finalAudioFile = File(audioDir, "$songId.$ext")
        val artworkFile = File(artworkDir, "${songId}_art.jpg")

        try {
            setProgress(workDataOf(KEY_SONG_ID to songId, KEY_PROGRESS to 0.02f))

            // 1. Download audio stream to internal storage
            val audioRequest = Request.Builder().url(resolvedStreamUrl).build()
            NetworkModule.okHttpClient.newCall(audioRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty audio body")
                val totalBytes = body.contentLength()
                var bytesCopied = 0L
                val buffer = ByteArray(8192)
                var lastReportedProgress = 0.02f

                body.byteStream().use { input ->
                    FileOutputStream(tempAudioFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val progress = if (totalBytes > 0L) {
                                (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.90f)
                            } else {
                                0.50f
                            }
                            if (progress - lastReportedProgress >= 0.04f) {
                                lastReportedProgress = progress
                                setProgress(workDataOf(KEY_SONG_ID to songId, KEY_PROGRESS to progress))
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (finalAudioFile.exists()) finalAudioFile.delete()
            if (!tempAudioFile.renameTo(finalAudioFile)) {
                tempAudioFile.copyTo(finalAudioFile, overwrite = true)
                tempAudioFile.delete()
            }

            setProgress(workDataOf(KEY_SONG_ID to songId, KEY_PROGRESS to 0.94f))

            // 2. Download or cache album art to internal storage for offline rendering
            val localArtworkPath = saveArtworkOffline(
                artworkUrl = artworkUrl,
                fallbackArtworkRes = fallbackArtworkRes,
                destination = artworkFile
            )

            val totalSizeBytes = finalAudioFile.length() +
                if (localArtworkPath.isNotEmpty()) artworkFile.length() else 0L

            val songDao = AppDatabase.getInstance(applicationContext).songDao()
            songDao.upsertCachedSong(
                CachedSongEntity(
                    id = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    artworkUrl = artworkUrl,
                    streamUrl = resolvedStreamUrl,
                    genre = genre,
                    audioQualityLabel = qualityLabel,
                    fallbackArtworkRes = fallbackArtworkRes
                )
            )
            songDao.insertDownloadedSong(
                DownloadedSongEntity(
                    id = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    artworkUrl = artworkUrl,
                    localArtworkPath = localArtworkPath,
                    streamUrl = resolvedStreamUrl,
                    localFilePath = finalAudioFile.absolutePath,
                    fileSizeBytes = totalSizeBytes,
                    genre = genre,
                    audioQualityLabel = qualityLabel,
                    fallbackArtworkRes = fallbackArtworkRes
                )
            )

            setProgress(workDataOf(KEY_SONG_ID to songId, KEY_PROGRESS to 1.0f))
            Result.success(workDataOf(KEY_SONG_ID to songId))
        } catch (e: Exception) {
            if (tempAudioFile.exists()) tempAudioFile.delete()
            Result.failure(
                workDataOf(
                    KEY_SONG_ID to songId,
                    KEY_ERROR to (e.localizedMessage ?: "Download failed")
                )
            )
        }
    }

    private fun saveArtworkOffline(
        artworkUrl: String,
        fallbackArtworkRes: Int?,
        destination: File
    ): String {
        return runCatching {
            if (artworkUrl.startsWith("http", ignoreCase = true)) {
                val request = Request.Builder().url(artworkUrl).build()
                NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(destination).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (destination.exists() && destination.length() > 0L) {
                            return destination.absolutePath
                        }
                    }
                }
            }
            if (fallbackArtworkRes != null) {
                applicationContext.resources.openRawResource(fallbackArtworkRes).use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destination.exists() && destination.length() > 0L) {
                    return destination.absolutePath
                }
            }
            ""
        }.getOrDefault("")
    }

    companion object {
        const val TAG_SONG_DOWNLOAD = "sonora_song_download"
        const val KEY_SONG_ID = "song_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_DURATION = "duration"
        const val KEY_ARTWORK_URL = "artwork_url"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_GENRE = "genre"
        const val KEY_QUALITY_LABEL = "quality_label"
        const val KEY_FALLBACK_ARTWORK_RES = "fallback_artwork_res"
        const val KEY_PROGRESS = "download_progress"
        const val KEY_ERROR = "download_error"

        fun uniqueWorkName(songId: String): String = "download_$songId"

        fun createInputData(song: Song) = workDataOf(
            KEY_SONG_ID to song.id,
            KEY_TITLE to song.title,
            KEY_ARTIST to song.artist,
            KEY_ALBUM to song.album,
            KEY_DURATION to song.duration,
            KEY_ARTWORK_URL to song.artworkUrl,
            KEY_STREAM_URL to song.streamUrl,
            KEY_GENRE to song.genre,
            KEY_QUALITY_LABEL to song.audioQualityLabel,
            KEY_FALLBACK_ARTWORK_RES to (song.fallbackArtworkRes ?: -1)
        )
    }
}
