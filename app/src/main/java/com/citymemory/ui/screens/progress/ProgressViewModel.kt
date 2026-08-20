package com.citymemory.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.di.appContainer
import com.citymemory.domain.ExplorationSummarizer
import com.citymemory.domain.Neighbourhoods
import com.citymemory.domain.model.Achievement
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.domain.model.MapLabel
import com.citymemory.domain.repository.CityGeometryProvider
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val cityName: String = "",
    val progress: ExplorationProgress = ExplorationProgress.Empty,
    val achievements: List<Achievement> = emptyList(),
) {
    val unlockedCount: Int get() = achievements.count { it.isUnlocked }
}

class ProgressViewModel(
    repository: PlaceRepository,
    private val geometryProvider: CityGeometryProvider,
    cityId: String = MumbaiSeed.CITY_ID,
) : ViewModel() {

    /**
     * The city's named areas, which this screen needs only to count them.
     *
     * Loaded here rather than taken from the map, because Progress is reachable
     * without Explore ever having been on screen. It is one asset read behind a
     * `MutableStateFlow`, so the screen renders immediately with no
     * neighbourhood line and gains it a frame later, rather than waiting.
     */
    private val areas = MutableStateFlow<List<MapLabel>>(emptyList())

    init {
        viewModelScope.launch {
            areas.value = Neighbourhoods.areasIn(geometryProvider.geometryFor(cityId).labels)
        }
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        repository.observeCity(cityId),
        repository.observePlaces(cityId),
        areas,
    ) { city, places, cityAreas ->
        ProgressUiState(
            isLoading = false,
            cityName = city?.name.orEmpty(),
            progress = ExplorationSummarizer.progressOf(places, cityAreas),
            // Recomputed from the place list every emission — there is no
            // "achievement unlocked" record anywhere that could go stale.
            achievements = ExplorationSummarizer.achievementsOf(places, cityAreas),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProgressUiState(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    repository = appContainer.placeRepository,
                    geometryProvider = appContainer.cityGeometryProvider,
                )
            }
        }
    }
}
