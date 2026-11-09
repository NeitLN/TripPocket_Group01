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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
import vn.edu.vlu.trippocket.data.model.PlaceInfo
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.repository.InvalidPlaceException
import vn.edu.vlu.trippocket.data.repository.PlaceInfoRepository
import vn.edu.vlu.trippocket.data.repository.PlaceProblem
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.domain.ApiError
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
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

    /**
     * Matches from Open-Meteo, never empty (that is [Empty]). [results] carry the Wikipedia
     * photo of each place; it is filled in a moment after the list itself appears.
     */
    data class Results(val results: List<SearchResult>) : SearchState {
        /** The places alone, for code that only saves one. */
        val places: List<Place> get() = results.map { it.place }
    }

    /** The API answered with no match: "No places found". */
    data object Empty : SearchState

    /** The request failed; the screen offers Try again and the sentence for [error]. */
    data class Error(val error: ApiError) : SearchState
}

/**
 * One row of the search dropdown (Milestone 4): the place and its Wikipedia photo. [photoUrl]
 * is null when there is no article or no thumbnail, and the row then shows the default image.
 */
data class SearchResult(
    val place: Place,
    val photoUrl: String? = null,
)

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

    /** The place now has [placeName] (WP). */
    data class Renamed(val placeName: String) : SaveMessage

    /** The note of [placeName] was saved (WP). */
    data class NoteSaved(val placeName: String) : SaveMessage

    /** [placeName] was removed from the trip, with its cached forecast (WP). */
    data class Deleted(val placeName: String) : SaveMessage

    /** The write was refused; [problem] says which field, so the dialog can mark it (WP). */
    data class Invalid(val problem: PlaceProblem) : SaveMessage
}

data class SavedPlacesUiState(
    val places: List<SavedPlace> = emptyList(),
    val selectedPlaceId: String? = null,
    val message: SaveMessage? = null,
) {
    /** The place whose forecast the screen shows. */
    val selected: SavedPlace? get() = places.firstOrNull { it.placeId == selectedPlaceId }

    /** Note of [selected], for the place panel; empty when there is no place or no note. */
    val selectedNote: String get() = selected?.note.orEmpty()
}

/**
 * The "About this place" card (Milestone 4): the Wikipedia summary of the selected place. Kept
 * apart from [ForecastUiState], with its own Loading / Error, so a slow or missing article never
 * changes the forecast. Every field is null when the trip has no selected place: the card is
 * then hidden.
 */
data class PlaceInfoUiState(
    /** null when there is no selected place. */
    val placeName: String? = null,
    val loading: Boolean = false,
    /** null when Wikipedia has no article for this place: the card shows nothing. */
    val info: PlaceInfo? = null,
    /** Why the summary failed; null when it did not. */
    val error: ApiError? = null,
) {
    /** true when there is a place to describe; the screen hides the whole card otherwise. */
    val visible: Boolean get() = placeName != null

    /** true when there is an introduction to show under the photo. */
    val hasExtract: Boolean get() = !info?.extract.isNullOrBlank()
}

