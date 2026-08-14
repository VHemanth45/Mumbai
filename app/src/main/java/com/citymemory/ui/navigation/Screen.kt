package com.citymemory.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app. String routes rather than type-safe
 * serialization: five destinations and one argument do not justify the extra
 * plugin, and the ids stay greppable.
 */
sealed class Screen(val route: String) {

    data object Explore : Screen("explore")
    data object Discover : Screen("discover")
    data object Wishlist : Screen("wishlist")
    data object Progress : Screen("progress")

    data object PlaceDetail : Screen("place/{placeId}") {
        const val ARG_PLACE_ID = "placeId"
        fun routeFor(placeId: String): String = "place/$placeId"
    }
}

/** The four tabs, in bar order. */
enum class BottomDestination(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    EXPLORE(Screen.Explore, "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    DISCOVER(Screen.Discover, "Discover", Icons.Filled.Search, Icons.Outlined.Search),
    WISHLIST(Screen.Wishlist, "Wishlist", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    PROGRESS(Screen.Progress, "Progress", Icons.Filled.Insights, Icons.Outlined.Insights),
    ;

    companion object {
        fun forRoute(route: String?): BottomDestination? =
            entries.firstOrNull { it.screen.route == route }
    }
}
