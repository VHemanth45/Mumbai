package com.citymemory.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.di.appContainer
import com.citymemory.domain.model.Place
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WishlistUiState(
    val isLoading: Boolean = true,
    val pending: List<Place> = emptyList(),
    val explored: List<Place> = emptyList(),
) {
    val isEmpty: Boolean get() = pending.isEmpty() && explored.isEmpty()
    val totalCount: Int get() = pending.size + explored.size
}

class WishlistViewModel(
    private val repository: PlaceRepository,
    cityId: String = MumbaiSeed.CITY_ID,
) : ViewModel() {

    val uiState: StateFlow<WishlistUiState> = repository.observePlaces(cityId)
        .map { places ->
            val wishlisted = places.filter { it.isWishlisted }
            WishlistUiState(
                isLoading = false,
                // Still-to-visit first: the wishlist is a to-do list, and the
                // places already explored are just a satisfying tail.
                pending = wishlisted.filterNot { it.isVisited },
                explored = wishlisted.filter { it.isVisited },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = WishlistUiState(),
        )

    fun onRemoveFromWishlist(place: Place) {
        viewModelScope.launch { repository.setWishlisted(place.id, false) }
    }

    fun onToggleVisited(place: Place) {
        viewModelScope.launch { repository.setVisited(place.id, !place.isVisited) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WishlistViewModel(repository = appContainer.placeRepository)
            }
        }
    }
}
