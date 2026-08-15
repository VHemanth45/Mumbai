package com.citymemory.ui.screens.place

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlacePhoto
import com.citymemory.ui.components.EmptyState
import com.citymemory.ui.components.LoadingState
import com.citymemory.ui.theme.CityNight
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.GlowCore
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.WishCyan
import com.citymemory.ui.theme.hue
import com.citymemory.ui.theme.icon
import com.citymemory.ui.util.rememberPlaceNavigator
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlaceDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceDetailViewModel = viewModel(factory = PlaceDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isAddingPhoto by viewModel.addingPhoto.collectAsStateWithLifecycle()
    val photoError by viewModel.photoError.collectAsStateWithLifecycle()

    // Surfaced rather than swallowed: from the user's side they picked a photo
    // and nothing appeared, which reads as a broken screen not a bad file.
    LaunchedEffect(photoError) {
        photoError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onPhotoErrorShown()
        }
    }

    val navigateTo = rememberPlaceNavigator { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState()

            state.isMissing -> Column(Modifier.fillMaxSize().padding(innerPadding)) {
                BackButton(onBack)
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Place not found",
                    message = "This place is no longer part of the city dataset.",
                )
            }

            else -> {
                val place = state.place ?: return@Scaffold
                PlaceDetailContent(
                    place = place,
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    onBack = onBack,
                    onNavigate = { navigateTo(place) },
                    onToggleWishlist = viewModel::onToggleWishlist,
                    onToggleVisited = {
                        val wasVisited = place.isVisited
                        viewModel.onToggleVisited()
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = if (wasVisited) {
                                    "${place.name} is dark again"
                                } else {
                                    "${place.name} is lit"
                                },
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onToggleVisited()
                            }
                        }
                    },
                    onAddressChange = viewModel::onAddressChanged,
                    onDelete = { viewModel.onDeleteUserPlace(onDeleted = onBack) },
                    photos = state.photos,
                    isAddingPhoto = isAddingPhoto,
                    onPhotoPicked = viewModel::onPhotoPicked,
                    onDeletePhoto = viewModel::onDeletePhoto,
                )
            }
        }
    }
}

@Composable
private fun PlaceDetailContent(
    place: Place,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onToggleWishlist: () -> Unit,
    onToggleVisited: () -> Unit,
    onAddressChange: (String) -> Unit,
    onDelete: () -> Unit,
    photos: List<PlacePhoto>,
    isAddingPhoto: Boolean,
    onPhotoPicked: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PlaceHero(place = place, onBack = onBack)

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = place.category.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = place.category.hue,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = place.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (place.isVisited) {
                Spacer(Modifier.height(10.dp))
                ExploredBadge(visitedAt = place.visitedAt)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = place.description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )

            if (place.hasReview) {
                Spacer(Modifier.height(20.dp))
                YourVerdict(place)
            }

            Spacer(Modifier.height(20.dp))

            // Directly under the verdict, and above the address, because these
            // two are the same thing: what *you* have to say about this place,
            // as against what the extract has to say about it. The address and
            // the coordinates are reference data and belong below them.
            PlacePhotosSection(
                photos = photos,
                isAdding = isAddingPhoto,
                onPhotoPicked = onPhotoPicked,
                onDeletePhoto = onDeletePhoto,
            )

            Spacer(Modifier.height(24.dp))

            LocationRow(place = place, onAddressChange = onAddressChange)

            if (place.isUserAdded) {
                Spacer(Modifier.height(12.dp))
                RemovePlaceRow(name = place.name, onDelete = onDelete)
            }

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onToggleWishlist,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (place.isWishlisted) WishCyan.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Icon(
                        imageVector = if (place.isWishlisted) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = null,
                        tint = if (place.isWishlisted) WishCyan else TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (place.isWishlisted) "Wishlisted" else "Wishlist",
                        color = if (place.isWishlisted) WishCyan else TextSecondary,
                    )
                }

                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Navigate", color = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onToggleVisited,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (place.isVisited) Color.White.copy(alpha = 0.08f) else GlowAmber,
                    contentColor = if (place.isVisited) GlowAmber else CityNight,
                ),
            ) {
                if (place.isVisited) {
                    Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Explored", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(
                        text = "Mark as Visited",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (place.isVisited) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap again to undo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(bottomPadding + 32.dp))
        }
    }
}

