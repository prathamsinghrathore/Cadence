package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.DownloadState
import com.example.data.RepeatMode
import com.example.data.Song
import com.example.data.lyrics.LyricsUiState
import com.example.player.PlaybackStateSnapshot
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.LocalAmbientPalette
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PulseRose
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private val TrackSlideSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun FloatingGlassMiniPlayer(
    song: Song,
    playback: PlaybackStateSnapshot,
    positionFlow: StateFlow<Long>,
    downloadState: DownloadState,
    hazeState: HazeState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    verticalDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 54.dp.toPx() }
    val maxSwipeDragPx = with(density) { 96.dp.toPx() }
    val horizontalDragX = remember { Animatable(0f) }

    val horizontalDragState = rememberDraggableState { dragAmount ->
        coroutineScope.launch {
            val nextOffset = (horizontalDragX.value + dragAmount)
                .coerceIn(-maxSwipeDragPx, maxSwipeDragPx)
            horizontalDragX.snapTo(nextOffset)
        }
    }

    Column(
        modifier = modifier
            .liquidGlass(
                hazeState = hazeState,
                cornerRadius = 26.dp,
                isInteractive = true
            )
            .then(verticalDragModifier)
            .clickable(onClick = onExpand)
            .testTag("mini_player_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swipeable & horizontally sliding artwork + track title/artist region
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = horizontalDragState,
                        onDragStopped = { velocity ->
                            val dragged = horizontalDragX.value
                            coroutineScope.launch {
                                when {
                                    dragged <= -swipeThresholdPx || velocity < -600f -> onSkipNext()
                                    dragged >= swipeThresholdPx || velocity > 600f -> onSkipPrevious()
                                }
                                horizontalDragX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    )
                    .graphicsLayer {
                        val offset = horizontalDragX.value
                        translationX = offset
                        val dragProgress = (abs(offset) / maxSwipeDragPx).coerceIn(0f, 1f)
                        alpha = 1f - (dragProgress * 0.25f)
                    }
            ) {
                    val isNext = playback.lastSkipDirection >= 0
                    AnimatedContent(
                        targetState = song,
                        contentKey = { it.id },
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = TrackSlideSpring) { width ->
                                if (isNext) width else -width
                            } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(animationSpec = TrackSlideSpring) { width ->
                                    if (isNext) -width else width
                                } + fadeOut(tween(150)))
                        },
                        label = "miniPlayerTrackSlide"
                    ) { activeTrack ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongArtworkImage(
                                artworkUrl = activeTrack.artworkUrl,
                                fallbackArtworkRes = activeTrack.fallbackArtworkRes,
                                contentDescription = stringResource(R.string.cd_album_art),
                                modifier = Modifier.size(46.dp),
                                cornerRadius = 11.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = activeTrack.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (downloadState is DownloadState.Downloaded || activeTrack.isDownloaded) {
                                        Icon(
                                            imageVector = Icons.Rounded.OfflinePin,
                                            contentDescription = stringResource(R.string.player_offline_badge),
                                            tint = AuroraCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (playback.isBuffering) "Buffering stream…" else activeTrack.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (playback.isBuffering) ambient.secondaryGlow else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_play_pause")
                ) {
                    if (playback.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ambient.secondaryGlow,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playback.isPlaying) R.string.cd_pause else R.string.cd_play
                            ),
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

        MiniPlayerProgressBar(
            isBuffering = playback.isBuffering,
            durationMs = playback.durationMs.coerceAtLeast(song.duration),
            positionFlow = positionFlow,
            accentColor = ambient.secondaryGlow
        )
    }
}

/**
 * Isolated composable that collects currentPositionMs so the rest of the
 * Mini-Player and screen hierarchy never recompose on 250ms playback ticks.
 */
@Composable
private fun MiniPlayerProgressBar(
    isBuffering: Boolean,
    durationMs: Long,
    positionFlow: StateFlow<Long>,
    accentColor: Color
) {
    if (isBuffering) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
    } else {
        val currentPositionMs by positionFlow.collectAsStateWithLifecycle()
        val progress = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
    }
}

