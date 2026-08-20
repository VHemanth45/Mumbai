package com.citymemory.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.domain.model.Place
import com.citymemory.ui.components.GlowProgressBar
import com.citymemory.ui.components.explorationBarDescription
import com.citymemory.ui.components.explorationHeadlineDescription
import com.citymemory.ui.components.explorationHeadlineLabel
import com.citymemory.ui.components.explorationSubtitle
import com.citymemory.ui.components.LoadingState
import com.citymemory.ui.components.PlaceThumbnail
import com.citymemory.ui.map.CityMapView
import com.citymemory.ui.map.PICK_ANCHOR_FRACTION
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
 *
 * The map is looked at, not picked from — touching it only moves the camera.
 * A place is chosen by name in the search box, which flies the map to it and
 * opens the card where the visit, the rating and the opinion are recorded.
 */
@Composable
fun ExploreScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Read here, rather than deeper down with the rest of the add-a-place
    // state, because the map takes it as a parameter. It is a de-duplicated
    // boolean for exactly that reason — see ExploreViewModel.isPickingLocation.
    val isPicking by viewModel.isPickingLocation.collectAsStateWithLifecycle()

    // The automatic-logging surface. Collected here rather than folded into
    // `uiState` for the same reason `isPicking` is: `uiState` is what the map
    // is given, so anything added to it recomposes the map when it changes.
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val autoLogEnabled by viewModel.autoLogEnabled.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()

    // Also a map parameter, and also a one-shot: it changes when "use my
    // location" comes back with a fix, and the map flies there once.
    val flyTo by viewModel.flyTo.collectAsStateWithLifecycle()

    // Hoisted above the sheet on purpose. The form lives inside an
    // `AnimatedVisibility`, so closing it removes that subtree from
    // composition — an `asked` flag stored down there would reset to false and
    // quietly re-arm a button the system has stopped answering.
    var askedForLocation by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(CityNight)) {
        if (state.isLoading) {
            LoadingState()
            return@Box
        }

        CityMapView(
            geometry = state.geometry,
            places = state.places,
            selectedPlaceId = state.selectedPlace?.id,
            focusedPlace = state.selectedPlace,
            onViewportCenterChange = viewModel::onViewportCenterChanged,
            pickingLocation = isPicking,
            flyTo = flyTo,
        )

        // Aligned to the same fraction of the same Box the map fills, so the
        // ring sits exactly over the point the map reports. Both read
        // PICK_ANCHOR_FRACTION; a bias runs -1..1 across the axis, so the
        // fraction has to be mapped onto that range.
        AddPlaceCrosshair(
            viewModel = viewModel,
            modifier = Modifier.align(BiasAlignment(0f, 2f * PICK_ANCHOR_FRACTION - 1f)),
        )

        ExploreHeader(
            cityName = state.cityName,
            progress = state.progress,
            query = state.searchQuery,
            results = state.searchResults,
            onQueryChange = viewModel::onSearchQueryChange,
            onResultClick = { place ->
                // The map is about to fly; the keyboard would cover where it lands.
                focusManager.clearFocus()
                viewModel.onPlaceSelected(place)
            },
            onAddPlace = { name ->
                focusManager.clearFocus()
                viewModel.onAddPlaceRequested(name)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        MapLegend(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 20.dp),
        )

        val appContext = LocalContext.current.applicationContext
        LaunchedEffect(Unit) { viewModel.rememberContext(appContext) }

        LoggingActions(
            autoLogEnabled = autoLogEnabled,
            isImporting = isImporting,
            onPhotosPicked = viewModel::onPhotosPicked,
            onSetAutoLog = { enabled -> viewModel.setAutoLogEnabled(appContext, enabled) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
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
                PlaceVisitCard(
                    place = place,
                    onOpen = { onPlaceClick(place.id) },
                    onDismiss = {
                        focusManager.clearFocus()
                        viewModel.onSelectionDismissed()
                    },
                    onSave = { visited, rating, note ->
                        focusManager.clearFocus()
                        viewModel.onSaveVisit(place, visited, rating, note)
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            ImportResultBanner(
                result = importResult,
                onDismiss = viewModel::onImportResultShown,
            )

            val suggestion = suggestions.firstOrNull()
            AnimatedVisibility(
                // Stands down while a place card is open: they occupy the same
                // corner, and a question stacked on top of the thing the user
                // is already doing is an interruption rather than an offer.
                visible = suggestion != null && state.selectedPlace == null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                // Held so the card keeps its content while it slides out,
                // rather than blanking the instant it is answered.
                var shown by remember { mutableStateOf(suggestion) }
                LaunchedEffect(suggestion) { suggestion?.let { shown = it } }
                shown?.let { current ->
                    SuggestionCard(
                        suggestion = current,
                        remaining = (suggestions.size - 1).coerceAtLeast(0),
                        onConfirm = { viewModel.onSuggestionConfirmed(current) },
                        onDismiss = { viewModel.onSuggestionDismissed(current) },
                        now = System.currentTimeMillis(),
                    )
                }
            }
        }

        // Collects its own state, so typing a name into it never recomposes
        // the map above. See `AddPlaceSheet`.
        AddPlaceSheet(
            viewModel = viewModel,
            askedForLocation = askedForLocation,
            onAskedForLocation = { askedForLocation = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ExploreHeader(
    cityName: String,
    progress: ExplorationProgress,
    query: String,
    results: List<Place>,
    onQueryChange: (String) -> Unit,
    onResultClick: (Place) -> Unit,
    onAddPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CityNight.copy(alpha = 0.96f),
                        CityNight.copy(alpha = 0.88f),
                        CityNight.copy(alpha = 0.72f),
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

        // The count, not the percentage. 22 of 31,657 places is 0.07%, which
        // rounds to the 0% this used to show in the largest type on the screen
        // — a number that cannot move until the 317th place and reads as "you
        // have done nothing" to someone who has been to twenty-two.
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = explorationHeadlineDescription(progress)
            },
        ) {
            Text(
                text = "${progress.visitedCount}",
                style = MaterialTheme.typography.displayLarge,
                color = GlowCore,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = explorationHeadlineLabel(progress),
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        GlowProgressBar(
            fraction = progress.levelFraction,
            contentDescription = explorationBarDescription(progress),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = explorationSubtitle(progress),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(14.dp))

        PlaceSearchField(query = query, onQueryChange = onQueryChange)

        if (query.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            SearchResults(
                results = results,
                query = query,
                onResultClick = onResultClick,
                onAddPlace = onAddPlace,
            )
        }
    }
}

/** Finds a place by name and hands the map somewhere to fly to. */
@Composable
private fun PlaceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Search a place to zoom to",
                style = MaterialTheme.typography.bodyLarge,
                color = TextTertiary,
            )
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
private fun SearchResults(
    results: List<Place>,
    query: String,
    onResultClick: (Place) -> Unit,
    onAddPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CitySurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        tonalElevation = 6.dp,
    ) {
        if (results.isEmpty()) {
            // The catalog now holds every place OpenStreetMap has mapped in
            // Mumbai, so getting here means the place genuinely is not on the
            // map — which makes this the right moment to offer to add it, with
            // the name already typed.
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "No place called “$query” in the catalog.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onAddPlace(query) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlowAmber,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add “$query”", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@Surface
        }

        Column(Modifier.padding(vertical = 6.dp)) {
            results.forEach { place ->
                SearchResultRow(place = place, onClick = { onResultClick(place) })
            }
            // Also reachable when the search *did* match: what you went to may
            // still not be one of the things it matched.
            AddPlaceRow(query = query, onClick = { onAddPlace(query) })
        }
    }
}

@Composable
private fun SearchResultRow(
    place: Place,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceThumbnail(place = place, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = place.category.displayName +
                    if (place.isVisited) "  ·  Explored" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (place.isVisited) GlowAmber else TextTertiary,
            )
            // Six results all called "Cafe Coffee Day" are only tellable apart
            // by where they are.
            place.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        place.rating?.let { rating ->
            Text(
                text = "$rating★",
                style = MaterialTheme.typography.labelMedium,
                color = GlowAmber,
            )
        }
    }
}

/** The last row of a result list: none of these, add the one I mean. */
@Composable
private fun AddPlaceRow(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GlowAmber.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = GlowAmber,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Add “$query”",
                style = MaterialTheme.typography.bodyLarge,
                color = GlowAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Not in the catalog? Put it on your map",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
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

/**
 * The card the search opens: mark the place explored, score it, say what you
 * thought of it.
 *
 * The three are edited locally and committed together, so a rating typed
 * halfway is not persisted as a half-opinion, and so the text field is not
 * fighting a database write on every keystroke. Local state is keyed on the
 * place id, which is what makes searching for a second place start a clean form
 * while re-reading the same one keeps what is in front of you.
 */
@Composable
private fun PlaceVisitCard(
    place: Place,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (visited: Boolean, rating: Int?, note: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visited by remember(place.id) { mutableStateOf(place.isVisited) }
    var rating by remember(place.id) { mutableStateOf(place.rating) }
    var note by remember(place.id) { mutableStateOf(place.note.orEmpty()) }

    val dirty = visited != place.isVisited ||
        rating != place.rating ||
        note.trim() != place.note.orEmpty()

    Surface(
        modifier = modifier.fillMaxWidth().imePadding(),
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

            FilterChip(
                selected = visited,
                onClick = { visited = !visited },
                label = {
                    Text(
                        text = if (visited) "I have been here" else "Mark as visited",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = if (visited) {
                    { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = CitySurface,
                    labelColor = TextSecondary,
                    selectedContainerColor = GlowAmber.copy(alpha = 0.16f),
                    selectedLabelColor = GlowAmber,
                    selectedLeadingIconColor = GlowAmber,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = visited,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = GlowAmber.copy(alpha = 0.45f),
                ),
            )

            Spacer(Modifier.height(14.dp))

            RatingRow(rating = rating, onRatingChange = { rating = it })

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "What did you think of it?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedBorderColor = GlowAmber.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = GlowAmber,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )

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
                    onClick = { onSave(visited, rating, note) },
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlowAmber,
                        contentColor = CityNight,
                        disabledContainerColor = Color.White.copy(alpha = 0.10f),
                        disabledContentColor = TextSecondary,
                    ),
                ) {
                    Text(
                        text = if (!dirty && (place.isVisited || place.hasReview)) "Saved" else "Save",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Five stars, out of five. Tapping the star a place already has clears the
 * rating, which is the only way back to "not rated" once one is set.
 */
@Composable
private fun RatingRow(
    rating: Int?,
    onRatingChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Your rating",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
        )
        Spacer(Modifier.width(10.dp))

        for (star in 1..MAX_RATING) {
            val filled = rating != null && star <= rating
            IconButton(
                onClick = { onRatingChange(if (rating == star) null else star) },
                modifier = Modifier
                    .size(34.dp)
                    .semantics {
                        contentDescription = if (rating == star) {
                            "Clear rating"
                        } else {
                            "Rate $star out of $MAX_RATING"
                        }
                    },
            ) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (filled) GlowAmber else TextTertiary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(6.dp))
        Text(
            text = rating?.let { "$it/$MAX_RATING" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = GlowAmber,
            // The stars already announce the score; this is the same fact again.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private const val MAX_RATING = 5
