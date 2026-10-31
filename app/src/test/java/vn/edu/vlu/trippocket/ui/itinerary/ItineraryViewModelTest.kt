package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeGeocodingApi
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** I1: the itinerary screen reads the selected day from Room. Owner of the module: Bảo. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ItineraryViewModelTest {

    private val t = TestDatabase()
    private val repository = ItineraryRepository(t.db.activityDao(), t.writer)

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        t.db.tripDao().insert(t.trip(MockData.TRIP_DALAT))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private fun handle() = SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT))

    private fun weather() = WeatherRepository(
        FakeGeocodingApi(),
        FakeForecastApi(),
        t.db.weatherCacheDao(),
        t.db.savedPlaceDao(),
    )

    private fun viewModel(handle: SavedStateHandle = handle()) =
        ItineraryViewModel(handle, SampleTripRepository(), weather(), repository)

    private fun insert(id: String, dayIndex: Int, startTime: Int, cost: Long, orderIndex: Int) =
        ActivityEntity(id, MockData.TRIP_DALAT, dayIndex, "Activity $id", "", startTime, cost, "", orderIndex, null)

    private suspend fun ItineraryViewModel.loaded(activities: Int) =
        withTimeout(5_000) { uiState.first { it.activities.size == activities } }

    @Test
    fun itinerary_readsTheSelectedDayFromRoom_andSavesTheTab() = runBlocking {
        t.db.activityDao().insert(insert("a1", dayIndex = 0, startTime = 6 * 60 + 30, cost = 300_000, orderIndex = 0))
        t.db.activityDao().insert(insert("a2", dayIndex = 0, startTime = 13 * 60 + 30, cost = 1_500_000, orderIndex = 1))
        t.db.activityDao().insert(insert("a3", dayIndex = 1, startTime = 5 * 60, cost = 210_000, orderIndex = 0))
        val state = handle()
        val viewModel = viewModel(state)

        val first = viewModel.loaded(activities = 2)
        assertEquals(4, first.days.size)
        assertEquals(listOf("a1", "a2"), first.activities.map { it.activityId })
        assertEquals(1_800_000, first.estimatedCost)
        assertEquals(3, first.activityCount)
        assertTrue(first.canAdd)
        // Kept on the sample data until W5: the trip's forecast starts the day after the first tab.
        assertNull(first.forecast)

        viewModel.selectDay(1)
        val second = withTimeout(5_000) { viewModel.uiState.first { it.selectedDay == 1 && it.activities.size == 1 } }
        assertEquals(listOf("a3"), second.activities.map { it.activityId })
        assertEquals(210_000, second.estimatedCost)
        assertEquals(second.days[1], second.forecast?.date)

        // A re-created screen opens on the saved tab.
        val again = viewModel(state)
        assertEquals(1, again.uiState.value.selectedDay)
        assertEquals(4, again.uiState.value.days.size)
    }

    @Test
    fun atTheCap_theDayStillShows_butAddIsClosed() = runBlocking {
        repeat(Limits.MAX_ACTIVITIES) { i ->
            t.db.activityDao().insert(insert("a$i", dayIndex = 0, startTime = 9 * 60, cost = 0, orderIndex = i))
        }
        val viewModel = viewModel()

        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.activityCount == Limits.MAX_ACTIVITIES }
        }
        assertFalse(state.canAdd)
        assertEquals(Limits.MAX_ACTIVITIES, state.activities.size)
        assertEquals(0, state.estimatedCost)
    }

    @Test
    fun anUnknownTrip_showsTheEmptyState() {
        val viewModel = ItineraryViewModel(
            SavedStateHandle(mapOf(ARG_TRIP_ID to "missing")),
            SampleTripRepository(),
            weather(),
            repository,
        )

        assertEquals(ItineraryUiState(), viewModel.uiState.value)
        viewModel.selectDay(2)
        assertEquals(ItineraryUiState(), viewModel.uiState.value)
    }

    @Test
    fun reorderAndMove_saveTheNewOrder_oneRevisionPerChange() = runBlocking {
        listOf("a", "b", "c").forEachIndexed { i, id ->
            t.db.activityDao().insert(insert(id, dayIndex = 0, startTime = 9 * 60, cost = 0, orderIndex = i))
        }
        val viewModel = viewModel()
        viewModel.loaded(activities = 3)

        viewModel.reorder(listOf("c", "a", "b"))
        val dragged = withTimeout(5_000) {
            viewModel.uiState.first { it.activities.map { activity -> activity.activityId } == listOf("c", "a", "b") }
        }
        assertEquals(listOf("c", "a", "b"), dragged.activities.map { it.activityId })
        assertEquals(6, revision())

        // The same write path as a drag, for the Move down button (c moves from first to second).
        viewModel.moveDown("c")
        withTimeout(5_000) {
            viewModel.uiState.first { it.activities.map { activity -> activity.activityId } == listOf("a", "c", "b") }
        }
        assertEquals(7, revision())
    }

    private suspend fun revision() =
        t.db.tripDao().observeTrip(MockData.TRIP_DALAT, TestDatabase.OWNER).first()!!.localRevision
}
