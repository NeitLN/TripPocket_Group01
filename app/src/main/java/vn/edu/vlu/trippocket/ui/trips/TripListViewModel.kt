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
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.TripGroup
import vn.edu.vlu.trippocket.domain.TripGrouping
import java.time.LocalDate

/** A trip with the totals its card shows, so the adapter never reads the repository. */
data class TripCard(val trip: Trip, val memberCount: Int, val spent: Long)

/** One row of the grouped list: a group label or a trip card. */
sealed interface TripListRow {
    data class Header(val group: TripGroup) : TripListRow
    data class Item(val card: TripCard) : TripListRow
}

data class TripListUiState(
    val loading: Boolean = true,
    val trips: List<TripCard> = emptyList(),
    /** [trips] split into Happening now / Upcoming / Past, ready for the adapter. */
    val rows: List<TripListRow> = emptyList(),
    /** Trips that are not synced yet (pending or failed). */
    val unsyncedCount: Int = 0,
    /** Saved on this phone, waiting for the backup job. */
    val pendingCount: Int = 0,
    /** Saved on this phone, last backup attempt failed. */
    val failedCount: Int = 0,
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
    private val auth: AuthRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val uiState: StateFlow<TripListUiState> = combine(
        tripRepository.observeTrips(),
        savedStateHandle.getStateFlow(KEY_EMPTY, false),
    ) { trips, showEmpty ->
        val cards = if (showEmpty) emptyList() else trips.map(::toCard)
        TripListUiState(
            loading = false,
            trips = cards,
            rows = TripGrouping.group(cards, today(), { it.trip.startDate }, { it.trip.endDate })
                .flatMap { (group, members) -> listOf(TripListRow.Header(group)) + members.map(TripListRow::Item) },
            unsyncedCount = cards.count { it.trip.syncStatus != SyncStatus.SYNCED },
            pendingCount = cards.count { it.trip.syncStatus == SyncStatus.PENDING },
            failedCount = cards.count { it.trip.syncStatus == SyncStatus.FAILED },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState())

    /** Overflow menu > "Preview empty state". Kept in SavedStateHandle so it survives rotation. */
    fun toggleEmptyPreview() {
        savedStateHandle[KEY_EMPTY] = !(savedStateHandle.get<Boolean>(KEY_EMPTY) ?: false)
    }

    /**
     * Overflow menu > "Sign out" (A1). The session ends, so [AuthRepository.currentUid] turns
     * null and the list hides the trips; the rows stay in Room for the next sign-in.
     */
    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            // TODO(A1): cancel this device's reminders through ReminderReconciler once its
            // module (Bảo/Minh) is merged; no reconciler exists on this branch yet.
        }
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
                TripListViewModel(
                    createSavedStateHandle(),
                    container.tripRepository,
                    container.sampleTripRepository,
                    container.authRepository,
                )
            }
        }
    }
}
