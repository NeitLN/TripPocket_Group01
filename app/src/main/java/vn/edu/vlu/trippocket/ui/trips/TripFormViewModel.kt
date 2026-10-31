package vn.edu.vlu.trippocket.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.TripInput
import vn.edu.vlu.trippocket.data.repository.InvalidTripException
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.domain.TripProblem
import java.time.LocalDate

/** Argument of the trip to edit; absent when creating one. */
const val ARG_EDIT_TRIP_ID = "edit_trip_id"

data class TripFormUiState(
    val editing: Boolean = false,
    /** true while the trip to edit is read from Room. */
    val loading: Boolean = false,
    val name: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** The fields the last save fails; empty = no error. */
    val problems: Set<TripProblem> = emptySet(),
    /** Save pressed with no dates chosen. */
    val datesMissing: Boolean = false,
    val notFound: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** One-shot: the id of the trip just created or edited; the screen closes, then calls [TripFormViewModel.savedShown]. */
    val savedTripId: String? = null,
) {
    fun has(problem: TripProblem): Boolean = problem in problems
}

/**
 * New / edit trip form (A2). Fields are checked here for immediate feedback; the repository
 * checks them again inside its transaction, plus the rules that need Room (ownership, days that
 * still hold activities).
 */
class TripFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: String? = savedStateHandle[ARG_EDIT_TRIP_ID]

    private val _uiState = MutableStateFlow(TripFormUiState(editing = tripId != null, loading = tripId != null))
    val uiState: StateFlow<TripFormUiState> = _uiState.asStateFlow()

    init {
        val tripId = tripId
        if (tripId != null) {
            viewModelScope.launch {
                val trip = try {
                    tripRepository.getTrip(tripId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                _uiState.update { state ->
                    if (trip == null) {
                        state.copy(loading = false, notFound = true)
                    } else {
                        state.copy(
                            loading = false,
                            name = trip.name,
                            destination = trip.destination,
                            startDate = trip.startDate,
                            endDate = trip.endDate,
                        )
                    }
                }
            }
        }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }

    fun onDestinationChanged(value: String) = _uiState.update { it.copy(destination = value) }

    fun onDatesChanged(start: LocalDate, end: LocalDate) = _uiState.update {
        it.copy(startDate = start, endDate = end, datesMissing = false, problems = it.problems - DATE_PROBLEMS)
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.loading || state.savedTripId != null) return

        val start = state.startDate
        val end = state.endDate
        val problem = fieldProblem(state)
        if (problem != null || start == null || end == null) {
            _uiState.update { it.copy(problems = setOfNotNull(problem), datesMissing = start == null || end == null) }
            return
        }

        val input = TripInput(state.name, state.destination, start, end)
        _uiState.update { it.copy(saving = true, problems = emptySet(), datesMissing = false, saveFailed = false) }
        viewModelScope.launch {
            var failed: TripProblem? = null
            var saveFailed = false
            var savedId: String? = null
            try {
                savedId = if (tripId == null) {
                    tripRepository.createTrip(input)
                } else {
                    tripRepository.updateTrip(tripId, input)
                    tripId
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidTripException) {
                failed = e.problem
            } catch (e: Exception) {
                saveFailed = true
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    problems = setOfNotNull(failed?.takeIf { p -> p != TripProblem.NOT_FOUND }),
                    notFound = failed == TripProblem.NOT_FOUND,
                    saveFailed = saveFailed,
                    savedTripId = savedId,
                )
            }
        }
    }

    /** The screen handled [TripFormUiState.savedTripId]. */
    fun savedShown() = _uiState.update { it.copy(savedTripId = null) }

    private fun fieldProblem(state: TripFormUiState): TripProblem? {
        val name = state.name.trim()
        val destination = state.destination.trim()
        val start = state.startDate
        val end = state.endDate
        return when {
            name.isEmpty() -> TripProblem.BLANK_NAME
            name.length > Limits.MAX_TEXT -> TripProblem.NAME_TOO_LONG
            destination.isEmpty() -> TripProblem.BLANK_DESTINATION
            destination.length > Limits.MAX_TEXT -> TripProblem.DESTINATION_TOO_LONG
            start != null && end != null && end < start -> TripProblem.DATES_REVERSED
            else -> null
        }
    }

    companion object {
        private val DATE_PROBLEMS = setOf(TripProblem.DATES_REVERSED, TripProblem.DAYS_IN_USE)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripFormViewModel(createSavedStateHandle(), appContainer().tripRepository) }
        }
    }
}
