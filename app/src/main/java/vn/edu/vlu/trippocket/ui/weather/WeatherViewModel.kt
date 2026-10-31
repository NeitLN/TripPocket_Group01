package vn.edu.vlu.trippocket.ui.weather

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate
import java.time.LocalDateTime

/** Dropdown under the search field (W1). */
sealed interface SearchState {
    /** The field is empty: no dropdown. */
    data object Idle : SearchState

    data object Loading : SearchState

    /** Matches from Open-Meteo; never empty (that is [Empty]). */
    data class Results(val places: List<Place>) : SearchState

    /** The API answered with no match: "No places found". */
    data object Empty : SearchState

    /** Network, HTTP or JSON failure; the screen offers Try again. */
    data object Error : SearchState
}

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
            // Trip days after the last forecast day, or every day when there is no forecast at
            // all (empty cache): "Forecast not available yet", never a crash on last().
            val lastDate = forecast.lastOrNull()?.date
            val missing = (0 until trip.dayCount)
                .map(trip::dateOfDay)
                .filter { lastDate == null || it > lastDate }
                .map { ForecastRow(it, tripDayOf(it), null) }
            return rows + missing
        }
    }
}

/**
 * Screen 4: place search, saved places and the cached forecast.
 *
 * Search (W1) follows the text field: typing is debounced, the previous request is cancelled
 * when the query changes, and a failed search keeps the screen usable for the next query.
 */
class WeatherViewModel(
    savedStateHandle: SavedStateHandle,
    tripRepository: SampleTripRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    private val trip = tripRepository.trip(savedStateHandle[ARG_TRIP_ID] ?: "")

    /** Names of the places that have a forecast in the (simulated) cache. */
    private val placesWithCache = mutableSetOf<String>()
    private var lastUpdated: LocalDateTime = weatherRepository.forecastFetchedAt

    private val searchQuery = MutableStateFlow("")

    /** Retry needs its own signal: setting the same query on [searchQuery] again emits nothing. */
    private val searchRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Eagerly, so a Retry tap is never dropped while the screen is not collecting and
     * [SearchState] can be read at any time.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val search: StateFlow<SearchState> = merge(
        searchQuery.debounce(SEARCH_DEBOUNCE_MS),
        searchRetry.map { searchQuery.value },
    )
        .flatMapLatest(::searchFlow)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchState.Idle)

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
        searchQuery.value = query
    }

    /** The Try again button under a [SearchState.Error]: runs the current query once more. */
    fun retrySearch() {
        searchRetry.tryEmit(Unit)
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

    /**
     * One search per query: blank clears the dropdown, otherwise Loading then Results/Empty.
     * A cancelled search (the user typed on) is not an error and is rethrown.
     */
    private fun searchFlow(query: String): Flow<SearchState> = flow {
        if (query.isBlank()) {
            emit(SearchState.Idle)
            return@flow
        }
        emit(SearchState.Loading)
        // emit stays outside the try, so a failure downstream is never caught here (flow
        // exception transparency).
        val result = try {
            val places = weatherRepository.searchPlaces(query)
            if (places.isEmpty()) SearchState.Empty else SearchState.Results(places)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // IOException when offline, HttpException for a bad status, JSON errors: one UI state.
            SearchState.Error
        }
        emit(result)
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
        private const val SEARCH_DEBOUNCE_MS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeatherViewModel(createSavedStateHandle(), appContainer().sampleTripRepository, appContainer().weatherRepository)
            }
        }
    }
}
