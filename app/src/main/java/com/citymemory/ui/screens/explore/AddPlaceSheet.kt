package com.citymemory.ui.screens.explore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.ui.util.LOCATION_PERMISSIONS
import com.citymemory.ui.util.LocationPermissionState
import com.citymemory.ui.util.locationPermissionState
import com.citymemory.ui.util.openAppSettings
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.hue
import com.citymemory.ui.theme.icon
import com.citymemory.ui.theme.shortName
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

/**
 * The form for a place the catalog does not have.
 *
 * The catalog ships every place OpenStreetMap has mapped inside Mumbai, and it
 * is still going to be missing the stall that opened last month and the roof
 * nobody has got round to mapping. This is how those get in.
 *
 * **Everything here collects from the view model rather than taking state as a
 * parameter, and it does so as deep down as it can.** Reading the draft in
 * `ExploreScreen`'s body would put every keystroke through a recomposition of
 * the whole screen, `CityMapView` included, which is the one thing that file is
 * arranged to avoid. Reading the pinned location any higher than
 * [PinnedLocationRow] would do the same thing to this form on every frame of
 * every pan.
 */
@Composable
fun AddPlaceSheet(
    viewModel: ExploreViewModel,
    /** Whether this install has ever put the permission dialog up. */
    askedForLocation: Boolean,
    onAskedForLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.addDraft.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // The permission has to be asked for from the UI — a view model has no
    // Activity to ask with — so the launcher lives here and the view model is
    // only ever told to go and look. Asking again after a grant is what makes
    // the first tap do the thing rather than only opening a dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        onAskedForLocation()
        if (granted.values.any { it }) viewModel.onUseMyLocation(context)
    }

    // Held so the sheet keeps its content while it slides out, rather than
    // blanking the instant it is dismissed. Same trick as the visit card.
    var last by remember { mutableStateOf(draft) }
    if (draft != null) last = draft

    AnimatedVisibility(
        visible = draft != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        last?.let { current ->
            AddPlaceForm(
                draft = current,
                pinnedLocation = viewModel.pinnedLocationLabel,
                hasLocation = viewModel.hasPinnedLocation,
                locating = viewModel.locating,
                onDraftChange = viewModel::onDraftChanged,
                permission = locationPermissionState(context, askedForLocation),
                onUseMyLocation = {
                    focusManager.clearFocus()
                    // Granted: go straight to the fix. Refusable: ask, and the
                    // callback above goes for the fix on a yes. Permanently
                    // denied never reaches here — the button is disabled and
                    // offers Settings instead, because asking again would put
                    // no dialog up and look like a broken button.
                    if (viewModel.hasLocationPermission(context)) {
                        viewModel.onUseMyLocation(context)
                    } else {
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                },
                onOpenSettings = { openAppSettings(context) },
                onCancel = {
                    focusManager.clearFocus()
                    viewModel.onAddPlaceCancelled()
                },
                onSave = {
                    focusManager.clearFocus()
                    viewModel.onAddPlaceConfirmed()
                },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun AddPlaceForm(
    draft: AddPlaceDraft,
    pinnedLocation: StateFlow<String?>,
    hasLocation: StateFlow<Boolean>,
    locating: StateFlow<LocatingState>,
    permission: LocationPermissionState,
    onDraftChange: (AddPlaceDraft) -> Unit,
    onUseMyLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
        shape = RoundedCornerShape(24.dp),
        color = CitySurface,
        border = BorderStroke(1.dp, GlowAmber.copy(alpha = 0.18f)),
        tonalElevation = 8.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "ADD A PLACE",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Marked as explored, and counted like any other place.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Spacer(Modifier.height(14.dp))

            SheetField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                placeholder = "Name",
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(10.dp))

            SheetField(
                value = draft.address,
                onValueChange = { onDraftChange(draft.copy(address = it)) },
                placeholder = "Address (optional)",
                imeAction = ImeAction.Done,
            )

            Spacer(Modifier.height(14.dp))

            CategoryPicker(
                selected = draft.category,
                onSelect = { onDraftChange(draft.copy(category = it)) },
            )

            Spacer(Modifier.height(14.dp))

            PinnedLocationRow(pinnedLocation)

            Spacer(Modifier.height(10.dp))

            UseMyLocationRow(
                locating = locating,
                permission = permission,
                onUseMyLocation = onUseMyLocation,
                onOpenSettings = onOpenSettings,
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Cancel")
                }
                SaveButton(
                    draft = draft,
                    hasLocation = hasLocation,
                    onSave = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Where the ring currently is.
 *
 * Its own composable, taking the flow rather than the value, so the sixty
 * updates a second a pan produces recompose this row and nothing above it. The
 * ring over the map is the control; this only reports what it is over.
 */
@Composable
private fun PinnedLocationRow(
    pinnedLocation: StateFlow<String?>,
    modifier: Modifier = Modifier,
) {
    val label by pinnedLocation.collectAsStateWithLifecycle()

    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = GlowAmber,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label ?: "Waiting for the map",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text(
                text = "Drag the map to move the ring",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

/**
 * The shortcut for the ordinary case: you are standing in the place.
 *
 * Panning a map to where you already are is busywork, and at street zoom it is
 * slow busywork. This is one tap instead — and it is a shortcut rather than a
 * requirement, so every failure below leaves the ring exactly where the user
 * put it and says what happened rather than blocking the form.
 */
@Composable
private fun UseMyLocationRow(
    locating: StateFlow<LocatingState>,
    permission: LocationPermissionState,
    onUseMyLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by locating.collectAsStateWithLifecycle()
    val blocked = permission == LocationPermissionState.PERMANENTLY_DENIED
    val busy = state == LocatingState.Locating

    Column(modifier) {
        if (blocked) {
            // Asking again would put no dialog up at all, so the button stops
            // pretending and points at the one place that can still fix it.
            OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                Icon(
                    Icons.Outlined.MyLocation,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Turn on location in Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Location is off for City Memory — drag the ring instead",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            return@Column
        }

        OutlinedButton(
            onClick = onUseMyLocation,
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                Icons.Outlined.MyLocation,
                contentDescription = null,
                tint = if (busy) TextTertiary else GlowAmber,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (busy) "Finding you…" else "Use my location",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
        }

        val message = when {
            state is LocatingState.Located -> (state as LocatingState.Located).message
            state is LocatingState.Failed -> (state as LocatingState.Failed).message
            // Said before the second ask rather than after it, because after it
            // there may be no dialog left to explain.
            permission == LocationPermissionState.NEEDS_RATIONALE ->
                "City Memory only reads your location when you tap this"
            else -> null
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (state is LocatingState.Failed) TextTertiary else GlowAmber,
            )
        }
    }
}

/**
 * The commit.
 *
 * Collects [hasLocation] itself so that "there is nowhere to put this yet" is
 * visible in the button rather than being a tap that silently does nothing —
 * which is what it was, because the view model returns early with no pinned
 * location and said nothing about it. The flow is a de-duplicated boolean, so
 * this does not recompose while the ring moves.
 */
@Composable
private fun SaveButton(
    draft: AddPlaceDraft,
    hasLocation: StateFlow<Boolean>,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val located by hasLocation.collectAsStateWithLifecycle()

    Button(
        onClick = onSave,
        enabled = draft.canSave && located,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlowAmber,
            contentColor = Color.Black,
        ),
    ) {
        Text(if (draft.isSaving) "Saving…" else "Add place")
    }
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
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
private fun CategoryPicker(
    selected: PlaceCategory,
    onSelect: (PlaceCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two rows of three rather than a scrolling strip: six is few enough to show
    // at once, and a chip you have to scroll to find is one you will not use.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceCategory.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    val isSelected = category == selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(category.shortName, style = MaterialTheme.typography.labelMedium)
                        },
                        leadingIcon = {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = category.hue.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = category.hue,
                            labelColor = TextSecondary,
                            iconColor = TextTertiary,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The ring the form is talking about, over the exact middle of the map, which
 * is where a new place lands.
 *
 * An overlay in the same `Box` as the map rather than something drawn inside
 * the map's draw pass: the reveal compositing stays untouched, and this costs
 * the map nothing at all when no place is being added.
 */
@Composable
fun AddPlaceCrosshair(
    viewModel: ExploreViewModel,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.addDraft.collectAsStateWithLifecycle()

    AnimatedVisibility(visible = draft != null, enter = fadeIn(), exit = fadeOut()) {
        Canvas(
            modifier = modifier
                .size(CROSSHAIR_SIZE)
                .semantics { contentDescription = "The new place goes here" },
        ) {
            val middle = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - RING_WIDTH.toPx()
            drawCircle(GlowAmber.copy(alpha = 0.14f), radius = radius, center = middle)
            drawCircle(
                color = GlowAmber,
                radius = radius,
                center = middle,
                style = Stroke(width = RING_WIDTH.toPx()),
            )
            // A dot in the middle, because the ring alone leaves the exact point
            // ambiguous at the moment it matters most.
            drawCircle(GlowAmber, radius = RING_WIDTH.toPx(), center = middle)
        }
    }
}

private val CROSSHAIR_SIZE = 48.dp
private val RING_WIDTH = 2.dp

/** Formats a coordinate the way the pinned row shows it. */
internal fun formatCoordinate(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
