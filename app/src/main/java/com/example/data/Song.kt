package com.example.data

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Immutable
sealed interface DownloadState {
    @Immutable
    data object NotDownloaded : DownloadState

    @Immutable
    data class Downloading(val progress: Float) : DownloadState

    @Immutable
    data class Downloaded(val localPath: String, val fileSizeBytes: Long) : DownloadState
}

@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val isDownloaded: Boolean = false,
    val genre: String = "Music",
    val isTrending: Boolean = false,
    val audioQualityLabel: String = "320kbps AAC",
    @param:DrawableRes val fallbackArtworkRes: Int? = null
)

@Immutable
data class PlaylistUiModel(
    val id: Long,
    val name: String,
    val description: String,
    val songs: List<Song>,
    val createdAt: Long
)
