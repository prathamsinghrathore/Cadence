package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningHistoryDao {

    @Query(
        """
        SELECT * FROM listening_history
        WHERE playCount >= 2
        ORDER BY playCount DESC, lastPlayedTimestamp DESC
        LIMIT :limit
        """
    )
    fun getMostListenedSongs(limit: Int = 10): Flow<List<ListeningHistoryEntity>>

    @Query(
        """
        SELECT artist FROM listening_history
        WHERE artist != ''
        GROUP BY artist
        ORDER BY SUM(playCount) DESC, MAX(lastPlayedTimestamp) DESC
        LIMIT :limit
        """
    )
    fun getTopArtists(limit: Int = 3): Flow<List<String>>

    @Query(
        """
        SELECT artist FROM listening_history
        WHERE artist != ''
        GROUP BY artist
        ORDER BY SUM(playCount) DESC, MAX(lastPlayedTimestamp) DESC
        LIMIT :limit
        """
    )
    suspend fun getTopArtistsOnce(limit: Int = 3): List<String>

    @Query(
        """
        UPDATE listening_history
        SET playCount = playCount + 1,
            lastPlayedTimestamp = :timestamp,
            title = :title,
            artist = :artist,
            album = :album,
            duration = :duration,
            artworkUrl = :artworkUrl,
            streamUrl = :streamUrl,
            genre = :genre
        WHERE songId = :songId
        """
    )
    suspend fun incrementPlayCount(
        songId: String,
        title: String,
        artist: String,
        album: String,
        duration: Long,
        artworkUrl: String,
        streamUrl: String,
        genre: String,
        timestamp: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(entity: ListeningHistoryEntity): Long

    /**
     * Atomically increments `playCount = playCount + 1` and refreshes `lastPlayedTimestamp`
     * if the song already exists, otherwise inserts a new history record with `playCount = 1`.
     */
    @Transaction
    suspend fun recordSongPlay(
        song: Song,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val updatedRows = incrementPlayCount(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            genre = song.genre,
            timestamp = timestamp
        )
        if (updatedRows == 0) {
            insertHistory(
                ListeningHistoryEntity.fromSong(
                    song = song,
                    playCount = 1,
                    timestamp = timestamp
                )
            )
        }
    }
}
