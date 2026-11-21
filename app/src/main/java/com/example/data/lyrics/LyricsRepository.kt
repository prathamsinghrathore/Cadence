package com.example.data.lyrics

import com.example.data.CachedLyricsEntity
import com.example.data.Song
import com.example.data.SongDao
import com.example.data.remote.LrcLibApiService
import com.example.data.remote.LrcLibTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Fetches time-synced (.lrc) and plain lyrics from LRCLIB with automatic Room offline caching.
 *
 * Resolution pipeline:
 * 1. Check Room `cached_lyrics` table by `song.id` (instant offline replay).
 * 2. Call `GET https://lrclib.net/api/get` with cleaned title, primary artist, album, and duration.
 * 3. If 404 or null/blank `syncedLyrics`, call `GET https://lrclib.net/api/search?q={title} {artist}`
 *    and select the best synced match (preferring closest duration), falling back to plain lyrics.
 * 4. Persist found lyrics into Room so future replays and offline playback load instantly.
 */
class LyricsRepository(
    private val songDao: SongDao,
    private val lrcLibApiService: LrcLibApiService
) {

    suspend fun getLyricsForSong(
        song: Song,
        forceRefresh: Boolean = false
    ): LyricsUiState = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = runCatching { songDao.getCachedLyrics(song.id) }.getOrNull()
            if (cached != null) {
                val cachedState = mapEntityToUiState(cached)
                if (cachedState != null) {
                    return@withContext cachedState
                }
            }
        }

        val cleanedTitle = LrcParser.cleanTrackTitle(song.title)
        val primaryArtist = LrcParser.extractPrimaryArtist(song.artist)
        val cleanedAlbum = LrcParser.cleanAlbumName(song.album)
        val durationSeconds = (song.duration / 1000L).toInt().takeIf { it > 0 }

        var bestPlainFallback: LrcLibTrackDto? = null
        var instrumentalFallback = false

        // Step 1: Primary exact lookup (GET api/get)
        if (cleanedTitle.isNotBlank() && primaryArtist.isNotBlank()) {
            val exactResult = runCatching {
                lrcLibApiService.getLyrics(
                    trackName = cleanedTitle,
                    artistName = primaryArtist,
                    albumName = cleanedAlbum.takeIf { it.isNotBlank() },
                    durationSeconds = durationSeconds
                )
            }.getOrNull()

            val body = exactResult?.takeIf { it.isSuccessful }?.body()
            if (body != null) {
                val synced = body.syncedLyrics?.trim().orEmpty()
                if (synced.isNotEmpty()) {
                    val parsed = LrcParser.parseSyncedLyrics(synced)
                    if (parsed.isNotEmpty()) {
                        cacheResult(song.id, syncedLyrics = synced, plainLyrics = body.plainLyrics, isInstrumental = false)
                        return@withContext LyricsUiState.Synced(
                            songId = song.id,
                            lines = parsed,
                            isFromCache = false
                        )
                    }
                }
                if (!body.plainLyrics.isNullOrBlank()) {
                    bestPlainFallback = body
                }
                if (body.instrumental) {
                    instrumentalFallback = true
                }
            }
        }

        // Step 2: Fallback search (GET api/search?q={cleanedTitle} {primaryArtist})
        val searchQueries = buildList {
            if (cleanedTitle.isNotBlank() && primaryArtist.isNotBlank()) {
                add("$cleanedTitle $primaryArtist")
            }
            if (cleanedTitle.isNotBlank()) {
                add(cleanedTitle)
            }
        }.distinct()

        for (query in searchQueries) {
            val searchResponse = runCatching {
                lrcLibApiService.searchLyrics(query = query)
            }.getOrNull()

            val candidates = searchResponse?.takeIf { it.isSuccessful }?.body().orEmpty()
            if (candidates.isEmpty()) continue

            // Prefer the first / closest-duration result containing valid syncedLyrics
            val syncedCandidates = candidates.filter { !it.syncedLyrics.isNullOrBlank() }
            if (syncedCandidates.isNotEmpty()) {
                val chosen = pickBestCandidate(syncedCandidates, durationSeconds)
                val syncedRaw = chosen.syncedLyrics?.trim().orEmpty()
                val parsed = LrcParser.parseSyncedLyrics(syncedRaw)
                if (parsed.isNotEmpty()) {
                    cacheResult(
                        songId = song.id,
                        syncedLyrics = syncedRaw,
                        plainLyrics = chosen.plainLyrics,
                        isInstrumental = false
                    )
                    return@withContext LyricsUiState.Synced(
                        songId = song.id,
                        lines = parsed,
                        isFromCache = false
                    )
                }
            }

            if (bestPlainFallback == null) {
                val plainCandidates = candidates.filter { !it.plainLyrics.isNullOrBlank() }
                if (plainCandidates.isNotEmpty()) {
                    bestPlainFallback = pickBestCandidate(plainCandidates, durationSeconds)
                }
            }

            if (!instrumentalFallback && candidates.any { it.instrumental }) {
                instrumentalFallback = true
            }
        }

        // Step 3: Fall back to plain (unsynced) lyrics if available
        val plainRaw = bestPlainFallback?.plainLyrics?.trim().orEmpty()
        if (plainRaw.isNotEmpty()) {
            val plainLines = LrcParser.parsePlainLyrics(plainRaw)
            if (plainLines.isNotEmpty()) {
                cacheResult(
                    songId = song.id,
                    syncedLyrics = null,
                    plainLyrics = plainRaw,
                    isInstrumental = false
                )
                return@withContext LyricsUiState.Plain(
                    songId = song.id,
                    lines = plainLines,
                    isFromCache = false
                )
            }
        }

        if (instrumentalFallback) {
            cacheResult(
                songId = song.id,
                syncedLyrics = null,
                plainLyrics = null,
                isInstrumental = true
            )
            return@withContext LyricsUiState.Instrumental(
                songId = song.id,
                isFromCache = false
            )
        }

        LyricsUiState.NotFound(
            songId = song.id,
            message = "Lyrics aren't available for this track yet."
        )
    }

    private fun pickBestCandidate(
        candidates: List<LrcLibTrackDto>,
        targetDurationSeconds: Int?
    ): LrcLibTrackDto {
        if (targetDurationSeconds == null || candidates.size == 1) {
            return candidates.first()
        }
        return candidates.minByOrNull { dto ->
            val d = dto.duration?.toInt()
            if (d != null && d > 0) abs(d - targetDurationSeconds) else Int.MAX_VALUE
        } ?: candidates.first()
    }

    private suspend fun cacheResult(
        songId: String,
        syncedLyrics: String?,
        plainLyrics: String?,
        isInstrumental: Boolean
    ) {
        runCatching {
            songDao.upsertCachedLyrics(
                CachedLyricsEntity(
                    songId = songId,
                    syncedLyrics = syncedLyrics,
                    plainLyrics = plainLyrics,
                    isInstrumental = isInstrumental
                )
            )
        }
    }

    private fun mapEntityToUiState(entity: CachedLyricsEntity): LyricsUiState? {
        val syncedRaw = entity.syncedLyrics?.trim().orEmpty()
        if (syncedRaw.isNotEmpty()) {
            val parsed = LrcParser.parseSyncedLyrics(syncedRaw)
            if (parsed.isNotEmpty()) {
                return LyricsUiState.Synced(
                    songId = entity.songId,
                    lines = parsed,
                    isFromCache = true
                )
            }
        }
        val plainRaw = entity.plainLyrics?.trim().orEmpty()
        if (plainRaw.isNotEmpty()) {
            val lines = LrcParser.parsePlainLyrics(plainRaw)
            if (lines.isNotEmpty()) {
                return LyricsUiState.Plain(
                    songId = entity.songId,
                    lines = lines,
                    isFromCache = true
                )
            }
        }
        if (entity.isInstrumental) {
            return LyricsUiState.Instrumental(
                songId = entity.songId,
                isFromCache = true
            )
        }
        return null
    }
}
