package vn.edu.vlu.trippocket.ui.weather

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate
import java.time.LocalDateTime

/** Dropdown under the search field. */
data class SearchUiState(
    /** null hides the dropdown; an empty list shows "No places found". */
    val results: List<Place>? = null,
    val networkError: Boolean = false,
)

data class SavedPlacesUiState(
    val places: List<Place> = emptyList(),
    val selected: Place? = null,
)

data class ForecastUiState(
    /** null when the trip has no saved place yet. */
    val place: Place? = null,
    /** false when the first load for [place] failed: the screen shows Retry. */
    val loaded: Boolean = false,
    val lastUpdated: LocalDateTime? = null,
    /** true while the screen still shows the forecast stored at the last sync. */
    val fromCache: Boolean = false,
    val rows: List<ForecastRow> = emptyList(),
) {
    val today: ForecastDay? get() = rows.firstOrNull()?.forecast
}

/** One forecast row; [forecast] is null for a trip day beyond the 16-day range. */
data class ForecastRow(val date: LocalDate, val tripDay: Int?, val forecast: ForecastDay?) {
    companion object {
        fun build(forecast: List<ForecastDay>, trip: Trip): List<ForecastRow> {
            fun tripDayOf(date: LocalDate): Int? =
                if (date < trip.startDate || date > trip.endDate) null
                else (date.toEpochDay() - trip.startDate.toEpochDay()).toInt() + 1

            val rows = forecast.map { ForecastRow(it.date, tripDayOf(it.date), it) }
            // Trip days after the last forecast day: "Forecast not available yet".
            val lastDate = forecast.last().date
            val missing = (0 until trip.dayCount)
                .map(trip::dateOfDay)
                .filter { it > lastDate }
                .map { ForecastRow(it, tripDayOf(it), null) }
            return rows + missing
        }
    }
}

/**
 * Screen 4: place search, saved places and the cached forecast.
 *
 * The repository simulates the API so every UI state can be shown:
 * - type a city (accents optional, e.g. "da lat")  -> result dropdown
 * - type something unknown, e.g. "xyz"             -> "No places found"
 * - type "offline"                                  -> search error in the TextInputLayout
 * - pick a place that is not saved yet              -> place is saved, first forecast load
 *                                                      fails, Retry loads it
 */
class WeatherViewModel(
    savedStateHandle: SavedStateHandle,
    tripRepository: TripRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    private val trip = tripRepository.trip(savedStateHandle[ARG_TRIP_ID] ?: "")

    /** Names of the places that have a forecast in the (simulated) cache. */
    private val placesWithCache = mutableSetOf<String>()
    private var lastUpdated: LocalDateTime = weatherRepository.forecastFetchedAt

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _places = MutableStateFlow(SavedPlacesUiState())
    val places: StateFlow<SavedPlacesUiState> = _places.asStateFlow()

    private val _forecast = MutableStateFlow(ForecastUiState())
    val forecast: StateFlow<ForecastUiState> = _forecast.asStateFlow()

    init {
        val saved = trip?.let { weatherRepository.savedPlaces(it.tripId) }.orEmpty()
        placesWithCache += saved.map { it.name }
        _places.value = SavedPlacesUiState(saved, saved.firstOrNull())
        loadForecast()
    }

    fun onQueryChanged(query: String) {
        _search.value = if (query.isBlank()) {
            // A blank query clears the results without any API call.
            SearchUiState()
        } else {
            weatherRepository.searchPlaces(query).fold(
                onSuccess = { SearchUiState(results = it) },
                onFailure = { SearchUiState(networkError = true) },
            )
        }
    }

    /** Saves [place] if needed and shows it. Returns true when it was not saved before. */
    fun pickPlace(place: Place): Boolean {
        val saved = _places.value.places
        val isNew = saved.none { it.name == place.name }
        _places.value = SavedPlacesUiState(if (isNew) saved + place else saved, place)
        loadForecast()
        return isNew
    }

    fun selectPlace(place: Place) {
        _places.update { it.copy(selected = place) }
        loadForecast()
    }

    fun refresh() {
        lastUpdated = LocalDateTime.now()
        loadForecast()
    }

    fun retry() {
        _places.value.selected?.let { placesWithCache += it.name }
        refresh()
    }

    private fun loadForecast() {
        val place = _places.value.selected
        val trip = trip
        _forecast.value = when {
            place == null || trip == null -> ForecastUiState()
            place.name !in placesWithCache -> ForecastUiState(place)
            else -> ForecastUiState(
                place = place,
                loaded = true,
                lastUpdated = lastUpdated,
                fromCache = lastUpdated == weatherRepository.forecastFetchedAt,
                rows = ForecastRow.build(weatherRepository.forecast(place), trip),
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeatherViewModel(createSavedStateHandle(), TripRepository(), WeatherRepository())
            }
        }
    }
}
