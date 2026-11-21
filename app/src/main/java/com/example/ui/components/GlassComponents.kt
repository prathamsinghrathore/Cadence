package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.data.DownloadState
import com.example.data.Song
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.LocalAmbientPalette
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import java.util.Locale

// CompositionLocal providing the ambient artwork/blob backdrop HazeState to hero cards
// inside NavHost screens without creating a HazeSource self-feedback loop.
val LocalBackdropHazeState = staticCompositionLocalOf<HazeState?> { null }

// Pre-allocated Apple iOS Liquid Glass optical constants & HazeStyle.
private val LiquidGlassBackdropBase = Color(0xFF12141A)
private val BitChordAcrylicTop = Color(0xFF181A20)
private val BitChordAcrylicBottom = Color(0xFF101216)

private val LiquidGlassHazeStyle = HazeStyle(
    backgroundColor = LiquidGlassBackdropBase.copy(alpha = 0.36f),
    tint = HazeTint(Color.Transparent),
    blurRadius = 36.dp,
    noiseFactor = 0.025f,
    fallbackTint = HazeTint(Color(0xFF14161E).copy(alpha = 0.68f))
)

private val LiquidGlassPressSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMediumLow
)

private val BitChordCardFillBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.10f),
        Color.White.copy(alpha = 0.03f),
        BitChordAcrylicBottom.copy(alpha = 0.56f)
    )
)

private val BitChordCardActiveFillBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.14f),
        Color(0xFF202432).copy(alpha = 0.44f),
        Color(0xFF12141C).copy(alpha = 0.60f)
    )
)

private val BitChordSpecularRimBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.36f),
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.22f)
    )
)

/**
 * Apple iOS-style Liquid Glass modifier featuring 5 GPU-cached optical layers:
 * 1. Vibrant Backdrop Blur (36.dp Haze blur + 0.025f optical grain, luminous dark base)
 * 2. Liquid Translucent Body Fill (multi-stop vertical sheen + active Palette color diffusion)
 * 3. Curved Top Specular Reflection (convex top 28.dp glass meniscus light catch)
 * 4. Inner Depth Shadow (1.5.dp inset dark cavity rim)
 * 5. Chromatic / Specular Rim Border (1.dp diagonal bevel refraction)
 *
 * When [isInteractive] is true, adds a non-consuming GPU spring scale press (0.97f)
 * and a subtle radial light bloom centered on the user's touch point.
 */
fun Modifier.liquidGlass(
    hazeState: HazeState?,
    cornerRadius: Dp = 26.dp,
    tintColor: Color = Color.Unspecified,
    isInteractive: Boolean = false
): Modifier = liquidGlass(
    hazeState = hazeState,
    shape = RoundedCornerShape(cornerRadius),
    cornerRadiusHint = cornerRadius,
    tintColor = tintColor,
    isInteractive = isInteractive
)

