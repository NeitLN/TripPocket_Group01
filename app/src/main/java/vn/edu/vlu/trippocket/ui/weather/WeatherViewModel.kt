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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.CachedForecast
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.data.repository.toWeatherError
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.domain.WeatherError
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Dropdown under the search field (W1). */
sealed interface SearchState {
    /** The field is empty: no dropdown. */
    data object Idle : SearchState

    data object Loading : SearchState

    /** Matches from Open-Meteo; never empty (that is [Empty]). */
    data class Results(val places: List<Place>) : SearchState

    /** The API answered with no match: "No places found". */
    data object Empty : SearchState

    /** The request failed; the screen offers Try again and the sentence for [error]. */
    data class Error(val error: WeatherError) : SearchState
}

/**
 * The result of saving a search result, shown once as a snackbar and then cleared with
 * [WeatherViewModel.messageShown]. This is a message rather than a flag, so the screen does not
 * have to compare "before" and "after" to know what happened.
 */
sealed interface SaveMessage {
    /** A new place was added to the trip. */
    data class Saved(val placeName: String) : SaveMessage

    /** The same coordinates were already saved; nothing was added. */
    data class AlreadySaved(val placeName: String) : SaveMessage

    /** The trip already has 30 places (W2 limit). */
    data object LimitReached : SaveMessage

    /** The write failed (invalid name, database error); nothing was added. */
    data object Failed : SaveMessage
}

data class SavedPlacesUiState(
    val places: List<SavedPlace> = emptyList(),
    val selectedPlaceId: String? = null,
    val message: SaveMessage? = null,
) {
    /** The place whose forecast the screen shows. */
    val selected: SavedPlace? get() = places.firstOrNull { it.placeId == selectedPlaceId }
}

