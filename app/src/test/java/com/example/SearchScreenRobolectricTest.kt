package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.SearchUiState
import com.example.ui.SonoraUiState
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.SonoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search screen renders idle state with recent searches and category tiles without crashing`() {
        var latestQuery = ""
        composeRule.setContent {
            SonoraTheme {
                SearchScreen(
                    uiState = SonoraUiState(
                        searchQuery = "",
                        searchState = SearchUiState.Idle,
                        recentSearches = listOf("Arijit Singh", "Diljit Dosanjh")
                    ),
                    bottomContentPadding = 120.dp,
                    onQueryChange = { latestQuery = it },
                    onSubmitSearch = { latestQuery = it },
                    onDeleteRecentSearch = {},
                    onClearRecentSearches = {},
                    onPlaySong = { _, _ -> },
                    onToggleDownload = {},
                    onOpenAddToPlaylist = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_input_field").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_chip_Arijit Singh").assertIsDisplayed()
        composeRule.onNodeWithTag("mood_tile_Bollywood").assertIsDisplayed()
        composeRule.onNodeWithTag("mood_tile_Bollywood").performClick()
        assertEquals("Bollywood", latestQuery)

        composeRule.onNodeWithTag("search_input_field").performTextInput("Kesariya")
        assertEquals("Kesariya", latestQuery)
    }

    @Test
    fun `search screen renders results state without crashing`() {
        val sampleSong = Song(
            id = "5WXAlMNt",
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            duration = 268_000L,
            artworkUrl = "",
            streamUrl = "https://example.com/test.mp4"
        )
        composeRule.setContent {
            SonoraTheme {
                SearchScreen(
                    uiState = SonoraUiState(
                        searchQuery = "Kesariya",
                        searchState = SearchUiState.Success(listOf(sampleSong))
                    ),
                    bottomContentPadding = 120.dp,
                    onQueryChange = {},
                    onSubmitSearch = {},
                    onDeleteRecentSearch = {},
                    onClearRecentSearches = {},
                    onPlaySong = { _, _ -> },
                    onToggleDownload = {},
                    onOpenAddToPlaylist = {}
                )
            }
        }

        composeRule.onNodeWithTag("song_row_5WXAlMNt").assertIsDisplayed()
    }
}
