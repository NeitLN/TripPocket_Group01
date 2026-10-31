package vn.edu.vlu.trippocket.ui.itinerary

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
import vn.edu.vlu.trippocket.data.model.ActivityInput
import vn.edu.vlu.trippocket.data.repository.InvalidActivityException
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.domain.ActivityProblem
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalTime

/** Nav argument of the activity to edit; absent when adding a new one (I1-ui declares it). */
const val ARG_ACTIVITY_ID = "activityId"

/** Time shown before the user picks one for a new activity. */
private val DEFAULT_START_TIME = LocalTime.of(9, 0)

data class ItineraryFormUiState(
    /** true when the form edits an existing activity, false when it adds one. */
    val editing: Boolean = false,
    /** true while the activity is read from Room. */
    val loading: Boolean = false,
    val title: String = "",
    val place: String = "",
    val startTime: LocalTime = DEFAULT_START_TIME,
    /** Text of the cost field, so a half-typed number is not lost. */
    val cost: String = "",
    val note: String = "",
    /** Text of the reminder field in minutes; blank = no reminder. */
    val reminder: String = "",
    val dayIndex: Int = 0,
    val dayCount: Int = 0,
    /** The fields the last save or the current input fails; empty = no error. */
    val problems: Set<ActivityProblem> = emptySet(),
    /** The activity to edit no longer exists, or belongs to another trip. */
    val notFound: Boolean = false,
    val saving: Boolean = false,
    val limitReached: Boolean = false,
    /** The write failed (database error); the form stays open and the user can try again. */
    val saveFailed: Boolean = false,
    /** One-shot result: the screen closes after reading it, then calls [savedShown]. */
    val saved: Boolean = false,
    /** One-shot result: the screen closes after deleting, then calls [deletedShown]. */
    val deleted: Boolean = false,
) {
    fun has(problem: ActivityProblem): Boolean = problem in problems
}

/**
 * Add / edit activity form (I1). The fields are validated here for immediate feedback and the
 * repository checks them again inside its transaction; the save result is reported once through
 * [ItineraryFormUiState.saved].
 */
