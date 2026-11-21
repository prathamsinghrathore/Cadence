package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DownloadState
import com.example.data.PlaylistUiModel
import com.example.data.Song
import com.example.ui.SonoraUiState
import com.example.ui.components.AnimatedEqualizerBars
import com.example.ui.components.EmptyGlassCard
import com.example.ui.components.SongArtworkImage
import com.example.ui.components.formatDurationMs
import com.example.ui.components.rememberStaggeredEntranceModifier
import com.example.ui.components.sonoraGlassCard
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LocalAmbientPalette
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PulseRose
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun LibraryScreen(
    uiState: SonoraUiState,
    bottomContentPadding: Dp,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAllOffline: (List<Song>) -> Unit,
    onToggleDownload: (Song) -> Unit,
    onOpenAddToPlaylist: (Song) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onOpenPlaylistDetail: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onOpenMoreOptions: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val ambient = LocalAmbientPalette.current
    val downloadedSongs = uiState.downloadedSongs

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "library_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Surface(
                        onClick = onOpenCreatePlaylist,
                        shape = RoundedCornerShape(50),
                        color = ambient.primaryGlow.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, ambient.primaryGlow.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("library_new_playlist_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.library_create_playlist),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sonoraGlassCard(shape = RoundedCornerShape(18.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        stringResource(R.string.library_tab_downloads) to "library_tab_offline",
                        stringResource(R.string.library_tab_playlists) to "library_tab_playlists"
                    )
                    tabs.forEachIndexed { idx, (label, tag) ->
                        val active = selectedSection == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) ambient.primaryGlow.copy(alpha = 0.34f) else Color.Transparent
                                )
                                .clickable { selectedSection = idx }
                                .padding(vertical = 10.dp)
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (selectedSection == 0) {
            if (downloadedSongs.isEmpty()) {
                item(key = "offline_empty", contentType = "empty_card") {
                    EmptyGlassCard(
                        title = stringResource(R.string.library_offline_empty_title),
                        subtitle = stringResource(R.string.library_offline_empty_subtitle),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                item(key = "offline_summary", contentType = "summary_card") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .sonoraGlassCard(shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("offline_storage_summary"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = null,
                                tint = AuroraCyan
                            )
                            Column {
                                Text(
                                    text = "${downloadedSongs.size} offline songs",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${formatStorageBytes(uiState.totalOfflineBytes)} stored offline",
                                    style = TabularNumericStyle,
                                    color = AuroraCyan
                                )
                            }
                        }

                        Button(
                            onClick = { onPlayAllOffline(downloadedSongs) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraCyan,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("offline_play_all")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.library_play_all))
                        }
                    }
                }

                itemsIndexed(
                    items = downloadedSongs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "song_item" }
                ) { index, song ->
                    val isCurrent = uiState.playback.currentSong?.id == song.id
                    val downloadState = uiState.downloadStates[song.id]
                    val fileSizeBytes = (downloadState as? DownloadState.Downloaded)?.fileSizeBytes ?: 0L
                    val entranceModifier = rememberStaggeredEntranceModifier(
                        index = index,
                        triggerKey = "offline_${selectedSection}_${song.id}"
                    )

                    DownloadedSongCard(
                        song = song,
                        fileSizeBytes = fileSizeBytes,
                        isCurrentSong = isCurrent,
                        isPlaying = uiState.playback.isPlaying,
                        onPlayClick = { onPlaySong(song, downloadedSongs) },
                        onDeleteClick = { onToggleDownload(song) },
                        onMoreOptionsClick = { onOpenMoreOptions(song) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .then(entranceModifier)
                    )
                }
            }
        } else {
            if (uiState.playlists.isEmpty()) {
                item(key = "playlists_empty", contentType = "empty_card") {
                    EmptyGlassCard(
                        title = stringResource(R.string.library_playlists_empty_title),
                        subtitle = stringResource(R.string.library_playlists_empty_subtitle),
                        actionLabel = stringResource(R.string.library_create_playlist),
                        onActionClick = onOpenCreatePlaylist,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.playlists,
                    key = { _, playlist -> playlist.id },
                    contentType = { _, _ -> "playlist_item" }
                ) { index, playlist ->
                    val entranceModifier = rememberStaggeredEntranceModifier(
                        index = index,
                        triggerKey = "playlist_${selectedSection}_${playlist.id}"
                    )
                    PlaylistGlassCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylistDetail(playlist.id) },
                        onDeleteClick = { onDeletePlaylist(playlist.id) },
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
private fun DownloadedSongCard(
    song: Song,
    fileSizeBytes: Long,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ambient = LocalAmbientPalette.current
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
            .testTag("downloaded_song_card_${song.id}"),
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
                    AnimatedEqualizerBars(isPlaying = isPlaying, tint = ambient.secondaryGlow)
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
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "${formatDurationMs(song.duration)} • ${formatStorageBytes(fileSizeBytes)}",
                    style = TabularNumericStyle,
                    color = AuroraCyan
                )
            }
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("offline_delete_${song.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.cd_remove_download),
                tint = PulseRose
            )
        }

        IconButton(
            onClick = onMoreOptionsClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("offline_more_options_${song.id}")
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
private fun PlaylistGlassCard(
    playlist: PlaylistUiModel,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sonoraGlassCard(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("playlist_card_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val coverSong = playlist.songs.firstOrNull()
        if (coverSong != null) {
            SongArtworkImage(
                artworkUrl = coverSong.artworkUrl,
                fallbackArtworkRes = coverSong.fallbackArtworkRes,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                cornerRadius = 14.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElectricViolet.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = AuroraCyan
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append("${playlist.songs.size} tracks")
                if (playlist.description.isNotBlank()) {
                    append(" • ")
                    append(playlist.description)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("delete_playlist_${playlist.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${playlist.name}",
                tint = TextMuted
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary
        )
    }
}

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
