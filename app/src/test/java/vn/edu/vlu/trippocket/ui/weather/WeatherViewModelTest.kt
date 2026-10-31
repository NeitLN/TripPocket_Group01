package vn.edu.vlu.trippocket.ui.weather

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeGeocodingApi
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.io.IOException
import java.time.LocalDate

/** W1: search pipeline of the Weather screen on a fake Open-Meteo API. Owner: Duy Anh. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val dalat = FakeGeocodingApi.result("Da Lat")

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Main uses the runTest scheduler, so debounce and requests run on virtual time. */
    private fun TestScope.newViewModel(
        api: FakeGeocodingApi = FakeGeocodingApi { listOf(dalat) },
    ): WeatherViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return WeatherViewModel(
            SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT)),
            SampleTripRepository(),
            WeatherRepository(api),
        )
    }

    /** Lets the 300 ms debounce fire and the answer land. */
    private fun TestScope.runSearch() {
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
        runCurrent()
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
        assertEquals(SearchState.Error, viewModel.search.value)

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
        assertEquals(SearchState.Error, viewModel.search.value)

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
        val api = FakeGeocodingApi { listOf(dalat) }
        val viewModel = newViewModel(api)
        val forecastBefore = viewModel.forecast.value
        assertTrue(forecastBefore.loaded)
        assertFalse(forecastBefore.rows.isEmpty())

        api.failWith = IOException("offline")
        viewModel.onQueryChanged("da lat")
        runSearch()

        assertEquals(SearchState.Error, viewModel.search.value)
        assertEquals(forecastBefore, viewModel.forecast.value)
        assertTrue(viewModel.places.value.places.isNotEmpty())
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