fun Modifier.liquidGlass(
    hazeState: HazeState?,
    shape: Shape,
    cornerRadiusHint: Dp = 26.dp,
    tintColor: Color = Color.Unspecified,
    isInteractive: Boolean = false
): Modifier = composed {
    val ambient = LocalAmbientPalette.current
    val resolvedAccent = if (tintColor != Color.Unspecified) tintColor else ambient.vibrantColor
    val activeAccentColor by animateColorAsState(
        targetValue = resolvedAccent,
        animationSpec = tween(durationMillis = 520),
        label = "liquidGlassAccent"
    )

    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }

    val pressScale by animateFloatAsState(
        targetValue = if (isInteractive && isPressed) 0.97f else 1f,
        animationSpec = LiquidGlassPressSpring,
        label = "liquidGlassPressScale"
    )
    val touchBloomAlpha by animateFloatAsState(
        targetValue = if (isInteractive && isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "liquidGlassTouchBloom"
    )

    val touchObserver = if (isInteractive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                touchOffset = down.position
                isPressed = true
                try {
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                } finally {
                    isPressed = false
                }
            }
        }
    } else {
        Modifier
    }

    val gpuClipped = this
        .then(touchObserver)
        .graphicsLayer {
            if (isInteractive) {
                scaleX = pressScale
                scaleY = pressScale
            }
            clip = true
            this.shape = shape
        }

    // Layer 1: Vibrant Backdrop Blur (Haze 36.dp Gaussian + 0.025f frosted grain)
    val blurred = if (hazeState != null) {
        gpuClipped.hazeEffect(
            state = hazeState,
            style = LiquidGlassHazeStyle
        )
    } else {
        gpuClipped
    }

    // Layers 2–5 + Touch Light Bloom: all Brushes, Paths, and Strokes cached per size on GPU
    blurred.drawWithCache {
        val w = size.width
        val h = size.height
        val maxRadius = minOf(w, h) / 2f
        val radiusPx = if (shape == CircleShape) {
            maxRadius
        } else {
            cornerRadiusHint.toPx().coerceIn(0f, maxRadius)
        }
        val cornerRadiusObj = CornerRadius(radiusPx, radiusPx)

        // Layer 2 — Liquid Translucent Body Fill + vibrant Palette diffusion
        val backdropScrimColor = LiquidGlassBackdropBase.copy(alpha = 0.34f)
        val verticalSheenBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.04f),
                Color.Black.copy(alpha = 0.18f)
            ),
            startY = 0f,
            endY = h
        )
        val paletteTintBrush = Brush.linearGradient(
            colors = listOf(
                activeAccentColor.copy(alpha = 0.14f),
                activeAccentColor.copy(alpha = 0.09f),
                activeAccentColor.copy(alpha = 0.12f)
            ),
            start = Offset.Zero,
            end = Offset(w, h)
        )

        // Layer 3 — Curved Top Specular Reflection (convex top 28.dp light catch)
        val specularHeight = minOf(28.dp.toPx(), h * 0.52f).coerceAtLeast(1f)
        val topSpecularPath = Path().apply {
            addOval(
                Rect(
                    left = -w * 0.12f,
                    top = -specularHeight * 0.42f,
                    right = w * 1.12f,
                    bottom = specularHeight
                )
            )
        }
        val topSpecularBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            ),
            startY = 0f,
            endY = specularHeight
        )

        // Layer 4 — Inner Depth Shadow (1.5.dp inset dark rim just inside outer edge)
        val innerShadowWidthPx = 1.5.dp.toPx()
        val innerShadowInset = 1.dp.toPx() + innerShadowWidthPx / 2f
        val innerShadowTopLeft = Offset(innerShadowInset, innerShadowInset)
        val innerShadowSize = Size(
            width = (w - innerShadowInset * 2f).coerceAtLeast(0f),
            height = (h - innerShadowInset * 2f).coerceAtLeast(0f)
        )
        val innerShadowCorner = CornerRadius(
            (radiusPx - innerShadowInset).coerceAtLeast(0f),
            (radiusPx - innerShadowInset).coerceAtLeast(0f)
        )
        val innerShadowStroke = Stroke(width = innerShadowWidthPx)
        val innerShadowColor = Color.Black.copy(alpha = 0.25f)

        // Layer 5 — Chromatic / Specular Rim Border (1.dp diagonal bevel refraction)
        val rimWidthPx = 1.dp.toPx()
        val rimInset = rimWidthPx / 2f
        val rimTopLeft = Offset(rimInset, rimInset)
        val rimSize = Size(
            width = (w - rimWidthPx).coerceAtLeast(0f),
            height = (h - rimWidthPx).coerceAtLeast(0f)
        )
        val rimCorner = CornerRadius(
            (radiusPx - rimInset).coerceAtLeast(0f),
            (radiusPx - rimInset).coerceAtLeast(0f)
        )
        val rimStroke = Stroke(width = rimWidthPx)
        val chromaticRimBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.52f),
                Color.White.copy(alpha = 0.10f),
                activeAccentColor.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.35f)
            ),
            start = Offset.Zero,
            end = Offset(w, h)
        )

        val bloomRadius = (maxOf(w, h) * 0.52f).coerceIn(110f, 360f)

        onDrawWithContent {
            // Draw translucent glass body & top specular dome behind foreground content
            drawRoundRect(
                color = backdropScrimColor,
                cornerRadius = cornerRadiusObj
            )
            drawRoundRect(
                brush = paletteTintBrush,
                cornerRadius = cornerRadiusObj
            )
            drawRoundRect(
                brush = verticalSheenBrush,
                cornerRadius = cornerRadiusObj
            )
            drawPath(
                path = topSpecularPath,
                brush = topSpecularBrush
            )

            // Foreground text, icons, artwork
            drawContent()

            // Tactile radial light bloom on touch (interactive glass surfaces only)
            if (isInteractive && touchBloomAlpha > 0.01f && touchOffset != Offset.Unspecified) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f * touchBloomAlpha),
                            activeAccentColor.copy(alpha = 0.12f * touchBloomAlpha),
                            Color.Transparent
                        ),
                        center = touchOffset,
                        radius = bloomRadius
                    )
                )
            }

            // Inner depth shadow & outer chromatic refraction bevel
            if (innerShadowSize.width > 0f && innerShadowSize.height > 0f) {
                drawRoundRect(
                    color = innerShadowColor,
                    topLeft = innerShadowTopLeft,
                    size = innerShadowSize,
                    cornerRadius = innerShadowCorner,
                    style = innerShadowStroke
                )
            }
            if (rimSize.width > 0f && rimSize.height > 0f) {
                drawRoundRect(
                    brush = chromaticRimBrush,
                    topLeft = rimTopLeft,
                    size = rimSize,
                    cornerRadius = rimCorner,
                    style = rimStroke
                )
            }
        }
    }
}

