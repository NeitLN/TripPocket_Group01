package vn.edu.vlu.trippocket.ui.weather

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.WeatherCacheEntity
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi.Companion.of
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeGeocodingApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.GeocodingResult
import vn.edu.vlu.trippocket.data.repository.PlaceProblem
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.domain.WeatherError
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.io.IOException
import java.time.LocalDate

/**
 * W1 search and W3 saved places + cached forecast of the Weather screen.
 * Owner: Duy Anh. W4 cases (same name, rename, empty cache, retry): Bảo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WeatherViewModelTest {

    private val t = TestDatabase()
    private val forecastApi = FakeForecastApi()
    private var clock = 1_000L
    private var nextId = 0

    private val tripId = MockData.TRIP_DALAT
    private val dalat = FakeGeocodingApi.result("Da Lat")
    private val day1 = LocalDate.of(2026, 10, 31)
    private val vietnam = 11.94 to 108.44
    private val oregon = 45.52 to -122.98

    /** Holds the ViewModel so [tearDown] can cancel its scope before Main is reset. */
    private val viewModels = ViewModelStore()

    private val savedPlaces = SavedPlaceRepository(t.db.savedPlaceDao(), t.writer) { "p${++nextId}" }

    /** Prepares the cache before the ViewModel starts, like a previous run of the app. */
    private val weather = WeatherRepository(
        FakeGeocodingApi(),
        forecastApi,
        t.db.weatherCacheDao(),
        t.db.savedPlaceDao(),
        now = { clock },
    )

    @Before
    fun setUp() = runBlocking { t.db.tripDao().insert(t.trip(tripId)) }

    @After
    fun tearDown() {
        // A ViewModel still collecting Room would touch Main while the next test sets it.
        viewModels.clear()
        Dispatchers.resetMain()
        t.close()
    }

    /** Main uses the runTest scheduler, so debounce and requests run on virtual time. */
    private fun TestScope.newViewModel(
        api: FakeGeocodingApi = FakeGeocodingApi { listOf(dalat) },
    ): WeatherViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val factory = viewModelFactory {
            initializer {
                WeatherViewModel(
                    SavedStateHandle(mapOf(ARG_TRIP_ID to tripId)),
                    SampleTripRepository(),
                    WeatherRepository(api, forecastApi, t.db.weatherCacheDao(), t.db.savedPlaceDao(), now = { clock }),
                    savedPlaces,
                    now = { clock },
                )
            }
        }
        return ViewModelProvider(viewModels, factory)[WeatherViewModel::class.java]
    }

    /** Lets the 300 ms debounce fire and the answer land. */
    private fun TestScope.runSearch() {
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
        runCurrent()
    }

    private suspend fun seedPlace(at: Pair<Double, Double> = vietnam): SavedPlace {
        val id = savedPlaces.savePlace(tripId, Place("Da Lat", "Lam Dong", "Vietnam", at.first, at.second))
        return savedPlaces.observePlaces(tripId).first().first { it.placeId == id }
    }

    @Test
    fun aCity_showsLoading_thenResults(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        api.gate = CompletableDeferred()
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("  da lat  ")
        runSearch()
        assertEquals(SearchState.Loading, viewModel.search.value)

        api.gate?.complete(Unit)
        runCurrent()

        val state = viewModel.search.value
        assertTrue(state is SearchState.Results)
        assertEquals("Da Lat", (state as SearchState.Results).places.single().name)
        assertEquals("Vietnam", state.places.single().country)
        // The repository trims before calling Open-Meteo.
        assertEquals(listOf("da lat"), api.queries)
    }

    @Test
    fun aBlankQuery_isIdle_andNeverCallsTheApi(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("   ")
        runSearch()

        assertEquals(SearchState.Idle, viewModel.search.value)
        assertTrue(api.queries.isEmpty())
    }

    @Test
    fun anUnknownCity_isEmpty(): Unit = runTest {
        val api = FakeGeocodingApi { emptyList() }
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("xyz")
        runSearch()

        assertEquals(SearchState.Empty, viewModel.search.value)
        assertEquals(listOf("xyz"), api.queries)
    }

    @Test
    fun aLostConnection_showsError_andTheNextQueryStillWorks(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        api.failWith = IOException("offline")
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("da lat")
        runSearch()
        assertEquals(SearchState.Error(WeatherError.NETWORK), viewModel.search.value)

        api.failWith = null
        viewModel.onQueryChanged("da nang")
        runSearch()

        assertTrue(viewModel.search.value is SearchState.Results)
        assertEquals(listOf("da lat", "da nang"), api.queries)
    }

    @Test
    fun retrySearch_runsTheSameQueryAgain(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        api.failWith = IOException("offline")
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("da lat")
        runSearch()
        assertEquals(SearchState.Error(WeatherError.NETWORK), viewModel.search.value)

        api.failWith = null
        viewModel.retrySearch()
        runCurrent()

        assertTrue(viewModel.search.value is SearchState.Results)
        assertEquals(listOf("da lat", "da lat"), api.queries)
    }

    @Test
    fun typingFast_onlyTheLastQueryReachesTheApi(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("d")
        advanceTimeBy(100)
        viewModel.onQueryChanged("da")
        advanceTimeBy(100)
        viewModel.onQueryChanged("da l")
        runSearch()

        assertEquals(listOf("da l"), api.queries)
    }

    @Test
    fun anEmptyForecast_doesNotCrash_andMarksEveryTripDayUnavailable() {
        val trip = Trip(
            tripId = "t",
            name = "Đà Lạt",
            destination = "Lâm Đồng",
            startDate = LocalDate.of(2026, 10, 31),
            endDate = LocalDate.of(2026, 11, 2),
            syncStatus = SyncStatus.SYNCED,
        )

        val rows = ForecastRow.build(emptyList(), trip)

        assertEquals(3, rows.size)
        assertEquals(listOf(1, 2, 3), rows.map { it.tripDay })
        assertTrue(rows.all { it.forecast == null })
    }

    @Test
    fun aSearchError_leavesTheSavedPlaceAndForecastAlone(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1)
        weather.refreshForecast(place)

        val api = FakeGeocodingApi { listOf(dalat) }
        val viewModel = newViewModel(api)
        val before = viewModel.forecast.first { it.hasForecast }

        api.failWith = IOException("offline")
        viewModel.onQueryChanged("da lat")
        runSearch()

        assertEquals(SearchState.Error(WeatherError.NETWORK), viewModel.search.value)
        assertEquals(before, viewModel.forecast.value)
        assertEquals(place.placeId, viewModel.places.value.selectedPlaceId)
    }

    @Test
    fun savingAPlace_savesItToTheTrip_andDownloadsItsForecast(): Unit = runTest {
        val viewModel = newViewModel()
        forecastApi.response = of(day1, maxTemp = 24)

        viewModel.pickPlace(Place("Da Lat", "Lam Dong", "Vietnam", vietnam.first, vietnam.second))

        val state = viewModel.forecast.first { it.hasForecast }
        assertEquals("Da Lat", state.place?.name)
        assertEquals(24, state.today?.maxTemp)
        assertEquals(listOf(vietnam), forecastApi.requests)
        assertEquals(SaveMessage.Saved("Da Lat"), viewModel.places.value.message)

        val places = viewModel.places.value
        assertEquals(1, places.places.size)
        assertEquals(places.places.single().placeId, places.selectedPlaceId)
        assertEquals(places.places.single().placeId, state.place?.placeId)
    }

    @Test
    fun savingTheSameCoordinatesAgain_keepsOnePlace_andSaysItIsAlreadySaved(): Unit = runTest {
        val viewModel = newViewModel()
        val place = Place("Da Lat", "Lam Dong", "Vietnam", vietnam.first, vietnam.second)

        viewModel.pickPlace(place)
        viewModel.places.first { it.places.size == 1 && it.message is SaveMessage.Saved }

        viewModel.pickPlace(place)

        val state = viewModel.places.first { it.message is SaveMessage.AlreadySaved }
        assertEquals(1, state.places.size)
    }

    @Test
    fun the31stPlace_saysTheLimitIsReached_andWritesOrDownloadsNothing(): Unit = runTest {
        repeat(Limits.MAX_PLACES) { i ->
            savedPlaces.savePlace(tripId, Place("P$i", "", "Vietnam", 10.0 + i, 106.0))
        }
        // A fresh cache for the selected place, so opening the screen is not a stale refresh.
        forecastApi.response = of(day1)
        weather.refreshForecast(savedPlaces.observePlaces(tripId).first().first())
        forecastApi.requests.clear()
        val viewModel = newViewModel()
        viewModel.places.first { it.places.size == Limits.MAX_PLACES }
        val before = revision()

        viewModel.pickPlace(Place("Da Lat", "Lam Dong", "Vietnam", vietnam.first, vietnam.second))

        val state = viewModel.places.first { it.message == SaveMessage.LimitReached }
        assertEquals(Limits.MAX_PLACES, state.places.size)
        // The 31st place is refused before the write and before the forecast request.
        assertEquals(before, revision())
        assertTrue(forecastApi.requests.isEmpty())
    }

    @Test
    fun selectingAPlaceById_showsThatPlacesOwnCache(): Unit = runTest {
        // Same name, different coordinates: the id decides, not the name.
        val vn = seedPlace(vietnam)
        val us = seedPlace(oregon)
        forecastApi.response = of(day1, maxTemp = 22)
        weather.refreshForecast(vn)
        forecastApi.response = of(day1, maxTemp = 31)
        weather.refreshForecast(us)

        val viewModel = newViewModel()
        val first = viewModel.forecast.first { it.place?.placeId == vn.placeId && it.hasForecast }
        assertEquals(22, first.today?.maxTemp)

        viewModel.selectPlace(us.placeId)

        val second = viewModel.forecast.first { it.place?.placeId == us.placeId && it.hasForecast }
        assertEquals(31, second.today?.maxTemp)
    }

    @Test
    fun aFailedRefresh_keepsTheCachedRows_andMarksThemAsAnOfflineCopy(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 26)
        weather.refreshForecast(place)

        val viewModel = newViewModel()
        viewModel.forecast.first { it.hasForecast }

        forecastApi.failWith = IOException("offline")
        viewModel.refresh()

        val state = viewModel.forecast.first { it.failed }
        assertEquals(26, state.today?.maxTemp)
        assertTrue(state.fromCache)
        assertFalse(state.loading)
        assertEquals(WeatherError.NETWORK, state.error)
    }

    @Test
    fun retryAfterAFailure_downloadsTheForecastAgain_andClearsTheOfflineCopy(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 26)
        weather.refreshForecast(place)

        val viewModel = newViewModel()
        viewModel.forecast.first { it.hasForecast }

        forecastApi.failWith = IOException("offline")
        viewModel.refresh()
        viewModel.forecast.first { it.failed }

        forecastApi.failWith = null
        forecastApi.response = of(day1, maxTemp = 30)
        viewModel.retry()

        val state = viewModel.forecast.first { !it.failed && it.today?.maxTemp == 30 }
        assertFalse(state.fromCache)
    }

    @Test
    fun anOldCache_isDownloadedAgain_once_whenThePlaceIsShown(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 20)
        weather.refreshForecast(place)
        clock += 7 * 60 * 60 * 1000
        forecastApi.response = of(day1, maxTemp = 27)
        forecastApi.requests.clear()

        val viewModel = newViewModel()
        val state = viewModel.forecast.first { it.today?.maxTemp == 27 }

        assertFalse(state.failed)
        assertEquals(1, forecastApi.requests.size)
    }

    @Test
    fun aFreshCache_isNotDownloadedWhenThePlaceIsShown(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 20)
        weather.refreshForecast(place)
        forecastApi.requests.clear()

        val viewModel = newViewModel()
        val state = viewModel.forecast.first { it.hasForecast }

        assertEquals(20, state.today?.maxTemp)
        // The stale check runs before this state is emitted, so a refresh would already be running.
        assertTrue(forecastApi.requests.isEmpty())
    }

    @Test
    fun anOldCacheThatCannotBeDownloaded_keepsTheForecast_andSaysWhy(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 20)
        weather.refreshForecast(place)
        clock += 7 * 60 * 60 * 1000
        forecastApi.failWith = IOException("offline")
        forecastApi.requests.clear()

        val viewModel = newViewModel()
        val state = viewModel.forecast.first { it.failed }

        assertEquals(20, state.today?.maxTemp)
        assertTrue(state.hasForecast)
        assertTrue(state.fromCache)
        assertEquals(WeatherError.NETWORK, state.error)
        assertEquals(1, forecastApi.requests.size)
    }

    @Test
    fun refreshingAgainWhileTheFirstRequestRuns_sendsOneRequest(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 20)
        weather.refreshForecast(place)
        forecastApi.requests.clear()
        forecastApi.gate = CompletableDeferred()

        val viewModel = newViewModel()
        viewModel.forecast.first { it.hasForecast }

        viewModel.refresh()
        viewModel.forecast.first { it.refreshing }
        // A second tap while the first request is still open must not reach the API.
        viewModel.refresh()
        runCurrent()

        assertEquals(1, forecastApi.requests.size)
        forecastApi.gate?.complete(Unit)
        runCurrent()
        assertEquals(1, forecastApi.requests.size)
    }

    @Test
    fun tooManyRequests_showsTheRateLimitSentence(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        api.failWith = httpError(429)
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("da lat")
        runSearch()

        assertEquals(SearchState.Error(WeatherError.RATE_LIMITED), viewModel.search.value)
    }

    @Test
    fun aServerErrorWhileDownloading_keepsItsKind(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 20)
        weather.refreshForecast(place)
        clock += 7 * 60 * 60 * 1000
        forecastApi.failWith = httpError(503)

        val viewModel = newViewModel()
        val state = viewModel.forecast.first { it.failed }

        assertEquals(WeatherError.SERVER, state.error)
        // The old cache is still shown, marked as the offline copy.
        assertTrue(state.hasForecast)
        assertTrue(state.fromCache)
        assertFalse(state.loading)
    }

    @Test
    fun renamingAPlace_savesIt_andSaysSo(): Unit = runTest {
        val place = seedPlace()
        val viewModel = newViewModel()
        viewModel.places.first { it.places.size == 1 }

        viewModel.renamePlace(place.placeId, "  Đà Lạt  ")

        val state = viewModel.places.first { it.message is SaveMessage.Renamed }
        assertEquals(SaveMessage.Renamed("Đà Lạt"), state.message)
        assertEquals("Đà Lạt", savedPlaces.observePlaces(tripId).first().single().name)
        assertEquals(7, revision())

        viewModel.messageShown()
        assertNull(viewModel.places.value.message)
    }

    @Test
    fun aBlankOrTooLongName_isRefused_andKeepsTheOldOne(): Unit = runTest {
        val place = seedPlace()
        val viewModel = newViewModel()
        viewModel.places.first { it.places.size == 1 }

        viewModel.renamePlace(place.placeId, "   ")
        assertEquals(
            SaveMessage.Invalid(PlaceProblem.BLANK_NAME),
            viewModel.places.first { it.message is SaveMessage.Invalid }.message,
        )

        viewModel.renamePlace(place.placeId, "a".repeat(Limits.MAX_TEXT + 1))
        val state = viewModel.places.first { it.message == SaveMessage.Invalid(PlaceProblem.NAME_TOO_LONG) }
        assertEquals(SaveMessage.Invalid(PlaceProblem.NAME_TOO_LONG), state.message)

        assertEquals("Da Lat", savedPlaces.observePlaces(tripId).first().single().name)
        assertEquals(6, revision())
    }

    @Test
    fun theNote_isChecked_andShownForTheSelectedPlace(): Unit = runTest {
        val place = seedPlace()
        val viewModel = newViewModel()
        viewModel.places.first { it.places.size == 1 }

        viewModel.updatePlaceNote(place.placeId, "a".repeat(Limits.MAX_NOTE + 1))
        assertEquals(
            SaveMessage.Invalid(PlaceProblem.NOTE_TOO_LONG),
            viewModel.places.first { it.message is SaveMessage.Invalid }.message,
        )
        assertEquals("", viewModel.places.value.selectedNote)

        viewModel.updatePlaceNote(place.placeId, "  Mang áo ấm  ")
        viewModel.places.first { it.message == SaveMessage.NoteSaved("Da Lat") }
        val state = viewModel.places.first { it.selectedNote == "Mang áo ấm" }
        assertEquals("Mang áo ấm", state.selectedNote)
        assertEquals("Mang áo ấm", savedPlaces.observePlaces(tripId).first().single().note)
        assertEquals(7, revision())
    }

    @Test
    fun deletingTheSelectedPlace_selectsTheFirstRemaining_andDropsItsCache(): Unit = runTest {
        val first = seedPlace(vietnam)
        val second = seedPlace(oregon)
        t.db.weatherCacheDao().upsert(WeatherCacheEntity(first.placeId, "{}", 1L))
        val viewModel = newViewModel()
        viewModel.places.first { it.places.size == 2 && it.selectedPlaceId == first.placeId }
        viewModel.forecast.first { it.place?.placeId == first.placeId }

        viewModel.deletePlace(first.placeId)

        val state = viewModel.places.first {
            it.message is SaveMessage.Deleted && it.places.size == 1 && it.selectedPlaceId == second.placeId
        }
        assertEquals(SaveMessage.Deleted("Da Lat"), state.message)
        assertEquals(listOf(second.placeId), state.places.map { it.placeId })
        assertEquals(second.placeId, state.selectedPlaceId)
        assertNull(t.db.weatherCacheDao().observe(first.placeId).first())
        assertEquals(8, revision())
    }

    @Test
    fun deletingTheLastPlace_leavesTheScreenEmpty(): Unit = runTest {
        val place = seedPlace()
        val viewModel = newViewModel()
        viewModel.places.first { it.places.size == 1 }
        viewModel.forecast.first { it.place?.placeId == place.placeId }

        viewModel.deletePlace(place.placeId)

        val state = viewModel.places.first { it.message is SaveMessage.Deleted && it.places.isEmpty() }
        assertTrue(state.places.isEmpty())
        assertNull(state.selectedPlaceId)
        assertTrue(viewModel.forecast.first { it.place == null }.rows.isEmpty())
    }

    @Test
    fun editingAPlaceThatIsNotInTheTrip_isRefused(): Unit = runTest {
        val viewModel = newViewModel()
        viewModel.places.first { it.places.isEmpty() }

        viewModel.renamePlace("missing", "Name")

        assertEquals(
            SaveMessage.Invalid(PlaceProblem.NOT_FOUND),
            viewModel.places.first { it.message is SaveMessage.Invalid }.message,
        )
    }

    @Test
    fun twoPlacesWithTheSameName_areSavedApart_andEachGetsItsOwnForecast(): Unit = runTest {
        val viewModel = newViewModel()

        viewModel.pickPlace(Place("Da Lat", "Lam Dong", "Vietnam", vietnam.first, vietnam.second))
        val first = viewModel.places.first { it.places.size == 1 }.places.single()
        viewModel.forecast.first { it.place?.placeId == first.placeId && it.hasForecast }

        viewModel.pickPlace(Place("Da Lat", "Oregon", "United States", oregon.first, oregon.second))

        // Same name, other coordinates: a second place, not "already saved".
        val state = viewModel.places.first { it.places.size == 2 && it.message == SaveMessage.Saved("Da Lat") }
        val second = state.places.single { it.placeId != first.placeId }
        assertEquals(second.placeId, state.selectedPlaceId)
        assertEquals(oregon.first, second.latitude, 0.0)
        viewModel.forecast.first { it.place?.placeId == second.placeId && it.hasForecast }
        assertEquals(listOf(vietnam, oregon), forecastApi.requests)
    }

    @Test
    fun renamingAPlace_keepsItsId_itsSelection_andItsForecast(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 23)
        weather.refreshForecast(place)
        forecastApi.requests.clear()
        val viewModel = newViewModel()
        viewModel.forecast.first { it.hasForecast }

        viewModel.renamePlace(place.placeId, "Đà Lạt")

        // The cache is keyed by id, so the new name shows the same forecast without a download.
        val state = viewModel.forecast.first { it.place?.name == "Đà Lạt" }
        assertEquals(place.placeId, state.place?.placeId)
        assertEquals(place.placeId, viewModel.places.value.selectedPlaceId)
        assertEquals(23, state.today?.maxTemp)
        assertTrue(forecastApi.requests.isEmpty())
    }

    @Test
    fun aPlaceWithNoCache_showsItsTripDaysEmpty_andIsNotDownloadedOnItsOwn(): Unit = runTest {
        val place = seedPlace()
        val viewModel = newViewModel()

        val state = viewModel.forecast.first { it.place?.placeId == place.placeId }
        runCurrent()

        assertFalse(state.hasForecast)
        assertFalse(state.loading)
        assertFalse(state.failed)
        assertNull(state.lastUpdated)
        assertTrue(state.rows.isNotEmpty())
        assertTrue(state.rows.all { it.forecast == null })
        // No cache is not a stale cache: only saving the place or a refresh downloads it.
        assertTrue(forecastApi.requests.isEmpty())

        viewModel.refresh()
        viewModel.forecast.first { it.hasForecast }
        assertEquals(listOf(vietnam), forecastApi.requests)
    }

    @Test
    fun theFirstDownload_showsLoading_untilTheForecastArrives(): Unit = runTest {
        val viewModel = newViewModel()
        forecastApi.gate = CompletableDeferred()

        viewModel.pickPlace(Place("Da Lat", "Lam Dong", "Vietnam", vietnam.first, vietnam.second))

        val loading = viewModel.forecast.first { it.loading }
        assertFalse(loading.refreshing)
        assertFalse(loading.hasForecast)

        forecastApi.gate?.complete(Unit)
        val done = viewModel.forecast.first { it.hasForecast }
        assertFalse(done.loading)
        assertFalse(done.failed)
    }

    @Test
    fun aFirstDownloadThatFails_keepsThePlace_andIsNotAnOfflineCopy(): Unit = runTest {
        val viewModel = newViewModel()
        forecastApi.failWith = IOException("offline")

        viewModel.pickPlace(Place("Da Lat", "Lam Dong", "Vietnam", vietnam.first, vietnam.second))

        val state = viewModel.forecast.first { it.failed }
        assertEquals(WeatherError.NETWORK, state.error)
        assertFalse(state.hasForecast)
        // Nothing was cached, so there is no offline copy to show.
        assertFalse(state.fromCache)
        assertFalse(state.loading)
        assertEquals(1, savedPlaces.observePlaces(tripId).first().size)

        forecastApi.failWith = null
        viewModel.retry()
        assertFalse(viewModel.forecast.first { it.hasForecast }.failed)
    }

    @Test
    fun retrySearch_afterAnEmptyResult_runsTheSameQueryAgain(): Unit = runTest {
        var found = emptyList<GeocodingResult>()
        val api = FakeGeocodingApi { found }
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("da lat")
        runSearch()
        assertEquals(SearchState.Empty, viewModel.search.value)

        found = listOf(dalat)
        viewModel.retrySearch()
        runCurrent()

        assertTrue(viewModel.search.value is SearchState.Results)
        assertEquals(listOf("da lat", "da lat"), api.queries)
    }

    @Test
    fun clearingTheQueryWhileASearchRuns_goesBackToIdle_andTheLateAnswerNeverShows(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(dalat) }
        api.gate = CompletableDeferred()
        val viewModel = newViewModel(api)

        viewModel.onQueryChanged("da lat")
        runSearch()
        assertEquals(SearchState.Loading, viewModel.search.value)

        viewModel.onQueryChanged("")
        runSearch()
        assertEquals(SearchState.Idle, viewModel.search.value)

        // The cancelled request answers late: the dropdown stays closed.
        api.gate?.complete(Unit)
        runCurrent()
        assertEquals(SearchState.Idle, viewModel.search.value)

        // Retry with a blank query has nothing to run.
        viewModel.retrySearch()
        runCurrent()
        assertEquals(SearchState.Idle, viewModel.search.value)
        assertEquals(listOf("da lat"), api.queries)
    }

    @Test
    fun aNewQuery_cancelsTheRunningRequest_andOnlyItsResultsShow(): Unit = runTest {
        val api = FakeGeocodingApi { listOf(FakeGeocodingApi.result(it)) }
        api.gate = CompletableDeferred()
        val viewModel = newViewModel(api)
        val seen = mutableListOf<SearchState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.search.collect { seen += it } }

        viewModel.onQueryChanged("a")
        runSearch()
        assertEquals(SearchState.Loading, viewModel.search.value)

        viewModel.onQueryChanged("b")
        runSearch()
        api.gate?.complete(Unit)
        runCurrent()

        val state = viewModel.search.value
        assertTrue(state is SearchState.Results)
        assertEquals(listOf("b"), (state as SearchState.Results).places.map { it.name })
        assertEquals(listOf("a", "b"), api.queries)
        // flatMapLatest dropped "a" before it answered, so its results never reached the screen.
        assertFalse(seen.any { it is SearchState.Results && it.places.any { p -> p.name == "a" } })
    }

    @Test
    fun retryAndRefresh_downloadAgainByTheSavedCoordinates_withoutSavingThePlaceAgain(): Unit = runTest {
        val place = seedPlace()
        forecastApi.response = of(day1, maxTemp = 20)
        weather.refreshForecast(place)
        forecastApi.requests.clear()
        val viewModel = newViewModel()
        viewModel.forecast.first { it.hasForecast }
        val before = revision()

        forecastApi.response = of(day1, maxTemp = 27)
        viewModel.retry()
        viewModel.forecast.first { it.today?.maxTemp == 27 && !it.refreshing }

        forecastApi.response = of(day1, maxTemp = 28)
        viewModel.refresh()
        viewModel.forecast.first { it.today?.maxTemp == 28 && !it.refreshing }

        val places = savedPlaces.observePlaces(tripId).first()
        assertEquals(listOf(place.placeId), places.map { it.placeId })
        assertEquals(place.placeId, viewModel.places.value.selectedPlaceId)
        assertEquals(listOf(vietnam, vietnam), forecastApi.requests)
        // Only the cache changed: the trip was not written, so the place was not saved again.
        assertEquals(before, revision())
    }

    private suspend fun revision() =
        t.db.tripDao().observeTrip(tripId, TestDatabase.OWNER).first()!!.localRevision

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody()))

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