data class ForecastUiState(
    /** null when the trip has no saved place yet. */
    val place: SavedPlace? = null,
    /** true while the first download for [place] runs and there is nothing to show yet. */
    val loading: Boolean = false,
    /** true while a download runs but the cache below is still displayed. */
    val refreshing: Boolean = false,
    /** Why the last download failed; null when it did not. */
    val error: WeatherError? = null,
    val lastUpdated: LocalDateTime? = null,
    val rows: List<ForecastRow> = emptyList(),
) {
    val today: ForecastDay? get() = rows.firstOrNull()?.forecast

    /** true when the last download failed; the cached rows are then the offline copy. */
    val failed: Boolean get() = error != null

    /** true while the screen still shows the forecast stored at the last sync. */
    val fromCache: Boolean get() = failed && hasForecast

    /** true when the cache holds at least one day of real forecast. */
    val hasForecast: Boolean get() = rows.any { it.forecast != null }
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
 *
 * Saved places (W2) and the forecast (W3) come from Room. Saving a search result stores it and
 * downloads its forecast; Retry and pull-to-refresh only download the forecast again. The screen
 * itself never reads the API for a forecast.
 *
 * H: a forecast older than [FORECAST_MAX_AGE_MS] is downloaded again when its place is shown,
 * once per place, and a failure keeps the cache on screen. Every download goes through
 * [download], which drops a second request for a place that is already downloading.
 */
class WeatherViewModel(
    savedStateHandle: SavedStateHandle,
    tripRepository: SampleTripRepository,
    private val weatherRepository: WeatherRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val trip: Trip? = tripRepository.trip(savedStateHandle[ARG_TRIP_ID] ?: "")

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

    /** The download of the selected place, if any. */
    private val refresh = MutableStateFlow<Refresh?>(null)

    /** Place ids already checked for a stale cache, so a failed download is not retried forever. */
    private val autoRefreshed = mutableSetOf<String>()

    /** Place ids with a download running; a second request for the same place is dropped. */
    private val downloading = mutableSetOf<String>()

    /**
     * The selected place and its cache. [flatMapLatest] cancels the cache flow of the previous
     * place, so two places are never collected side by side while the user taps the chips.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loaded: Flow<Loaded> = _places
        .map { it.selected }
        .distinctUntilChanged()
        .flatMapLatest { place ->
            if (place == null) {
                flowOf(Loaded(null, null))
            } else {
                weatherRepository.observeForecast(place.placeId).map { Loaded(place, it) }
            }
        }

    val forecast: StateFlow<ForecastUiState> = combine(
        // The stale check runs before the state is built, so it is part of showing the place.
        loaded.onEach { autoRefreshIfStale(it) },
        refresh,
        ::forecastState,
    )
        .stateIn(viewModelScope, SharingStarted.Eagerly, ForecastUiState())


    init {
        val tripId = trip?.tripId
        if (tripId != null) {
            viewModelScope.launch {
                savedPlaceRepository.observePlaces(tripId).collect { places ->
                    _places.update { state ->
                        // Keep the selection while that place exists, otherwise show the first.
                        val selected = state.selectedPlaceId?.takeIf { id -> places.any { it.placeId == id } }
                        state.copy(places = places, selectedPlaceId = selected ?: places.firstOrNull()?.placeId)
                    }
                }
            }
        }
    }

    fun onQueryChanged(query: String) {
        searchQuery.value = query
    }

    /** The Try again button under a [SearchState.Error]: runs the current query once more. */
    fun retrySearch() {
        searchRetry.tryEmit(Unit)
    }

    /**
     * Saves a search result to the trip (W2) and downloads its forecast. The outcome is
     * reported once through [SavedPlacesUiState.message]; the screen clears it with
     * [messageShown] after showing the snackbar.
     */
    fun pickPlace(place: Place) {
        val tripId = trip?.tripId ?: return
        // W2 dedupes by coordinates, and it can only answer after the write, so the message is
        // decided here from the current list.
        val places = _places.value.places
        val alreadySaved = places.any {
            it.latitude == place.latitude && it.longitude == place.longitude
        }
        // H: the 31st place is refused before the write and before any forecast request; a place
        // already saved to the trip still works at the cap (it only selects its forecast).
        if (!alreadySaved && places.size >= Limits.MAX_PLACES) {
            _places.update { it.copy(message = SaveMessage.LimitReached) }
            return
        }
        viewModelScope.launch {
            val placeId = try {
                savedPlaceRepository.savePlace(tripId, place)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LimitExceededException) {
                _places.update { it.copy(message = SaveMessage.LimitReached) }
                return@launch
            } catch (e: Exception) {
                // InvalidPlaceException (blank name) or a database error: nothing was written.
                _places.update { it.copy(message = SaveMessage.Failed) }
                return@launch
            }
            _places.update {
                it.copy(
                    selectedPlaceId = placeId,
                    message = if (alreadySaved) SaveMessage.AlreadySaved(place.name)
                    else SaveMessage.Saved(place.name),
                )
            }
            downloadForecast(tripId, placeId)
        }
    }

    /** Shows the place selected by its [placeId], never by its name. */
    fun selectPlace(placeId: String) {
        _places.update { state ->
            if (state.places.any { it.placeId == placeId }) state.copy(selectedPlaceId = placeId) else state
        }
    }

    /** Pull-to-refresh and the Retry button: downloads the forecast of the selected place. */
    fun refresh() {
        val place = _places.value.selected ?: return
        viewModelScope.launch { download(place) }
    }

    fun retry() = refresh()

    /** The snackbar for [SavedPlacesUiState.message] was shown; do not show it again. */
    fun messageShown() {
        _places.update { it.copy(message = null) }
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
            // IOException, HttpException and JSON errors keep their kind, so the screen can say
            // whether it is the connection, the rate limit or the service.
            SearchState.Error(e.toWeatherError())
        }
        emit(result)
    }

    /** Finds the stored place (W2 may have cleaned its name) and downloads its forecast. */
    private suspend fun downloadForecast(tripId: String, placeId: String) {
        val place = savedPlaceRepository.observePlaces(tripId)
            .first { rows -> rows.any { it.placeId == placeId } }
            .first { it.placeId == placeId }
        download(place)
    }

    /**
     * Downloads the forecast and records the outcome. A failure keeps the cached rows: only
     * [refresh] changes, so the screen can mark them as the offline copy.
     *
     * One download per place at a time: the automatic refresh and a pull-to-refresh can ask at
     * the same moment, and the second request is dropped, not queued.
     */
    private suspend fun download(place: SavedPlace) {
        if (!downloading.add(place.placeId)) return
        try {
            refresh.value = Refresh(place.placeId, RefreshStatus.LOADING)
            var error: WeatherError? = null
            try {
                weatherRepository.refreshForecast(place)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toWeatherError()
            }
            refresh.value = Refresh(
                place.placeId,
                if (error == null) RefreshStatus.IDLE else RefreshStatus.FAILED,
                error,
            )
        } finally {
            downloading.remove(place.placeId)
        }
    }

    /**
     * H: when the place just shown has a cached forecast older than [FORECAST_MAX_AGE_MS],
     * downloads it once. A place with no cache at all is not stale: saving it already downloads
     * its forecast, and after a Restore the trip-wide refresh (A4) fills the cache.
     * [autoRefreshed] keeps a failed download from being retried every time the cache emits, and
     * [downloading] leaves a request that is already running alone.
     */
    private fun autoRefreshIfStale(loaded: Loaded) {
        val place = loaded.place ?: return
        if (place.placeId in autoRefreshed || place.placeId in downloading) return
        val cached = loaded.cached ?: return
        if (!cached.isStale(now(), FORECAST_MAX_AGE_MS)) return
        autoRefreshed += place.placeId
        viewModelScope.launch { download(place) }
    }

    private fun forecastState(loaded: Loaded, refresh: Refresh?): ForecastUiState {
        val place = loaded.place
        val trip = trip
        if (place == null || trip == null) return ForecastUiState()

        val cached = loaded.cached
        // A download of another place (the user tapped a chip meanwhile) is not this state.
        val status = refresh?.takeIf { it.placeId == place.placeId }
        return ForecastUiState(
            place = place,
            loading = status?.status == RefreshStatus.LOADING && cached == null,
            refreshing = status?.status == RefreshStatus.LOADING && cached != null,
            error = status?.error,
            lastUpdated = cached?.fetchedAt?.let(::toLocalDateTime),
            rows = ForecastRow.build(cached?.days.orEmpty(), trip),
        )
    }

    private fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        /** A cached forecast older than this is downloaded again when its place is shown. */
        private const val FORECAST_MAX_AGE_MS = 6L * 60 * 60 * 1000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeatherViewModel(
                    createSavedStateHandle(),
                    appContainer().sampleTripRepository,
                    appContainer().weatherRepository,
                    appContainer().savedPlaceRepository,
                )
            }
        }
    }
}

/** The selected place and its cached forecast, emitted together so they never disagree. */
private data class Loaded(val place: SavedPlace?, val cached: CachedForecast?)

private data class Refresh(
    val placeId: String,
    val status: RefreshStatus,
    val error: WeatherError? = null,
)

private enum class RefreshStatus { IDLE, LOADING, FAILED }
