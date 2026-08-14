package com.citymemory.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.ui.components.EmptyState
import com.citymemory.ui.components.LoadingState
import com.citymemory.ui.components.PlaceCard
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.shortName

@Composable
fun DiscoverScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = "Discover",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${state.totalCount} places waiting to be found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))

                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                )
            }
        }

        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DiscoverFilter.entries.toList(), key = { it.name }) { filter ->
                    CityFilterChip(
                        selected = state.filter == filter,
                        label = filter.label,
                        onClick = { viewModel.onFilterSelected(filter) },
                    )
                }
            }
        }

        item(key = "categories") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PlaceCategory.entries.toList(), key = { it.id }) { category ->
                    val count = state.categoryCounts[category] ?: 0
                    CityFilterChip(
                        selected = state.category == category,
                        label = "${category.shortName} $count",
                        onClick = { viewModel.onCategorySelected(category) },
                    )
                }
            }
        }

        if (state.places.isEmpty()) {
            item(key = "empty") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "Nothing matches",
                        message = "Try a different search or clear the filters to see the whole city.",
                    )
                    TextButton(onClick = viewModel::onClearFilters) {
                        Text("Clear filters", color = GlowAmber)
                    }
                }
            }
        } else {
            item(key = "count") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.places.size} " +
                            if (state.places.size == 1) "place" else "places",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    if (state.isFiltered) {
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            onClick = viewModel::onClearFilters,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlowAmber,
                            )
                        }
                    }
                }
            }

            items(state.places, key = { it.id }) { place ->
                PlaceCard(
                    place = place,
                    onClick = { onPlaceClick(place.id) },
                    onToggleWishlist = { viewModel.onToggleWishlist(place) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("Search places", style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = TextTertiary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextTertiary)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CitySurface,
            unfocusedContainerColor = CitySurface,
            focusedBorderColor = GlowAmber.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = GlowAmber,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun CityFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CitySurface,
            labelColor = TextSecondary,
            selectedContainerColor = GlowAmber.copy(alpha = 0.16f),
            selectedLabelColor = GlowAmber,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = GlowAmber.copy(alpha = 0.45f),
        ),
    )
}
