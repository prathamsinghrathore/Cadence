package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DownloadState
import com.example.data.PlaylistUiModel
import com.example.data.Song
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.LocalAmbientPalette
import com.example.ui.theme.PulseRose
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState

/**
 * In-window animated BitChord frosted glass overlay host.
 * Because it renders in the same window as the root HazeSource, Modifier.bitchordGlass
 * captures genuine 32.dp Gaussian backdrop blur over live album art and screens at 60/120 FPS.
 */
@Composable
fun BitChordGlassSheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    centerDialog: Boolean = false,
    popupMenu: Boolean = false,
    content: @Composable () -> Unit
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = if (centerDialog) Alignment.Center else Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = when {
                popupMenu -> {
                    fadeIn(tween(180)) +
                        scaleIn(
                            initialScale = 0.84f,
                            transformOrigin = TransformOrigin(1f, 0f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) +
                        slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                }
                centerDialog -> {
                    fadeIn(tween(200)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                else -> {
                    fadeIn(tween(200)) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            },
            exit = when {
                popupMenu -> {
                    fadeOut(tween(150)) +
                        scaleOut(
                            targetScale = 0.86f,
                            transformOrigin = TransformOrigin(1f, 0f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) +
                        slideOutVertically(
                            targetOffsetY = { it / 8 },
                            animationSpec = tween(150)
                        )
                }
                centerDialog -> {
                    fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150))
                }
                else -> {
                    fadeOut(tween(150)) + slideOutVertically(
                        targetOffsetY = { it / 3 },
                        animationSpec = tween(160)
                    )
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (popupMenu) {
                            transformOrigin = TransformOrigin(1f, 0f)
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume taps inside panel so backdrop doesn't dismiss
                    )
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GlassSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
        )
    }
}

@Composable
fun SongOptionsGlassSheet(
    song: Song?,
    downloadState: DownloadState,
    isFavorite: Boolean,
    sleepTimerRemainingMs: Long?,
    sleepTimerEndOfTrack: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (Song) -> Unit,
    onOpenArtist: (Song) -> Unit,
    onOpenSongInfo: (Song) -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    BitChordGlassSheetHost(
        visible = song != null,
        onDismiss = onDismiss,
        popupMenu = true
    ) {
        val activeSong = song ?: return@BitChordGlassSheetHost
        val ambient = LocalAmbientPalette.current
        val sleepBadge = when {
            sleepTimerRemainingMs != null -> formatDurationMs(sleepTimerRemainingMs)
            sleepTimerEndOfTrack -> "End of track"
            else -> null
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, cornerRadius = 28.dp)
                .padding(bottom = 14.dp)
                .verticalScroll(rememberScrollState())
                .testTag("song_options_glass_sheet")
        ) {
            GlassSheetHandle()

            // Header with song thumbnail, title, artist & favorite quick toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtworkImage(
                    artworkUrl = activeSong.artworkUrl,
                    fallbackArtworkRes = activeSong.fallbackArtworkRes,
                    contentDescription = stringResource(R.string.cd_album_art),
                    decodeSizePx = 160,
                    modifier = Modifier.size(54.dp),
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSong.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${activeSong.artist} • ${activeSong.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onToggleFavorite(activeSong) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (isFavorite) PulseRose else TextSecondary
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Clean BitChord vertical action list
            GlassMenuOptionRow(
                icon = Icons.Rounded.QueuePlayNext,
                label = stringResource(R.string.option_play_next),
                onClick = {
                    onPlayNext(activeSong)
                    onDismiss()
                },
                testTag = "option_play_next"
            )

            GlassMenuOptionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = stringResource(R.string.option_add_to_queue),
                onClick = {
                    onAddToQueue(activeSong)
                    onDismiss()
                },
                testTag = "option_add_to_queue"
            )

            val (downloadIcon, downloadLabel, downloadTint, downloadTrailing) = when (downloadState) {
                is DownloadState.Downloading -> Quadruple(
                    Icons.Rounded.Download,
                    stringResource(R.string.player_downloading),
                    AuroraCyan,
                    "${(downloadState.progress * 100).toInt()}%"
                )
                is DownloadState.Downloaded -> Quadruple(
                    Icons.Rounded.DownloadDone,
                    stringResource(R.string.player_downloaded),
                    AuroraCyan,
                    "Offline ready"
                )
                DownloadState.NotDownloaded -> Quadruple(
                    Icons.Rounded.Download,
                    stringResource(R.string.player_download),
                    TextPrimary,
                    activeSong.audioQualityLabel
                )
            }
            GlassMenuOptionRow(
                icon = downloadIcon,
                label = downloadLabel,
                iconTint = downloadTint,
                trailingText = downloadTrailing,
                onClick = {
                    onToggleDownload(activeSong)
                    onDismiss()
                },
                testTag = "option_download"
            )

            GlassMenuOptionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(R.string.player_add_to_playlist),
                onClick = {
                    onDismiss()
                    onAddToPlaylist(activeSong)
                },
                testTag = "option_add_to_playlist"
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            GlassMenuOptionRow(
                icon = Icons.Rounded.Album,
                label = stringResource(R.string.option_open_album),
                trailingText = activeSong.album.take(18),
                onClick = {
                    onDismiss()
                    onOpenAlbum(activeSong)
                },
                testTag = "option_open_album"
            )

            GlassMenuOptionRow(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.option_open_artist),
                trailingText = activeSong.artist.substringBefore(",").trim().take(18),
                onClick = {
                    onDismiss()
                    onOpenArtist(activeSong)
                },
                testTag = "option_open_artist"
            )

            GlassMenuOptionRow(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.option_song_info),
                onClick = {
                    onDismiss()
                    onOpenSongInfo(activeSong)
                },
                testTag = "option_song_info"
            )

            GlassMenuOptionRow(
                icon = Icons.Rounded.Bedtime,
                label = stringResource(R.string.option_sleep_timer),
                iconTint = if (sleepBadge != null) ambient.secondaryGlow else TextPrimary,
                trailingText = sleepBadge,
                trailingTint = ambient.secondaryGlow,
                onClick = {
                    onDismiss()
                    onOpenSleepTimer()
                },
                testTag = "option_sleep_timer"
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun GlassMenuOptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    iconTint: Color = TextPrimary,
    trailingText: String? = null,
    trailingTint: Color = TextMuted
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!trailingText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = trailingTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongInfoGlassSheet(
    song: Song?,
    downloadState: DownloadState,
    hazeState: HazeState,
    onDismiss: () -> Unit
) {
    BitChordGlassSheetHost(
        visible = song != null,
        onDismiss = onDismiss
    ) {
        val activeSong = song ?: return@BitChordGlassSheetHost
        val isOffline = downloadState is DownloadState.Downloaded || activeSong.isDownloaded

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, cornerRadius = 28.dp)
                .padding(horizontal = 22.dp)
                .padding(bottom = 22.dp)
                .testTag("song_info_glass_sheet")
        ) {
            GlassSheetHandle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.song_info_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SongArtworkImage(
                    artworkUrl = activeSong.artworkUrl,
                    fallbackArtworkRes = activeSong.fallbackArtworkRes,
                    contentDescription = stringResource(R.string.cd_album_art),
                    decodeSizePx = 220,
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 16.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSong.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeSong.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuroraCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            Spacer(modifier = Modifier.height(14.dp))

            InfoMetadataRow(label = "Album", value = activeSong.album.ifBlank { "Single" })
            InfoMetadataRow(label = "Genre", value = activeSong.genre)
            InfoMetadataRow(label = "Duration", value = formatDurationMs(activeSong.duration))
            InfoMetadataRow(label = "Audio Quality", value = activeSong.audioQualityLabel)
            InfoMetadataRow(
                label = "Playback Source",
                value = if (isOffline) "Offline Storage (No Wi-Fi needed)" else "Full-Length Cloud Stream",
                valueColor = if (isOffline) AuroraCyan else TextPrimary
            )
            InfoMetadataRow(label = "Track ID", value = activeSong.id)
        }
    }
}

@Composable
private fun InfoMetadataRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SleepTimerGlassSheet(
    visible: Boolean,
    remainingMs: Long?,
    endOfTrack: Boolean,
    hazeState: HazeState,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    BitChordGlassSheetHost(
        visible = visible,
        onDismiss = onDismiss
    ) {
        val ambient = LocalAmbientPalette.current
        val isActive = remainingMs != null || endOfTrack

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, cornerRadius = 28.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp)
                .testTag("sleep_timer_glass_sheet")
        ) {
            GlassSheetHandle()

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        remainingMs != null -> "Pausing in ${formatDurationMs(remainingMs)}"
                        endOfTrack -> "Pausing after the current track finishes"
                        else -> stringResource(R.string.sleep_timer_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) ambient.secondaryGlow else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val presets = listOf(15, 30, 45, 60)
            presets.forEach { minutes ->
                GlassMenuOptionRow(
                    icon = Icons.Rounded.Bedtime,
                    label = "$minutes minutes",
                    onClick = {
                        onSelectMinutes(minutes)
                        onDismiss()
                    },
                    testTag = "sleep_timer_${minutes}m"
                )
            }

            GlassMenuOptionRow(
                icon = Icons.Rounded.MusicNote,
                label = stringResource(R.string.sleep_timer_end_of_track),
                iconTint = if (endOfTrack) ambient.secondaryGlow else TextPrimary,
                trailingText = if (endOfTrack) "Active" else null,
                trailingTint = ambient.secondaryGlow,
                onClick = {
                    onSelectEndOfTrack()
                    onDismiss()
                },
                testTag = "sleep_timer_end_of_track"
            )

            if (isActive) {
                Spacer(modifier = Modifier.height(6.dp))
                GlassMenuOptionRow(
                    icon = Icons.Rounded.TimerOff,
                    label = stringResource(R.string.sleep_timer_cancel),
                    iconTint = PulseRose,
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                    testTag = "sleep_timer_cancel"
                )
            }
        }
    }
}