/**
 * The stand-in for a photograph, at hero scale. Visited places gain a warm
 * bloom that animates in — the one moment where the app is allowed to be showy.
 */
@Composable
private fun PlaceHero(place: Place, onBack: () -> Unit) {
    val glow by animateFloatAsState(
        targetValue = if (place.isVisited) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "heroGlow",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        place.category.hue.copy(alpha = 0.22f + 0.18f * glow),
                        GlowAmber.copy(alpha = 0.10f * glow),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Icon(
            imageVector = place.category.icon,
            contentDescription = null,
            tint = place.category.hue.copy(alpha = 0.22f + 0.25f * glow),
            modifier = Modifier
                .align(Alignment.Center)
                .size(112.dp),
        )

        if (place.isVisited) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(200.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(GlowCore.copy(alpha = 0.16f * glow), Color.Transparent),
                        ),
                    ),
            )
        }

        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .statusBarsPadding()
            .padding(8.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(CityNight.copy(alpha = 0.55f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ExploredBadge(visitedAt: Long?) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GlowAmber.copy(alpha = 0.14f),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = GlowAmber,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = visitedAt?.let { "Explored on ${formatVisitDate(it)}" } ?: "Explored",
                style = MaterialTheme.typography.labelMedium,
                color = GlowAmber,
            )
        }
    }
}

/**
 * What the user said about the place, back to them.
 *
 * Read-only here on purpose: the rating and the opinion are written on the
 * Explore card, next to the map that flew you here, and having two places to
 * edit the same sentence is how they end up disagreeing.
 */
@Composable
private fun YourVerdict(place: Place) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = GlowAmber.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, GlowAmber.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "YOUR VERDICT",
                style = MaterialTheme.typography.labelSmall,
                color = GlowAmber,
            )

            place.rating?.let { rating ->
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics {
                        contentDescription = "Rated $rating out of 5"
                    },
                ) {
                    for (star in 1..5) {
                        Icon(
                            imageVector = if (star <= rating) {
                                Icons.Filled.Star
                            } else {
                                Icons.Filled.StarBorder
                            },
                            contentDescription = null,
                            tint = if (star <= rating) GlowAmber else TextTertiary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }

            if (!place.note.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = place.note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LocationRow(place: Place, onAddressChange: (String) -> Unit) {
    // The address is editable in place rather than behind an edit screen: it is
    // one line, and the moment you want to fix it is the moment you are looking
    // at it and can see it is wrong.
    var editing by remember(place.id) { mutableStateOf(false) }
    var draft by remember(place.id, place.address) { mutableStateOf(place.address.orEmpty()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CitySurface,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Address",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(2.dp))

                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Street, area, pin code", color = TextTertiary)
                        },
                        singleLine = false,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CitySurface,
                            unfocusedContainerColor = CitySurface,
                            focusedBorderColor = GlowAmber.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = GlowAmber,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onAddressChange(draft)
                                editing = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlowAmber,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = {
                                draft = place.address.orEmpty()
                                editing = false
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                } else {
                    Text(
                        text = place.address ?: "No address yet — add the one you went to",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (place.address != null) TextSecondary else TextTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "%.4f, %.4f",
                            place.latitude,
                            place.longitude,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }

            if (!editing) {
                IconButton(onClick = { editing = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit address",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Removing a place the user added.
 *
 * Only ever shown for [Place.isUserAdded]. There is no equivalent for a
 * catalogued place: the catalog is regenerated from OpenStreetMap and a
 * deletion would come back on the next update, so offering it would be a lie.
 *
 * Two taps rather than a dialog. The row arms itself, and stays armed only
 * while the screen is composed, so leaving and coming back disarms it.
 */
@Composable
private fun RemovePlaceRow(
    name: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var armed by remember(name) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CitySurface,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "You added this place",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Text(
                    text = if (armed) "Remove it and everything you wrote?" else "Remove it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            if (armed) {
                OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(12.dp)) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = { armed = true }) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove ${'$'}name",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * The formatter is built per call rather than held in a field: caching one binds
 * whatever locale happened to be active at class-init, and dates would then keep
 * rendering in the old language after the user switches system locale.
 */
private fun formatVisitDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
