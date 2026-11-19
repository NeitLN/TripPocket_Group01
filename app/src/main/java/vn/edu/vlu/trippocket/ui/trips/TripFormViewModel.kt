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

/** Name and destination a new trip starts with ("Plan this trip" on a trip idea, Milestone 4). */
const val ARG_PREFILL_NAME = "prefill_name"
const val ARG_PREFILL_DESTINATION = "prefill_destination"

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
    private val savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: String? = savedStateHandle[ARG_EDIT_TRIP_ID]

    /** What the user typed before Android killed the app in the background; null on a fresh form. */
    private val draft: TripFormUiState? = if (savedStateHandle.get<Boolean>(KEY_DRAFT) == true) {
        TripFormUiState(
            editing = tripId != null,
            name = savedStateHandle[KEY_NAME] ?: "",
            destination = savedStateHandle[KEY_DESTINATION] ?: "",
            startDate = savedStateHandle.get<Long>(KEY_START)?.let(LocalDate::ofEpochDay),
            endDate = savedStateHandle.get<Long>(KEY_END)?.let(LocalDate::ofEpochDay),
        )
    } else {
        null
    }

    private val _uiState = MutableStateFlow(
        draft ?: TripFormUiState(
            editing = tripId != null,
            loading = tripId != null,
            // The prefill comes from a remote trip idea, so it is cut to what the form accepts.
            name = savedStateHandle.get<String>(ARG_PREFILL_NAME).orEmpty().trim().take(Limits.MAX_TEXT),
            destination = savedStateHandle.get<String>(ARG_PREFILL_DESTINATION).orEmpty().trim().take(Limits.MAX_TEXT),
        )
    )
    val uiState: StateFlow<TripFormUiState> = _uiState.asStateFlow()

    init {
        val tripId = tripId
        // A restored draft wins over the trip in Room: reloading it would throw the edits away.
        if (tripId != null && draft == null) {
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

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value) }
        keepDraft()
    }

    fun onDestinationChanged(value: String) {
        _uiState.update { it.copy(destination = value) }
        keepDraft()
    }

    fun onDatesChanged(start: LocalDate, end: LocalDate) {
        _uiState.update {
            it.copy(startDate = start, endDate = end, datesMissing = false, problems = it.problems - DATE_PROBLEMS)
        }
        keepDraft()
    }

    /** Copies the fields into the saved state, so they survive Android killing the app in the background. */
    private fun keepDraft() {
        val state = _uiState.value
        if (state.loading) return
        savedStateHandle[KEY_DRAFT] = true
        savedStateHandle[KEY_NAME] = state.name
        savedStateHandle[KEY_DESTINATION] = state.destination
        savedStateHandle[KEY_START] = state.startDate?.toEpochDay()
        savedStateHandle[KEY_END] = state.endDate?.toEpochDay()
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

    /** The screen showed the save-failed Snackbar; later states (each keystroke) must not show it again. */
    fun messageShown() = _uiState.update { it.copy(saveFailed = false) }

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

        private const val KEY_DRAFT = "draft"
        private const val KEY_NAME = "draft_name"
        private const val KEY_DESTINATION = "draft_destination"
        private const val KEY_START = "draft_start"
        private const val KEY_END = "draft_end"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripFormViewModel(createSavedStateHandle(), appContainer().tripRepository) }
        }
    }
}
