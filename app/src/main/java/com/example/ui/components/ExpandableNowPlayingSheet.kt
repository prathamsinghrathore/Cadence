package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.data.DownloadState
import com.example.data.Song
import com.example.data.lyrics.LyricsUiState
import com.example.player.PlaybackStateSnapshot
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val NowPlayingSheetSnapSpring = spring<Float>(
    dampingRatio = 0.84f,
    stiffness = Spring.StiffnessMediumLow
)

private val CollapsedMiniPlayerHeight = 70.dp
private val CollapsedHorizontalMargin = 16.dp
private val CollapsedMaxWidth = 560.dp
private val CollapsedCornerRadius = 26.dp
private val GapAboveBottomBar = 6.dp

/**
 * GPU-evaluated outline shape that morphs the sheet bounds continuously from the floating
 * collapsed Mini-Player pill (16.dp horizontal margin, 70.dp height, 24.dp corner radius)
 * to edge-to-edge full screen (0.dp margin, full height, 0.dp corner radius) without
 * triggering any Compose layout passes during vertical drag frames.
 */
private data class ExpandableSheetOutlineShape(
    val progress: Float,
    val collapsedHeightPx: Float,
    val collapsedHorizontalInsetPx: Float,
    val collapsedCornerRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val p = progress.coerceIn(0f, 1f)
        val insetX = lerp(collapsedHorizontalInsetPx, 0f, p)
        val left = insetX.coerceIn(0f, (size.width / 2f) - 1f)
        val right = (size.width - left).coerceAtLeast(left + 1f)
        val bottom = lerp(collapsedHeightPx, size.height, p).coerceIn(1f, size.height)
        val radius = lerp(collapsedCornerRadiusPx, 0f, p).coerceAtLeast(0f)

        return Outline.Rounded(
            RoundRect(
                left = left,
                top = 0f,
                right = right,
                bottom = bottom,
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
}

/**
 * Unified Spotify / Apple Music-style Swipe-Up Expandable Player Sheet.
 *
 * Driven by a single normalized [expandProgress] (`0f` = collapsed floating frosted Mini-Player
 * above the bottom nav bar, `1f` = full-screen frosted Now Playing experience).
 * All translation, clipping bounds, corner radius, and crossfade alphas are executed strictly
 * inside `Modifier.graphicsLayer` on the GPU for locked 60/120 FPS responsiveness.
 */
@Composable
fun ExpandableNowPlayingSheet(
    song: Song,
    playback: PlaybackStateSnapshot,
    positionFlow: StateFlow<Long>,
    lyricsState: LyricsUiState,
    downloadState: DownloadState,
    isFavorite: Boolean,
    sleepTimerRemainingMs: Long?,
    sleepTimerEndOfTrack: Boolean,
    expandProgress: Animatable<Float, AnimationVector1D>,
    bottomBarHeightPx: Float,
    rootHazeState: HazeState,
    playerHazeState: HazeState,
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isExpandedPastHalf by remember {
        derivedStateOf { expandProgress.value > 0.5f }
    }
    val showFullPlayerLayer by remember {
        derivedStateOf { expandProgress.value > 0.01f }
    }
    val showMiniPlayerLayer by remember {
        derivedStateOf { expandProgress.value < 0.45f }
    }

    fun animateToTarget(target: Float) {
        coroutineScope.launch {
            expandProgress.animateTo(
                targetValue = target.coerceIn(0f, 1f),
                animationSpec = NowPlayingSheetSnapSpring
            )
        }
    }

    // Intercept system Back gesture / button when the full player sheet is open
    BackHandler(enabled = isExpandedPastHalf) {
        animateToTarget(0f)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val miniPlayerHeightPx = with(density) { CollapsedMiniPlayerHeight.toPx() }
        val gapPx = with(density) { GapAboveBottomBar.toPx() }
        val fallbackNavHeightPx = with(density) { 96.dp.toPx() }
        val effectiveBottomBarHeightPx = if (bottomBarHeightPx > 0f) bottomBarHeightPx else fallbackNavHeightPx

        // Collapsed top Y sits cleanly above the floating glass Bottom Navigation Bar
        val collapsedTopYPx = (containerHeightPx - effectiveBottomBarHeightPx - miniPlayerHeightPx - gapPx)
            .coerceAtLeast(1f)

        val horizontalMarginPx = with(density) { CollapsedHorizontalMargin.toPx() }
        val maxCollapsedWidthPx = with(density) { CollapsedMaxWidth.toPx() }
        val targetCollapsedWidthPx = minOf(
            (containerWidthPx - horizontalMarginPx * 2f).coerceAtLeast(1f),
            maxCollapsedWidthPx
        )
        val collapsedHorizontalInsetPx = ((containerWidthPx - targetCollapsedWidthPx) / 2f)
            .coerceAtLeast(horizontalMarginPx)
        val collapsedCornerRadiusPx = with(density) { CollapsedCornerRadius.toPx() }

        var dragStartProgress by remember { mutableFloatStateOf(0f) }

        val verticalDragState = rememberDraggableState { deltaPx ->
            val deltaProgress = -deltaPx / collapsedTopYPx
            val next = (expandProgress.value + deltaProgress).coerceIn(0f, 1f)
            coroutineScope.launch {
                expandProgress.snapTo(next)
            }
        }

        val sheetVerticalDragModifier = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = verticalDragState,
            onDragStarted = {
                dragStartProgress = expandProgress.value
            },
            onDragStopped = { velocity ->
                val current = expandProgress.value
                val shouldExpand = if (dragStartProgress < 0.5f) {
                    // Swiping up from collapsed Mini-Player: snap open past 20% or upward fling < -600 px/s
                    velocity < -600f || (velocity <= 600f && current >= 0.20f)
                } else {
                    // Swiping down from expanded Full Player: collapse past 20% drag or downward fling > 600 px/s
                    !(velocity > 600f || (velocity >= -600f && current <= 0.80f))
                }
                animateToTarget(if (shouldExpand) 1f else 0f)
            }
        )

        // Single unified GPU-morphing sheet container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = expandProgress.value.coerceIn(0f, 1f)
                    translationY = (1f - p) * collapsedTopYPx
                    clip = true
                    shape = ExpandableSheetOutlineShape(
                        progress = p,
                        collapsedHeightPx = miniPlayerHeightPx,
                        collapsedHorizontalInsetPx = collapsedHorizontalInsetPx,
                        collapsedCornerRadiusPx = collapsedCornerRadiusPx
                    )
                }
        ) {
            // LAYER 1: Full-Screen Now Playing view (mounted only when expanding/expanded)
            if (showFullPlayerLayer) {
                FullScreenPlayer(
                    song = song,
                    playback = playback,
                    positionFlow = positionFlow,
                    lyricsState = lyricsState,
                    downloadState = downloadState,
                    isFavorite = isFavorite,
                    sleepTimerRemainingMs = sleepTimerRemainingMs,
                    sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                    hazeState = playerHazeState,
                    expandProgressProvider = { expandProgress.value },
                    sheetVerticalDragModifier = sheetVerticalDragModifier,
                    onCollapse = { animateToTarget(0f) },
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onToggleDownload = onToggleDownload,
                    onToggleFavorite = onToggleFavorite,
                    onRetryLyrics = onRetryLyrics,
                    onOpenPlaylistSheet = onOpenPlaylistSheet,
                    onOpenQueueSheet = onOpenQueueSheet,
                    onOpenMoreOptions = onOpenMoreOptions,
                    onOpenSleepTimer = onOpenSleepTimer,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 2: Floating Frosted Glass Mini-Player (cross-fades out smoothly during expansion)
            if (showMiniPlayerLayer) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = CollapsedHorizontalMargin)
                        .graphicsLayer {
                            val p = expandProgress.value.coerceIn(0f, 1f)
                            alpha = (1f - p * 2.5f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    FloatingGlassMiniPlayer(
                        song = song,
                        playback = playback,
                        positionFlow = positionFlow,
                        downloadState = downloadState,
                        hazeState = rootHazeState,
                        onExpand = { animateToTarget(1f) },
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        verticalDragModifier = sheetVerticalDragModifier,
                        modifier = Modifier
                            .widthIn(max = CollapsedMaxWidth)
                            .fillMaxWidth()
                            .height(CollapsedMiniPlayerHeight)
                    )
                }
            }
        }
    }
}
