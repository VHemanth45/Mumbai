package com.citymemory.ui.screens.explore

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.di.appContainer
import com.citymemory.domain.ExplorationSummarizer
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.CityGeometryProvider
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.map.FlyTarget
import com.citymemory.util.LocationFix
import com.citymemory.util.LocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class ExploreUiState(
    val isLoading: Boolean = true,
    val cityName: String = "",
    val places: List<Place> = emptyList(),
    val geometry: CityGeometry = CityGeometry.Empty,
    val progress: ExplorationProgress = ExplorationProgress.Empty,
    /**
     * The place the search picked out: the map flies to it and it opens as a
     * card. The map itself no longer selects anything — tapping it only zooms.
     */
    val selectedPlace: Place? = null,
    val searchQuery: String = "",
    /** Matches for [searchQuery], empty when the box is empty. */
    val searchResults: List<Place> = emptyList(),
)

/**
 * The place being typed in, while it is being typed in.
 *
 * [location] is not here: it comes off the map at up to sixty updates a second
 * while the user pans, and putting it in the same object as the text fields
 * would recompose the form on every frame of every drag. See [ExploreViewModel]
 * for why none of this lives in [ExploreUiState] either.
 */
data class AddPlaceDraft(
    val name: String = "",
    val address: String = "",
    val category: PlaceCategory = PlaceCategory.HIDDEN_GEM,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving
}

/** What the "use my location" button is doing, and what came of it. */
sealed interface LocatingState {
    data object Idle : LocatingState
    data object Locating : LocatingState

    /** A fix arrived. [message] says how good it was, or that it is out of town. */
    data class Located(val message: String) : LocatingState

    /** No fix, and the reason a person can act on. */
    data class Failed(val message: String) : LocatingState
}

