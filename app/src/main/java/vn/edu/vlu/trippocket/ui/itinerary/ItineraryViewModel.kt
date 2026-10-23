package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

data class ItineraryUiState(
    /** One date per day tab. Empty when the trip is not found. */
    val days: List<LocalDate> = emptyList(),
    val selectedDay: Int = 0,
    val activities: List<ItineraryActivity> = emptyList(),
    /** Weather of the trip's first saved place on that date; null if not in the cached forecast. */
    val forecast: ForecastDay? = null,
    val estimatedCost: Long = 0,
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
) {
    val selectedDate: LocalDate? get() = days.getOrNull(selectedDay)
}

/** Screen 3: day-by-day itinerary. */
class ItineraryViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    weatherRepository: WeatherRepository,
) : ViewModel() {

    private val trip = tripRepository.trip(savedStateHandle[ARG_TRIP_ID] ?: "")

    private val forecast: List<ForecastDay> = trip
        ?.let { weatherRepository.savedPlaces(it.tripId).firstOrNull() }
        ?.let(weatherRepository::forecast)
        .orEmpty()

    private val _uiState = MutableStateFlow(buildState(savedStateHandle[KEY_DAY] ?: 0))
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    fun selectDay(dayIndex: Int) {
        savedStateHandle[KEY_DAY] = dayIndex
        _uiState.value = buildState(dayIndex)
    }

    private fun buildState(dayIndex: Int): ItineraryUiState {
        val trip = trip ?: return ItineraryUiState()
        val date = trip.dateOfDay(dayIndex)
        val activities = tripRepository.activities(trip.tripId, dayIndex)
        val (done, total) = tripRepository.checklist(trip.tripId)
        return ItineraryUiState(
            days = (0 until trip.dayCount).map(trip::dateOfDay),
            selectedDay = dayIndex,
            activities = activities,
            forecast = forecast.firstOrNull { it.date == date },
            estimatedCost = activities.sumOf { it.estimatedCost },
            checklistDone = done,
            checklistTotal = total,
        )
    }

    companion object {
        private const val KEY_DAY = "selected_day"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ItineraryViewModel(createSavedStateHandle(), TripRepository(), WeatherRepository())
            }
        }
    }
}