@Composable
fun FullScreenPlayer(
    song: Song,
    playback: PlaybackStateSnapshot,
    positionFlow: StateFlow<Long>,
    lyricsState: LyricsUiState,
    downloadState: DownloadState,
    isFavorite: Boolean,
    sleepTimerRemainingMs: Long?,
    sleepTimerEndOfTrack: Boolean,
    hazeState: HazeState,
    expandProgressProvider: () -> Float = { 1f },
    sheetVerticalDragModifier: Modifier = Modifier,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryLyrics: () -> Unit,
    onOpenPlaylistSheet: () -> Unit,
    onOpenQueueSheet: () -> Unit,
    onOpenMoreOptions: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    // Real-time GPU horizontal swipe-to-skip state on album artwork & title
    val swipeOffsetX = remember { Animatable(0f) }
    val skipThresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipeOffsetPx = with(density) { 160.dp.toPx() }

    val artworkElevation by animateDpAsState(
        targetValue = if (playback.isPlaying) 28.dp else 10.dp,
        animationSpec = tween(450),
        label = "artElevation"
    )
    val artworkScalePadding by animateDpAsState(
        targetValue = if (playback.isPlaying) 0.dp else 10.dp,
        animationSpec = tween(450),
        label = "artPadding"
    )

    val durationMs = playback.durationMs.coerceAtLeast(song.duration).coerceAtLeast(1L)
    val isNext = playback.lastSkipDirection >= 0

    val vibrantColor by animateColorAsState(
        targetValue = ambient.vibrantColor,
        animationSpec = tween(durationMillis = 800),
        label = "playerVibrant"
    )
    val dominantColor by animateColorAsState(
        targetValue = ambient.dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "playerDominant"
    )
    val lightVibrantColor by animateColorAsState(
        targetValue = ambient.lightVibrantColor,
        animationSpec = tween(durationMillis = 800),
        label = "playerLightVibrant"
    )

    val horizontalSwipeState = rememberDraggableState { dragAmount ->
        coroutineScope.launch {
            val nextOffset = (swipeOffsetX.value + dragAmount)
                .coerceIn(-maxSwipeOffsetPx, maxSwipeOffsetPx)
            swipeOffsetX.snapTo(nextOffset)
        }
    }

    val trackHorizontalSwipeModifier = Modifier.draggable(
        orientation = Orientation.Horizontal,
        state = horizontalSwipeState,
        onDragStopped = { velocity ->
            val dragged = swipeOffsetX.value
            coroutineScope.launch {
                when {
                    dragged <= -skipThresholdPx || velocity < -600f -> onSkipNext()
                    dragged >= skipThresholdPx || velocity > 600f -> onSkipPrevious()
                }
                swipeOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    )

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // LAYER 1: Full-bleed luminous blurred album artwork (70.dp blur, Crossfade 800ms) +
        // 3 soft Palette radial blobs + subtle edge-only dark scrim.
        // Fades in smoothly right from expandProgress > 0f while acting as HazeSource for Now Playing glass.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (expandProgressProvider() * 2.2f).coerceIn(0f, 1f)
                }
                .background(ObsidianVoid)
                .hazeSource(state = hazeState)
        ) {
            Crossfade(
                targetState = song,
                animationSpec = tween(durationMillis = 800),
                label = "playerArtworkCrossfade"
            ) { activeArtSong ->
                SongArtworkImage(
                    artworkUrl = activeArtSong.artworkUrl,
                    fallbackArtworkRes = activeArtSong.fallbackArtworkRes,
                    contentDescription = null,
                    cornerRadius = 0.dp,
                    decodeSizePx = 220,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.55f
                            scaleX = 1.24f
                            scaleY = 1.24f
                        }
                        .blur(70.dp)
                )
            }

            // 3 vibrant Palette radial glow blobs floating behind the cover & glass dock
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                val maxDim = maxOf(size.width, size.height).coerceAtLeast(1f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            vibrantColor.copy(alpha = 0.52f),
                            vibrantColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.20f, size.height * 0.22f),
                        radius = maxDim * 0.72f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.46f),
                            dominantColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.56f),
                        radius = maxDim * 0.68f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            lightVibrantColor.copy(alpha = 0.44f),
                            lightVibrantColor.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.42f, size.height * 0.86f),
                        radius = maxDim * 0.65f
                    )
                )
            }

            // Subtle dark vignette at the top & bottom edges only so center stays luminous
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.16f to Color.Black.copy(alpha = 0.16f),
                            0.55f to Color.Black.copy(alpha = 0.20f),
                            0.84f to Color.Black.copy(alpha = 0.36f),
                            1.0f to Color.Black.copy(alpha = 0.52f)
                        )
                    )
            )
        }

        // LAYER 2: Foreground Now Playing UI (GPU cross-fades in cleanly from expandProgress 0.25f -> 1f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = expandProgressProvider().coerceIn(0f, 1f)
                    alpha = ((p - 0.25f) * 1.35f).coerceIn(0f, 1f)
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Drag Handle Pill + Header (supports real-time vertical swipe-down to dismiss)
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .then(sheetVerticalDragModifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 10.dp)
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.36f))
                        .testTag("full_player_drag_handle")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .size(44.dp)
                            .liquidGlass(
                                hazeState = hazeState,
                                shape = CircleShape,
                                isInteractive = true
                            )
                            .testTag("full_player_collapse")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_collapse_player),
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.player_now_playing),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .liquidGlass(hazeState = hazeState, cornerRadius = 50.dp)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                val badgeText = when {
                                    playback.isBuffering -> "BUFFERING…"
                                    downloadState is DownloadState.Downloaded || song.isDownloaded ->
                                        "${stringResource(R.string.player_offline_badge)} • ${song.audioQualityLabel}"
                                    else -> "${stringResource(R.string.player_hires_badge)} • ${song.audioQualityLabel}"
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (downloadState is DownloadState.Downloaded || playback.isBuffering) {
                                        AuroraCyan
                                    } else {
                                        TextPrimary
                                    }
                                )
                            }

                            if (sleepTimerRemainingMs != null || sleepTimerEndOfTrack) {
                                Row(
                                    modifier = Modifier
                                        .liquidGlass(
                                            hazeState = hazeState,
                                            cornerRadius = 50.dp,
                                            isInteractive = true
                                        )
                                        .clickable(onClick = onOpenSleepTimer)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = stringResource(R.string.option_sleep_timer),
                                        tint = ambient.secondaryGlow,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (sleepTimerRemainingMs != null) {
                                            formatDurationMs(sleepTimerRemainingMs)
                                        } else {
                                            "1x"
                                        },
                                        style = TabularNumericStyle,
                                        color = ambient.secondaryGlow
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenMoreOptions,
                        modifier = Modifier
                            .size(44.dp)
                            .liquidGlass(
                                hazeState = hazeState,
                                shape = CircleShape,
                                isInteractive = true
                            )
                            .testTag("full_player_more_options")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Central Stage: Smooth crossfade between Swipeable Album Artwork and Frosted Glass Synced Lyrics Panel
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .height(330.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                    },
                    label = "artworkLyricsStageToggle"
                ) { lyricsVisible ->
                    if (lyricsVisible) {
                        SyncedLyricsPanel(
                            songId = song.id,
                            lyricsState = lyricsState,
                            positionFlow = positionFlow,
                            hazeState = hazeState,
                            onSeekTo = onSeekTo,
                            onRetry = onRetryLyrics,
                            onShowArtwork = { showLyrics = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 340.dp)
                                .fillMaxWidth()
                                .padding(horizontal = artworkScalePadding)
                                .then(sheetVerticalDragModifier)
                                .then(trackHorizontalSwipeModifier)
                                .graphicsLayer {
                                    val offset = swipeOffsetX.value
                                    translationX = offset
                                    rotationZ = (offset / maxSwipeOffsetPx) * 4f
                                    val dragProgress = (abs(offset) / maxSwipeOffsetPx).coerceIn(0f, 1f)
                                    alpha = 1f - (dragProgress * 0.22f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = song,
                                contentKey = { it.id },
                                transitionSpec = {
                                    (slideInHorizontally(animationSpec = TrackSlideSpring) { width ->
                                        if (isNext) width else -width
                                    } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally(animationSpec = TrackSlideSpring) { width ->
                                            if (isNext) -width else width
                                        } + fadeOut(tween(180)))
                                },
                                label = "fullPlayerArtworkSlide"
                            ) { activeTrack ->
                                Box(
                                    modifier = Modifier
                                        .size(310.dp)
                                        .shadow(
                                            elevation = artworkElevation,
                                            shape = RoundedCornerShape(28.dp),
                                            ambientColor = ambient.primaryGlow,
                                            spotColor = ambient.secondaryGlow
                                        )
                                        .clip(RoundedCornerShape(28.dp))
                                        .clickable { showLyrics = true }
                                        .testTag("full_player_artwork_tap")
                                ) {
                                    SongArtworkImage(
                                        artworkUrl = activeTrack.artworkUrl,
                                        fallbackArtworkRes = activeTrack.fallbackArtworkRes,
                                        contentDescription = stringResource(R.string.cd_album_art),
                                        decodeSizePx = 512,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.28f),
                                                        Color.White.copy(alpha = 0.08f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(28.dp)
                                            ),
                                        cornerRadius = 28.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Swipeable & horizontally sliding Song Title / Artist + Favorite Button
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(trackHorizontalSwipeModifier)
                        .graphicsLayer {
                            translationX = swipeOffsetX.value * 0.45f
                        }
                ) {
                    AnimatedContent(
                        targetState = song,
                        contentKey = { it.id },
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = TrackSlideSpring) { width ->
                                if (isNext) width else -width
                            } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(animationSpec = TrackSlideSpring) { width ->
                                    if (isNext) -width else width
                                } + fadeOut(tween(160)))
                        },
                        label = "fullPlayerTitleSlide"
                    ) { activeTrack ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = activeTrack.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${activeTrack.artist} • ${activeTrack.album}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("full_player_favorite")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (isFavorite) PulseRose else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            FullPlayerWaveformSeekBar(
                songId = song.id,
                durationMs = durationMs,
                isPlaying = playback.isPlaying,
                positionFlow = positionFlow,
                activeAccentColor = ambient.secondaryGlow,
                hazeState = hazeState,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("full_player_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (playback.shuffleEnabled) ambient.secondaryGlow else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("full_player_prev")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = CircleShape,
                            ambientColor = ambient.primaryGlow,
                            spotColor = ambient.secondaryGlow
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ambient.primaryGlow, ambient.secondaryGlow)
                            )
                        )
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("full_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    if (playback.isBuffering) {
                        CircularProgressIndicator(
                            color = ObsidianVoid,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playback.isPlaying) R.string.cd_pause else R.string.cd_play
                            ),
                            tint = ObsidianVoid,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("full_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("full_player_repeat")
                ) {
                    val (icon, tint) = when (playback.repeatMode) {
                        RepeatMode.OFF -> Icons.Rounded.Repeat to TextMuted
                        RepeatMode.ALL -> Icons.Rounded.Repeat to ambient.secondaryGlow
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne to ambient.secondaryGlow
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.cd_repeat),
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apple iOS Liquid Glass Floating Action Dock (real-time 36.dp Haze over blurred album art)
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .liquidGlass(hazeState = hazeState, cornerRadius = 26.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lyrics Toggle Button
                Column(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (showLyrics) ambient.secondaryGlow.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .clickable { showLyrics = !showLyrics }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("full_player_lyrics_toggle"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = stringResource(R.string.cd_toggle_lyrics),
                        tint = if (showLyrics) ambient.secondaryGlow else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_lyrics),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showLyrics) ambient.secondaryGlow else TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                )

                // Download Toggle Button
                Column(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onToggleDownload)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("full_player_download"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            CircularProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier.size(20.dp),
                                color = AuroraCyan,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "${(downloadState.progress * 100).toInt()}%",
                                style = TabularNumericStyle,
                                color = AuroraCyan
                            )
                        }
                        is DownloadState.Downloaded -> {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = stringResource(R.string.cd_remove_download),
                                tint = AuroraCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.player_offline_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraCyan
                            )
                        }
                        DownloadState.NotDownloaded -> {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.cd_download_track),
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.player_download),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                )

                // Add to Playlist Button
                Column(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenPlaylistSheet)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("full_player_add_playlist"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Playlist",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                )

                // Up Next Queue Button
                Column(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenQueueSheet)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("full_player_queue_button"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.player_queue),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_queue),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
