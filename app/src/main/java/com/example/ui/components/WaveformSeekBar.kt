package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val WAVEFORM_BAR_COUNT = 52
private val UNPLAYED_BAR_COLOR = Color.White.copy(alpha = 0.24f)

/**
 * Isolated wrapper around [WaveformSeekBar] that collects `positionFlow` locally so the
 * Full-Screen Now Playing backdrop, artwork, and transport controls never recompose on
 * 250ms playback progress ticks.
 */
@Composable
fun FullPlayerWaveformSeekBar(
    songId: String,
    durationMs: Long,
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>,
    activeAccentColor: Color,
    hazeState: HazeState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by positionFlow.collectAsStateWithLifecycle()
    val safeDuration = durationMs.coerceAtLeast(1L)
    val liveProgress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val smoothProgress by animateFloatAsState(
        targetValue = liveProgress,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "waveformSmoothProgress"
    )

    WaveformSeekBar(
        songId = songId,
        durationMs = safeDuration,
        progressProvider = { smoothProgress },
        displayedPositionMsProvider = { currentPositionMs },
        isPlaying = isPlaying,
        activeAccentColor = activeAccentColor,
        hazeState = hazeState,
        onSeekTo = onSeekTo,
        modifier = modifier
    )
}

/**
 * Flagship Canvas-drawn Audio Waveform Seekbar with:
 * - 52 rounded vertical bars (3.dp target width, 2.5.dp gap, 6.dp..34.dp rhythmic silhouette
 *   seeded deterministically per [songId]).
 * - Dynamic Palette vibrant illumination for played bars + soft playhead glow + translucent
 *   frosted white for unplayed bars.
 * - Live beat-wave height animation near the active playhead while [isPlaying] is true,
 *   settling smoothly to resting heights when paused.
 * - Single-tap jump + smooth horizontal drag scrubbing with a floating frosted glass
 *   timestamp pill above the user's finger and 1.25x bar scale under the finger.
 */
