package com.citymemory.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.domain.model.Place
import com.citymemory.ui.components.GlowProgressBar
import com.citymemory.ui.components.LoadingState
import com.citymemory.ui.components.PlaceThumbnail
import com.citymemory.ui.map.CityMapView
import com.citymemory.ui.theme.CityNight
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.DimSlate
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.GlowCore
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.WishCyan

/**
 * The home screen, and the whole product in one view: a dark city with your
 * exploration written across it in light.
 */
@Composable
fun ExploreScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(CityNight)) {
        if (state.isLoading) {
            LoadingState()
            return@Box
        }

        CityMapView(
            geometry = state.geometry,
            places = state.places,
            selectedPlaceId = state.selectedPlace?.id,
            onPlaceSelected = viewModel::onPlaceSelected,
        )

        ExploreHeader(
            cityName = state.cityName,
            progress = state.progress,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        MapLegend(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 20.dp),
        )

        // Retained separately from the selection so the card keeps its content
        // while it slides out, instead of blanking the moment it is dismissed.
        var lastSelected by remember { mutableStateOf<Place?>(null) }
        LaunchedEffect(state.selectedPlace) {
            state.selectedPlace?.let { lastSelected = it }
        }

        AnimatedVisibility(
            visible = state.selectedPlace != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastSelected?.let { place ->
                PlacePeekCard(
                    place = place,
                    onOpen = { onPlaceClick(place.id) },
                    onToggleVisited = { viewModel.onToggleVisited(place) },
                    onDismiss = viewModel::onSelectionDismissed,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExploreHeader(
    cityName: String,
    progress: ExplorationProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CityNight.copy(alpha = 0.96f),
                        CityNight.copy(alpha = 0.80f),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 34.dp),
    ) {
        Text(
            text = cityName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${progress.percent}%",
                style = MaterialTheme.typography.displayLarge,
                color = GlowCore,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Explored",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        GlowProgressBar(
            fraction = progress.fraction,
            contentDescription = "${progress.percent} percent of ${cityName.ifBlank { "the city" }} explored",
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "${progress.visitedCount} / ${progress.totalCount} Places" +
                if (progress.wishlistCount > 0) "   ·   ${progress.wishlistCount} wishlisted" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendRow(GlowAmber, "Explored")
        LegendRow(WishCyan, "Wishlist")
        LegendRow(DimSlate, "Undiscovered")

        Spacer(Modifier.height(4.dp))

        // The zoom is the feature, not a nicety: at the overview an explored
        // area is a glow, and only close up is it the streets you walked.
        Text(
            text = "Pinch or double-tap to walk in",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}

/** Shown when a light on the map is tapped. */
@Composable
private fun PlacePeekCard(
    place: Place,
    onOpen: () -> Unit,
    onToggleVisited: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CitySurface,
        border = BorderStroke(
            1.dp,
            if (place.isVisited) GlowAmber.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.07f),
        ),
        tonalElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaceThumbnail(place = place, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = place.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = TextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Details")
                }
                Button(
                    onClick = onToggleVisited,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (place.isVisited) {
                            Color.White.copy(alpha = 0.10f)
                        } else {
                            GlowAmber
                        },
                        contentColor = if (place.isVisited) TextSecondary else CityNight,
                    ),
                ) {
                    if (place.isVisited) {
                        Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Explored", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Mark explored", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
