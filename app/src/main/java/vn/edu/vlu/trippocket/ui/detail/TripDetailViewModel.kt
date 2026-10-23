package vn.edu.vlu.trippocket.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.TripRepository

/** Title bar of the trip detail screen. The trip id comes from the intent extras. */
class TripDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: TripRepository,
) : ViewModel() {

    val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    /** null when the id does not match any trip. */
    val trip: StateFlow<Trip?> = MutableStateFlow(repository.trip(tripId))

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripDetailViewModel(createSavedStateHandle(), TripRepository()) }
        }
    }
}