/**
 * Legacy alias delegating seamlessly to the upgraded 5-layer [liquidGlass] shader so all
 * floating overlays, lyrics panels, and dialogs inherit iOS Liquid Glass optics automatically.
 */
fun Modifier.bitchordGlass(
    hazeState: HazeState?,
    cornerRadius: Dp = 26.dp,
    tintColor: Color = Color.Unspecified,
    isInteractive: Boolean = false
): Modifier = liquidGlass(
    hazeState = hazeState,
    cornerRadius = cornerRadius,
    tintColor = tintColor,
    isInteractive = isInteractive
)

fun Modifier.bitchordGlass(
    hazeState: HazeState?,
    shape: Shape,
    tintColor: Color = Color.Unspecified,
    isInteractive: Boolean = false
): Modifier = liquidGlass(
    hazeState = hazeState,
    shape = shape,
    cornerRadiusHint = if (shape == CircleShape) 999.dp else 26.dp,
    tintColor = tintColor,
    isInteractive = isInteractive
)

/**
 * Lightweight Liquid Glass card modifier for scrolling LazyColumn/LazyRow items.
 * Features translucent sheen, subtle top specular catch, and diagonal bevel rim with zero
 * per-item GPU blur passes so scrolling lists stay locked at 60/120 FPS.
 */
fun Modifier.sonoraGlassCard(
    shape: Shape = RoundedCornerShape(18.dp),
    highlighted: Boolean = false,
    accentBorderColor: Color? = null
): Modifier {
    val borderBrush = if (accentBorderColor != null) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                accentBorderColor.copy(alpha = 0.50f),
                accentBorderColor.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.24f)
            )
        )
    } else {
        BitChordSpecularRimBrush
    }
    return this
        .graphicsLayer {
            clip = true
            this.shape = shape
        }
        .background(
            brush = if (highlighted) BitChordCardActiveFillBrush else BitChordCardFillBrush,
            shape = shape
        )
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}

/**
 * GPU-only staggered vertical slide-up + fade entrance for LazyColumn list items.
 * Reads animation progress exclusively inside Modifier.graphicsLayer so it never
 * triggers recompositions or layout passes during the slide.
 */
@Composable
fun rememberStaggeredEntranceModifier(
    index: Int,
    triggerKey: Any = Unit,
    slideDistance: Dp = 20.dp
): Modifier {
    val progress = remember(triggerKey) { Animatable(0f) }
    val slidePx = with(LocalDensity.current) { slideDistance.toPx() }
    LaunchedEffect(triggerKey) {
        val staggerMs = (index.coerceIn(0, 7) * 26L)
        if (staggerMs > 0L) delay(staggerMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return Modifier.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * slidePx
    }
}

@Composable
fun AmbientGradientBackground(
    currentSong: Song? = null,
    backdropHazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val ambient = LocalAmbientPalette.current
    val vibrantColor by animateColorAsState(
        targetValue = ambient.vibrantColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientVibrant"
    )
    val dominantColor by animateColorAsState(
        targetValue = ambient.dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientDominant"
    )
    val lightVibrantColor by animateColorAsState(
        targetValue = ambient.lightVibrantColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientLightVibrant"
    )
    val deepTint by animateColorAsState(
        targetValue = ambient.deepTint,
        animationSpec = tween(durationMillis = 800),
        label = "ambientDeep"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // GPU-accelerated dynamic song-colored backdrop layer (dedicated HazeSource for hero cards)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backdropHazeState != null) {
                        Modifier.hazeSource(state = backdropHazeState)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer()
        ) {
            // Subtle deep tinted foundation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                deepTint.copy(alpha = 0.55f),
                                ObsidianBase,
                                ObsidianVoid
                            )
                        )
                    )
            )

            // 1. Current song's album artwork scaled up + heavy 70.dp Gaussian blur at 0.45f alpha
            Crossfade(
                targetState = currentSong,
                animationSpec = tween(durationMillis = 800),
                label = "ambientArtworkCrossfade"
            ) { song ->
                if (song != null) {
                    SongArtworkImage(
                        artworkUrl = song.artworkUrl,
                        fallbackArtworkRes = song.fallbackArtworkRes,
                        contentDescription = null,
                        cornerRadius = 0.dp,
                        decodeSizePx = 160,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.45f
                                scaleX = 1.24f
                                scaleY = 1.24f
                            }
                            .blur(70.dp)
                    )
                }
            }

            // 2. Three large soft radial gradient blobs from vibrant, dominant & lightVibrant swatches
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                val maxDim = maxOf(size.width, size.height).coerceAtLeast(1f)

                // Blob 1: Vibrant swatch glowing across upper-left & hero region
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            vibrantColor.copy(alpha = 0.52f),
                            vibrantColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.18f),
                        radius = maxDim * 0.72f
                    )
                )

                // Blob 2: Dominant swatch floating across center-right behind cards & options sheets
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.48f),
                            dominantColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.86f, size.height * 0.52f),
                        radius = maxDim * 0.70f
                    )
                )

                // Blob 3: Light Vibrant swatch shining right behind the floating mini-player & bottom bar
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            lightVibrantColor.copy(alpha = 0.42f),
                            lightVibrantColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.34f, size.height * 0.86f),
                        radius = maxDim * 0.66f
                    )
                )
            }

            // 3. Subtle dark scrim ONLY at the very top/bottom edges so center & glass panels stay luminous
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.42f),
                            0.14f to Color.Black.copy(alpha = 0.10f),
                            0.55f to Color.Black.copy(alpha = 0.08f),
                            0.84f to Color.Black.copy(alpha = 0.24f),
                            1.0f to Color.Black.copy(alpha = 0.45f)
                        )
                    )
            )
        }

        CompositionLocalProvider(LocalBackdropHazeState provides backdropHazeState) {
            content()
        }
    }
}

