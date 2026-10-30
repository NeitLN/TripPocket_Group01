package vn.edu.vlu.trippocket.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository

/** A trip with the totals its card shows, so the adapter never reads the repository. */
data class TripCard(val trip: Trip, val memberCount: Int, val spent: Long)

data class TripListUiState(
    val loading: Boolean = true,
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
    tripRepository: TripRepository,
    // Member count and spent total move to Minh's ExpenseRepository with E1/E2.
    private val sampleRepository: SampleTripRepository,
) : ViewModel() {

    val uiState: StateFlow<TripListUiState> = combine(
        tripRepository.observeTrips(),
        savedStateHandle.getStateFlow(KEY_EMPTY, false),
    ) { trips, showEmpty ->
        val cards = if (showEmpty) emptyList() else trips.map(::toCard)
        TripListUiState(
            loading = false,
            trips = cards,
            unsyncedCount = cards.count { it.trip.syncStatus != SyncStatus.SYNCED },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState())

    /** Overflow menu > "Preview empty state". Kept in SavedStateHandle so it survives rotation. */
    fun toggleEmptyPreview() {
        savedStateHandle[KEY_EMPTY] = !(savedStateHandle.get<Boolean>(KEY_EMPTY) ?: false)
    }

    private fun toCard(trip: Trip) = TripCard(
        trip = trip,
        memberCount = sampleRepository.members(trip.tripId).size,
        spent = sampleRepository.spentTotal(trip.tripId),
    )

    companion object {
        private const val KEY_EMPTY = "show_empty_preview"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                TripListViewModel(createSavedStateHandle(), container.tripRepository, container.sampleTripRepository)
            }
        }
    }
}
