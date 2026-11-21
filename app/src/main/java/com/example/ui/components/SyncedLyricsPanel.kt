package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.lyrics.LrcParser
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.LyricsUiState
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.LocalAmbientPalette
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Apple Music / BitChord-style Live Time-Synced Lyrics panel built on top of real-time
 * frosted acrylic glass ([bitchordGlass]).
 *
 * Performance guarantee:
 * - Playback position ticks (`positionFlow`) are collected ONLY inside [SyncedLyricsScroller]
 *   so the outer Now Playing layout, blurred backdrop, and transport controls never recompose.
 * - Line scale & alpha animations run exclusively inside `Modifier.graphicsLayer` on the GPU.
 */
@Composable
fun SyncedLyricsPanel(
    songId: String,
    lyricsState: LyricsUiState,
    positionFlow: StateFlow<Long>,
    hazeState: HazeState,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onShowArtwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current

    Column(
        modifier = modifier
            .bitchordGlass(hazeState = hazeState, cornerRadius = 26.dp)
            .testTag("synced_lyrics_panel")
    ) {
        // Compact frosted header bar inside the lyrics glass container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isSynced = lyricsState is LyricsUiState.Synced && lyricsState.songId == songId
                val badgeDotColor = when {
                    isSynced -> ambient.secondaryGlow
                    lyricsState is LyricsUiState.Plain && lyricsState.songId == songId -> AuroraCyan
                    else -> TextMuted
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeDotColor)
                )
                Text(
                    text = when {
                        isSynced -> stringResource(R.string.lyrics_live_synced)
                        lyricsState is LyricsUiState.Plain && lyricsState.songId == songId ->
                            stringResource(R.string.lyrics_plain_badge)
                        else -> stringResource(R.string.lyrics_title)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp
                )
            }

            IconButton(
                onClick = onShowArtwork,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_show_artwork_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = stringResource(R.string.cd_show_artwork),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Main lyrics viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                lyricsState is LyricsUiState.Synced && lyricsState.songId == songId -> {
                    SyncedLyricsScroller(
                        songId = songId,
                        lines = lyricsState.lines,
                        positionFlow = positionFlow,
                        hazeState = hazeState,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                lyricsState is LyricsUiState.Plain && lyricsState.songId == songId -> {
                    PlainLyricsScroller(
                        lines = lyricsState.lines,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                lyricsState is LyricsUiState.Instrumental && lyricsState.songId == songId -> {
                    LyricsStatusPlaceholder(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.lyrics_instrumental_title),
                        subtitle = stringResource(R.string.lyrics_instrumental_subtitle),
                        actionLabel = null,
                        hazeState = hazeState,
                        onAction = null
                    )
                }

                lyricsState is LyricsUiState.NotFound && lyricsState.songId == songId -> {
                    LyricsStatusPlaceholder(
                        icon = Icons.Rounded.Lyrics,
                        title = stringResource(R.string.lyrics_not_found_title),
                        subtitle = lyricsState.message,
                        actionLabel = stringResource(R.string.lyrics_retry),
                        hazeState = hazeState,
                        onAction = onRetry
                    )
                }

                else -> {
                    LyricsLoadingSkeleton(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Isolated composable that collects `positionFlow` locally so 250ms position ticks
 * only recompute `activeIndex` inside the lyrics list.
 */
@Composable
private fun SyncedLyricsScroller(
    songId: String,
    lines: List<LyricLine>,
    positionFlow: StateFlow<Long>,
    hazeState: HazeState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val currentPositionMs by positionFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var isUserScrolling by remember(songId) { mutableStateOf(false) }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserScrolling = true
        }
    }

    val activeIndex = remember(currentPositionMs, lines) {
        lines.indexOfLast { it.timeMs <= currentPositionMs }
    }

    // Smoothly auto-center the active lyric line unless the user is manually browsing
    LaunchedEffect(activeIndex, isUserScrolling, viewportHeightPx) {
        if (!isUserScrolling && activeIndex >= 0 && viewportHeightPx > 0) {
            val centerOffset = (viewportHeightPx * 0.36f).toInt()
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -centerOffset
            )
        }
    }

    Box(
        modifier = modifier.onSizeChanged { size ->
            if (size.height > 0 && size.height != viewportHeightPx) {
                viewportHeightPx = size.height
            }
        }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 24.dp,
                top = 68.dp,
                bottom = 86.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .lyricsEdgeFadeMask()
                .testTag("synced_lyrics_list")
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${songId}_${index}_${line.timeMs}" },
                contentType = { _, _ -> "synced_lyric_line" }
            ) { index, line ->
                val isActive = index == activeIndex
                SyncedLyricRow(
                    line = line,
                    isActive = isActive,
                    glowColor = ambient.secondaryGlow,
                    onClick = {
                        isUserScrolling = false
                        onSeekTo(line.timeMs)
                    }
                )
            }
        }

        // Floating Frosted Glass "Sync" pill when user manually scrolls away
        AnimatedVisibility(
            visible = isUserScrolling,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .bitchordGlass(hazeState = hazeState, cornerRadius = 50.dp)
                    .clickable {
                        isUserScrolling = false
                        val targetIdx = activeIndex.coerceAtLeast(0)
                        val centerOffset = (viewportHeightPx * 0.36f).toInt()
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                index = targetIdx,
                                scrollOffset = -centerOffset
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("lyrics_sync_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.cd_sync_lyrics),
                    tint = ambient.secondaryGlow,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.lyrics_sync),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricRow(
    line: LyricLine,
    isActive: Boolean,
    glowColor: Color,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyricLineScale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.38f,
        animationSpec = tween(durationMillis = 220),
        label = "lyricLineAlpha"
    )

    val isInstrumentalDots = line.text == LrcParser.INSTRUMENTAL_BREAK_SYMBOL

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
    ) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (isInstrumentalDots) 24.sp else 20.sp,
                lineHeight = 28.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = if (isInstrumentalDots) 4.sp else 0.sp,
                shadow = if (isActive) {
                    Shadow(
                        color = glowColor.copy(alpha = 0.65f),
                        blurRadius = 18f
                    )
                } else {
                    null
                }
            ),
            color = if (isActive) TextPrimary else TextPrimary.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

@Composable
private fun PlainLyricsScroller(
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 28.dp,
            bottom = 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .lyricsEdgeFadeMask()
            .testTag("plain_lyrics_list")
    ) {
        itemsIndexed(
            items = lines,
            key = { idx, _ -> "plain_$idx" },
            contentType = { _, _ -> "plain_lyric_line" }
        ) { _, text ->
            if (text.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun LyricsLoadingSkeleton(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "lyricsSkeleton")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lyricsSkeletonAlpha"
    )

    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val widths = listOf(0.74f, 0.90f, 0.62f, 0.84f, 0.54f, 0.78f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer { alpha = pulseAlpha }
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lyrics_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun LyricsStatusPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    hazeState: HazeState,
    onAction: (() -> Unit)?
) {
    val ambient = LocalAmbientPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ambient.secondaryGlow.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ambient.secondaryGlow,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .bitchordGlass(hazeState = hazeState, cornerRadius = 50.dp)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("lyrics_retry_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = ambient.secondaryGlow,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Applies a silky top & bottom vertical alpha fade mask using an offscreen GPU layer
 * so scrolling lyric lines melt seamlessly into the frosted acrylic container.
 */
private fun Modifier.lyricsEdgeFadeMask(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.14f to Color.Black,
                0.86f to Color.Black,
                1.00f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }
