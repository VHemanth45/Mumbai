package com.citymemory.ui.screens.explore

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
import com.citymemory.domain.model.Place
import com.citymemory.domain.repository.CityGeometryProvider
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExploreUiState(
    val isLoading: Boolean = true,
    val cityName: String = "",
    val places: List<Place> = emptyList(),
    val geometry: CityGeometry = CityGeometry.Empty,
    val progress: ExplorationProgress = ExplorationProgress.Empty,
    /** The place whose light was last tapped on the map, shown as a peek card. */
    val selectedPlace: Place? = null,
)

class ExploreViewModel(
    private val repository: PlaceRepository,
    private val geometryProvider: CityGeometryProvider,
    private val cityId: String = MumbaiSeed.CITY_ID,
) : ViewModel() {

    private val selectedPlaceId = MutableStateFlow<String?>(null)
    private val geometry = MutableStateFlow(CityGeometry.Empty)

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
    ) { city, places, cityGeometry, selectedId ->
        ExploreUiState(
            isLoading = false,
            cityName = city?.name.orEmpty(),
            places = places,
            geometry = cityGeometry,
            // Derived here rather than stored, so the headline number can never
            // disagree with the lights on the map.
            progress = ExplorationSummarizer.progressOf(places),
            selectedPlace = places.firstOrNull { it.id == selectedId },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExploreUiState(),
    )

    fun onPlaceSelected(place: Place) {
        selectedPlaceId.value = place.id
    }

    fun onSelectionDismissed() {
        selectedPlaceId.value = null
    }

    fun onToggleWishlist(place: Place) {
        viewModelScope.launch { repository.setWishlisted(place.id, !place.isWishlisted) }
    }

    fun onToggleVisited(place: Place) {
        viewModelScope.launch { repository.setVisited(place.id, !place.isVisited) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExploreViewModel(
                    repository = appContainer.placeRepository,
                    geometryProvider = appContainer.cityGeometryProvider,
                )
            }
        }
    }
}
