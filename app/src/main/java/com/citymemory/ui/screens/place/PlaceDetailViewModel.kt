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
import com.citymemory.domain.model.PlacePhoto
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaceDetailUiState(
    val isLoading: Boolean = true,
    val place: Place? = null,
    /** The user's own photos of this place, oldest first. */
    val photos: List<PlacePhoto> = emptyList(),
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

    val uiState: StateFlow<PlaceDetailUiState> = combine(
        repository.observePlace(placeId),
        repository.observePhotos(placeId),
    ) { place, photos ->
        PlaceDetailUiState(isLoading = false, place = place, photos = photos)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PlaceDetailUiState(),
    )

    /**
     * True for the moment between picking a photo and the copy landing.
     *
     * Import decodes, rotates and re-encodes a 12 MP image, which is long
     * enough to see. Without this the screen would look like nothing happened.
     */
    private val _addingPhoto = MutableStateFlow(false)
    val addingPhoto: StateFlow<Boolean> = _addingPhoto.asStateFlow()

    /** Set when an import fails, so the screen can say so rather than nothing. */
    private val _photoError = MutableStateFlow<String?>(null)
    val photoError: StateFlow<String?> = _photoError.asStateFlow()

    fun onPhotoPicked(uri: String) {
        _addingPhoto.value = true
        viewModelScope.launch {
            val added = repository.addPhoto(placeId, uri)
            _addingPhoto.value = false
            // From the user's side they picked a photo and nothing appeared, so
            // silence would read as a broken screen rather than a bad file.
            if (!added) _photoError.value = "That photo could not be added"
        }
    }

    fun onPhotoErrorShown() {
        _photoError.value = null
    }

    fun onDeletePhoto(photoId: String) {
        viewModelScope.launch { repository.deletePhoto(photoId) }
    }

    fun onToggleVisited() {
        val place = uiState.value.place ?: return
        viewModelScope.launch { repository.setVisited(place.id, !place.isVisited) }
    }

    fun onToggleWishlist() {
        val place = uiState.value.place ?: return
        viewModelScope.launch { repository.setWishlisted(place.id, !place.isWishlisted) }
    }

    /**
     * Writes the address the user typed.
     *
     * Offered on every place, not only the ones they added: about one place in
     * four has a street address in OpenStreetMap and the rest carry only a
     * locality and pin code, so for most of the catalog the person who went
     * there knows this better than the extract does.
     */
    fun onAddressChanged(address: String) {
        val place = uiState.value.place ?: return
        viewModelScope.launch { repository.setAddress(place.id, address) }
    }

    /** Only ever offered for a place the user added; the catalog is read-only. */
    fun onDeleteUserPlace(onDeleted: () -> Unit) {
        val place = uiState.value.place ?: return
        if (!place.isUserAdded) return
        viewModelScope.launch {
            repository.deleteUserPlace(place.id)
            onDeleted()
        }
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
