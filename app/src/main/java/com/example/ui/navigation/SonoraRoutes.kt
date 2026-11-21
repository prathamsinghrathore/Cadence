package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

sealed interface SonoraRoute {
    @Serializable
    data object Home : SonoraRoute

    @Serializable
    data object Search : SonoraRoute

    @Serializable
    data object Library : SonoraRoute

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : SonoraRoute
}

enum class BottomNavTab(
    val route: SonoraRoute,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        route = SonoraRoute.Home,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    SEARCH(
        route = SonoraRoute.Search,
        labelRes = R.string.nav_search,
        selectedIcon = Icons.Rounded.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_tab_search"
    ),
    LIBRARY(
        route = SonoraRoute.Library,
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Rounded.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        testTag = "nav_tab_library"
    )
}
