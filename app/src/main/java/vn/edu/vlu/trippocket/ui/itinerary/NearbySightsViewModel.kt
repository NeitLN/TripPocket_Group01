package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ActivityInput
import vn.edu.vlu.trippocket.data.model.Sight
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.repository.InvalidActivityException
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.SightRepository
import vn.edu.vlu.trippocket.domain.ApiError
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalTime

/** Day the sights screen was opened for: the day selected in the Itinerary tab. */
const val ARG_DAY_INDEX = "dayIndex"

/**
 * What "Add to day" did, shown once as a snackbar and then cleared with [messageShown].
 * [Added.dayNumber] is already 1-based, so the screen never adds one again.
 */
sealed interface SightsMessage {
    data class Added(val dayNumber: Int) : SightsMessage

    /** The trip already has 200 activities. */
    data object LimitReached : SightsMessage

    /** The write was refused (day gone, title problem) or failed. */
    data object AddFailed : SightsMessage
}

/** What the "Add to day" button of one sight can do right now. */
enum class AddState {
    CAN_ADD,

    /** An activity with this sight's title is already in the selected day. */
    ADDED,

    /** The trip already has [Limits.MAX_ACTIVITIES] activities. */
    TRIP_FULL,
}

/** One row of the list: the sight and the state of its button. */
data class SightRow(val sight: Sight, val addState: AddState)

data class NearbySightsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Name of the trip's first saved place, the centre of the search; null until it is read. */
    val placeName: String? = null,
    val sights: List<Sight> = emptyList(),
    val error: ApiError? = null,
    /** The trip has no saved place at all: the screen says so and never calls the API. */
    val noPlace: Boolean = false,
    val message: SightsMessage? = null,
    /** Titles of the activities already in the selected day, read from Room. */
    val addedTitles: Set<String> = emptySet(),
    /** The trip has [Limits.MAX_ACTIVITIES] activities, so nothing more can be added. */
    val tripFull: Boolean = false,
) {
    /**
     * The sights with their button state: a sight already in the day reads "Added", and a full
     * trip turns every button off, so the user sees it before tapping instead of after.
     */
    val rows: List<SightRow>
        get() = sights.map { sight ->
            val addState = when {
                sight.title.take(Limits.MAX_TEXT) in addedTitles -> AddState.ADDED
                tripFull -> AddState.TRIP_FULL
                else -> AddState.CAN_ADD
            }
            SightRow(sight, addState)
        }

    /**
     * The API answered with nothing within the radius. A separate sentence from [noPlace]:
     * here the place exists and a later refresh may still find something.
     */
    val empty: Boolean get() = !loading && error == null && !noPlace && sights.isEmpty()
}

/**
 * Nearby sights around the trip's first saved place (Milestone 4). The screen only renders
 * [uiState] and forwards clicks: the Wikipedia search goes through [SightRepository], and
 * "Add to day" writes through [ItineraryRepository], so the trip is backed up like any other
 * activity.
 *
 * The list is loaded when the trip's first saved place is known, so rotating the screen keeps
 * it; pull-to-refresh and Retry ask Wikipedia again. The place is watched: saving one in the
 * Weather tab while this screen is open (it shares the trip's ViewModels) loads the list
 * instead of staying on "Add a place first". A trip without a saved place never calls the API.
 */
