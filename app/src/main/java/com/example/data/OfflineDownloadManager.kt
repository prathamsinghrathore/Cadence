package com.example.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.download.SongDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class OfflineDownloadManager(
    private val context: Context,
    private val songDao: SongDao,
    private val scope: CoroutineScope
) {
    private val workManager: WorkManager = WorkManager.getInstance(context)

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val reportedFailureIds = mutableSetOf<String>()

    val activeDownloads: Flow<Map<String, Float>> =
        workManager.getWorkInfosByTagFlow(SongDownloadWorker.TAG_SONG_DOWNLOAD)
            .map { infos ->
                val progressMap = mutableMapOf<String, Float>()
                infos.forEach { info ->
                    val songId = info.tags
                        .firstOrNull { it.startsWith(SONG_TAG_PREFIX) }
                        ?.removePrefix(SONG_TAG_PREFIX)
                        ?: info.progress.getString(SongDownloadWorker.KEY_SONG_ID)
                        ?: return@forEach

                    when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> {
                            progressMap[songId] = 0.02f
                        }
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getFloat(SongDownloadWorker.KEY_PROGRESS, 0.05f)
                            progressMap[songId] = progress.coerceIn(0.02f, 0.99f)
                        }
                        WorkInfo.State.FAILED -> {
                            if (reportedFailureIds.add(info.id.toString())) {
                                val err = info.outputData.getString(SongDownloadWorker.KEY_ERROR)
                                    ?: "Download failed"
                                _downloadError.value = err
                            }
                        }
                        else -> Unit
                    }
                }
                progressMap
            }

    fun clearError() {
        _downloadError.value = null
    }

    fun startDownload(song: Song) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
            .setInputData(SongDownloadWorker.createInputData(song))
            .setConstraints(constraints)
            .addTag(SongDownloadWorker.TAG_SONG_DOWNLOAD)
            .addTag("$SONG_TAG_PREFIX${song.id}")
            .build()

        workManager.enqueueUniqueWork(
            SongDownloadWorker.uniqueWorkName(song.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun removeDownload(songId: String) {
        workManager.cancelUniqueWork(SongDownloadWorker.uniqueWorkName(songId))
        scope.launch(Dispatchers.IO) {
            val existing = songDao.getDownloadedSongById(songId)
            existing?.let { entity ->
                if (entity.localFilePath.isNotBlank()) {
                    File(entity.localFilePath).takeIf { it.exists() }?.delete()
                }
                if (entity.localArtworkPath.isNotBlank()) {
                    File(entity.localArtworkPath).takeIf { it.exists() }?.delete()
                }
            }
            val audioDir = File(context.filesDir, "offline_audio")
            listOf("mp4", "m4a", "aac", "mp3", "tmp").forEach { ext ->
                File(audioDir, "$songId.$ext").takeIf { it.exists() }?.delete()
            }
            val artworkDir = File(context.filesDir, "offline_artwork")
            File(artworkDir, "${songId}_art.jpg").takeIf { it.exists() }?.delete()

            songDao.deleteDownloadedSong(songId)
        }
    }

    companion object {
        private const val SONG_TAG_PREFIX = "song_id:"
    }
}
