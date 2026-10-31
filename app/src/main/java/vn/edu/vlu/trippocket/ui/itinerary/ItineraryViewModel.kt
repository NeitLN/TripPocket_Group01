package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

data class ItineraryUiState(
    /** One date per day tab. Empty when the trip is not found. */
    val days: List<LocalDate> = emptyList(),
    val selectedDay: Int = 0,
    val activities: List<ItineraryActivity> = emptyList(),
    /** Weather of the trip's first saved place on that date; null if not in the cached forecast. */
    val forecast: ForecastDay? = null,
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
 * Screen 3, day by day. The activities of the selected day come from Room
 * ([ItineraryRepository]); the day tabs, the checklist count and the forecast stay on the
 * sample data until their own tasks (W5, I2) move them.
 */
class ItineraryViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val tripRepository: SampleTripRepository,
    weatherRepository: WeatherRepository,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val trip: Trip? = tripRepository.trip(savedStateHandle[ARG_TRIP_ID] ?: "")

    private val forecast: List<ForecastDay> = trip
        ?.let { weatherRepository.savedPlaces(it.tripId).firstOrNull() }
        ?.let(weatherRepository::forecast)
        .orEmpty()

    private val dayCount: Int = trip?.dayCount ?: 0

    private val selectedDay = MutableStateFlow(
        (savedStateHandle.get<Int>(KEY_DAY) ?: 0).coerceIn(0, (dayCount - 1).coerceAtLeast(0))
    )

    /**
     * Eagerly, so the screen always has a state to read and a tab tap is never missed.
     * [flatMapLatest] cancels the queries of the previous day when another day is selected.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ItineraryUiState> = trip?.let { trip ->
        combine(
            selectedDay,
            selectedDay.flatMapLatest { day -> itineraryRepository.observeDay(trip.tripId, day) },
            itineraryRepository.observeActivities(trip.tripId),
        ) { day, activities, all -> state(day, activities, all.size) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, state(selectedDay.value, emptyList(), 0))
    } ?: MutableStateFlow(ItineraryUiState()).asStateFlow()

    fun selectDay(dayIndex: Int) {
        val day = dayIndex.coerceIn(0, (dayCount - 1).coerceAtLeast(0))
        savedStateHandle[KEY_DAY] = day
        selectedDay.value = day
    }

    private fun state(dayIndex: Int, activities: List<ItineraryActivity>, activityCount: Int): ItineraryUiState {
        val trip = trip ?: return ItineraryUiState()
        val dates = (0 until trip.dayCount).map(trip::dateOfDay)
        val (done, total) = tripRepository.checklist(trip.tripId)
        return ItineraryUiState(
            days = dates,
            selectedDay = dayIndex,
            activities = activities,
            forecast = dates.getOrNull(dayIndex)?.let { date -> forecast.firstOrNull { it.date == date } },
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
                    appContainer().sampleTripRepository,
                    appContainer().weatherRepository,
                    appContainer().itineraryRepository,
                )
            }
        }
    }
}