@Composable
fun WaveformSeekBar(
    songId: String,
    durationMs: Long,
    progressProvider: () -> Float,
    isPlaying: Boolean,
    activeAccentColor: Color,
    hazeState: HazeState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    displayedPositionMsProvider: () -> Long = { (progressProvider() * durationMs).toLong() }
) {
    val hapticFeedback = LocalHapticFeedback.current
    val safeDurationMs = durationMs.coerceAtLeast(1L)

    // Deterministic unique waveform silhouette per track
    val amplitudes = remember(songId) {
        generateWaveformAmplitudes(songId, WAVEFORM_BAR_COUNT)
    }

    val animatedAccent by animateColorAsState(
        targetValue = activeAccentColor,
        animationSpec = tween(durationMillis = 550),
        label = "waveformAccentColor"
    )

    // Live beat wave phase (runs on GPU draw pass only, zero layout thrash)
    val infiniteTransition = rememberInfiniteTransition(label = "waveformPulse")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformBeatPhase"
    )

    // Smoothly ramps live wave motion in when playing and settles back to rest when paused
    val playingIntensity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "waveformPlayIntensity"
    )

    var isScrubbing by remember(songId) { mutableStateOf(false) }
    var scrubFraction by remember(songId) { mutableFloatStateOf(0f) }
    var waveformWidthPx by remember { mutableFloatStateOf(1f) }
    var pillWidthPx by remember { mutableIntStateOf(0) }

    val scrubBarScale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "waveformScrubBarScale"
    )

    val displayedMs = if (isScrubbing) {
        (scrubFraction * safeDurationMs).toLong().coerceIn(0L, safeDurationMs)
    } else {
        displayedPositionMsProvider().coerceIn(0L, safeDurationMs)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { size ->
                    if (size.width > 0) {
                        waveformWidthPx = size.width.toFloat()
                    }
                }
                .pointerInput(songId, safeDurationMs) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeekTo((fraction * safeDurationMs).toLong())
                    }
                }
                .pointerInput(songId, safeDurationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            scrubFraction = (startOffset.x / width).coerceIn(0f, 1f)
                            isScrubbing = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            val targetMs = (scrubFraction * safeDurationMs).toLong()
                            isScrubbing = false
                            onSeekTo(targetMs)
                        },
                        onDragCancel = {
                            isScrubbing = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            scrubFraction = (change.position.x / width).coerceIn(0f, 1f)
                        }
                    )
                }
                .testTag("full_player_seekbar"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val count = amplitudes.size
                if (count == 0 || size.width <= 0f || size.height <= 0f) return@Canvas

                val effectiveProgress = if (isScrubbing) {
                    scrubFraction.coerceIn(0f, 1f)
                } else {
                    progressProvider().coerceIn(0f, 1f)
                }

                val desiredBarWidthPx = 3.dp.toPx()
                val desiredGapPx = 2.5.dp.toPx()
                val desiredTotalWidth = count * desiredBarWidthPx + (count - 1) * desiredGapPx

                val barWidthPx: Float
                val gapPx: Float
                val startX: Float
                if (desiredTotalWidth <= size.width) {
                    barWidthPx = desiredBarWidthPx
                    gapPx = (size.width - count * barWidthPx) / (count - 1).coerceAtLeast(1)
                    startX = 0f
                } else {
                    val scale = size.width / desiredTotalWidth
                    barWidthPx = (desiredBarWidthPx * scale).coerceAtLeast(1.8.dp.toPx())
                    gapPx = ((size.width - count * barWidthPx) / (count - 1).coerceAtLeast(1))
                        .coerceAtLeast(1.dp.toPx())
                    startX = 0f
                }

                val minBarHeightPx = 6.dp.toPx()
                val maxBarHeightPx = 34.dp.toPx().coerceAtMost(size.height * 0.86f)
                val centerY = size.height / 2f

                val playheadFloatIndex = effectiveProgress * (count - 1)
                val activeBarIndex = playheadFloatIndex.toInt().coerceIn(0, count - 1)

                // Soft glowing halo behind the active playhead bar
                val playheadX = startX + playheadFloatIndex * (barWidthPx + gapPx) + barWidthPx / 2f
                val glowRadius = 26.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = if (isScrubbing) 0.56f else 0.38f),
                            animatedAccent.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(playheadX, centerY),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(playheadX, centerY)
                )

                val cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)

                for (i in 0 until count) {
                    val baseAmp = amplitudes[i]
                    val distFromPlayhead = abs(i - playheadFloatIndex)

                    // Live beat pulse wave concentrated smoothly around the active playhead
                    val beatModulation = if (playingIntensity > 0.005f && !isScrubbing && distFromPlayhead < 6.5f) {
                        val proximity = exp(-(distFromPlayhead * distFromPlayhead) / 8.0f)
                        val wave = (sin(wavePhase * 2f - i * 0.68f) * 0.15f +
                            cos(wavePhase * 3f + i * 0.42f) * 0.07f)
                        wave * proximity * playingIntensity
                    } else {
                        0f
                    }

                    // Scale up bar directly under the user's finger (1.25x) while scrubbing
                    val fingerScale = if (isScrubbing && distFromPlayhead < 1.6f) {
                        val influence = (1f - (distFromPlayhead / 1.6f)).coerceIn(0f, 1f)
                        1f + (scrubBarScale - 1f) * influence
                    } else {
                        1f
                    }

                    val rawHeight = (minBarHeightPx + (baseAmp + beatModulation).coerceIn(0.04f, 1.0f) *
                        (maxBarHeightPx - minBarHeightPx)) * fingerScale
                    val finalHeight = rawHeight.coerceIn(minBarHeightPx, size.height)

                    val x = startX + i * (barWidthPx + gapPx)
                    val topY = centerY - finalHeight / 2f

                    val barColor = when {
                        i < activeBarIndex -> animatedAccent
                        i == activeBarIndex -> {
                            if (isScrubbing) {
                                Color.White
                            } else {
                                Color(
                                    red = (animatedAccent.red * 0.72f + 0.28f).coerceIn(0f, 1f),
                                    green = (animatedAccent.green * 0.72f + 0.28f).coerceIn(0f, 1f),
                                    blue = (animatedAccent.blue * 0.72f + 0.28f).coerceIn(0f, 1f),
                                    alpha = 1f
                                )
                            }
                        }
                        else -> UNPLAYED_BAR_COLOR
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, topY),
                        size = Size(barWidthPx, finalHeight),
                        cornerRadius = cornerRadius
                    )
                }
            }

            // Floating Frosted Glass Timestamp Pill right above the user's finger while scrubbing
            androidx.compose.animation.AnimatedVisibility(
                visible = isScrubbing,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-34).dp)
                    .onSizeChanged { pillWidthPx = it.width }
                    .graphicsLayer {
                        val fingerX = scrubFraction * waveformWidthPx
                        val halfPill = pillWidthPx / 2f
                        val maxX = (waveformWidthPx - pillWidthPx).coerceAtLeast(0f)
                        translationX = (fingerX - halfPill).coerceIn(0f, maxX)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .bitchordGlass(hazeState = hazeState, cornerRadius = 50.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("waveform_scrub_timestamp_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatDurationMs(displayedMs),
                        style = TabularNumericStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Elapsed & total duration labels cleanly aligned directly beneath left & right waveform edges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(displayedMs),
                style = TabularNumericStyle,
                color = if (isScrubbing) animatedAccent else TextSecondary
            )
            Text(
                text = formatDurationMs(safeDurationMs),
                style = TabularNumericStyle,
                color = TextMuted
            )
        }
    }
}

/**
 * Generates a natural rhythmic musical silhouette of [count] normalized bar amplitudes (`0.08f..1.0f`),
 * seeded deterministically from [songId] so each track displays its own signature waveform.
 */
private fun generateWaveformAmplitudes(songId: String, count: Int): FloatArray {
    val seed = songId.hashCode().toLong() * 31L + 7_919L
    val random = Random(seed)
    val phaseA = random.nextFloat() * 6.28f
    val phaseB = random.nextFloat() * 6.28f
    val chorusShift = random.nextFloat() * 0.14f - 0.07f

    return FloatArray(count) { index ->
        val t = index.toFloat() / (count - 1).coerceAtLeast(1)

        // Organic song-structure dynamics: intro build -> chorus -> bridge -> final chorus -> outro
        val introOutroFade = when {
            t < 0.09f -> 0.35f + (t / 0.09f) * 0.65f
            t > 0.90f -> 0.32f + ((1f - t) / 0.10f) * 0.68f
            else -> 1.0f
        }
        val chorusWave = 0.56f +
            0.22f * sin((t * 3.2f + chorusShift) * PI.toFloat() + phaseA) +
            0.14f * cos(t * 7.5f * PI.toFloat() + phaseB)
        val beatPulse = 0.14f * abs(sin(index * 0.85f + phaseA))
        val microVariation = (random.nextFloat() - 0.5f) * 0.28f

        ((chorusWave + beatPulse + microVariation) * introOutroFade).coerceIn(0.08f, 1.0f)
    }
}
