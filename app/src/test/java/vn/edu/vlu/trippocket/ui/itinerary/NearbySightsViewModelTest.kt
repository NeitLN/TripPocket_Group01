package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.remote.wikipedia.FakeGeoSearchApi
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.SightRepository
import vn.edu.vlu.trippocket.domain.ApiError
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.io.IOException

/**
 * Milestone 4: nearby sights, both the list states and "Add to day". The repository and Room
 * are real; only Wikipedia is a fake, so the write really reaches the in-memory database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NearbySightsViewModelTest {

    private val t = TestDatabase()
    private val viewModels = ViewModelStore()

    private val tripId = MockData.TRIP_DALAT
    private var nextPlaceId = 0

    private val savedPlaces = SavedPlaceRepository(t.db.savedPlaceDao(), t.writer) { "p${++nextPlaceId}" }
    private val itinerary = ItineraryRepository(t.db.activityDao(), t.writer)

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip(tripId))
    }

    @After
    fun tearDown() {
        // A ViewModel still collecting Room would touch Main while the next test sets it.
        viewModels.clear()
        Dispatchers.resetMain()
        t.close()
    }

    private fun TestScope.newViewModel(
        api: FakeGeoSearchApi = FakeGeoSearchApi(),
        dayIndex: Int = 1,
    ): NearbySightsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // The repository does its request on the test scheduler, like Retrofit on IO.
        val io = StandardTestDispatcher(testScheduler)
        val factory = viewModelFactory {
            initializer {
                NearbySightsViewModel(
                    SavedStateHandle(
                        mapOf(ARG_TRIP_ID to tripId, ARG_DAY_INDEX to dayIndex)
                    ),
                    savedPlaces,
                    SightRepository(api, io),
                    itinerary,
                )
            }
        }
        return ViewModelProvider(viewModels, factory)[NearbySightsViewModel::class.java]
    }

    private suspend fun seedPlace() {
        savedPlaces.savePlace(tripId, Place("Da Lat", "Lam Dong", "Vietnam", 11.9404, 108.4583))
    }

    @Test
    fun aTripWithoutAPlace_showsNoPlace_andNeverCallsTheApi(): Unit = runTest {
        val api = FakeGeoSearchApi()
        val viewModel = newViewModel(api)

        val state = viewModel.uiState.first { !it.loading }

        assertTrue(state.noPlace)
        // Empty (Wikipedia found nothing) is a different sentence from No place.
        assertFalse(state.empty)
        assertNull(state.error)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun aSavedPlace_showsLoading_thenTheSights(): Unit = runTest {
        seedPlace()
        val api = FakeGeoSearchApi(FakeGeoSearchApi.response(FakeGeoSearchApi.page(1, "Hồ Xuân Hương")))
        api.gate = CompletableDeferred()
        val viewModel = newViewModel(api)

        val loading = viewModel.uiState.first { it.loading && it.placeName != null }
        assertEquals("Da Lat", loading.placeName)
        assertTrue(loading.sights.isEmpty())

        api.gate?.complete(Unit)
        val state = viewModel.uiState.first { !it.loading }

        assertEquals(listOf("Hồ Xuân Hương"), state.sights.map { it.title })
        assertNull(state.error)
        assertFalse(state.noPlace)
        // Sent once, around the saved place, with a dot decimal separator.
        assertEquals("11.940400|108.458300", api.requests.single().coord)
        assertEquals(1, api.requests.size)
    }

    @Test
    fun aFailure_showsTheError_andRetryLoadsTheList(): Unit = runTest {
        seedPlace()
        val api = FakeGeoSearchApi(FakeGeoSearchApi.response(FakeGeoSearchApi.page(1, "Hồ Xuân Hương")))
        api.failWith = IOException("offline")
        val viewModel = newViewModel(api)

        val failed = viewModel.uiState.first { it.error != null }
        assertEquals(ApiError.OFFLINE, failed.error)
        assertTrue(failed.sights.isEmpty())

        api.failWith = null
        viewModel.retry()

        val state = viewModel.uiState.first { it.error == null && it.sights.isNotEmpty() }
        assertEquals(listOf("Hồ Xuân Hương"), state.sights.map { it.title })
        assertEquals(2, api.requests.size)
    }

    @Test
    fun addToDay_writesTheSight_intoTheSelectedDay(): Unit = runTest {
        seedPlace()
        val api = FakeGeoSearchApi(
            FakeGeoSearchApi.response(FakeGeoSearchApi.page(1, "Hồ Xuân Hương", description = "Lake in Da Lat"))
        )
        val viewModel = newViewModel(api, dayIndex = 1)
        val sight = viewModel.uiState.first { it.sights.isNotEmpty() }.sights.single()

        viewModel.addToDay(sight)

        assertEquals(
            SightsMessage.Added(2),
            viewModel.uiState.first { it.message != null }.message,
        )
        val row = t.db.activityDao().dayRows(tripId, 1).single()
        assertEquals("Hồ Xuân Hương", row.title)
        assertEquals("Hồ Xuân Hương", row.place)
        assertEquals(9 * 60, row.startTime)
        assertEquals(0L, row.estimatedCost)
        assertEquals("Lake in Da Lat", row.note)
        assertNull(row.reminderMinutesBefore)
    }

    @Test
    fun aDoubleTap_addsOneActivity_only(): Unit = runTest {
        seedPlace()
        val api = FakeGeoSearchApi(FakeGeoSearchApi.response(FakeGeoSearchApi.page(1, "Hồ Xuân Hương")))
        val viewModel = newViewModel(api)
        val sight = viewModel.uiState.first { it.sights.isNotEmpty() }.sights.single()

        viewModel.addToDay(sight)
        viewModel.addToDay(sight)

        viewModel.uiState.first { it.message is SightsMessage.Added }
        assertEquals(1, t.db.activityDao().countByTrip(tripId))
    }

    @Test
    fun theActivityLimit_isReported_insteadOfCrashing(): Unit = runTest {
        repeat(Limits.MAX_ACTIVITIES) { index ->
            t.db.activityDao().insert(
                ActivityEntity(
                    activityId = "a$index",
                    tripId = tripId,
                    dayIndex = 0,
                    title = "Activity $index",
                    place = "",
                    startTime = index % (24 * 60),
                    estimatedCost = 0,
                    note = "",
                    orderIndex = index,
                    reminderMinutesBefore = null,
                )
            )
        }
        seedPlace()
        val api = FakeGeoSearchApi(FakeGeoSearchApi.response(FakeGeoSearchApi.page(1, "Hồ Xuân Hương")))
        val viewModel = newViewModel(api)
        val sight = viewModel.uiState.first { it.sights.isNotEmpty() }.sights.single()

        viewModel.addToDay(sight)

        assertEquals(
            SightsMessage.LimitReached,
            viewModel.uiState.first { it.message != null }.message,
        )
        assertEquals(Limits.MAX_ACTIVITIES, t.db.activityDao().countByTrip(tripId))
    }

    @Test
    fun messageShown_clearsTheMessage(): Unit = runTest {
        seedPlace()
        val api = FakeGeoSearchApi(FakeGeoSearchApi.response(FakeGeoSearchApi.page(1, "Hồ Xuân Hương")))
        val viewModel = newViewModel(api)
        val sight = viewModel.uiState.first { it.sights.isNotEmpty() }.sights.single()
        viewModel.addToDay(sight)
        viewModel.uiState.first { it.message != null }

        viewModel.messageShown()

        assertNull(viewModel.uiState.value.message)
    }
}
