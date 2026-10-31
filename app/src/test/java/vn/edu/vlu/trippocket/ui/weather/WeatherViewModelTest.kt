package vn.edu.vlu.trippocket.ui.weather

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi.Companion.of
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeGeocodingApi
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
 * Owner: Duy Anh.
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
        Dispatchers.resetMain()
        t.close()
    }

    /** Main uses the runTest scheduler, so debounce and requests run on virtual time. */
    private fun TestScope.newViewModel(
        api: FakeGeocodingApi = FakeGeocodingApi { listOf(dalat) },
    ): WeatherViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return WeatherViewModel(
            SavedStateHandle(mapOf(ARG_TRIP_ID to tripId)),
            SampleTripRepository(),
            WeatherRepository(api, forecastApi, t.db.weatherCacheDao(), t.db.savedPlaceDao(), now = { clock }),
            savedPlaces,
            now = { clock },
        )
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

    private suspend fun revision() =
        t.db.tripDao().observeTrip(tripId, TestDatabase.OWNER).first()!!.localRevision

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody()))

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
