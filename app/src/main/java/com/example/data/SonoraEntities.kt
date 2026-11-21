package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artworkUrl: String,
    val localArtworkPath: String,
    val streamUrl: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val genre: String,
    val audioQualityLabel: String,
    val fallbackArtworkRes: Int? = null,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        val resolvedArtwork = if (localArtworkPath.isNotBlank() && File(localArtworkPath).exists()) {
            File(localArtworkPath).toURI().toString()
        } else {
            artworkUrl
        }
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            artworkUrl = resolvedArtwork,
            streamUrl = streamUrl,
            isDownloaded = true,
            genre = genre,
            audioQualityLabel = audioQualityLabel,
            fallbackArtworkRes = fallbackArtworkRes
        )
    }
}

@Entity(tableName = "cached_songs")
data class CachedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val genre: String,
    val audioQualityLabel: String,
    val fallbackArtworkRes: Int? = null
) {
    fun toSong(isDownloaded: Boolean): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        isDownloaded = isDownloaded,
        genre = genre,
        audioQualityLabel = audioQualityLabel,
        fallbackArtworkRes = fallbackArtworkRes
    )

    companion object {
        fun fromSong(song: Song): CachedSongEntity = CachedSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            genre = song.genre,
            audioQualityLabel = song.audioQualityLabel,
            fallbackArtworkRes = song.fallbackArtworkRes
        )
    }
}

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val songId: String,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val isInstrumental: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val playCount: Int = 1,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val genre: String = "Bollywood",
    val audioQualityLabel: String = "320 kbps AAC",
    val fallbackArtworkRes: Int? = null
) {
    fun toSong(isDownloaded: Boolean = false): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        isDownloaded = isDownloaded,
        genre = genre,
        audioQualityLabel = audioQualityLabel,
        fallbackArtworkRes = fallbackArtworkRes
    )

    companion object {
        fun fromSong(
            song: Song,
            playCount: Int = 1,
            timestamp: Long = System.currentTimeMillis()
        ): ListeningHistoryEntity = ListeningHistoryEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            playCount = playCount,
            lastPlayedTimestamp = timestamp,
            genre = song.genre,
            audioQualityLabel = song.audioQualityLabel,
            fallbackArtworkRes = song.fallbackArtworkRes
        )
    }
}

@androidx.compose.runtime.Immutable
data class MostListenedSong(
    val song: Song,
    val playCount: Int,
    val lastPlayedTimestamp: Long
)
