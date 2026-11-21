package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun observeDownloadedSongs(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs WHERE id = :songId LIMIT 1")
    suspend fun getDownloadedSongById(songId: String): DownloadedSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedSong(song: DownloadedSongEntity)

    @Query("DELETE FROM downloaded_songs WHERE id = :songId")
    suspend fun deleteDownloadedSong(songId: String)

    @Query("SELECT * FROM cached_songs")
    fun observeCachedSongs(): Flow<List<CachedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedSong(entity: CachedSongEntity)

    @Query("SELECT trackId FROM favorite_tracks")
    fun observeFavoriteTrackIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY addedAt ASC")
    fun observeAllPlaylistTrackRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTrackRefsForPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrackRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT * FROM cached_lyrics WHERE songId = :songId LIMIT 1")
    suspend fun getCachedLyrics(songId: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedLyrics(entity: CachedLyricsEntity)
}
