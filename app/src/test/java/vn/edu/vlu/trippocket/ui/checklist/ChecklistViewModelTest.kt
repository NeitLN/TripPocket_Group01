package vn.edu.vlu.trippocket.ui.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.repository.ChecklistRepository
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Checklist tab on a real (in-memory) Room database (I2). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChecklistViewModelTest {

    /**
     * Room runs on these threads so tearDown can wait for them. The ViewModel collects Room with
     * stateIn(Eagerly): an invalidation that arrives after a test ends resumes that collector on
     * Dispatchers.Main (the Unconfined test dispatcher runs it on Room's thread), and if that
     * happens while tearDown resets Main, kotlinx-coroutines-test throws "Dispatchers.Main is
     * used concurrently with setting it" (seen once in a full run). Same fix as WeatherViewModelTest.
     */
    private val roomExecutor = Executors.newFixedThreadPool(4)
    private val t = TestDatabase(roomExecutor)

    /** Holds the ViewModels so tearDown can cancel their scopes before Main is reset. */
    private val viewModels = ViewModelStore()
    private var created = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { t.db.tripDao().insert(t.trip("t1")) }
    }

    @After
    fun tearDown() {
        // A ViewModel still collecting Room would touch Main while the next test sets it.
        viewModels.clear()
        // Let Room finish what it already started (and its resumes reach Main) before Main changes.
        roomExecutor.shutdown()
        val roomIdle = roomExecutor.awaitTermination(10, TimeUnit.SECONDS)
        Dispatchers.resetMain()
        t.close()
        assertTrue("Room work was still running 10 s after the test; resetting Main could race it", roomIdle)
    }

    private fun viewModel(): ChecklistViewModel {
        val factory = viewModelFactory {
            initializer {
                ChecklistViewModel(
                    SavedStateHandle(mapOf(ARG_TRIP_ID to "t1")),
                    ChecklistRepository(t.db.checklistDao(), t.writer),
                )
            }
        }
        // A new key each time: a test that builds a second ViewModel gets a fresh one.
        return ViewModelProvider(viewModels, factory)["vm${created++}", ChecklistViewModel::class.java]
    }

    private suspend fun ChecklistViewModel.state(total: Int) =
        withTimeout(5_000) { uiState.first { !it.loading && it.total == total } }

    private suspend fun ChecklistViewModel.nextMessage() = withTimeout(5_000) { messages.first() }

    @Test
    fun add_tick_andCount(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.add("Áo khoác")
        assertEquals(ChecklistMessage.Added, viewModel.nextMessage())
        viewModel.add("Sạc dự phòng")
        viewModel.nextMessage()

        val item = viewModel.state(total = 2).items.first()
        viewModel.setDone(item, done = true)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.done == 1 } }
        assertEquals(2, state.total)
        assertTrue(state.canAdd)
    }

    @Test
    fun aBlankItem_showsAProblem_andSavesNothing(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.add("   ")
        assertEquals(ChecklistMessage.Problem(R.string.checklist_error_blank), viewModel.nextMessage())
        assertEquals(0, viewModel.state(total = 0).total)
    }

    @Test
    fun atTheCap_addIsOff_andDeleteOffersUndo(): Unit = runBlocking {
        repeat(Limits.MAX_CHECKLIST_ITEMS) { i ->
            t.db.checklistDao().insert(ChecklistItemEntity("seed$i", "t1", "Item $i", isDone = false))
        }
        val viewModel = viewModel()
        val full = viewModel.state(total = Limits.MAX_CHECKLIST_ITEMS)
        assertFalse(full.canAdd)

        viewModel.delete(full.items.first())
        val deleted = viewModel.nextMessage() as ChecklistMessage.Deleted
        assertTrue(viewModel.state(total = Limits.MAX_CHECKLIST_ITEMS - 1).canAdd)

        viewModel.undoDelete(deleted.deleted)
        assertEquals(deleted.deleted.item, viewModel.state(total = Limits.MAX_CHECKLIST_ITEMS).items.first())
    }

    @Test
    fun twoQuickDeletes_bothOfferUndo(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.add("A")
        viewModel.nextMessage()
        viewModel.add("B")
        viewModel.nextMessage()
        val (a, b) = viewModel.state(total = 2).items

        viewModel.delete(a)
        viewModel.delete(b)

        val first = viewModel.nextMessage() as ChecklistMessage.Deleted
        val second = viewModel.nextMessage() as ChecklistMessage.Deleted
        assertEquals(listOf("A", "B"), listOf(first.deleted.item.title, second.deleted.item.title))
    }
}
