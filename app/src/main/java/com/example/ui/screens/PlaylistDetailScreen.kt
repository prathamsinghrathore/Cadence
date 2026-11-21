package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DownloadState
import com.example.data.PlaylistUiModel
import com.example.data.Song
import com.example.ui.SonoraUiState
import com.example.ui.components.EmptyGlassCard
import com.example.ui.components.SongRowItem
import com.example.ui.components.rememberStaggeredEntranceModifier
import com.example.ui.components.sonoraGlassCard
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistUiModel?,
    uiState: SonoraUiState,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onToggleDownload: (Song) -> Unit,
    onRemoveFromPlaylist: (Long, String) -> Unit,
    onOpenAddToPlaylist: (Song) -> Unit,
    onOpenMoreOptions: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "pl_detail_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_detail_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sonoraGlassCard(shape = RoundedCornerShape(24.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist?.name ?: "Playlist",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val count = playlist?.songs?.size ?: 0
                        Text(
                            text = if (playlist?.description.isNullOrBlank()) {
                                "$count tracks"
                            } else {
                                "$count tracks • ${playlist?.description}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    if (playlist != null && playlist.songs.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { onPlayAll(playlist.songs) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraCyan,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("playlist_detail_play_all")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.library_play_all))
                        }
                    }
                }
            }
        }

        if (playlist == null || playlist.songs.isEmpty()) {
            item(key = "pl_detail_empty", contentType = "empty_card") {
                EmptyGlassCard(
                    title = "Empty playlist",
                    subtitle = "Tap the playlist icon on any song across Home or Search to add tracks here.",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        } else {
            itemsIndexed(
                items = playlist.songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song_item" }
            ) { index, song ->
                val isCurrent = uiState.playback.currentSong?.id == song.id
                val entranceModifier = rememberStaggeredEntranceModifier(
                    index = index,
                    triggerKey = "pl_${playlist.id}_${song.id}"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .then(entranceModifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongRowItem(
                        song = song,
                        isCurrentSong = isCurrent,
                        isPlaying = uiState.playback.isPlaying,
                        isBuffering = isCurrent && uiState.playback.isBuffering,
                        downloadState = uiState.downloadStates[song.id] ?: DownloadState.NotDownloaded,
                        onPlayClick = { onPlaySong(song, playlist.songs) },
                        onDownloadClick = { onToggleDownload(song) },
                        onAddToPlaylistClick = { onOpenAddToPlaylist(song) },
                        onMoreOptionsClick = { onOpenMoreOptions(song) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveFromPlaylist(playlist.id, song.id) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "Remove from playlist",
                            tint = TextMuted
                        )
                    }
                }
            }
        }
    }
}
