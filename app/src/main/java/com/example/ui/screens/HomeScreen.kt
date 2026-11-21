package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DownloadState
import com.example.data.MostListenedSong
import com.example.data.Song
import com.example.ui.SonoraUiState
import com.example.ui.components.EmptyGlassCard
import com.example.ui.components.GenreFilterChip
import com.example.ui.components.LocalBackdropHazeState
import com.example.ui.components.SongArtworkImage
import com.example.ui.components.SongRowItem
import com.example.ui.components.formatDurationMs
import com.example.ui.components.liquidGlass
import com.example.ui.components.rememberStaggeredEntranceModifier
import com.example.ui.components.sonoraGlassCard
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.LocalAmbientPalette
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: SonoraUiState,
    bottomContentPadding: Dp,
    onSelectGenre: (String) -> Unit,
    onRetryHome: () -> Unit,
    onRefreshHome: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleDownload: (Song) -> Unit,
    onOpenAddToPlaylist: (Song) -> Unit,
    onOpenMoreOptions: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val pullToRefreshState = rememberPullToRefreshState()
    val featuredSong = uiState.trendingSongs.firstOrNull() ?: uiState.allSongs.firstOrNull()
    val recommendedSongs = uiState.recommendedFeedSongs
    val mostListened = uiState.mostListenedSongs

    PullToRefreshBox(
        isRefreshing = uiState.isHomeRefreshing,
        onRefresh = onRefreshHome,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isHomeRefreshing,
                containerColor = ObsidianElevated,
                color = ambient.secondaryGlow,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "home_header") {
                var quoteRevealed by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    quoteRevealed = true
                }
                val quoteTextColor = lerp(
                    start = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    stop = ambient.secondaryGlow.copy(alpha = 0.85f),
                    fraction = 0.35f
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_sonora_icon),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF9B68C8).copy(alpha = 0.50f),
                                            Color(0xFFF5A642).copy(alpha = 0.55f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .testTag("home_brand_logo")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            AnimatedContent(
                                targetState = if (quoteRevealed) uiState.homeQuote else "",
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(durationMillis = 460)) +
                                        slideInVertically(
                                            animationSpec = tween(durationMillis = 460),
                                            initialOffsetY = { it / 3 }
                                        )) togetherWith fadeOut(animationSpec = tween(durationMillis = 180))
                                },
                                label = "home_quote_transition"
                            ) { displayedQuote ->
                                Text(
                                    text = displayedQuote.ifEmpty { " " },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = quoteTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("home_rotating_quote")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "home_genres", contentType = "genres_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.genres,
                        key = { genre -> genre },
                        contentType = { "genre_chip" }
                    ) { genre ->
                        GenreFilterChip(
                            label = genre,
                            selected = uiState.selectedGenre == genre,
                            onClick = { onSelectGenre(genre) }
                        )
                    }
                }
            }

            if (uiState.isHomeLoading && recommendedSongs.isEmpty()) {
                item(key = "home_loading", contentType = "loading_card") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .sonoraGlassCard(shape = RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = AuroraCyan,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Curating a fresh mix for you…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else if (uiState.homeError != null && recommendedSongs.isEmpty()) {
                item(key = "home_error", contentType = "error_card") {
                    EmptyGlassCard(
                        title = "Catalog unavailable",
                        subtitle = uiState.homeError,
                        actionLabel = "Retry",
                        onActionClick = onRetryHome,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                if (featuredSong != null) {
                    item(key = "home_hero", contentType = "hero_card") {
                        FeaturedHeroCard(
                            song = featuredSong,
                            onPlayMix = {
                                onPlaySong(featuredSong, uiState.trendingSongs.ifEmpty { recommendedSongs })
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                if (uiState.trendingSongs.isNotEmpty()) {
                    item(key = "home_trending_header", contentType = "section_header") {
                        Text(
                            text = stringResource(R.string.home_trending_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    item(key = "home_trending_row", contentType = "trending_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = uiState.trendingSongs,
                                key = { song -> song.id },
                                contentType = { "song_item" }
                            ) { song ->
                                val isCurrent = uiState.playback.currentSong?.id == song.id
                                TrendingSongCard(
                                    song = song,
                                    isCurrent = isCurrent,
                                    onClick = { onPlaySong(song, uiState.trendingSongs) }
                                )
                            }
                        }
                    }
                }

                // Personalized "Most Listened" (On Repeat) section directly below Trending Now
                if (mostListened.isNotEmpty()) {
                    item(key = "home_most_listened_header", contentType = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_most_listened_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = ambient.primaryGlow.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, ambient.secondaryGlow.copy(alpha = 0.42f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Repeat,
                                        contentDescription = null,
                                        tint = ambient.secondaryGlow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.home_on_repeat_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ambient.secondaryGlow
                                    )
                                }
                            }
                        }
                    }

                    item(key = "home_most_listened_row", contentType = "most_listened_row") {
                        val mostListenedQueue = mostListened.map { it.song }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.testTag("home_most_listened_row")
                        ) {
                            items(
                                items = mostListened,
                                key = { entry -> "most_${entry.song.id}" },
                                contentType = { "most_listened_item" }
                            ) { entry ->
                                val isCurrent = uiState.playback.currentSong?.id == entry.song.id
                                MostListenedSongCard(
                                    entry = entry,
                                    isCurrent = isCurrent,
                                    onClick = { onPlaySong(entry.song, mostListenedQueue) }
                                )
                            }
                        }
                    }
                }

                item(key = "home_recommended_header", contentType = "section_header") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = stringResource(R.string.home_recommended_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        if (uiState.selectedGenre == "All" && uiState.topArtists.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Blended with ${uiState.topArtists.take(2).joinToString(" & ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ambient.secondaryGlow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = recommendedSongs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "song_item" }
                ) { index, song ->
                    val isCurrent = uiState.playback.currentSong?.id == song.id
                    val entranceModifier = rememberStaggeredEntranceModifier(
                        index = index,
                        triggerKey = "${uiState.selectedGenre}_${song.id}"
                    )
                    SongRowItem(
                        song = song,
                        isCurrentSong = isCurrent,
                        isPlaying = uiState.playback.isPlaying,
                        isBuffering = isCurrent && uiState.playback.isBuffering,
                        downloadState = uiState.downloadStates[song.id] ?: DownloadState.NotDownloaded,
                        onPlayClick = { onPlaySong(song, recommendedSongs) },
                        onDownloadClick = { onToggleDownload(song) },
                        onAddToPlaylistClick = { onOpenAddToPlaylist(song) },
                        onMoreOptionsClick = { onOpenMoreOptions(song) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .then(entranceModifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun MostListenedSongCard(
    entry: MostListenedSong,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val ambient = LocalAmbientPalette.current
    val song = entry.song

    Column(
        modifier = Modifier
            .width(164.dp)
            .sonoraGlassCard(
                shape = RoundedCornerShape(22.dp),
                highlighted = isCurrent,
                accentBorderColor = if (isCurrent) ambient.secondaryGlow else null
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("most_listened_card_${song.id}")
    ) {
        Box {
            SongArtworkImage(
                artworkUrl = song.artworkUrl,
                fallbackArtworkRes = song.fallbackArtworkRes,
                contentDescription = stringResource(R.string.cd_album_art),
                decodeSizePx = 320,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
                cornerRadius = 14.dp
            )

            // Frosted Play-Count Pill Badge (e.g. ▶ 5x)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF101218).copy(alpha = 0.78f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("play_count_badge_${song.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = ambient.secondaryGlow,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${entry.playCount}x",
                    style = TabularNumericStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = if (isCurrent) ambient.secondaryGlow else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) ambient.secondaryGlow else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatDurationMs(song.duration),
                style = TabularNumericStyle,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun FeaturedHeroCard(
    song: Song,
    onPlayMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
    val backdropHazeState = LocalBackdropHazeState.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                hazeState = backdropHazeState,
                cornerRadius = 26.dp,
                tintColor = ambient.secondaryGlow,
                isInteractive = true
            )
            .clickable(onClick = onPlayMix)
            .padding(16.dp)
            .testTag("home_featured_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SongArtworkImage(
                artworkUrl = song.artworkUrl,
                fallbackArtworkRes = song.fallbackArtworkRes,
                contentDescription = stringResource(R.string.cd_album_art),
                decodeSizePx = 280,
                modifier = Modifier.size(106.dp),
                cornerRadius = 18.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ambient.primaryGlow.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, GlassBorderTop)
                ) {
                    Text(
                        text = stringResource(R.string.home_featured_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = song.album,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.title} • ${song.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onPlayMix,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ambient.secondaryGlow,
                        contentColor = ObsidianVoid
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("home_play_mix_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.home_play_mix),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingSongCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val ambient = LocalAmbientPalette.current
    Column(
        modifier = Modifier
            .width(164.dp)
            .sonoraGlassCard(
                shape = RoundedCornerShape(22.dp),
                highlighted = isCurrent,
                accentBorderColor = if (isCurrent) ambient.secondaryGlow else null
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("trending_card_${song.id}")
    ) {
        Box {
            SongArtworkImage(
                artworkUrl = song.artworkUrl,
                fallbackArtworkRes = song.fallbackArtworkRes,
                contentDescription = stringResource(R.string.cd_album_art),
                decodeSizePx = 320,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
                cornerRadius = 14.dp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = if (isCurrent) ambient.secondaryGlow else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) ambient.secondaryGlow else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatDurationMs(song.duration),
                style = TabularNumericStyle,
                color = TextMuted
            )
        }
    }
}
