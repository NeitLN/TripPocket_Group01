package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.domain.DailyWeather
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

data class ItineraryUiState(
    /** One date per day tab. Empty when the trip is not found. */
    val days: List<LocalDate> = emptyList(),
    val selectedDay: Int = 0,
    val activities: List<ItineraryActivity> = emptyList(),
    /**
     * W5: weather of the trip's first saved place on that date, from the Room cache (works
     * offline); null when the cache has no such day, shown as "Forecast not available yet".
     */
    val forecast: ForecastDay? = null,
    /** false: the trip has no saved place yet, so the screen says how to add one instead. */
    val hasSavedPlace: Boolean = false,
    /** Planned cost of the selected day. */
    val estimatedCost: Long = 0,
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
    /** Every activity of the trip, for the "n/200" counter. */
    val activityCount: Int = 0,
) {
    val selectedDate: LocalDate? get() = days.getOrNull(selectedDay)

    /** The form is closed at the cap; the repository checks it again inside the transaction. */
    val canAdd: Boolean get() = activityCount < Limits.MAX_ACTIVITIES
}

/**
 * Screen 3, day by day. The trip (its days, so a trip created or re-dated in A2 shows the right
 * tabs), the activities and the weather (W5: the cached forecast of the first saved place, via
 * WeatherRepository.observeDailyWeather) come from Room; the checklist count stays on the
 * sample data until I2 moves it.
 */
class ItineraryViewModel(
    private val savedStateHandle: SavedStateHandle,
    tripRepository: TripRepository,
    private val sampleRepository: SampleTripRepository,
    weatherRepository: WeatherRepository,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    /** Days of the trip as last read from Room; 0 until the trip is loaded. */
    private var dayCount: Int = 0

    private val selectedDay = MutableStateFlow((savedStateHandle.get<Int>(KEY_DAY) ?: 0).coerceAtLeast(0))

    /**
     * Eagerly, so the screen always has a state to read and a tab tap is never missed.
     * [flatMapLatest] cancels the queries of the previous day when another day is selected.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ItineraryUiState> = combine(
        tripRepository.observeTrip(tripId),
        selectedDay,
        selectedDay.flatMapLatest { day -> itineraryRepository.observeDay(tripId, day) },
        itineraryRepository.observeActivities(tripId),
        weatherRepository.observeDailyWeather(tripId),
    ) { trip, day, activities, all, weather ->
        dayCount = trip?.dayCount ?: 0
        if (trip == null) return@combine ItineraryUiState()
        // A trip made shorter (A2) or a restored tab past the end: fall back to the last day.
        val shown = day.coerceIn(0, dayCount - 1)
        if (shown != day) selectDay(shown)
        state(trip, shown, if (shown == day) activities else emptyList(), all.size, weather)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ItineraryUiState())

    fun selectDay(dayIndex: Int) {
        val day = dayIndex.coerceIn(0, (dayCount - 1).coerceAtLeast(0))
        savedStateHandle[KEY_DAY] = day
        selectedDay.value = day
    }

    /**
     * Saves the order of the selected day after a drag (I3). Called once, on drop, so one drop
     * is one revision; dropping an activity back where it was writes nothing.
     */
    fun reorder(orderedActivityIds: List<String>) {
        val day = selectedDay.value
        write { itineraryRepository.reorder(tripId, day, orderedActivityIds) }
    }

    /** "Move up" of one activity, for the same order without a drag (WCAG 2.2 AA). */
    fun moveUp(activityId: String) {
        write { itineraryRepository.moveUp(tripId, activityId) }
    }

    /** "Move down" of one activity, for the same order without a drag (WCAG 2.2 AA). */
    fun moveDown(activityId: String) {
        write { itineraryRepository.moveDown(tripId, activityId) }
    }

    /**
     * A rejected reorder leaves Room with the old order, so the list re-emits it by itself and
     * there is nothing to show; only cancellation must keep propagating.
     */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Room still holds the old order; the flow below puts the list back.
            }
        }
    }

    private fun state(
        trip: Trip,
        dayIndex: Int,
        activities: List<ItineraryActivity>,
        activityCount: Int,
        weather: DailyWeather,
    ): ItineraryUiState {
        val dates = (0 until trip.dayCount).map(trip::dateOfDay)
        val (done, total) = sampleRepository.checklist(trip.tripId)
        return ItineraryUiState(
            days = dates,
            selectedDay = dayIndex,
            activities = activities,
            // Exact date only (ForecastMatcher): a day outside the cache never shows another day.
            forecast = (weather as? DailyWeather.Cached)?.let { cached -> dates.getOrNull(dayIndex)?.let(cached::on) },
            hasSavedPlace = weather is DailyWeather.Cached,
            estimatedCost = activities.sumOf { it.estimatedCost },
            checklistDone = done,
            checklistTotal = total,
            activityCount = activityCount,
        )
    }

    companion object {
        private const val KEY_DAY = "selected_day"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ItineraryViewModel(
                    createSavedStateHandle(),
                    appContainer().tripRepository,
                    appContainer().sampleTripRepository,
                    appContainer().weatherRepository,
                    appContainer().itineraryRepository,
                )
            }
        }
    }
}