@Composable
fun SongArtworkImage(
    artworkUrl: String,
    @DrawableRes fallbackArtworkRes: Int?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    decodeSizePx: Int = 160
) {
    val context = LocalContext.current
    val dataModel: Any = artworkUrl.ifBlank {
        fallbackArtworkRes ?: R.drawable.img_album_nebula_drift
    }
    val fallbackRes = fallbackArtworkRes ?: R.drawable.img_album_nebula_drift
    val request = remember(dataModel, fallbackRes, decodeSizePx) {
        ImageRequest.Builder(context)
            .data(dataModel)
            .size(decodeSizePx)
            .crossfade(150)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${dataModel}_$decodeSizePx")
            .placeholder(fallbackRes)
            .error(fallbackRes)
            .build()
    }
    val clipModifier = if (cornerRadius > 0.dp) {
        modifier.clip(RoundedCornerShape(cornerRadius))
    } else {
        modifier
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = clipModifier.background(ObsidianElevated)
    )
}

@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    tint: Color = AuroraCyan,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar3"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.35f, 0.55f, 0.4f)
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16 * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint)
            )
        }
    }
}

@Composable
fun SongRowItem(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    downloadState: DownloadState,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onMoreOptionsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val isOfflineReady = downloadState is DownloadState.Downloaded || song.isDownloaded

    Row(
        modifier = modifier
            .fillMaxWidth()
            .sonoraGlassCard(
                shape = RoundedCornerShape(18.dp),
                highlighted = isCurrentSong,
                accentBorderColor = if (isCurrentSong) ambient.secondaryGlow else null
            )
            .clickable(onClick = onPlayClick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .testTag("song_row_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            SongArtworkImage(
                artworkUrl = song.artworkUrl,
                fallbackArtworkRes = song.fallbackArtworkRes,
                contentDescription = stringResource(R.string.cd_album_art),
                modifier = Modifier.size(54.dp),
                cornerRadius = 12.dp
            )
            if (isCurrentSong) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ambient.secondaryGlow,
                            strokeWidth = 2.dp
                        )
                    } else {
                        AnimatedEqualizerBars(
                            isPlaying = isPlaying,
                            tint = ambient.secondaryGlow
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentSong) ambient.secondaryGlow else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isOfflineReady) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.player_downloaded),
                        tint = AuroraCyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = formatDurationMs(song.duration),
                    style = TabularNumericStyle,
                    color = TextMuted
                )
            }
        }

        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("download_btn_${song.id}")
        ) {
            when (downloadState) {
                is DownloadState.Downloading -> {
                    CircularProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.size(20.dp),
                        color = AuroraCyan,
                        strokeWidth = 2.dp
                    )
                }
                is DownloadState.Downloaded -> {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = stringResource(R.string.cd_remove_download),
                        tint = AuroraCyan
                    )
                }
                DownloadState.NotDownloaded -> {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.cd_download_track),
                        tint = TextSecondary
                    )
                }
            }
        }

        IconButton(
            onClick = onMoreOptionsClick ?: onAddToPlaylistClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("more_options_btn_${song.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
                tint = TextSecondary
            )
        }
    }
}

@Composable
fun GenreFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            ambient.primaryGlow.copy(alpha = 0.28f)
        } else {
            BitChordAcrylicTop.copy(alpha = 0.62f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                ambient.primaryGlow.copy(alpha = 0.72f)
            } else {
                Color.White.copy(alpha = 0.14f)
            }
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("genre_chip_${label.lowercase(Locale.US)}")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
fun EmptyGlassCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sonoraGlassCard(shape = RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.18f))
                .border(1.dp, GlassBorderTop, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = AuroraCyan,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
