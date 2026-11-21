package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.AppDatabase
import com.example.data.DownloadState
import com.example.data.DownloadedSongEntity
import com.example.data.RepeatMode
import com.example.data.Song
import com.example.data.remote.StreamUrlResolver
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class PlaybackStateSnapshot(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackError: String? = null,
    val lastSkipDirection: Int = 1
)

class SonoraPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val songDao = AppDatabase.getInstance(context).songDao()

    private val _state = MutableStateFlow(PlaybackStateSnapshot())
    val state: StateFlow<PlaybackStateSnapshot> = _state.asStateFlow()

    // Isolated high-frequency playback position stream so only seekbars/progress bars
    // recompose every 250ms instead of triggering screen-wide recompositions.
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private val _sleepTimerEndOfTrack = MutableStateFlow(false)
    val sleepTimerEndOfTrack: StateFlow<Boolean> = _sleepTimerEndOfTrack.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var playQueueJob: Job? = null
    private var retryJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pendingPlayAction: ((MediaController) -> Unit)? = null
    private val artworkBytesCache = mutableMapOf<Int, ByteArray>()
    private val knownSongsById = mutableMapOf<String, Song>()

    private var activeQueue: List<Song> = emptyList()
    private var activeIndex: Int = 0
    private var latestDownloadStates: Map<String, DownloadState> = emptyMap()
    private var hasRetriedLowerBitrate: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startPositionPolling() else stopPositionPolling()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val ctrl = controller
            val duration = ctrl?.duration?.takeIf { it > 0L }
                ?: _state.value.currentSong?.duration
                ?: 0L
            _state.value = _state.value.copy(
                isBuffering = buffering,
                durationMs = duration,
                playbackError = if (playbackState == Player.STATE_READY) null else _state.value.playbackError
            )

            // Honor "End of current track" sleep timer before auto-advancing
            if (playbackState == Player.STATE_ENDED && _sleepTimerEndOfTrack.value) {
                _sleepTimerEndOfTrack.value = false
                controller?.pause()
                return
            }

            // Auto-advance with fresh stream resolution when current track finishes
            if (playbackState == Player.STATE_ENDED &&
                _state.value.repeatMode != RepeatMode.ONE &&
                activeQueue.isNotEmpty()
            ) {
                val hasMore = activeIndex < activeQueue.lastIndex || _state.value.repeatMode == RepeatMode.ALL
                if (hasMore) {
                    skipToNext()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.mediaId?.let { knownSongsById[it] }
            val ctrl = controller
            _currentPositionMs.value = 0L
            _state.value = _state.value.copy(
                currentSong = song ?: _state.value.currentSong,
                durationMs = ctrl?.duration?.takeIf { it > 0L } ?: song?.duration ?: 0L,
                playbackError = null
            )
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.value = _state.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            _state.value = _state.value.copy(repeatMode = mapped)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
            val current = _state.value.currentSong

            if (!hasRetriedLowerBitrate &&
                current != null &&
                !current.isDownloaded &&
                !StreamUrlResolver.isAudiusSong(current)
            ) {
                hasRetriedLowerBitrate = true
                retryLowerBitrate(current, error)
                return
            }

            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                playbackError = formatPlaybackError(error)
            )
        }
    }

    private fun retryLowerBitrate(song: Song, originalError: PlaybackException) {
        retryJob?.cancel()
        retryJob = scope.launch {
            _state.value = _state.value.copy(isBuffering = true, playbackError = null)
            val fallbackUrl = StreamUrlResolver.resolveStreamUrl(song, preferredBitrate = 160)
                ?: StreamUrlResolver.resolveStreamUrl(song, preferredBitrate = 96)

            if (fallbackUrl.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    playbackError = formatPlaybackError(originalError)
                )
                return@launch
            }

            val updatedSong = song.copy(
                streamUrl = fallbackUrl,
                audioQualityLabel = "160kbps AAC"
            )
            knownSongsById[updatedSong.id] = updatedSong
            _state.value = _state.value.copy(currentSong = updatedSong)

            val mediaItem = withContext(Dispatchers.Default) {
                buildMediaItem(updatedSong, null, latestDownloadStates[updatedSong.id])
            }
            controller?.let { ctrl ->
                ctrl.setMediaItem(mediaItem)
                ctrl.prepare()
                ctrl.play()
            } ?: run {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    playbackError = formatPlaybackError(originalError)
                )
            }
        }
    }

    private fun formatPlaybackError(error: PlaybackException): String {
        val causeMsg = error.cause?.localizedMessage ?: error.localizedMessage ?: "Stream failed"
        return "${error.errorCodeName}: $causeMsg"
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    syncFromController(mediaController)
                    pendingPlayAction?.invoke(mediaController)
                    pendingPlayAction = null
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun seedQueueIfIdle(songs: List<Song>) {
        if (songs.isEmpty()) return
        songs.forEach { knownSongsById[it.id] = it }
        val ctrl = controller
        val hasActiveMedia = ctrl != null && ctrl.mediaItemCount > 0
        if (!hasActiveMedia && _state.value.currentSong == null) {
            val first = songs.first()
            activeQueue = songs
            activeIndex = 0
            _state.value = _state.value.copy(
                currentSong = first,
                queue = songs,
                durationMs = first.duration
            )
        }
    }

    private fun syncFromController(ctrl: MediaController) {
        val currentId = ctrl.currentMediaItem?.mediaId
        val activeSong = currentId?.let { knownSongsById[it] } ?: _state.value.currentSong
        val queue = activeQueue.ifEmpty { _state.value.queue }
        _currentPositionMs.value = ctrl.currentPosition.coerceAtLeast(0L)
        _state.value = _state.value.copy(
            currentSong = activeSong,
            queue = queue,
            isPlaying = ctrl.isPlaying,
            isBuffering = ctrl.playbackState == Player.STATE_BUFFERING,
            durationMs = ctrl.duration.takeIf { it > 0L } ?: activeSong?.duration ?: 0L,
            shuffleEnabled = ctrl.shuffleModeEnabled,
            repeatMode = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        )
        if (ctrl.isPlaying) startPositionPolling()
    }

    fun playQueue(
        songs: List<Song>,
        startIndex: Int,
        downloadStates: Map<String, DownloadState>,
        skipDirection: Int = 1
    ) {
        if (songs.isEmpty()) return
        songs.forEach { knownSongsById[it.id] = it }

        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        activeQueue = songs
        activeIndex = safeIndex
        latestDownloadStates = downloadStates
        hasRetriedLowerBitrate = false

        val initialTarget = songs[safeIndex]

        retryJob?.cancel()
        playQueueJob?.cancel()
        playQueueJob = scope.launch {
            _currentPositionMs.value = 0L
            _state.value = _state.value.copy(
                currentSong = initialTarget,
                queue = songs,
                isBuffering = true,
                durationMs = initialTarget.duration,
                playbackError = null,
                lastSkipDirection = skipDirection
            )

            // 1. Check Room DB for offline local file first (Smart Playback Switching)
            val downloadedRecord = withContext(Dispatchers.IO) {
                songDao.getDownloadedSongById(initialTarget.id)
            }
            val localFile = downloadedRecord?.localFilePath?.takeIf { it.isNotBlank() }?.let(::File)
            val isOfflineReady = localFile != null && localFile.exists()

            // 2. Always resolve a fresh live CDN stream URL right before playback if streaming
            val resolvedSong = if (isOfflineReady && downloadedRecord != null) {
                downloadedRecord.toSong().copy(
                    genre = initialTarget.genre,
                    isTrending = initialTarget.isTrending
                )
            } else {
                val freshUrl = StreamUrlResolver.resolveStreamUrl(
                    song = initialTarget,
                    preferredBitrate = 320
                )
                if (freshUrl.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        playbackError = "STREAM_RESOLUTION_FAILED: Could not obtain live audio URL for ${initialTarget.title}"
                    )
                    return@launch
                }
                initialTarget.copy(
                    streamUrl = freshUrl,
                    isDownloaded = false
                )
            }

            knownSongsById[resolvedSong.id] = resolvedSong
            val updatedQueue = songs.mapIndexed { idx, s ->
                if (idx == safeIndex) resolvedSong else s
            }
            activeQueue = updatedQueue

            _state.value = _state.value.copy(
                currentSong = resolvedSong,
                queue = updatedQueue,
                isBuffering = !resolvedSong.isDownloaded,
                durationMs = resolvedSong.duration
            )

            val mediaItem = withContext(Dispatchers.Default) {
                buildMediaItem(resolvedSong, downloadedRecord, downloadStates[resolvedSong.id])
            }

            val action: (MediaController) -> Unit = { ctrl ->
                ctrl.setMediaItem(mediaItem)
                ctrl.prepare()
                ctrl.play()
            }

            val ctrl = controller
            if (ctrl != null) {
                action(ctrl)
            } else {
                pendingPlayAction = action
                connect()
            }
        }
    }

    fun togglePlayPause(downloadStates: Map<String, DownloadState>) {
        latestDownloadStates = downloadStates
        val ctrl = controller
        if (ctrl == null || ctrl.mediaItemCount == 0) {
            val target = _state.value.currentSong ?: return
            val queue = activeQueue.ifEmpty { _state.value.queue.ifEmpty { listOf(target) } }
            val index = queue.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
            playQueue(queue, index, downloadStates)
            return
        }
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            if (ctrl.playbackState == Player.STATE_IDLE || ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.prepare()
            }
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        _currentPositionMs.value = clamped
        controller?.seekTo(clamped)
    }

    fun skipToNext() {
        val queue = activeQueue.ifEmpty { _state.value.queue }
        if (queue.isEmpty()) return

        val nextIndex = when {
            _state.value.shuffleEnabled && queue.size > 1 -> {
                val candidates = queue.indices.filter { it != activeIndex }
                candidates.random()
            }
            activeIndex < queue.lastIndex -> activeIndex + 1
            else -> 0
        }
        playQueue(queue, nextIndex, latestDownloadStates, skipDirection = 1)
    }

    fun skipToPrevious() {
        val ctrl = controller
        if (ctrl != null && ctrl.currentPosition > 3_000L) {
            ctrl.seekTo(0L)
            _currentPositionMs.value = 0L
            return
        }
        val queue = activeQueue.ifEmpty { _state.value.queue }
        if (queue.isEmpty()) {
            ctrl?.seekTo(0L)
            return
        }
        val prevIndex = if (activeIndex > 0) activeIndex - 1 else queue.lastIndex
        playQueue(queue, prevIndex, latestDownloadStates, skipDirection = -1)
    }

    fun toggleShuffle() {
        val ctrl = controller
        val next = !_state.value.shuffleEnabled
        _state.value = _state.value.copy(shuffleEnabled = next)
        ctrl?.shuffleModeEnabled = next
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = next)
        controller?.repeatMode = when (next) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(playbackError = null)
    }

    fun playNext(song: Song) {
        knownSongsById[song.id] = song
        val currentQueue = activeQueue.ifEmpty { _state.value.queue }.toMutableList()
        if (currentQueue.isEmpty()) {
            activeQueue = listOf(song)
            activeIndex = 0
            _state.value = _state.value.copy(queue = activeQueue)
            return
        }
        val existingIdx = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIdx == activeIndex) return
        if (existingIdx >= 0) {
            currentQueue.removeAt(existingIdx)
            if (existingIdx < activeIndex) {
                activeIndex = (activeIndex - 1).coerceAtLeast(0)
            }
        }
        val insertIndex = (activeIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIndex, song)
        activeQueue = currentQueue.toList()
        _state.value = _state.value.copy(queue = activeQueue)
    }

    fun addToQueue(song: Song) {
        knownSongsById[song.id] = song
        val currentQueue = activeQueue.ifEmpty { _state.value.queue }.toMutableList()
        val existingIdx = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIdx == activeIndex && currentQueue.isNotEmpty()) return
        if (existingIdx >= 0) {
            currentQueue.removeAt(existingIdx)
            if (existingIdx < activeIndex) {
                activeIndex = (activeIndex - 1).coerceAtLeast(0)
            }
        }
        currentQueue.add(song)
        activeQueue = currentQueue.toList()
        _state.value = _state.value.copy(queue = activeQueue)
    }

    fun setSleepTimerMinutes(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        val totalMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (isActive && remaining > 0L) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = if (remaining > 0L) remaining else null
            }
            if (isActive) {
                controller?.pause()
                _sleepTimerRemainingMs.value = null
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        cancelSleepTimer()
        _sleepTimerEndOfTrack.value = true
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
        _sleepTimerEndOfTrack.value = false
    }

    private fun buildMediaItem(
        song: Song,
        roomEntity: DownloadedSongEntity?,
        downloadState: DownloadState?
    ): MediaItem {
        val roomFile = roomEntity?.localFilePath?.takeIf { it.isNotBlank() }?.let(::File)
        val stateFile = (downloadState as? DownloadState.Downloaded)?.localPath?.let(::File)

        val uri = when {
            roomFile != null && roomFile.exists() -> Uri.fromFile(roomFile)
            stateFile != null && stateFile.exists() -> Uri.fromFile(stateFile)
            else -> Uri.parse(song.streamUrl)
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setIsPlayable(true)

        val localArtFile = roomEntity?.localArtworkPath?.takeIf { it.isNotBlank() }?.let(::File)
        val localArtBytes = when {
            localArtFile != null && localArtFile.exists() ->
                runCatching { localArtFile.readBytes() }.getOrNull()
            song.fallbackArtworkRes != null ->
                getArtworkBytes(song.fallbackArtworkRes)
            else -> null
        }

        if (localArtBytes != null) {
            metadataBuilder.setArtworkData(localArtBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        } else if (song.artworkUrl.startsWith("http", ignoreCase = true)) {
            metadataBuilder.setArtworkUri(Uri.parse(song.artworkUrl))
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun getArtworkBytes(artworkRes: Int): ByteArray? {
        return artworkBytesCache.getOrPut(artworkRes) {
            runCatching {
                context.resources.openRawResource(artworkRes).use { it.readBytes() }
            }.getOrNull() ?: return null
        }
    }

    private fun startPositionPolling() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { ctrl ->
                    _currentPositionMs.value = ctrl.currentPosition.coerceAtLeast(0L)
                    val ctrlDuration = ctrl.duration
                    if (ctrlDuration > 0L && ctrlDuration != _state.value.durationMs) {
                        _state.value = _state.value.copy(durationMs = ctrlDuration)
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    fun release() {
        playQueueJob?.cancel()
        retryJob?.cancel()
        sleepTimerJob?.cancel()
        stopPositionPolling()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    companion object {
        private const val TAG = "SonoraPlayback"
    }
}
