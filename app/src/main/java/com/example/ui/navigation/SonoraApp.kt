package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.SonoraApplication
import com.example.data.DownloadState
import com.example.data.Song
import com.example.player.MusicPlayerViewModel
import com.example.ui.SonoraViewModel
import com.example.ui.components.AddToPlaylistGlassSheet
import com.example.ui.components.AmbientGradientBackground
import com.example.ui.components.CreatePlaylistGlassDialog
import com.example.ui.components.ExpandableNowPlayingSheet
import com.example.ui.components.FloatingGlassBottomBar
import com.example.ui.components.NowPlayingSheetSnapSpring
import com.example.ui.components.QueueGlassSheet
import com.example.ui.components.SleepTimerGlassSheet
import com.example.ui.components.SongInfoGlassSheet
import com.example.ui.components.SongOptionsGlassSheet
import com.example.ui.components.liquidGlass
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.PaletteExtractor
import com.example.ui.theme.SonoraTheme
import com.example.ui.theme.TextPrimary
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private val NavSlideSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun NavDestination?.routeOrderIndex(): Int = when {
    this == null -> 0
    hasRoute<SonoraRoute.Home>() -> 0
    hasRoute<SonoraRoute.Search>() -> 1
    hasRoute<SonoraRoute.Library>() -> 2
    hasRoute<SonoraRoute.PlaylistDetail>() -> 3
    else -> 0
}

