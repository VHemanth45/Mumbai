package com.citymemory.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.di.appContainer
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which slice of the catalog the Discover list is showing. */
enum class DiscoverFilter(val label: String) {
    ALL("All"),
    UNEXPLORED("Unexplored"),
    EXPLORED("Explored"),
    WISHLIST("Wishlist"),
}

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val category: PlaceCategory? = null,
    val filter: DiscoverFilter = DiscoverFilter.ALL,
    val places: List<Place> = emptyList(),
    val totalCount: Int = 0,
    val categoryCounts: Map<PlaceCategory, Int> = emptyMap(),
) {
    val isFiltered: Boolean
        get() = query.isNotBlank() || category != null || filter != DiscoverFilter.ALL
}

class DiscoverViewModel(
    private val repository: PlaceRepository,
    cityId: String = MumbaiSeed.CITY_ID,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<PlaceCategory?>(null)
    private val filter = MutableStateFlow(DiscoverFilter.ALL)

    val uiState: StateFlow<DiscoverUiState> = combine(
        repository.observePlaces(cityId),
        query,
        category,
        filter,
    ) { places, currentQuery, currentCategory, currentFilter ->
        DiscoverUiState(
            isLoading = false,
            query = currentQuery,
            category = currentCategory,
            filter = currentFilter,
            places = places.applyFilters(currentQuery, currentCategory, currentFilter),
            totalCount = places.size,
            categoryCounts = places.groupingBy { it.category }.eachCount(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DiscoverUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Tapping the active category clears it, so the chip row is a toggle. */
    fun onCategorySelected(value: PlaceCategory?) {
        category.value = if (category.value == value) null else value
    }

    fun onFilterSelected(value: DiscoverFilter) {
        filter.value = value
    }

    fun onClearFilters() {
        query.value = ""
        category.value = null
        filter.value = DiscoverFilter.ALL
    }

    fun onToggleWishlist(place: Place) {
        viewModelScope.launch { repository.setWishlisted(place.id, !place.isWishlisted) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Filtering runs in memory over the full catalog rather than in SQL.
         * At 80 places that is instantaneous and keeps every screen fed by the
         * one observePlaces stream; a city with tens of thousands of places
         * would want this pushed down into a query instead.
         */
        private fun List<Place>.applyFilters(
            query: String,
            category: PlaceCategory?,
            filter: DiscoverFilter,
        ): List<Place> {
            val trimmed = query.trim()
            return asSequence()
                .filter { category == null || it.category == category }
                .filter {
                    when (filter) {
                        DiscoverFilter.ALL -> true
                        DiscoverFilter.UNEXPLORED -> !it.isVisited
                        DiscoverFilter.EXPLORED -> it.isVisited
                        DiscoverFilter.WISHLIST -> it.isWishlisted
                    }
                }
                .filter {
                    trimmed.isEmpty() ||
                        it.name.contains(trimmed, ignoreCase = true) ||
                        it.description.contains(trimmed, ignoreCase = true) ||
                        it.category.displayName.contains(trimmed, ignoreCase = true)
                }
                .toList()
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DiscoverViewModel(repository = appContainer.placeRepository)
            }
        }
    }
}
