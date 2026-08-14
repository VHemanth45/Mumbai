package com.citymemory.ui.screens.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.citymemory.domain.model.Place
import com.citymemory.ui.components.EmptyState
import com.citymemory.ui.components.LoadingState
import com.citymemory.ui.components.PlaceCard
import com.citymemory.ui.components.WishlistToggle
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.WishCyan
import com.citymemory.ui.util.rememberPlaceNavigator
import kotlinx.coroutines.launch

@Composable
fun WishlistScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WishlistViewModel = viewModel(factory = WishlistViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val navigateTo = rememberPlaceNavigator { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // The app-level Scaffold already accounts for the bottom bar and the
        // navigation bar; taking insets again here would double the padding.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState()

            state.isEmpty -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                WishlistHeader(count = 0)
                EmptyState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "Nothing saved yet",
                    message = "Tap the bookmark on any place in Discover to plan where to go next.",
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") { WishlistHeader(count = state.totalCount) }

                if (state.pending.isNotEmpty()) {
                    item(key = "pending-header") { SectionLabel("TO EXPLORE") }
                    items(state.pending, key = { it.id }) { place ->
                        WishlistRow(place, onPlaceClick, navigateTo, viewModel)
                    }
                }

                if (state.explored.isNotEmpty()) {
                    item(key = "explored-header") { SectionLabel("ALREADY EXPLORED") }
                    items(state.explored, key = { it.id }) { place ->
                        WishlistRow(place, onPlaceClick, navigateTo, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistRow(
    place: Place,
    onPlaceClick: (String) -> Unit,
    navigateTo: (Place) -> Unit,
    viewModel: WishlistViewModel,
) {
    PlaceCard(
        place = place,
        onClick = { onPlaceClick(place.id) },
        modifier = Modifier.padding(horizontal = 20.dp),
        trailing = {
            Row {
                IconButton(onClick = { navigateTo(place) }) {
                    Icon(
                        imageVector = Icons.Outlined.NearMe,
                        contentDescription = "Navigate to ${place.name}",
                        tint = TextSecondary,
                    )
                }
                WishlistToggle(
                    isWishlisted = true,
                    placeName = place.name,
                    onToggle = { viewModel.onRemoveFromWishlist(place) },
                )
            }
        },
    )
}

@Composable
private fun WishlistHeader(count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = "Wishlist",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (count == 0) {
                "Places you want to reach"
            } else {
                "$count ${if (count == 1) "place" else "places"} saved"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (count == 0) TextSecondary else WishCyan,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}
