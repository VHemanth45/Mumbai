package com.citymemory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.citymemory.ui.navigation.BottomDestination
import com.citymemory.ui.navigation.Screen
import com.citymemory.ui.screens.discover.DiscoverScreen
import com.citymemory.ui.screens.explore.ExploreScreen
import com.citymemory.ui.screens.place.PlaceDetailScreen
import com.citymemory.ui.screens.progress.ProgressScreen
import com.citymemory.ui.screens.wishlist.WishlistScreen
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextTertiary

@Composable
fun CityMemoryApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = BottomDestination.forRoute(backStackEntry?.destination?.route)

    val openPlace: (String) -> Unit = { placeId ->
        navController.navigate(Screen.PlaceDetail.routeFor(placeId))
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Screens manage their own top inset so the map can bleed behind the
        // status bar; only the bottom bar is laid out by the Scaffold.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentTab != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                CityBottomBar(
                    current = currentTab,
                    onSelect = { destination -> navController.switchTab(destination) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Explore.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Screen.Explore.route) {
                ExploreScreen(onPlaceClick = openPlace)
            }
            composable(Screen.Discover.route) {
                DiscoverScreen(onPlaceClick = openPlace)
            }
            composable(Screen.Wishlist.route) {
                WishlistScreen(onPlaceClick = openPlace)
            }
            composable(Screen.Progress.route) {
                ProgressScreen()
            }
            composable(
                route = Screen.PlaceDetail.route,
                arguments = listOf(
                    navArgument(Screen.PlaceDetail.ARG_PLACE_ID) { type = NavType.StringType },
                ),
            ) {
                PlaceDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Standard tab behaviour: one entry per tab on the back stack, scroll position
 * and filters preserved when you come back to a tab.
 */
private fun NavHostController.switchTab(destination: BottomDestination) {
    navigate(destination.screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun CityBottomBar(
    current: BottomDestination?,
    onSelect: (BottomDestination) -> Unit,
) {
    NavigationBar(
        containerColor = CitySurface,
        tonalElevation = 0.dp,
    ) {
        BottomDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GlowAmber,
                    selectedTextColor = GlowAmber,
                    indicatorColor = GlowAmber.copy(alpha = 0.14f),
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                ),
            )
        }
    }
}
