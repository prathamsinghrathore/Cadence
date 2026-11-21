package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppDatabase
import com.example.data.DownloadState
import com.example.data.MostListenedSong
import com.example.data.OfflineDownloadManager
import com.example.data.PlaylistUiModel
import com.example.data.Song
import com.example.data.SonoraRepository
import com.example.data.lyrics.LyricsRepository
import com.example.data.lyrics.LyricsUiState
import com.example.data.remote.NetworkModule
import com.example.player.PlaybackStateSnapshot
import com.example.ui.theme.AmbientPaletteColors
import com.example.ui.theme.PaletteExtractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
sealed interface SearchUiState {
    @Immutable
    data object Idle : SearchUiState

    @Immutable
    data object Loading : SearchUiState

    @Immutable
    data class Success(val songs: List<Song>) : SearchUiState

    @Immutable
    data class Error(val message: String) : SearchUiState
}

@Immutable
data class SonoraUiState(
    val homeSongs: List<Song> = emptyList(),
    val smartRecommendedSongs: List<Song> = emptyList(),
    val mostListenedSongs: List<MostListenedSong> = emptyList(),
    val topArtists: List<String> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val genres: List<String> = listOf("All", "Bollywood", "Punjabi", "English"),
    val selectedGenre: String = "All",
    val isHomeLoading: Boolean = true,
    val isHomeRefreshing: Boolean = false,
    val homeError: String? = null,
    val searchQuery: String = "",
    val searchState: SearchUiState = SearchUiState.Idle,
    val recentSearches: List<String> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val favoriteIds: Set<String> = emptySet(),
    val playlists: List<PlaylistUiModel> = emptyList(),
    val playback: PlaybackStateSnapshot = PlaybackStateSnapshot(),
    val ambientPalette: AmbientPaletteColors = AmbientPaletteColors(),
    val downloadError: String? = null,
    val homeQuote: String = ""
) {
    val filteredHomeSongs: List<Song>
        get() = if (selectedGenre == "All") {
            homeSongs
        } else {
            homeSongs.filter { it.genre.equals(selectedGenre, ignoreCase = true) }
        }

    val trendingSongs: List<Song>
        get() {
            val base = filteredHomeSongs.ifEmpty { homeSongs }
            return base.filter { it.isTrending }.ifEmpty { base.take(8) }
        }

    /**
     * Blends fresh tracks from the user's top listened artists into Recommended For You
     * while avoiding duplicating the Featured Mix hero track at the top.
     */
    val recommendedFeedSongs: List<Song>
        get() {
            val base = filteredHomeSongs
            val featuredId = trendingSongs.firstOrNull()?.id
            val blended = if (selectedGenre == "All" && smartRecommendedSongs.isNotEmpty()) {
                val interleaved = mutableListOf<Song>()
                val smartList = smartRecommendedSongs
                val discoveryList = base.filterNot { it.id == featuredId }
                val maxLen = maxOf(smartList.size, discoveryList.size)
                for (i in 0 until maxLen) {
                    if (i < smartList.size) interleaved.add(smartList[i])
                    if (i < discoveryList.size) interleaved.add(discoveryList[i])
                }
                interleaved.distinctBy { it.id }
            } else {
                base.filterNot { it.id == featuredId }.ifEmpty { base }
            }
            return blended
        }

    val downloadedSongs: List<Song>
        get() = allSongs.filter { downloadStates[it.id] is DownloadState.Downloaded || it.isDownloaded }

    val totalOfflineBytes: Long
        get() = downloadStates.values
            .filterIsInstance<DownloadState.Downloaded>()
            .sumOf { it.fileSizeBytes }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val songDao = database.songDao()
    private val listeningHistoryDao = database.listeningHistoryDao()
    private val downloadManager = OfflineDownloadManager(
        context = application,
        songDao = songDao,
        scope = viewModelScope
    )
    private val repository = SonoraRepository(
        songDao = songDao,
        listeningHistoryDao = listeningHistoryDao,
        apiService = NetworkModule.musicApiService,
        downloadManager = downloadManager
    )
    private val lyricsRepository = LyricsRepository(
        songDao = songDao,
        lrcLibApiService = NetworkModule.lrcLibApiService
    )

    private val selectedGenre = MutableStateFlow("All")
    private val searchQuery = MutableStateFlow("")
    private val ambientPalette = MutableStateFlow(AmbientPaletteColors())

    private val sessionHomeQuote: String = pickLaunchMusicQuote(application)

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    private var lyricsJob: Job? = null

    init {
        refreshHomeCatalog(isPullToRefresh = false)
    }

    private fun pickLaunchMusicQuote(application: Application): String {
        val quotes = application.resources.getStringArray(R.array.home_music_quotes)
        if (quotes.isEmpty()) return ""
        if (quotes.size == 1) return quotes[0]

        val prefs = application.getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
        val lastIndex = prefs.getInt(KEY_LAST_QUOTE_INDEX, -1)
        val candidateIndices = quotes.indices.filter { it != lastIndex }
        val chosenIndex = candidateIndices.random()
        prefs.edit().putInt(KEY_LAST_QUOTE_INDEX, chosenIndex).apply()
        return quotes[chosenIndex]
    }

    private val debouncedSearchState: StateFlow<SearchUiState> = searchQuery
        .map { it.trim() }
        .debounce(400L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                flowOf(SearchUiState.Idle)
            } else {
                repository.searchSongs(query)
                    .map { result ->
                        result.fold(
                            onSuccess = { songs -> SearchUiState.Success(songs) },
                            onFailure = { err ->
                                SearchUiState.Error(
                                    err.localizedMessage ?: "Unable to fetch songs. Check your connection."
                                )
                            }
                        )
                    }
                    .onStart { emit(SearchUiState.Loading) }
                    .catch { err ->
                        emit(
                            SearchUiState.Error(
                                err.localizedMessage ?: "Unable to fetch songs. Check your connection."
                            )
                        )
                    }
            }
        }
        .catch { err ->
            emit(
                SearchUiState.Error(
                    err.localizedMessage ?: "Search failed unexpectedly."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState.Idle
        )

    private val filterInputs = combine(
        selectedGenre,
        searchQuery,
        debouncedSearchState,
        ambientPalette,
        repository.isHomeLoading,
        repository.isHomeRefreshing,
        repository.homeError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        FilterSnapshot(
            genre = values[0] as String,
            query = values[1] as String,
            searchState = values[2] as SearchUiState,
            palette = values[3] as AmbientPaletteColors,
            isHomeLoading = values[4] as Boolean,
            isHomeRefreshing = values[5] as Boolean,
            homeError = values[6] as String?
        )
    }

    private val historyInputs = combine(
        repository.mostListenedSongs,
        repository.topArtists,
        repository.smartRecommendedSongs
    ) { mostListened, topArtists, smartRecs ->
        HistorySnapshot(
            mostListenedSongs = mostListened,
            topArtists = topArtists,
            smartRecommendedSongs = smartRecs
        )
    }

    private val libraryInputs = combine(
        repository.homeSongs,
        repository.allKnownSongs,
        repository.recentSearches,
        repository.downloadStates,
        repository.favoriteTrackIds,
        repository.playlists,
        repository.downloadError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LibrarySnapshot(
            homeSongs = values[0] as List<Song>,
            allSongs = values[1] as List<Song>,
            recentSearches = values[2] as List<String>,
            downloadStates = values[3] as Map<String, DownloadState>,
            favoriteIds = values[4] as Set<String>,
            playlists = values[5] as List<PlaylistUiModel>,
            downloadError = values[6] as String?
        )
    }

    val uiState: StateFlow<SonoraUiState> = combine(
        filterInputs,
        libraryInputs,
        historyInputs
    ) { filters, library, history ->
        val updatedSearchState = when (val s = filters.searchState) {
            is SearchUiState.Success -> {
                SearchUiState.Success(
                    s.songs.map { song ->
                        song.copy(isDownloaded = library.downloadStates[song.id] is DownloadState.Downloaded)
                    }
                )
            }
            else -> s
        }
        SonoraUiState(
            homeSongs = library.homeSongs,
            smartRecommendedSongs = history.smartRecommendedSongs,
            mostListenedSongs = history.mostListenedSongs,
            topArtists = history.topArtists,
            allSongs = library.allSongs,
            genres = repository.genres,
            selectedGenre = filters.genre,
            isHomeLoading = filters.isHomeLoading,
            isHomeRefreshing = filters.isHomeRefreshing,
            homeError = filters.homeError,
            searchQuery = filters.query,
            searchState = updatedSearchState,
            recentSearches = library.recentSearches,
            downloadStates = library.downloadStates,
            favoriteIds = library.favoriteIds,
            playlists = library.playlists,
            ambientPalette = filters.palette,
            downloadError = library.downloadError,
            homeQuote = sessionHomeQuote
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SonoraUiState(
            genres = repository.genres,
            homeQuote = sessionHomeQuote
        )
    )

    fun refreshHomeCatalog(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.refreshHomeCatalog(
                activeGenre = selectedGenre.value,
                isPullToRefresh = isPullToRefresh
            )
        }
    }

    fun extractPaletteForSong(song: Song) {
        val cacheKey = PaletteExtractor.cacheKeyFor(song)
        val cached = PaletteExtractor.getCached(cacheKey)
        if (cached != null) {
            if (ambientPalette.value != cached) {
                ambientPalette.value = cached
            }
            return
        }
        viewModelScope.launch {
            val extracted = PaletteExtractor.extractFromSong(getApplication(), song)
            if (ambientPalette.value != extracted) {
                ambientPalette.value = extracted
            }
        }
    }

    fun loadLyricsForSong(song: Song, forceRefresh: Boolean = false) {
        val current = _lyricsState.value
        val alreadyLoadedForSong = when (current) {
            is LyricsUiState.Synced -> current.songId == song.id
            is LyricsUiState.Plain -> current.songId == song.id
            is LyricsUiState.Instrumental -> current.songId == song.id
            is LyricsUiState.Loading -> current.songId == song.id
            else -> false
        }
        if (!forceRefresh && alreadyLoadedForSong) return

        lyricsJob?.cancel()
        _lyricsState.value = LyricsUiState.Loading(song.id)
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepository.getLyricsForSong(song, forceRefresh = forceRefresh)
            _lyricsState.value = result
        }
    }

    fun selectGenre(genre: String) {
        selectedGenre.value = genre
        viewModelScope.launch {
            repository.loadGenreFeedIfNeeded(genre)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun submitSearch(query: String = searchQuery.value) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            searchQuery.value = trimmed
            viewModelScope.launch {
                repository.saveRecentSearch(trimmed)
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    fun toggleDownload(song: Song) {
        when (uiState.value.downloadStates[song.id]) {
            is DownloadState.Downloaded -> repository.removeDownload(song.id)
            is DownloadState.Downloading -> repository.removeDownload(song.id)
            else -> repository.startDownload(song)
        }
    }

    fun toggleFavorite(songId: String) {
        val isFav = uiState.value.favoriteIds.contains(songId)
        viewModelScope.launch {
            repository.toggleFavorite(songId, isFav)
        }
    }

    fun createPlaylist(name: String, description: String = "", initialSong: Song? = null) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name, description)
            if (id > 0L && initialSong != null) {
                repository.addTrackToPlaylist(id, initialSong)
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, song)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, songId)
        }
    }

    fun clearDownloadError() {
        repository.clearDownloadError()
    }

    private data class FilterSnapshot(
        val genre: String,
        val query: String,
        val searchState: SearchUiState,
        val palette: AmbientPaletteColors,
        val isHomeLoading: Boolean,
        val isHomeRefreshing: Boolean,
        val homeError: String?
    )

    private data class HistorySnapshot(
        val mostListenedSongs: List<MostListenedSong>,
        val topArtists: List<String>,
        val smartRecommendedSongs: List<Song>
    )

    private data class LibrarySnapshot(
        val homeSongs: List<Song>,
        val allSongs: List<Song>,
        val recentSearches: List<String>,
        val downloadStates: Map<String, DownloadState>,
        val favoriteIds: Set<String>,
        val playlists: List<PlaylistUiModel>,
        val downloadError: String?
    )

    companion object {
        private const val PREFS_SESSION = "sonora_session_prefs"
        private const val KEY_LAST_QUOTE_INDEX = "last_home_quote_index"
    }
}