@Composable
fun AddToPlaylistGlassSheet(
    song: Song?,
    playlists: List<PlaylistUiModel>,
    hazeState: HazeState,
    onSelectPlaylist: (Long) -> Unit,
    onCreateNewPlaylistClick: () -> Unit,
    onDismiss: () -> Unit
) {
    BitChordGlassSheetHost(
        visible = song != null,
        onDismiss = onDismiss
    ) {
        val activeSong = song ?: return@BitChordGlassSheetHost
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .liquidGlass(hazeState = hazeState, cornerRadius = 28.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            GlassSheetHandle()

            Text(
                text = stringResource(R.string.player_add_to_playlist),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${activeSong.title} • ${activeSong.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                onClick = onCreateNewPlaylistClick,
                shape = RoundedCornerShape(16.dp),
                color = ElectricViolet.copy(alpha = 0.24f),
                border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_new_playlist_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = AuroraCyan)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.library_create_playlist),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_playlists_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = playlists,
                        key = { playlist -> playlist.id },
                        contentType = { "playlist_sheet_item" }
                    ) { playlist ->
                        val alreadyAdded = playlist.songs.any { it.id == activeSong.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sonoraGlassCard(shape = RoundedCornerShape(16.dp))
                                .clickable { onSelectPlaylist(playlist.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = AuroraCyan
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${playlist.songs.size} tracks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            if (alreadyAdded) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = AuroraCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueGlassSheet(
    visible: Boolean,
    queue: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    hazeState: HazeState,
    onSelectSong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    BitChordGlassSheetHost(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .liquidGlass(hazeState = hazeState, cornerRadius = 28.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
                .testTag("queue_glass_sheet")
        ) {
            GlassSheetHandle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_queue),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "${queue.size} tracks",
                    style = TabularNumericStyle,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = queue,
                    key = { song -> song.id },
                    contentType = { "song_item" }
                ) { song ->
                    val isCurrent = song.id == currentSongId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sonoraGlassCard(
                                shape = RoundedCornerShape(14.dp),
                                highlighted = isCurrent,
                                accentBorderColor = if (isCurrent) AuroraCyan else null
                            )
                            .clickable { onSelectSong(song) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongArtworkImage(
                            artworkUrl = song.artworkUrl,
                            fallbackArtworkRes = song.fallbackArtworkRes,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) AuroraCyan else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            AnimatedEqualizerBars(isPlaying = isPlaying, tint = AuroraCyan)
                        } else {
                            Text(
                                text = formatDurationMs(song.duration),
                                style = TabularNumericStyle,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistGlassDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember(visible) { mutableStateOf("") }
    var description by remember(visible) { mutableStateOf("") }

    BitChordGlassSheetHost(
        visible = visible,
        onDismiss = onDismiss,
        centerDialog = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(hazeState = hazeState, cornerRadius = 28.dp)
                .padding(22.dp)
        ) {
            Text(
                text = stringResource(R.string.library_create_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraCyan,
                    unfocusedBorderColor = GlassBorderTop,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Vibe or notes (optional)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraCyan,
                    unfocusedBorderColor = GlassBorderTop,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(name, description) },
                    enabled = name.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("confirm_create_playlist")
                ) {
                    Text("Create")
                }
            }
        }
    }
}
