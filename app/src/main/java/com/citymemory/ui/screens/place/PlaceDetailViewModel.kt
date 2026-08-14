package com.citymemory.ui.screens.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.citymemory.di.appContainer
import com.citymemory.domain.model.Place
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.navigation.Screen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaceDetailUiState(
    val isLoading: Boolean = true,
    val place: Place? = null,
) {
    /** True once loading has finished and the id turned out not to exist. */
    val isMissing: Boolean get() = !isLoading && place == null
}

class PlaceDetailViewModel(
    private val repository: PlaceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[Screen.PlaceDetail.ARG_PLACE_ID]) {
        "PlaceDetail requires a ${Screen.PlaceDetail.ARG_PLACE_ID} argument"
    }

    val uiState: StateFlow<PlaceDetailUiState> = repository.observePlace(placeId)
        .map { PlaceDetailUiState(isLoading = false, place = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlaceDetailUiState(),
        )

    fun onToggleVisited() {
        val place = uiState.value.place ?: return
        viewModelScope.launch { repository.setVisited(place.id, !place.isVisited) }
    }

    fun onToggleWishlist() {
        val place = uiState.value.place ?: return
        viewModelScope.launch { repository.setWishlisted(place.id, !place.isWishlisted) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlaceDetailViewModel(
                    repository = appContainer.placeRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
