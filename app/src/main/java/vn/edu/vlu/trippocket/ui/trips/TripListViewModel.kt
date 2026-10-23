package vn.edu.vlu.trippocket.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.TripRepository

/** A trip with the totals its card shows, so the adapter never reads the repository. */
data class TripCard(val trip: Trip, val memberCount: Int, val spent: Long)

data class TripListUiState(
    val trips: List<TripCard> = emptyList(),
    /** Trips that are not synced yet (pending or failed). */
    val unsyncedCount: Int = 0,
)

/**
 * Screen 2. Shared by MainActivity (overflow menu) and TripListFragment (the list),
 * so the menu talks to the ViewModel instead of looking up the fragment.
 */
class TripListViewModel(
    private val savedStateHandle: SavedStateHandle,
    repository: TripRepository,
) : ViewModel() {

    private val allTrips = repository.trips().map { trip ->
        TripCard(trip, repository.members(trip.tripId).size, repository.spentTotal(trip.tripId))
    }

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    /** Overflow menu > "Preview empty state". Kept in SavedStateHandle so it survives rotation. */
    fun toggleEmptyPreview() {
        savedStateHandle[KEY_EMPTY] = !(savedStateHandle.get<Boolean>(KEY_EMPTY) ?: false)
        _uiState.value = buildState()
    }

    private fun buildState(): TripListUiState {
        val trips = if (savedStateHandle.get<Boolean>(KEY_EMPTY) == true) emptyList() else allTrips
        return TripListUiState(
            trips = trips,
            unsyncedCount = trips.count { it.trip.syncStatus != SyncStatus.SYNCED },
        )
    }

    companion object {
        private const val KEY_EMPTY = "show_empty_preview"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripListViewModel(createSavedStateHandle(), TripRepository()) }
        }
    }
}
