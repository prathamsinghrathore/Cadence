package com.example.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadState
import com.example.data.RepeatMode
import com.example.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackController = SonoraPlaybackController(application, viewModelScope)
    private val listeningHistoryDao = AppDatabase.getInstance(application).listeningHistoryDao()

    val playbackState: StateFlow<PlaybackStateSnapshot> = playbackController.state

    val currentSong: StateFlow<Song?> = playbackController.state
        .map { it.currentSong }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = playbackController.state.value.currentSong
        )

    val isPlaying: StateFlow<Boolean> = playbackController.state
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val currentPosition: StateFlow<Long> = playbackController.currentPositionMs

    val duration: StateFlow<Long> = playbackController.state
        .map { it.durationMs }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = playbackController.state.value.durationMs
        )

    val playbackQueue: StateFlow<List<Song>> = playbackController.state
        .map { it.queue }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val shuffleEnabled: StateFlow<Boolean> = playbackController.state
        .map { it.shuffleEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val repeatMode: StateFlow<RepeatMode> = playbackController.state
        .map { it.repeatMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RepeatMode.OFF
        )

    val playbackError: StateFlow<String?> = playbackController.state
        .map { it.playbackError }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val sleepTimerRemainingMs: StateFlow<Long?> = playbackController.sleepTimerRemainingMs
    val sleepTimerEndOfTrack: StateFlow<Boolean> = playbackController.sleepTimerEndOfTrack

    private var latestDownloadStates: Map<String, DownloadState> = emptyMap()

    // Tracks the song ID already recorded for the current playback session to prevent double-counting
    @Volatile
    private var lastRecordedSongId: String? = null

    init {
        playbackController.connect()
        observeListeningDurationForHistory()
    }

    /**
     * Records a play automatically when playback advances to a new track (e.g. via Next/Prev or
     * natural queue progression) and the user listens for at least 10 seconds.
     */
    private fun observeListeningDurationForHistory() {
        viewModelScope.launch {
            combine(
                playbackController.state.map { it.currentSong to it.isPlaying }.distinctUntilChanged(),
                playbackController.currentPositionMs.map { it >= 10_000L }.distinctUntilChanged()
            ) { (song, playing), reachedTenSeconds ->
                Triple(song, playing, reachedTenSeconds)
            }.collectLatest { (song, playing, reachedTenSeconds) ->
                if (song == null) {
                    lastRecordedSongId = null
                    return@collectLatest
                }
                if (song.id != lastRecordedSongId && playing && reachedTenSeconds) {
                    recordSongPlayInternal(song)
                }
            }
        }
    }

    private fun recordSongPlayInternal(song: Song) {
        lastRecordedSongId = song.id
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                listeningHistoryDao.recordSongPlay(song)
            }
        }
    }

    fun updateDownloadStates(downloadStates: Map<String, DownloadState>) {
        latestDownloadStates = downloadStates
    }

    fun seedQueueIfIdle(songs: List<Song>) {
        playbackController.seedQueueIfIdle(songs)
    }

    fun playSong(
        song: Song,
        queue: List<Song> = emptyList(),
        downloadStates: Map<String, DownloadState> = latestDownloadStates
    ) {
        val resolvedQueue = queue.ifEmpty { listOf(song) }
        val startIndex = resolvedQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackController.playQueue(
            songs = resolvedQueue,
            startIndex = startIndex,
            downloadStates = downloadStates
        )
        recordSongPlayInternal(song)
    }

    fun togglePlayPause(downloadStates: Map<String, DownloadState> = latestDownloadStates) {
        playbackController.togglePlayPause(downloadStates)
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun skipNext() {
        lastRecordedSongId = null
        playbackController.skipToNext()
    }

    fun skipPrevious() {
        lastRecordedSongId = null
        playbackController.skipToPrevious()
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackController.cycleRepeatMode()
    }

    fun clearError() {
        playbackController.clearError()
    }

    fun playNext(song: Song) {
        playbackController.playNext(song)
    }

    fun addToQueue(song: Song) {
        playbackController.addToQueue(song)
    }

    fun setSleepTimerMinutes(minutes: Int) {
        playbackController.setSleepTimerMinutes(minutes)
    }

    fun setSleepTimerEndOfTrack() {
        playbackController.setSleepTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