class ItineraryFormViewModel(
    savedStateHandle: SavedStateHandle,
    tripRepository: SampleTripRepository,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""
    private val activityId: String? = savedStateHandle[ARG_ACTIVITY_ID]
    private val dayCount: Int = tripRepository.trip(tripId)?.dayCount ?: 0

    private val _uiState = MutableStateFlow(
        ItineraryFormUiState(
            editing = activityId != null,
            loading = activityId != null,
            dayCount = dayCount,
        )
    )
    val uiState: StateFlow<ItineraryFormUiState> = _uiState.asStateFlow()

    init {
        val activityId = activityId
        if (activityId != null) {
            viewModelScope.launch {
                val activity = try {
                    itineraryRepository.getActivity(activityId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                _uiState.update { state ->
                    if (activity == null || activity.tripId != tripId) {
                        state.copy(loading = false, notFound = true)
                    } else {
                        state.copy(
                            loading = false,
                            title = activity.title,
                            place = activity.place,
                            startTime = activity.startTime,
                            cost = if (activity.estimatedCost > 0) activity.estimatedCost.toString() else "",
                            note = activity.note,
                            reminder = activity.reminderMinutesBefore?.toString().orEmpty(),
                            dayIndex = activity.dayIndex,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value, saved = false) }

    fun onPlaceChanged(value: String) = _uiState.update { it.copy(place = value, saved = false) }

    fun onStartTimeChanged(value: LocalTime) = _uiState.update { it.copy(startTime = value, saved = false) }

    fun onCostChanged(value: String) = _uiState.update { it.copy(cost = value, saved = false) }

    fun onNoteChanged(value: String) = _uiState.update { it.copy(note = value, saved = false) }

    fun onReminderChanged(value: String) = _uiState.update { it.copy(reminder = value, saved = false) }

    fun onDayChanged(value: Int) = _uiState.update {
        it.copy(dayIndex = value.coerceIn(0, (it.dayCount - 1).coerceAtLeast(0)), saved = false)
    }

    /** Saves the form; nothing is written when a field is invalid. */
    fun save() {
        val state = _uiState.value
        if (state.saving || state.saved || state.deleted) return

        val input = try {
            state.toInput()
        } catch (e: InvalidActivityException) {
            _uiState.update { it.copy(problems = setOf(e.problem)) }
            return
        }

        _uiState.update { it.copy(saving = true, problems = emptySet(), limitReached = false) }
        viewModelScope.launch {
            var problem: ActivityProblem? = null
            var limitReached = false
            var saveFailed = false
            try {
                if (activityId == null) {
                    itineraryRepository.addActivity(tripId, input)
                } else {
                    itineraryRepository.updateActivity(tripId, activityId, input)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LimitExceededException) {
                limitReached = true
            } catch (e: InvalidActivityException) {
                problem = e.problem
            } catch (e: Exception) {
                // A database failure: keep the form open, the user can try again.
                saveFailed = true
            }
            val failed = problem
            _uiState.update {
                it.copy(
                    saving = false,
                    problems = listOfNotNull(failed).toSet(),
                    limitReached = limitReached,
                    saveFailed = saveFailed,
                    saved = failed == null && !limitReached && !saveFailed,
                )
            }
        }
    }

    /** Deletes an existing activity; does nothing when creating a new one. */
    fun delete() {
        val activityId = activityId ?: return
        val state = _uiState.value
        if (state.saving || state.saved || state.deleted) return

        _uiState.update { it.copy(saving = true, problems = emptySet(), limitReached = false, saveFailed = false) }
        viewModelScope.launch {
            var notFound = false
            var saveFailed = false
            try {
                itineraryRepository.deleteActivity(tripId, activityId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidActivityException) {
                if (e.problem == ActivityProblem.NOT_FOUND) {
                    notFound = true
                } else {
                    saveFailed = true
                }
            } catch (e: Exception) {
                saveFailed = true
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    notFound = notFound,
                    saveFailed = saveFailed,
                    deleted = !notFound && !saveFailed,
                )
            }
        }
    }

    /** The screen handled [ItineraryFormUiState.saved]; do not close twice. */
    fun savedShown() {
        _uiState.update { it.copy(saved = false) }
    }

    /** The screen handled [ItineraryFormUiState.deleted]; do not close twice. */
    fun deletedShown() {
        _uiState.update { it.copy(deleted = false) }
    }

    /** Field checks for immediate feedback; the repository checks the same rules again. */
    private fun ItineraryFormUiState.toInput(): ActivityInput {
        val title = title.trim()
        val place = place.trim()
        val note = note.trim()
        val problem = when {
            title.isEmpty() -> ActivityProblem.BLANK_TITLE
            title.length > Limits.MAX_TEXT -> ActivityProblem.TITLE_TOO_LONG
            place.length > Limits.MAX_TEXT -> ActivityProblem.PLACE_TOO_LONG
            note.length > Limits.MAX_NOTE -> ActivityProblem.NOTE_TOO_LONG
            dayIndex !in 0 until dayCount -> ActivityProblem.DAY_OUT_OF_RANGE
            else -> null
        }
        if (problem != null) throw InvalidActivityException(problem)

        val costText = cost.trim()
        val estimatedCost = if (costText.isEmpty()) {
            0L
        } else {
            costText.toLongOrNull() ?: throw InvalidActivityException(ActivityProblem.COST_INVALID)
        }
        if (estimatedCost < 0) throw InvalidActivityException(ActivityProblem.COST_NEGATIVE)

        val reminderText = reminder.trim()
        val reminderMinutes = if (reminderText.isEmpty()) {
            null
        } else {
            reminderText.toIntOrNull() ?: throw InvalidActivityException(ActivityProblem.REMINDER_INVALID)
        }
        if (reminderMinutes != null && reminderMinutes < 0) {
            throw InvalidActivityException(ActivityProblem.REMINDER_INVALID)
        }

        return ActivityInput(
            dayIndex = dayIndex,
            title = title,
            place = place,
            startTime = startTime,
            estimatedCost = estimatedCost,
            note = note,
            reminderMinutesBefore = reminderMinutes,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ItineraryFormViewModel(
                    createSavedStateHandle(),
                    appContainer().sampleTripRepository,
                    appContainer().itineraryRepository,
                )
            }
        }
    }
}