data class ForecastUiState(
    /** null when the trip has no saved place yet. */
    val place: SavedPlace? = null,
    /** true while the first download for [place] runs and there is nothing to show yet. */
    val loading: Boolean = false,
    /** true while a download runs but the cache below is still displayed. */
    val refreshing: Boolean = false,
    /** Why the last download failed; null when it did not. */
    val error: ApiError? = null,
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
 *
 * Milestone 4: each search result also carries a Wikipedia photo (the first
 * [MAX_PHOTO_LOOKUPS] places, side by side), and the selected place has its own "About this
 * place" state from [placeInfoRepository]. One place whose summary fails only loses its photo.
 */
class WeatherViewModel(
    savedStateHandle: SavedStateHandle,
    tripRepository: TripRepository,
    private val weatherRepository: WeatherRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val placeInfoRepository: PlaceInfoRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    /**
     * The trip from Room, like every other tab: a trip created or edited with A2 is here too, not
     * only the sample trips. null while it loads, or when the trip is missing, deleted or owned by
     * another account; the forecast is then empty.
     *
     * The writes use [tripId] and do not wait for this flow, so a tap right after the screen opens
     * is not lost; a trip that does not exist is refused by TripWriter and reported as
     * [SaveMessage.Failed].
     */
    private val trip: StateFlow<Trip?> = tripRepository.observeTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
        // Edited dates move the trip-day labels and the "not available yet" rows at once.
        trip,
        ::forecastState,
    )
        .stateIn(viewModelScope, SharingStarted.Eagerly, ForecastUiState())

    /** Retry needs its own signal: the same place emitted again would change nothing. */
    private val placeInfoRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The "About this place" card follows the selected place, with its own Loading / Success /
     * Error. A rename changes the Wikipedia title, so it loads again; a note edit does not.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val placeInfo: StateFlow<PlaceInfoUiState> = merge(
        _places.map { it.selected }
            .distinctUntilChangedBy { place -> place?.let { it.placeId to it.name } },
        placeInfoRetry.map { _places.value.selected },
    )
        .flatMapLatest(::placeInfoFlow)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaceInfoUiState())

    init {
        if (tripId.isNotEmpty()) {
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

    /** The Try again button of the About card: loads the selected place's summary again. */
    fun retryPlaceInfo() {
        placeInfoRetry.tryEmit(Unit)
    }

    /**
     * Saves a search result to the trip (W2) and downloads its forecast. The outcome is
     * reported once through [SavedPlacesUiState.message]; the screen clears it with
     * [messageShown] after showing the snackbar.
     */
    fun pickPlace(place: Place) {
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

    /** Renames a saved place (WP); one write, reported once through [SavedPlacesUiState.message]. */
    fun renamePlace(placeId: String, name: String) {
        val newName = name.trim()
        writePlace({ savedPlaceRepository.renamePlace(tripId, placeId, name) }) {
            SaveMessage.Renamed(newName)
        }
    }

    /** Saves the note of a saved place (WP); an empty note removes it. */
    fun updatePlaceNote(placeId: String, note: String) {
        val placeName = _places.value.places.firstOrNull { it.placeId == placeId }?.name.orEmpty()
        writePlace({ savedPlaceRepository.updateNote(tripId, placeId, note) }) {
            SaveMessage.NoteSaved(placeName)
        }
    }

    /**
     * Removes a saved place and its cached forecast (WP). Removing the selected place makes the
     * places flow pick the first remaining one, or none at all.
     */
    fun deletePlace(placeId: String) {
        val placeName = _places.value.places.firstOrNull { it.placeId == placeId }?.name.orEmpty()
        writePlace({ savedPlaceRepository.deletePlace(tripId, placeId) }) {
            SaveMessage.Deleted(placeName)
        }
    }

    /**
     * Runs one write on a saved place and reports the outcome once. A refused field keeps the
     * old values and reports [SaveMessage.Invalid]; a database failure reports [SaveMessage.Failed].
     */
    private fun writePlace(block: suspend () -> Unit, onSuccess: () -> SaveMessage) {
        viewModelScope.launch {
            val message = try {
                block()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidPlaceException) {
                SaveMessage.Invalid(e.problem)
            } catch (e: Exception) {
                SaveMessage.Failed
            }
            _places.update { it.copy(message = message) }
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
     * Results are emitted twice: once as soon as the places arrive (photo placeholders) and once
     * with the Wikipedia photos, which download side by side. [safeApiCall] returns the failure
     * instead of throwing, so every emit below stays outside a try (flow exception transparency).
     */
    private fun searchFlow(query: String): Flow<SearchState> = flow {
        if (query.isBlank()) {
            emit(SearchState.Idle)
            return@flow
        }
        emit(SearchState.Loading)
        val places = when (val result = weatherRepository.searchPlaces(query)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                emit(SearchState.Error(result.error))
                return@flow
            }
        }
        if (places.isEmpty()) {
            emit(SearchState.Empty)
            return@flow
        }
        emit(SearchState.Results(places.map { SearchResult(it) }))
        emit(SearchState.Results(withPhotos(places)))
    }

    /**
     * The first [MAX_PHOTO_LOOKUPS] places with their Wikipedia photo, loaded side by side in
     * this flow's scope (the ViewModel scope). A place whose summary fails, or that has no
     * article, keeps a null [SearchResult.photoUrl]: one bad row never hides the others.
     */
    private suspend fun withPhotos(places: List<Place>): List<SearchResult> = coroutineScope {
        places.mapIndexed { index, place ->
            async {
                val photo = if (index < MAX_PHOTO_LOOKUPS) photoOf(place.name) else null
                SearchResult(place, photo)
            }
        }.awaitAll()
    }

    private suspend fun photoOf(name: String): String? =
        when (val result = placeInfoRepository.placeInfo(name)) {
            is ApiResult.Success -> result.data?.imageUrl
            // No article (404 is already Success(null)) or a failed request: default image.
            is ApiResult.Failure -> null
        }

    /**
     * The About card of [place]: Loading, then the summary or the reason it could not be loaded.
     * Cancelled and restarted by [flatMapLatest] when the selected place changes.
     */
    private fun placeInfoFlow(place: SavedPlace?): Flow<PlaceInfoUiState> = flow {
        if (place == null) {
            emit(PlaceInfoUiState())
            return@flow
        }
        emit(PlaceInfoUiState(placeName = place.name, loading = true))
        val state = when (val result = placeInfoRepository.placeInfo(place.name)) {
            is ApiResult.Success -> PlaceInfoUiState(placeName = place.name, info = result.data)
            is ApiResult.Failure -> PlaceInfoUiState(placeName = place.name, error = result.error)
        }
        emit(state)
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
            val error = when (val result = weatherRepository.refreshForecast(place)) {
                is ApiResult.Success -> null
                is ApiResult.Failure -> result.error
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

    private fun forecastState(loaded: Loaded, refresh: Refresh?, trip: Trip?): ForecastUiState {
        val place = loaded.place
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

        /** Only the first search results get a Wikipedia photo, like the API will show. */
        private const val MAX_PHOTO_LOOKUPS = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeatherViewModel(
                    createSavedStateHandle(),
                    appContainer().tripRepository,
                    appContainer().weatherRepository,
                    appContainer().savedPlaceRepository,
                    appContainer().placeInfoRepository,
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
    val error: ApiError? = null,
)

private enum class RefreshStatus { IDLE, LOADING, FAILED }
