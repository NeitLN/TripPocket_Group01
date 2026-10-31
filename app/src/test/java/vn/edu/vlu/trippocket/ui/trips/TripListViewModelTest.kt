package vn.edu.vlu.trippocket.ui.trips

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.repository.FakeAuthRepository
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository

/** Trip list on a real (in-memory) Room database. Owner: Tiến. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripListViewModelTest {

    private val t = TestDatabase()
    private val auth = FakeAuthRepository(TestDatabase.OWNER)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    /** Every uid the ViewModel asked the reminder reconciler to rebuild alarms for. */
    private val reminderSyncs = mutableListOf<String?>()

    private fun viewModel() = TripListViewModel(
        SavedStateHandle(),
        TripRepository(t.db.tripDao(), auth, t.writer),
        ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer),
        auth,
        syncReminders = { reminderSyncs += it },
    )

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
    fun summaryCountsWaitingAndFailedBackups(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("synced"))
        t.db.tripDao().insert(t.trip("waiting").copy(syncStatus = SyncStatus.PENDING))
        t.db.tripDao().insert(t.trip("failed").copy(syncStatus = SyncStatus.FAILED))

        val state = viewModel().loaded(trips = 3)
        assertEquals(1, state.pendingCount)
        assertEquals(1, state.failedCount)
    }

    @Test
    fun signingOut_hidesEveryTrip(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("mine"))
        val viewModel = viewModel()
        viewModel.loaded(trips = 1)

        auth.setUid(null)
        viewModel.loaded(trips = 0)
    }

    @Test
    fun theSignOutAction_endsTheSession_andKeepsTheLocalTrips(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("mine"))
        val viewModel = viewModel()
        viewModel.loaded(trips = 1)

        viewModel.signOut()

        assertEquals(1, auth.signOutCalls)
        assertNull(auth.currentUid.value)
        // Signed out: every alarm on this phone is cancelled.
        assertEquals(listOf<String?>(null), reminderSyncs)
        viewModel.loaded(trips = 0)
        // Still in Room for the next sign-in of the same account.
        assertEquals(1, t.db.tripDao().observeTrips(TestDatabase.OWNER).first().size)
    }
}
