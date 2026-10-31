package vn.edu.vlu.trippocket.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.TripRepository

/** Whether the trip in the intent can be shown to the signed-in account. */
sealed interface TripGate {
    data object Loading : TripGate
    data class Ready(val trip: Trip) : TripGate

    /** Missing, deleted, owned by another account, or signed out: the screen closes. */
    data object NotFound : TripGate
}

/**
 * Activity-scoped: holds the trip id for every tab and form of TripDetailActivity
 * (docs/ARCHITECTURE.md, Navigation). Fragments reach it with activityViewModels()
 * or, for their own ViewModel, with [tripViewModels].
 */
class TripDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: TripRepository,
) : ViewModel() {

    val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    val gate: StateFlow<TripGate> = repository.observeTrip(tripId)
        .map { trip -> if (trip == null) TripGate.NotFound else TripGate.Ready(trip) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripGate.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripDetailViewModel(createSavedStateHandle(), appContainer().tripRepository) }
        }
    }
}