@Composable
fun SonoraApp(
    viewModel: SonoraViewModel = viewModel(),
    musicPlayerViewModel: MusicPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val libraryState by viewModel.uiState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val playbackState by musicPlayerViewModel.playbackState.collectAsStateWithLifecycle()
    val currentSong by musicPlayerViewModel.currentSong.collectAsStateWithLifecycle()
    val playbackQueue by musicPlayerViewModel.playbackQueue.collectAsStateWithLifecycle()
    val playbackError by musicPlayerViewModel.playbackError.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by musicPlayerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val sleepTimerEndOfTrack by musicPlayerViewModel.sleepTimerEndOfTrack.collectAsStateWithLifecycle()

    val uiState = remember(libraryState, playbackState) {
        libraryState.copy(playback = playbackState)
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Dedicated HazeStates per layer hierarchy to avoid self-feedback loops:
    // - backdropHazeState captures ONLY the blurred album artwork + ambient blobs for hero cards
    // - rootHazeState captures the full-screen ambient background + NavHost content for nav/mini-player/sheets
    // - playerHazeState captures the full-bleed blurred album art backdrop in FullScreenPlayer
    val backdropHazeState = rememberHazeState()
    val rootHazeState = rememberHazeState()
    val playerHazeState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }

    var savedCrashTrace by remember { mutableStateOf(SonoraApplication.getSavedCrashTrace(context)) }
    val playerExpandProgress = remember { Animatable(0f) }
    var bottomBarHeightPx by remember { mutableFloatStateOf(0f) }
    var optionsTargetSong by remember { mutableStateOf<Song?>(null) }
    var infoTargetSong by remember { mutableStateOf<Song?>(null) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var playlistTargetSong by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var pendingSongForNewPlaylist by remember { mutableStateOf<Song?>(null) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Playback works regardless; notification shows if granted */ }
    )

    fun ensureNotificationPermissionIfNeeded() {
        if (hasRequestedNotificationPermission) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                hasRequestedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun showToastMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun collapsePlayerSheet() {
        coroutineScope.launch {
            playerExpandProgress.animateTo(
                targetValue = 0f,
                animationSpec = NowPlayingSheetSnapSpring
            )
        }
    }

    fun navigateToSearchWithQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return
        collapsePlayerSheet()
        viewModel.submitSearch(cleanQuery)
        if (navController.currentDestination?.hasRoute<SonoraRoute.Search>() != true) {
            navController.navigate(SonoraRoute.Search) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(libraryState.downloadStates) {
        musicPlayerViewModel.updateDownloadStates(libraryState.downloadStates)
    }

    LaunchedEffect(libraryState.homeSongs, libraryState.downloadedSongs) {
        val seedList = libraryState.homeSongs.ifEmpty { libraryState.downloadedSongs }
        if (seedList.isNotEmpty()) {
            musicPlayerViewModel.seedQueueIfIdle(seedList)
        }
    }

    val artworkCacheKey = currentSong?.let { PaletteExtractor.cacheKeyFor(it) }
    val resolvedAmbientPalette = remember(artworkCacheKey, uiState.ambientPalette) {
        artworkCacheKey?.let { PaletteExtractor.getCached(it) } ?: uiState.ambientPalette
    }

    LaunchedEffect(artworkCacheKey) {
        currentSong?.let { song ->
            viewModel.extractPaletteForSong(song)
        }
    }

    LaunchedEffect(currentSong?.id) {
        currentSong?.let { song ->
            viewModel.loadLyricsForSong(song)
        }
    }

    val errorMessage = playbackError ?: libraryState.downloadError
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            musicPlayerViewModel.clearError()
            viewModel.clearDownloadError()
        }
    }

    val currentDestination = navBackStackEntry?.destination
    val selectedTab = when {
        currentDestination?.hasRoute<SonoraRoute.Search>() == true -> BottomNavTab.SEARCH
        currentDestination?.hasRoute<SonoraRoute.Library>() == true ||
            currentDestination?.hasRoute<SonoraRoute.PlaylistDetail>() == true -> BottomNavTab.LIBRARY
        else -> BottomNavTab.HOME
    }

    val activeSong = currentSong
    LaunchedEffect(activeSong == null) {
        if (activeSong == null && playerExpandProgress.value > 0f) {
            playerExpandProgress.snapTo(0f)
        }
    }

    val isExpandedPlayerVisible by remember {
        derivedStateOf { activeSong != null && playerExpandProgress.value > 0.5f }
    }
    val activeHazeState = if (isExpandedPlayerVisible) playerHazeState else rootHazeState
    val bottomContentPadding = if (activeSong != null) 188.dp else 114.dp

    SonoraTheme(ambientPalette = resolvedAmbientPalette) {
        Box(modifier = Modifier.fillMaxSize()) {
            // LAYER 1: Persistent Main Scaffold (stays mounted beneath the sliding Now Playing sheet
            // so vertical drag-to-dismiss reveals the live Home/Search/Library screen smoothly).
            AmbientGradientBackground(
                currentSong = activeSong,
                backdropHazeState = backdropHazeState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = rootHazeState)
            ) {
                    NavHost(
                        navController = navController,
                        startDestination = SonoraRoute.Home,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            val movingForward = targetState.destination.routeOrderIndex() >=
                                initialState.destination.routeOrderIndex()
                            slideInHorizontally(animationSpec = NavSlideSpring) { fullWidth ->
                                if (movingForward) fullWidth else -fullWidth
                            } + fadeIn(animationSpec = tween(220))
                        },
                        exitTransition = {
                            val movingForward = targetState.destination.routeOrderIndex() >=
                                initialState.destination.routeOrderIndex()
                            slideOutHorizontally(animationSpec = NavSlideSpring) { fullWidth ->
                                if (movingForward) -(fullWidth / 3) else (fullWidth / 3)
                            } + fadeOut(animationSpec = tween(180))
                        },
                        popEnterTransition = {
                            val movingForward = targetState.destination.routeOrderIndex() >=
                                initialState.destination.routeOrderIndex()
                            slideInHorizontally(animationSpec = NavSlideSpring) { fullWidth ->
                                if (movingForward) fullWidth else -fullWidth
                            } + fadeIn(animationSpec = tween(220))
                        },
                        popExitTransition = {
                            val movingForward = targetState.destination.routeOrderIndex() >=
                                initialState.destination.routeOrderIndex()
                            slideOutHorizontally(animationSpec = NavSlideSpring) { fullWidth ->
                                if (movingForward) -(fullWidth / 3) else (fullWidth / 3)
                            } + fadeOut(animationSpec = tween(180))
                        }
                    ) {
                        composable<SonoraRoute.Home> {
                            HomeScreen(
                                uiState = uiState,
                                bottomContentPadding = bottomContentPadding,
                                onSelectGenre = viewModel::selectGenre,
                                onRetryHome = { viewModel.refreshHomeCatalog(isPullToRefresh = false) },
                                onRefreshHome = { viewModel.refreshHomeCatalog(isPullToRefresh = true) },
                                onPlaySong = { song, queue ->
                                    ensureNotificationPermissionIfNeeded()
                                    musicPlayerViewModel.playSong(song, queue, uiState.downloadStates)
                                },
                                onToggleDownload = viewModel::toggleDownload,
                                onOpenAddToPlaylist = { song -> playlistTargetSong = song },
                                onOpenMoreOptions = { song -> optionsTargetSong = song }
                            )
                        }

                        composable<SonoraRoute.Search> {
                            SearchScreen(
                                uiState = uiState,
                                bottomContentPadding = bottomContentPadding,
                                onQueryChange = viewModel::updateSearchQuery,
                                onSubmitSearch = viewModel::submitSearch,
                                onDeleteRecentSearch = viewModel::deleteRecentSearch,
                                onClearRecentSearches = viewModel::clearRecentSearches,
                                onPlaySong = { song, queue ->
                                    ensureNotificationPermissionIfNeeded()
                                    musicPlayerViewModel.playSong(song, queue, uiState.downloadStates)
                                },
                                onToggleDownload = viewModel::toggleDownload,
                                onOpenAddToPlaylist = { song -> playlistTargetSong = song },
                                onOpenMoreOptions = { song -> optionsTargetSong = song }
                            )
                        }

                        composable<SonoraRoute.Library> {
                            LibraryScreen(
                                uiState = uiState,
                                bottomContentPadding = bottomContentPadding,
                                onPlaySong = { song, queue ->
                                    ensureNotificationPermissionIfNeeded()
                                    musicPlayerViewModel.playSong(song, queue, uiState.downloadStates)
                                },
                                onPlayAllOffline = { songs ->
                                    songs.firstOrNull()?.let { first ->
                                        ensureNotificationPermissionIfNeeded()
                                        musicPlayerViewModel.playSong(first, songs, uiState.downloadStates)
                                    }
                                },
                                onToggleDownload = viewModel::toggleDownload,
                                onOpenAddToPlaylist = { song -> playlistTargetSong = song },
                                onOpenCreatePlaylist = {
                                    pendingSongForNewPlaylist = null
                                    showCreatePlaylistDialog = true
                                },
                                onOpenPlaylistDetail = { playlistId ->
                                    navController.navigate(SonoraRoute.PlaylistDetail(playlistId))
                                },
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onOpenMoreOptions = { song -> optionsTargetSong = song }
                            )
                        }

                        composable<SonoraRoute.PlaylistDetail> { entry ->
                            val route = entry.toRoute<SonoraRoute.PlaylistDetail>()
                            val playlist = uiState.playlists.find { it.id == route.playlistId }
                            PlaylistDetailScreen(
                                playlist = playlist,
                                uiState = uiState,
                                bottomContentPadding = bottomContentPadding,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { song, queue ->
                                    ensureNotificationPermissionIfNeeded()
                                    musicPlayerViewModel.playSong(song, queue, uiState.downloadStates)
                                },
                                onPlayAll = { songs ->
                                    songs.firstOrNull()?.let { first ->
                                        ensureNotificationPermissionIfNeeded()
                                        musicPlayerViewModel.playSong(first, songs, uiState.downloadStates)
                                    }
                                },
                                onToggleDownload = viewModel::toggleDownload,
                                onRemoveFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onOpenAddToPlaylist = { song -> playlistTargetSong = song },
                                onOpenMoreOptions = { song -> optionsTargetSong = song }
                            )
                        }
                    }
                }

            // LAYER 2: Unified Interactive Swipe-Up Expandable Player Sheet (Mini-Player <-> Full Player)
            if (activeSong != null) {
                ExpandableNowPlayingSheet(
                    song = activeSong,
                    playback = playbackState,
                    positionFlow = musicPlayerViewModel.currentPosition,
                    lyricsState = lyricsState,
                    downloadState = uiState.downloadStates[activeSong.id] ?: DownloadState.NotDownloaded,
                    isFavorite = uiState.favoriteIds.contains(activeSong.id),
                    sleepTimerRemainingMs = sleepTimerRemainingMs,
                    sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                    expandProgress = playerExpandProgress,
                    bottomBarHeightPx = bottomBarHeightPx,
                    rootHazeState = rootHazeState,
                    playerHazeState = playerHazeState,
                    onTogglePlayPause = {
                        ensureNotificationPermissionIfNeeded()
                        musicPlayerViewModel.togglePlayPause(uiState.downloadStates)
                    },
                    onSeekTo = musicPlayerViewModel::seekTo,
                    onSkipNext = musicPlayerViewModel::skipNext,
                    onSkipPrevious = musicPlayerViewModel::skipPrevious,
                    onToggleShuffle = musicPlayerViewModel::toggleShuffle,
                    onCycleRepeat = musicPlayerViewModel::cycleRepeatMode,
                    onToggleDownload = { viewModel.toggleDownload(activeSong) },
                    onToggleFavorite = { viewModel.toggleFavorite(activeSong.id) },
                    onRetryLyrics = { viewModel.loadLyricsForSong(activeSong, forceRefresh = true) },
                    onOpenPlaylistSheet = { playlistTargetSong = activeSong },
                    onOpenQueueSheet = { showQueueSheet = true },
                    onOpenMoreOptions = { optionsTargetSong = activeSong },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 3: Floating Glass Bottom Navigation Bar — smoothly synced to expandProgress on GPU
            val showBottomBarTouchLayer by remember {
                derivedStateOf { playerExpandProgress.value < 0.96f }
            }
            if (showBottomBarTouchLayer) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                bottomBarHeightPx = size.height.toFloat()
                            }
                        }
                        .graphicsLayer {
                            val p = playerExpandProgress.value.coerceIn(0f, 1f)
                            translationY = p * bottomBarHeightPx
                            alpha = (1f - p).coerceIn(0f, 1f)
                        }
                ) {
                    FloatingGlassBottomBar(
                        selectedTab = selectedTab,
                        hazeState = rootHazeState,
                        onTabSelected = { tab ->
                            val alreadyOnTab = when (tab) {
                                BottomNavTab.HOME -> currentDestination?.hasRoute<SonoraRoute.Home>() == true
                                BottomNavTab.SEARCH -> currentDestination?.hasRoute<SonoraRoute.Search>() == true
                                BottomNavTab.LIBRARY -> currentDestination?.hasRoute<SonoraRoute.Library>() == true
                            }
                            if (!alreadyOnTab) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }

            // LAYER 3: Frosted Glass Snackbar Toast
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (isExpandedPlayerVisible) 24.dp else 164.dp,
                        start = 18.dp,
                        end = 18.dp
                    )
            ) { data ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(hazeState = activeHazeState, cornerRadius = 20.dp)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // BitChord Floating 3-Dot Song Options Frosted Acrylic Sheet
            SongOptionsGlassSheet(
                song = optionsTargetSong,
                downloadState = optionsTargetSong?.let { uiState.downloadStates[it.id] }
                    ?: DownloadState.NotDownloaded,
                isFavorite = optionsTargetSong?.let { uiState.favoriteIds.contains(it.id) } == true,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                hazeState = activeHazeState,
                onDismiss = { optionsTargetSong = null },
                onPlayNext = { song ->
                    musicPlayerViewModel.playNext(song)
                    showToastMessage("Playing next: ${song.title}")
                },
                onAddToQueue = { song ->
                    musicPlayerViewModel.addToQueue(song)
                    showToastMessage("Added to queue: ${song.title}")
                },
                onToggleDownload = { song ->
                    viewModel.toggleDownload(song)
                },
                onAddToPlaylist = { song ->
                    playlistTargetSong = song
                },
                onToggleFavorite = { song ->
                    viewModel.toggleFavorite(song.id)
                },
                onOpenAlbum = { song ->
                    navigateToSearchWithQuery(song.album.ifBlank { song.title })
                },
                onOpenArtist = { song ->
                    val primaryArtist = song.artist.substringBefore(",").trim().ifBlank { song.artist }
                    navigateToSearchWithQuery(primaryArtist)
                },
                onOpenSongInfo = { song ->
                    infoTargetSong = song
                },
                onOpenSleepTimer = {
                    showSleepTimerSheet = true
                }
            )

            // BitChord Floating Song Info Acrylic Sheet
            SongInfoGlassSheet(
                song = infoTargetSong,
                downloadState = infoTargetSong?.let { uiState.downloadStates[it.id] }
                    ?: DownloadState.NotDownloaded,
                hazeState = activeHazeState,
                onDismiss = { infoTargetSong = null }
            )

            // BitChord Floating Sleep Timer Acrylic Sheet
            SleepTimerGlassSheet(
                visible = showSleepTimerSheet,
                remainingMs = sleepTimerRemainingMs,
                endOfTrack = sleepTimerEndOfTrack,
                hazeState = activeHazeState,
                onSelectMinutes = { minutes ->
                    musicPlayerViewModel.setSleepTimerMinutes(minutes)
                    showToastMessage("Sleep timer set for $minutes min")
                },
                onSelectEndOfTrack = {
                    musicPlayerViewModel.setSleepTimerEndOfTrack()
                    showToastMessage("Playback will pause after this track")
                },
                onCancelTimer = {
                    musicPlayerViewModel.cancelSleepTimer()
                    showToastMessage("Sleep timer turned off")
                },
                onDismiss = { showSleepTimerSheet = false }
            )

            // BitChord Floating Add-to-Playlist Acrylic Sheet
            AddToPlaylistGlassSheet(
                song = playlistTargetSong,
                playlists = uiState.playlists,
                hazeState = activeHazeState,
                onSelectPlaylist = { playlistId ->
                    val target = playlistTargetSong
                    if (target != null) {
                        viewModel.addTrackToPlaylist(playlistId, target)
                        showToastMessage("Added ${target.title} to playlist")
                    }
                    playlistTargetSong = null
                },
                onCreateNewPlaylistClick = {
                    pendingSongForNewPlaylist = playlistTargetSong
                    playlistTargetSong = null
                    showCreatePlaylistDialog = true
                },
                onDismiss = { playlistTargetSong = null }
            )

            // BitChord Floating Create Playlist Acrylic Dialog
            CreatePlaylistGlassDialog(
                visible = showCreatePlaylistDialog,
                hazeState = activeHazeState,
                onDismiss = {
                    showCreatePlaylistDialog = false
                    pendingSongForNewPlaylist = null
                },
                onConfirm = { name, desc ->
                    viewModel.createPlaylist(name, desc, pendingSongForNewPlaylist)
                    showCreatePlaylistDialog = false
                    pendingSongForNewPlaylist = null
                }
            )

            // BitChord Floating Playback Queue Acrylic Sheet
            QueueGlassSheet(
                visible = showQueueSheet,
                queue = playbackQueue.ifEmpty { uiState.allSongs },
                currentSongId = currentSong?.id,
                isPlaying = playbackState.isPlaying,
                hazeState = activeHazeState,
                onSelectSong = { song ->
                    ensureNotificationPermissionIfNeeded()
                    val queue = playbackQueue.ifEmpty { uiState.allSongs }
                    musicPlayerViewModel.playSong(song, queue, uiState.downloadStates)
                    showQueueSheet = false
                },
                onDismiss = { showQueueSheet = false }
            )

            savedCrashTrace?.let { trace ->
                AlertDialog(
                    onDismissRequest = {
                        SonoraApplication.clearSavedCrashTrace(context)
                        savedCrashTrace = null
                    },
                    title = { Text("Previous Crash Log") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(text = trace)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(trace))
                                SonoraApplication.clearSavedCrashTrace(context)
                                savedCrashTrace = null
                            }
                        ) {
                            Text("Copy & Dismiss")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                SonoraApplication.clearSavedCrashTrace(context)
                                savedCrashTrace = null
                            }
                        ) {
                            Text("Dismiss")
                        }
                    }
                )
            }
        }
    }
}