class NearbySightsViewModel(
    savedStateHandle: SavedStateHandle,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val sightRepository: SightRepository,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    /** The day selected in the Itinerary tab when the screen was opened; 0-based. */
    private val dayIndex: Int = savedStateHandle[ARG_DAY_INDEX] ?: 0

    private val _uiState = MutableStateFlow(NearbySightsUiState())
    val uiState: StateFlow<NearbySightsUiState> = _uiState.asStateFlow()

    /** The request in flight, so a second Retry or pull while it runs is dropped. */
    private var loadJob: Job? = null

    /** true while one "Add to day" write runs, so a double tap adds one activity. */
    private var addingSight = false

    /** The saved place the list was loaded around, so another place starts from an empty list. */
    private var centreId: String? = null

    init {
        // Room sends the first saved place now and again whenever it changes: saved in the
        // Weather tab, deleted, or replaced. Each change starts a new search around it.
        viewModelScope.launch {
            savedPlaceRepository.observePlaces(tripId)
                .map { places -> places.firstOrNull()?.placeId }
                .distinctUntilChanged()
                .collect { load(newPlace = true) }
        }
        // The selected day and the trip's activity count, so the buttons follow every write:
        // this screen's own "Add to day", or an edit in the Itinerary tab.
        viewModelScope.launch {
            combine(
                itineraryRepository.observeDay(tripId, dayIndex),
                itineraryRepository.observeActivities(tripId),
            ) { day, all ->
                day.map { it.title }.toSet() to (all.size >= Limits.MAX_ACTIVITIES)
            }.collect { (addedTitles, tripFull) ->
                _uiState.update { it.copy(addedTitles = addedTitles, tripFull = tripFull) }
            }
        }
    }

    /** Pull-to-refresh and the Retry button: one new request around the same place. */
    fun refresh() = load()

    fun retry() = load()

    /**
     * Adds [sight] at 09:00 on the selected day, with its name as title and place and its
     * description as note. A refused or failed write reports a message instead of crashing;
     * the screen clears it with [messageShown].
     */
    fun addToDay(sight: Sight) {
        if (addingSight) return
        addingSight = true
        viewModelScope.launch {
            val message = try {
                itineraryRepository.addActivity(
                    tripId,
                    ActivityInput(
                        dayIndex = dayIndex,
                        title = sight.title.take(Limits.MAX_TEXT),
                        place = sight.title.take(Limits.MAX_TEXT),
                        startTime = ADD_TIME,
                        estimatedCost = 0,
                        note = sight.description.orEmpty().take(Limits.MAX_NOTE),
                        reminderMinutesBefore = null,
                    ),
                )
                SightsMessage.Added(dayIndex + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LimitExceededException) {
                SightsMessage.LimitReached
            } catch (e: InvalidActivityException) {
                SightsMessage.AddFailed
            } finally {
                addingSight = false
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    /** The snackbar for [NearbySightsUiState.message] was shown; do not show it again. */
    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Searches around the first saved place. A second Retry or pull while a request runs is
     * dropped; a [newPlace] cancels the old request, whose answer is for the old place.
     */
    private fun load(newPlace: Boolean = false) {
        if (!newPlace && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // The banner of an earlier failure goes away while the new request runs.
            _uiState.update { it.copy(error = null) }
            val place = savedPlaceRepository.observePlaces(tripId).first().firstOrNull()
            if (place == null) {
                centreId = null
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        noPlace = true,
                        placeName = null,
                        sights = emptyList(),
                    )
                }
                return@launch
            }
            if (place.placeId != centreId) {
                centreId = place.placeId
                _uiState.update { it.copy(sights = emptyList()) }
            }
            _uiState.update {
                it.copy(
                    loading = it.sights.isEmpty(),
                    refreshing = it.sights.isNotEmpty(),
                    noPlace = false,
                    placeName = place.name,
                )
            }
            when (val result = sightRepository.nearby(place.latitude, place.longitude)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(loading = false, refreshing = false, sights = result.data, error = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(loading = false, refreshing = false, error = result.error)
                }
            }
        }
    }

    companion object {
        /** Time of the activity created from a sight: the start of a sightseeing day. */
        private val ADD_TIME: LocalTime = LocalTime.of(9, 0)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NearbySightsViewModel(
                    createSavedStateHandle(),
                    appContainer().savedPlaceRepository,
                    appContainer().sightRepository,
                    appContainer().itineraryRepository,
                )
            }
        }
    }
}