class ExploreViewModel(
    private val repository: PlaceRepository,
    private val geometryProvider: CityGeometryProvider,
    private val locationSource: LocationSource,
    private val cityId: String = MumbaiSeed.CITY_ID,
) : ViewModel() {

    private val selectedPlaceId = MutableStateFlow<String?>(null)
    private val geometry = MutableStateFlow(CityGeometry.Empty)
    private val searchQuery = MutableStateFlow("")

    /**
     * Deliberately outside [uiState], and so is [pinnedLocation].
     *
     * `uiState` is what `CityMapView` is given, so anything folded into it
     * recomposes the map when it changes — and these two change on every
     * keystroke and every frame of a pan respectively. Kept separate, the form
     * recomposes and the map does not.
     */
    private val _addDraft = MutableStateFlow<AddPlaceDraft?>(null)
    val addDraft: StateFlow<AddPlaceDraft?> = _addDraft.asStateFlow()

    /**
     * Whether a place is being positioned right now.
     *
     * Derived and de-duplicated rather than read off [addDraft], because this
     * one *is* read by `ExploreScreen`'s body — it has to be, the map takes it
     * as a parameter — and reading the draft there would recompose the whole
     * screen, map included, on every keystroke. As a distinct boolean it
     * changes exactly twice per place added.
     */
    val isPickingLocation: StateFlow<Boolean> = _addDraft
        .map { it != null }
        .distinctUntilChanged()
        // Eagerly, not WhileSubscribed. These derived booleans are read for
        // *decisions* — whether the map zooms, whether Save is live — and a
        // `WhileSubscribed` flow with no collector holds its initial value
        // rather than the truth. The upstream is a MutableStateFlow and a
        // comparison, so there is nothing to save by being lazy about it.
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Where the ring currently is; null until the map has been drawn and has
     * said. Exposed as itself so callers that need the coordinate rather than
     * the label are not going through a formatted string to get it.
     */
    private val _pinnedLocation = MutableStateFlow<GeoPoint?>(null)
    val pinnedLocation: StateFlow<GeoPoint?> = _pinnedLocation.asStateFlow()

    /**
     * The same thing as text, which is all the form needs.
     *
     * Formatted here rather than in the composable so the flow the form
     * collects only changes when the *displayed* value does: five decimal
     * places is a bit over a metre, so a pan that has not moved the pin that
     * far does not recompose anything at all.
     */
    val pinnedLocationLabel: StateFlow<String?> = _pinnedLocation
        .map { point -> point?.let { formatCoordinate(it.latitude, it.longitude) } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * Whether there is anywhere to save the place to yet.
     *
     * Its own flow rather than a null check on [pinnedLocationLabel], which
     * changes every time the ring moves a metre. This flips once, when the map
     * first reports where it is looking, so the Save button can depend on it
     * without recomposing the form through a pan.
     */
    val hasPinnedLocation: StateFlow<Boolean> = _pinnedLocation
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            geometry.value = geometryProvider.geometryFor(cityId)
        }
    }

    val uiState: StateFlow<ExploreUiState> = combine(
        repository.observeCity(cityId),
        repository.observePlaces(cityId),
        geometry,
        selectedPlaceId,
        searchQuery,
    ) { city, places, cityGeometry, selectedId, query ->
        ExploreUiState(
            isLoading = false,
            cityName = city?.name.orEmpty(),
            places = places,
            geometry = cityGeometry,
            // Derived here rather than stored, so the headline number can never
            // disagree with the lights on the map.
            progress = ExplorationSummarizer.progressOf(places),
            selectedPlace = places.firstOrNull { it.id == selectedId },
            searchQuery = query,
            // Searched off the same list the map draws, so a result's stars and
            // its light on the map are always the same fact.
            searchResults = places.matching(query),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExploreUiState(),
    )

    fun onPlaceSelected(place: Place) {
        selectedPlaceId.value = place.id
        // The result list has done its job and would otherwise cover the very
        // place the map is flying to.
        searchQuery.value = ""
    }

    fun onSelectionDismissed() {
        selectedPlaceId.value = null
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    /**
     * Commits the whole card at once: whether the place has been explored, what
     * it scored, and what the user thought of it.
     *
     * One entry point rather than three so a half-filled form cannot be left
     * half-saved — the visit and the verdict are the same decision.
     */
    fun onSaveVisit(place: Place, visited: Boolean, rating: Int?, note: String) {
        viewModelScope.launch {
            if (visited != place.isVisited) {
                repository.setVisited(place.id, visited)
            }
            repository.setReview(place.id, rating, note)
        }
    }

    fun onToggleWishlist(place: Place) {
        viewModelScope.launch { repository.setWishlisted(place.id, !place.isWishlisted) }
    }

    fun onToggleVisited(place: Place) {
        viewModelScope.launch { repository.setVisited(place.id, !place.isVisited) }
    }

    // ---- Adding a place the catalog does not have -------------------------

    /**
     * Called by the map on every camera settle, whether or not anything is
     * being added. Reporting unconditionally is what keeps the map's parameters
     * stable — see `CityMapView.onViewportCenterChange`.
     */
    fun onViewportCenterChanged(point: GeoPoint) {
        _pinnedLocation.value = point
    }

    /**
     * Opens the form, seeded with whatever was typed into the search box.
     *
     * That is the whole reason this entry point is on a failed search: you look
     * for the place you went to, it is not in the catalog, and the name you
     * already typed is the name of the place you are about to add.
     */
    fun onAddPlaceRequested(name: String) {
        _addDraft.value = AddPlaceDraft(name = name.trim())
        searchQuery.value = ""
        selectedPlaceId.value = null
        // Otherwise the form opens showing "Found you, to about 12 m" about the
        // *previous* place, which reads as a claim about this one.
        _locating.value = LocatingState.Idle
        _flyTo.value = null
    }

    fun onDraftChanged(draft: AddPlaceDraft) {
        // Ignored once saving has started, so a keystroke landing mid-write
        // cannot resurrect the form after it has been dismissed.
        if (_addDraft.value?.isSaving == true) return
        _addDraft.value = draft
    }

    fun onAddPlaceCancelled() {
        _addDraft.value = null
        _locating.value = LocatingState.Idle
        _flyTo.value = null
    }

    // ---- Use my location --------------------------------------------------

    private val _locating = MutableStateFlow<LocatingState>(LocatingState.Idle)
    val locating: StateFlow<LocatingState> = _locating.asStateFlow()

    /**
     * Somewhere the map has been asked to go — see [FlyTarget] for why it
     * carries a token.
     *
     * It is never fed from what the map reports, which would be a loop: the map
     * says where it is looking, and if that drove the camera the camera would
     * drive the report.
     */
    private var flyToken = 0L
    private val _flyTo = MutableStateFlow<FlyTarget?>(null)
    val flyTo: StateFlow<FlyTarget?> = _flyTo.asStateFlow()

    /**
     * Puts the ring where the user is standing.
     *
     * Takes a [Context] rather than holding one, because a view model that
     * holds a Context is a leaked Activity waiting to happen and because the
     * permission check has to be made against the one asking.
     */
    /** Asked before offering the shortcut, so a granted app does not re-prompt. */
    fun hasLocationPermission(context: Context): Boolean = locationSource.hasPermission(context)

    fun onUseMyLocation(context: Context) {
        if (_locating.value == LocatingState.Locating) return
        _locating.value = LocatingState.Locating
        viewModelScope.launch {
            _locating.value = when (val fix = locationSource.currentLocation(context)) {
                is LocationFix.Found -> acceptFix(fix)
                LocationFix.PermissionDenied ->
                    LocatingState.Failed("Location permission is off")
                LocationFix.Unavailable ->
                    LocatingState.Failed("Turn on location to use this")
                LocationFix.TimedOut ->
                    LocatingState.Failed("No signal yet — try outside, or drag the ring")
            }
        }
    }

    /**
     * A fix is only useful if it is somewhere this map can draw.
     *
     * The catalog is one city. Someone in Pune gets a perfectly good fix that
     * the projector would put off the canvas, and silently flying the camera to
     * the edge of Mumbai would look like a bug rather than an answer — so it
     * says so and leaves the ring where they put it.
     */
    private fun acceptFix(fix: LocationFix.Found): LocatingState {
        val bounds = geometry.value.bounds
        val inside = fix.point.latitude in bounds.minLatitude..bounds.maxLatitude &&
            fix.point.longitude in bounds.minLongitude..bounds.maxLongitude
        if (!inside) {
            return LocatingState.Failed("You are outside Mumbai — drag the ring instead")
        }

        // Deliberately *not* `_pinnedLocation.value = fix.point`. The ring is
        // what gets saved, and it must stay the only thing that decides that:
        // the camera flies to the fix and reports where it arrived, exactly as
        // it does for a pan. Setting both here is what let them disagree.
        _flyTo.value = FlyTarget(fix.point, ++flyToken)
        val accuracy = fix.accuracyMeters?.roundToInt()
        return LocatingState.Located(
            if (accuracy != null) "Found you, to about $accuracy m" else "Found you",
        )
    }

    /**
     * Writes the place and opens it, so the map flies to what was just added
     * and the card is there to rate it. Marked visited by the repository: the
     * reason to add a place by hand is almost always that you just came back.
     */
    fun onAddPlaceConfirmed() {
        val draft = _addDraft.value ?: return
        val location = _pinnedLocation.value ?: return
        if (!draft.canSave) return

        _addDraft.value = draft.copy(isSaving = true)
        viewModelScope.launch {
            val id = repository.addUserPlace(
                cityId = cityId,
                name = draft.name,
                category = draft.category,
                latitude = location.latitude,
                longitude = location.longitude,
                address = draft.address,
            )
            _addDraft.value = null
            selectedPlaceId.value = id
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Enough to find what you meant, few enough not to bury the map. */
        const val MAX_SEARCH_RESULTS = 8

        /**
         * Places matching [query] by name, address or category, best guess first.
         *
         * Search is over the seeded catalog, not over the map: the shipped
         * geometry carries no names, and the app requests no INTERNET
         * permission, so there is nothing else here to look a place up in.
         *
         * Every word has to match something, which is what makes a catalog with
         * thirty-two Starbucks in it usable: "starbucks bandra" is one word
         * against the name and one against the address, and it finds the one you
         * mean instead of the first six the list happens to hold.
         */
        internal fun List<Place>.matching(query: String): List<Place> {
            val words = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList()
            return asSequence()
                .filter { place ->
                    words.all { word ->
                        place.name.contains(word, ignoreCase = true) ||
                            place.address?.contains(word, ignoreCase = true) == true ||
                            place.category.displayName.contains(word, ignoreCase = true)
                    }
                }
                // A name that *starts* with what was typed is almost always the
                // one meant, and the sort is stable so the rest keep map order.
                .sortedByDescending { it.name.startsWith(words.first(), ignoreCase = true) }
                .take(MAX_SEARCH_RESULTS)
                .toList()
        }

        private val WHITESPACE = Regex("\\s+")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExploreViewModel(
                    repository = appContainer.placeRepository,
                    geometryProvider = appContainer.cityGeometryProvider,
                    locationSource = appContainer.locationSource,
                )
            }
        }
    }
}
