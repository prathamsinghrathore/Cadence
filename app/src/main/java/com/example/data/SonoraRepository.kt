package com.example.data

import com.example.data.lyrics.LrcParser
import com.example.data.remote.AudiusApiService
import com.example.data.remote.MusicApiService
import com.example.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.File

class SonoraRepository(
    private val songDao: SongDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val apiService: MusicApiService,
    private val downloadManager: OfflineDownloadManager,
    private val audiusApiService: AudiusApiService = NetworkModule.audiusApiService
) {
    val genres: List<String> = listOf("All", "Bollywood", "Punjabi", "English")

    companion object {
        // 25+ rotating curated queries & moods across Bollywood, Punjabi, and English
        // so every app launch and pull-to-refresh surfaces fresh, varied mixes.
        private val BOLLYWOOD_QUERY_POOL = listOf(
            "Latest Bollywood Hits 2026",
            "Arijit Singh Romantic Hits",
            "Pritam Hit Songs",
            "Bollywood Chill Lo-Fi",
            "Bollywood Party Anthems",
            "Shreya Ghoshal Melodies",
            "Atif Aslam Best Songs",
            "AR Rahman Classics",
            "Bollywood Trending 2025"
        )

        private val PUNJABI_QUERY_POOL = listOf(
            "Trending Punjabi Party",
            "Diljit Dosanjh Hits",
            "Desi Hip Hop KR\$NA",
            "AP Dhillon Top Songs",
            "Karan Aujla Hits",
            "Shubh Punjabi Songs",
            "Sidhu Moose Wala Hits",
            "Punjabi Romantic Chill"
        )

        private val ENGLISH_QUERY_POOL = listOf(
            "Top Global Pop Hits",
            "The Weeknd Best Songs",
            "Billie Eilish Hits",
            "Taylor Swift Top Tracks",
            "Dua Lipa Dance Hits",
            "Coldplay Best Songs",
            "Global Chill R&B Hits",
            "Midnight Synthwave Pop"
        )
    }

    private val lastUsedQueries = mutableSetOf<String>()

    val downloadError: Flow<String?> = downloadManager.downloadError

    private val _homeFeedSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _smartRecommendedSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _isHomeLoading = MutableStateFlow(true)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

    private val _isHomeRefreshing = MutableStateFlow(false)
    val isHomeRefreshing: StateFlow<Boolean> = _isHomeRefreshing.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    val downloadedSongsMap: Flow<Map<String, DownloadedSongEntity>> =
        songDao.observeDownloadedSongs().map { entities ->
            entities.filter { File(it.localFilePath).exists() }
                .associateBy { it.id }
        }

    private val cachedSongsMap: Flow<Map<String, CachedSongEntity>> =
        songDao.observeCachedSongs().map { entities ->
            entities.associateBy { it.id }
        }

    val mostListenedSongs: Flow<List<MostListenedSong>> = combine(
        listeningHistoryDao.getMostListenedSongs(limit = 10),
        downloadedSongsMap
    ) { historyEntities, downloaded ->
        historyEntities.map { entity ->
            val dl = downloaded[entity.songId]
            val song = dl?.toSong()?.copy(genre = entity.genre)
                ?: entity.toSong(isDownloaded = false)
            MostListenedSong(
                song = song,
                playCount = entity.playCount,
                lastPlayedTimestamp = entity.lastPlayedTimestamp
            )
        }
    }

    val topArtists: Flow<List<String>> = listeningHistoryDao.getTopArtists(limit = 3).map { artists ->
        artists
            .map { LrcParser.extractPrimaryArtist(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val homeSongs: Flow<List<Song>> = combine(
        _homeFeedSongs,
        cachedSongsMap,
        downloadedSongsMap
    ) { remoteFeed, cached, downloaded ->
        val source = if (remoteFeed.isNotEmpty()) {
            remoteFeed
        } else {
            cached.values.shuffled().mapIndexed { idx, entity ->
                entity.toSong(isDownloaded = downloaded.containsKey(entity.id))
                    .copy(isTrending = idx < 8)
            }
        }
        source.map { song ->
            val dl = downloaded[song.id]
            if (dl != null) {
                dl.toSong().copy(genre = song.genre, isTrending = song.isTrending)
            } else {
                song.copy(isDownloaded = false)
            }
        }
    }

    val smartRecommendedSongs: Flow<List<Song>> = combine(
        _smartRecommendedSongs,
        downloadedSongsMap
    ) { artistRecs, downloaded ->
        artistRecs.map { song ->
            val dl = downloaded[song.id]
            if (dl != null) {
                dl.toSong().copy(genre = song.genre, isTrending = false)
            } else {
                song.copy(isDownloaded = false)
            }
        }
    }

    val allKnownSongs: Flow<List<Song>> = combine(
        _homeFeedSongs,
        _smartRecommendedSongs,
        cachedSongsMap,
        downloadedSongsMap,
        mostListenedSongs
    ) { remoteFeed, smartRecs, cached, downloaded, mostListened ->
        val downloadedSongs = downloaded.values.map { it.toSong() }
        val cachedSongs = cached.values.map { entity ->
            entity.toSong(isDownloaded = downloaded.containsKey(entity.id))
        }
        val historySongs = mostListened.map { it.song }
        (remoteFeed.map { song ->
            downloaded[song.id]?.toSong()?.copy(genre = song.genre, isTrending = song.isTrending)
                ?: song.copy(isDownloaded = false)
        } + smartRecs + historySongs + downloadedSongs + cachedSongs).distinctBy { it.id }
    }

    val downloadStates: Flow<Map<String, DownloadState>> = combine(
        allKnownSongs,
        downloadedSongsMap,
        downloadManager.activeDownloads
    ) { knownSongs, downloaded, active ->
        val result = mutableMapOf<String, DownloadState>()
        val ids = (knownSongs.map { it.id } + downloaded.keys + active.keys).toSet()
        ids.forEach { songId ->
            val progress = active[songId]
            val local = downloaded[songId]
            result[songId] = when {
                progress != null -> DownloadState.Downloading(progress)
                local != null -> DownloadState.Downloaded(local.localFilePath, local.fileSizeBytes)
                else -> DownloadState.NotDownloaded
            }
        }
        result
    }

    val favoriteTrackIds: Flow<Set<String>> =
        songDao.observeFavoriteTrackIds().map { it.toSet() }

    val recentSearches: Flow<List<String>> =
        songDao.observeRecentSearches().map { list -> list.map { it.query } }

    val playlists: Flow<List<PlaylistUiModel>> = combine(
        songDao.observePlaylists(),
        songDao.observeAllPlaylistTrackRefs(),
        allKnownSongs
    ) { playlists, refs, knownSongs ->
        val songMap = knownSongs.associateBy { it.id }
        val grouped = refs.groupBy { it.playlistId }
        playlists.map { entity ->
            val songs = grouped[entity.id]
                ?.mapNotNull { ref -> songMap[ref.trackId] }
                ?: emptyList()
            PlaylistUiModel(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                songs = songs,
                createdAt = entity.createdAt
            )
        }
    }

    /**
     * Picks 2–3 fresh randomized queries for the active genre chip, fetches them in parallel,
     * blends fresh tracks from the user's top listened artists (when available), deduplicates
     * by `song.id`, and shuffles so every launch & pull-to-refresh feels brand-new.
     */
    suspend fun refreshHomeCatalog(
        activeGenre: String = "All",
        isPullToRefresh: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (isPullToRefresh) {
            _isHomeRefreshing.value = true
        } else {
            _isHomeLoading.value = true
        }
        _homeError.value = null

        try {
            val downloadedIds = downloadedSongsMap.first().keys
            val selectedQueries = pickFreshQueriesForGenre(activeGenre)
            val topArtistNames = runCatching {
                listeningHistoryDao.getTopArtistsOnce(limit = 3)
                    .map { LrcParser.extractPrimaryArtist(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.getOrDefault(emptyList())

            val (freshCatalog, artistRecommendations) = supervisorScope {
                val categoryDeferreds = selectedQueries.map { (query, genreLabel) ->
                    async {
                        fetchJioSaavnSongs(
                            query = query,
                            limit = 12,
                            downloadedIds = downloadedIds,
                            genreOverride = genreLabel,
                            trendingCount = 0
                        )
                    }
                }

                // Smart Recommendations: fetch fresh tracks for up to 2 top listened artists in parallel
                val artistDeferreds = topArtistNames.take(2).map { artist ->
                    async {
                        fetchJioSaavnSongs(
                            query = "$artist Hit Songs",
                            limit = 8,
                            downloadedIds = downloadedIds,
                            genreOverride = null,
                            trendingCount = 0
                        )
                    }
                }

                val categoryResults = categoryDeferreds.awaitAll().flatten()
                    .distinctBy { it.id }
                    .shuffled()
                    .mapIndexed { idx, song ->
                        song.copy(isTrending = idx < 8)
                    }

                val artistResults = artistDeferreds.awaitAll().flatten()
                    .distinctBy { it.id }
                    .shuffled()

                categoryResults to artistResults
            }

            if (artistRecommendations.isNotEmpty()) {
                _smartRecommendedSongs.value = artistRecommendations
                artistRecommendations.take(8).forEach { song ->
                    songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
                }
            }

            if (freshCatalog.isNotEmpty()) {
                val mergedFeed = if (activeGenre.equals("All", ignoreCase = true) || isPullToRefresh) {
                    freshCatalog
                } else {
                    (freshCatalog + _homeFeedSongs.value.filterNot {
                        it.genre.equals(activeGenre, ignoreCase = true)
                    }).distinctBy { it.id }
                }
                _homeFeedSongs.value = mergedFeed
                freshCatalog.take(18).forEach { song ->
                    songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
                }
                return@withContext
            }

            // 2. Automatic Fallback: Audius Open Music API (shuffled for variety)
            val audiusTrending = fetchAudiusTrending(downloadedIds)
                .shuffled()
                .mapIndexed { idx, song -> song.copy(isTrending = idx < 8) }
            if (audiusTrending.isNotEmpty()) {
                _homeFeedSongs.value = audiusTrending
                audiusTrending.take(15).forEach { song ->
                    songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
                }
                return@withContext
            }

            // 3. Offline cache check
            val cachedExisting = cachedSongsMap.first()
            if (cachedExisting.isEmpty() && downloadedIds.isEmpty()) {
                _homeError.value = "Unable to load songs. Check your internet connection."
            }
        } catch (e: Exception) {
            val audiusFallback = runCatching {
                fetchAudiusTrending(downloadedSongsMap.first().keys)
                    .shuffled()
                    .mapIndexed { idx, song -> song.copy(isTrending = idx < 8) }
            }.getOrDefault(emptyList())

            if (audiusFallback.isNotEmpty()) {
                _homeFeedSongs.value = audiusFallback
                audiusFallback.take(15).forEach { song ->
                    songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
                }
            } else if (_homeFeedSongs.value.isEmpty()) {
                _homeError.value = e.localizedMessage ?: "Unable to load songs. Check your connection."
            }
        } finally {
            _isHomeLoading.value = false
            _isHomeRefreshing.value = false
        }
    }

    suspend fun loadGenreFeedIfNeeded(genre: String) = withContext(Dispatchers.IO) {
        if (genre.equals("All", ignoreCase = true)) return@withContext
        val currentForGenre = _homeFeedSongs.value.count { it.genre.equals(genre, ignoreCase = true) }
        if (currentForGenre >= 10) return@withContext

        _isHomeLoading.value = true
        try {
            val downloadedIds = downloadedSongsMap.first().keys
            val selectedQueries = pickFreshQueriesForGenre(genre)
            val freshGenreSongs = supervisorScope {
                selectedQueries.map { (query, genreLabel) ->
                    async {
                        fetchJioSaavnSongs(
                            query = query,
                            limit = 12,
                            downloadedIds = downloadedIds,
                            genreOverride = genreLabel,
                            trendingCount = 0
                        )
                    }
                }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
                    .shuffled()
                    .mapIndexed { idx, song -> song.copy(isTrending = idx < 6) }
            }

            val resolved = if (freshGenreSongs.isNotEmpty()) {
                freshGenreSongs
            } else {
                fetchAudiusSearch(genre, downloadedIds)
                    .shuffled()
                    .mapIndexed { idx, it -> it.copy(genre = genre, isTrending = idx < 6) }
            }
            if (resolved.isNotEmpty()) {
                _homeFeedSongs.value = (resolved + _homeFeedSongs.value).distinctBy { it.id }
                resolved.take(12).forEach { songDao.upsertCachedSong(CachedSongEntity.fromSong(it)) }
            }
        } finally {
            _isHomeLoading.value = false
        }
    }

    /**
     * Picks 2–3 distinct randomized queries for the requested genre while avoiding repeating
     * queries used in the immediately preceding refresh batch.
     */
    private fun pickFreshQueriesForGenre(genre: String): List<Pair<String, String>> {
        val picked: List<Pair<String, String>> = when {
            genre.equals("Bollywood", ignoreCase = true) ->
                pickDistinctFromPool(BOLLYWOOD_QUERY_POOL, count = 3).map { it to "Bollywood" }
            genre.equals("Punjabi", ignoreCase = true) ->
                pickDistinctFromPool(PUNJABI_QUERY_POOL, count = 3).map { it to "Punjabi" }
            genre.equals("English", ignoreCase = true) ->
                pickDistinctFromPool(ENGLISH_QUERY_POOL, count = 3).map { it to "English" }
            else -> listOf(
                pickDistinctFromPool(BOLLYWOOD_QUERY_POOL, count = 1).first() to "Bollywood",
                pickDistinctFromPool(PUNJABI_QUERY_POOL, count = 1).first() to "Punjabi",
                pickDistinctFromPool(ENGLISH_QUERY_POOL, count = 1).first() to "English"
            )
        }
        synchronized(lastUsedQueries) {
            if (lastUsedQueries.size > 15) lastUsedQueries.clear()
            lastUsedQueries.addAll(picked.map { it.first })
        }
        return picked
    }

    private fun pickDistinctFromPool(pool: List<String>, count: Int): List<String> {
        val unused = synchronized(lastUsedQueries) {
            pool.filterNot { it in lastUsedQueries }
        }
        val primary = unused.shuffled().take(count)
        if (primary.size >= count) return primary
        val remainder = (pool - primary.toSet()).shuffled().take(count - primary.size)
        return primary + remainder
    }

    fun searchSongs(query: String): Flow<Result<List<Song>>> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val downloadedMap = downloadedSongsMap.first()
        val downloadedIds = downloadedMap.keys

        try {
            val jioSaavnMatches = fetchJioSaavnSongs(
                query = trimmed,
                limit = 25,
                downloadedIds = downloadedIds,
                genreOverride = null,
                trendingCount = 0
            ).map { song ->
                downloadedMap[song.id]?.toSong()?.copy(genre = song.genre) ?: song
            }

            if (jioSaavnMatches.isNotEmpty()) {
                jioSaavnMatches.take(10).forEach { song ->
                    songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
                }
                emit(Result.success(jioSaavnMatches))
                return@flow
            }

            val audiusMatches = fetchAudiusSearch(trimmed, downloadedIds).map { song ->
                downloadedMap[song.id]?.toSong()?.copy(genre = song.genre) ?: song
            }
            if (audiusMatches.isNotEmpty()) {
                audiusMatches.take(10).forEach { song ->
                    songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
                }
                emit(Result.success(audiusMatches))
                return@flow
            }

            emit(Result.success(emptyList()))
        } catch (e: Exception) {
            val audiusMatches = runCatching {
                fetchAudiusSearch(trimmed, downloadedIds)
            }.getOrDefault(emptyList())

            if (audiusMatches.isNotEmpty()) {
                emit(Result.success(audiusMatches))
                return@flow
            }

            val offlineMatches = downloadedMap.values
                .map { it.toSong() }
                .filter { song ->
                    song.title.contains(trimmed, ignoreCase = true) ||
                        song.artist.contains(trimmed, ignoreCase = true) ||
                        song.album.contains(trimmed, ignoreCase = true) ||
                        song.genre.contains(trimmed, ignoreCase = true)
                }

            if (offlineMatches.isNotEmpty()) {
                emit(Result.success(offlineMatches))
            } else {
                emit(Result.failure(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchJioSaavnSongs(
        query: String,
        limit: Int,
        downloadedIds: Set<String>,
        genreOverride: String?,
        trendingCount: Int
    ): List<Song> {
        val response = runCatching {
            apiService.searchSongs(query = query, limit = limit, page = 1)
        }.getOrNull() ?: return emptyList()

        return response.results.mapIndexedNotNull { idx, dto ->
            dto.toSong(
                streamUrl = "jiosaavn://${dto.id}",
                isDownloaded = downloadedIds.contains(dto.id),
                genreOverride = genreOverride,
                isTrending = idx < trendingCount
            )
        }.distinctBy { it.id }
    }

    private suspend fun fetchAudiusTrending(downloadedIds: Set<String>): List<Song> {
        val response = runCatching {
            audiusApiService.getTrendingTracks(limit = 25)
        }.getOrNull() ?: return emptyList()

        return response.data.mapIndexedNotNull { idx, dto ->
            dto.toSong(
                isDownloaded = downloadedIds.contains("audius_${dto.id}"),
                isTrending = idx < 6
            )
        }.distinctBy { it.id }
    }

    private suspend fun fetchAudiusSearch(
        query: String,
        downloadedIds: Set<String>
    ): List<Song> {
        val response = runCatching {
            audiusApiService.searchTracks(query = query, limit = 25)
        }.getOrNull() ?: return emptyList()

        return response.data.mapNotNull { dto ->
            dto.toSong(isDownloaded = downloadedIds.contains("audius_${dto.id}"))
        }.distinctBy { it.id }
    }

    fun startDownload(song: Song) = downloadManager.startDownload(song)

    fun removeDownload(songId: String) = downloadManager.removeDownload(songId)

    fun clearDownloadError() = downloadManager.clearError()

    suspend fun toggleFavorite(songId: String, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            songDao.removeFavorite(songId)
        } else {
            songDao.addFavorite(FavoriteTrackEntity(trackId = songId))
        }
    }

    suspend fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            songDao.upsertRecentSearch(RecentSearchEntity(query = trimmed))
        }
    }

    suspend fun deleteRecentSearch(query: String) = songDao.deleteRecentSearch(query)

    suspend fun clearRecentSearches() = songDao.clearRecentSearches()

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return songDao.insertPlaylist(
            PlaylistEntity(name = trimmed, description = description.trim())
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        songDao.deletePlaylistTrackRefsForPlaylist(playlistId)
        songDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, song: Song) {
        songDao.upsertCachedSong(CachedSongEntity.fromSong(song))
        songDao.insertPlaylistTrackRef(
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = song.id)
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, songId: String) {
        songDao.removeTrackFromPlaylist(playlistId, songId)
    }
}
