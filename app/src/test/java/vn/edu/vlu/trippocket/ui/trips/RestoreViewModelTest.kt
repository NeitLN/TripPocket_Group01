package vn.edu.vlu.trippocket.ui.trips

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotCodecTest.Companion.fullSnapshot
import vn.edu.vlu.trippocket.data.repository.RestoreConflict
import vn.edu.vlu.trippocket.data.repository.RestorePlan
import vn.edu.vlu.trippocket.data.repository.RestoreResult
import java.io.IOException

/** A4: the "Restore from backup" flow of Screen 2, with the repository replaced by lambdas. */
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreViewModelTest {

    private val result = RestoreResult(restored = 1, kept = 0, changedMeanwhile = 0, rejected = 0)
    private val applied = mutableListOf<Pair<RestorePlan, Set<String>>>()
    private var plan = RestorePlan("uid-a", added = listOf(fullSnapshot("t1")), conflicts = emptyList(), unchanged = 0, rejected = 0)
    private var failure: Exception? = null

    private val viewModel by lazy {
        RestoreViewModel(
            prepare = { failure?.let { throw it } ?: plan },
            apply = { p, chosen -> applied += p to chosen; result },
        )
    }

    private val conflict = RestoreConflict(fullSnapshot("t2"), localRevision = 4, localName = "Huế", localUpdatedAt = 0, localDeleted = false)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun nextMessage() = runBlocking { viewModel.messages.first() }

    @Test
    fun nothingToChoose_isWrittenStraightAway() {
        viewModel.restore()

        assertEquals(listOf(plan to emptySet<String>()), applied)
        assertEquals(RestoreMessage.Done(result), nextMessage())
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun conflicts_waitForTheDialog_andOnlyTheTickedTripsUseTheBackup() {
        plan = plan.copy(conflicts = listOf(conflict))

        viewModel.restore()
        assertEquals(listOf(conflict), viewModel.uiState.value.conflicts)
        assertTrue(applied.isEmpty())

        viewModel.choose(setOf("t2"))

        assertEquals(listOf(plan to setOf("t2")), applied)
        assertTrue(viewModel.uiState.value.conflicts.isEmpty())
        assertEquals(RestoreMessage.Done(result), nextMessage())
    }

    @Test
    fun cancellingTheDialog_writesNothing() {
        plan = plan.copy(conflicts = listOf(conflict))
        viewModel.restore()

        viewModel.cancel()
        viewModel.choose(setOf("t2")) // a late tap after the cancel

        assertTrue(applied.isEmpty())
        assertEquals(RestoreMessage.Cancelled, nextMessage())
    }

    @Test
    fun anEmptyBackup_saysSo() {
        plan = RestorePlan("uid-a", emptyList(), emptyList(), unchanged = 0, rejected = 0)
        viewModel.restore()
        assertEquals(RestoreMessage.NoBackup, nextMessage())
    }

    @Test
    fun offline_saysSo_andTheMenuWorksAgain() {
        failure = IOException("offline")
        viewModel.restore()

        assertEquals(RestoreMessage.Offline, nextMessage())
        assertFalse(viewModel.uiState.value.busy)
    }
}
