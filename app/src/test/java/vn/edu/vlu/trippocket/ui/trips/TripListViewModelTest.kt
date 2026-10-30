package vn.edu.vlu.trippocket.ui.trips

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository

/** Trip list on a real (in-memory) Room database. Owner: Tiến. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripListViewModelTest {

    private val t = TestDatabase()
    private val uid = MutableStateFlow<String?>(TestDatabase.OWNER)
    private val auth = object : AuthRepository {
        override val currentUid = uid
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private fun viewModel() =
        TripListViewModel(SavedStateHandle(), TripRepository(t.db.tripDao(), auth), SampleTripRepository())

    private suspend fun TripListViewModel.loaded(trips: Int) =
        withTimeout(5_000) { uiState.first { !it.loading && it.trips.size == trips } }

    @Test
    fun showsOnlyTheSignedInAccountsTrips_andEmptyPreviewToggles(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("mine"))
        t.db.tripDao().insert(t.trip("theirs", owner = "uid-b"))
        val viewModel = viewModel()

        val state = viewModel.loaded(trips = 1)
        assertEquals("mine", state.trips.single().trip.tripId)

        viewModel.toggleEmptyPreview()
        assertTrue(viewModel.loaded(trips = 0).trips.isEmpty())
    }

    @Test
    fun signingOut_hidesEveryTrip(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("mine"))
        val viewModel = viewModel()
        viewModel.loaded(trips = 1)

        uid.value = null
        viewModel.loaded(trips = 0)
    }
}
