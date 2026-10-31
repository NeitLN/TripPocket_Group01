package vn.edu.vlu.trippocket.ui.checklist

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

/** Checklist tab on a real (in-memory) Room database (I2). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChecklistViewModelTest {

    private val t = TestDatabase()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { t.db.tripDao().insert(t.trip("t1")) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private fun viewModel() = ChecklistViewModel(
        SavedStateHandle(mapOf(ARG_TRIP_ID to "t1")),
        ChecklistRepository(t.db.checklistDao(), t.writer),
    )

    private suspend fun ChecklistViewModel.state(total: Int) =
        withTimeout(5_000) { uiState.first { !it.loading && it.total == total } }

    private suspend fun ChecklistViewModel.nextMessage() =
        withTimeout(5_000) { message.first { it != null } }!!.also { messageShown() }

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

        viewModel.undoDelete(deleted.item)
        assertTrue(viewModel.state(total = Limits.MAX_CHECKLIST_ITEMS).items.any { it.itemId == deleted.item.itemId })
    }
}
