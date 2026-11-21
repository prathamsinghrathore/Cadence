package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DownloadState
import com.example.data.Song
import com.example.ui.SearchUiState
import com.example.ui.SonoraUiState
import com.example.ui.components.EmptyGlassCard
import com.example.ui.components.SongRowItem
import com.example.ui.components.rememberStaggeredEntranceModifier
import com.example.ui.components.sonoraGlassCard
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassFillBottom
import com.example.ui.theme.PulseRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

private val SearchGlassShape = RoundedCornerShape(16.dp)
private val SearchGlassFill = Color.White.copy(alpha = 0.08f)
private val SearchGlassBorder = Color.White.copy(alpha = 0.15f)

@Composable
fun SearchScreen(
    uiState: SonoraUiState,
    bottomContentPadding: Dp,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleDownload: (Song) -> Unit,
    onOpenAddToPlaylist: (Song) -> Unit,
    onOpenMoreOptions: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // Single LazyColumn as the root scrollable container; no Haze modifiers or
    // immediate FocusRequester calls during composition.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "search_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_search),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sonoraGlassCard(shape = SearchGlassShape)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = AuroraCyan
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = SearchGlassShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSubmitSearch(uiState.searchQuery)
                                focusManager.clearFocus()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = AuroraCyan.copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input_field")
                    )
                }
            }
        }

        if (uiState.searchQuery.isBlank()) {
            if (uiState.recentSearches.isNotEmpty()) {
                item(key = "recent_searches_block") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.search_recent_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            TextButton(
                                onClick = onClearRecentSearches,
                                modifier = Modifier.testTag("clear_recent_searches")
                            ) {
                                Text(
                                    text = stringResource(R.string.search_clear_all),
                                    color = AuroraCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.recentSearches.forEach { query ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = GlassFillBottom,
                                    border = BorderStroke(1.dp, GlassBorderBottom),
                                    modifier = Modifier.testTag("recent_chip_$query")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { onSubmitSearch(query) }
                                            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = query,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove $query",
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDeleteRecentSearch(query) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "sonic_worlds_header") {
                Text(
                    text = stringResource(R.string.search_moods_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item(key = "sonic_worlds_grid") {
                val moodCards = listOf(
                    Triple("Bollywood", "Latest Hindi chartbusters", ElectricViolet),
                    Triple("Punjabi", "Punjabi beats & hits", PulseRose),
                    Triple("English Pop", "Global top tracks", AuroraCyan),
                    Triple("Arijit Singh", "Romantic & soulful", WarmAmber)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    moodCards.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { (genre, subtitle, accent) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(102.dp)
                                        .sonoraGlassCard(shape = SearchGlassShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    accent.copy(alpha = 0.24f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .clickable { onSubmitSearch(genre) }
                                        .padding(14.dp)
                                        .testTag("mood_tile_$genre")
                                ) {
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (val searchState = uiState.searchState) {
                SearchUiState.Idle, SearchUiState.Loading -> {
                    item(key = "search_loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .sonoraGlassCard(shape = SearchGlassShape)
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = AuroraCyan,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = "Searching streaming catalog…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                is SearchUiState.Error -> {
                    item(key = "search_error", contentType = "error_card") {
                        EmptyGlassCard(
                            title = "Search unavailable",
                            subtitle = searchState.message,
                            actionLabel = "Retry",
                            onActionClick = { onSubmitSearch(uiState.searchQuery) },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                is SearchUiState.Success -> {
                    val distinctSongs = searchState.songs.distinctBy { it.id }
                    if (distinctSongs.isEmpty()) {
                        item(key = "search_empty", contentType = "empty_card") {
                            EmptyGlassCard(
                                title = stringResource(R.string.search_no_results_title),
                                subtitle = stringResource(R.string.search_no_results_subtitle),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = distinctSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "song_item" }
                        ) { index, song ->
                            val isCurrent = uiState.playback.currentSong?.id == song.id
                            val entranceModifier = rememberStaggeredEntranceModifier(
                                index = index,
                                triggerKey = "${uiState.searchQuery}_${song.id}"
                            )
                            SongRowItem(
                                song = song,
                                isCurrentSong = isCurrent,
                                isPlaying = uiState.playback.isPlaying,
                                isBuffering = isCurrent && uiState.playback.isBuffering,
                                downloadState = uiState.downloadStates[song.id] ?: DownloadState.NotDownloaded,
                                onPlayClick = {
                                    onSubmitSearch(uiState.searchQuery)
                                    onPlaySong(song, distinctSongs)
                                },
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
    }
}
