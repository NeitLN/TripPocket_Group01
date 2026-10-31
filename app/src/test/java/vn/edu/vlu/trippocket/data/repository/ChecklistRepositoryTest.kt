package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.model.ChecklistItem
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits

/** Packing checklist on a real (in-memory) Room database (I2). */
@RunWith(RobolectricTestRunner::class)
class ChecklistRepositoryTest {

    private val t = TestDatabase()
    private var nextId = 0
    private val repository = ChecklistRepository(t.db.checklistDao(), t.writer) { "c${++nextId}" }

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.tripDao().insert(t.trip("t2"))
    }

    @After
    fun tearDown() = t.close()

    private suspend fun items(tripId: String = "t1") = repository.observe(tripId).first()
    private suspend fun revision(tripId: String = "t1") = t.db.tripDao().observeTrip(tripId, OWNER).first()!!.localRevision

    private inline fun expect(problem: ChecklistProblem, block: () -> Unit) {
        try {
            block()
            fail("Expected $problem")
        } catch (e: InvalidChecklistItemException) {
            assertEquals(problem, e.problem)
        }
    }

    @Test
    fun add_trimsTheTitle_keepsOrder_andMarksTheTripOnce(): Unit = runBlocking {
        val before = revision()
        repository.add("t1", "  Áo khoác ấm  ")
        repository.add("t1", "Sạc dự phòng")

        assertEquals(listOf("Áo khoác ấm", "Sạc dự phòng"), items().map { it.title })
        assertTrue(items().none { it.isDone })
        assertEquals(before + 2, revision())
    }

    @Test
    fun blankOrTooLongTitles_areRejected_withoutWriting(): Unit = runBlocking {
        val before = revision()
        expect(ChecklistProblem.BLANK) { repository.add("t1", "   ") }
        expect(ChecklistProblem.TOO_LONG) { repository.add("t1", "a".repeat(Limits.MAX_TEXT + 1)) }
        repository.add("t1", "a".repeat(Limits.MAX_TEXT))

        assertEquals(1, items().size)
        assertEquals(before + 1, revision())
    }

    @Test
    fun theHundredAndFirstItem_isRejected_andTheRevisionStays(): Unit = runBlocking {
        repeat(Limits.MAX_CHECKLIST_ITEMS) { i ->
            t.db.checklistDao().insert(ChecklistItemEntity("seed$i", "t1", "Item $i", isDone = false))
        }
        val before = revision()
        try {
            repository.add("t1", "One too many")
            fail("Expected LimitExceededException")
        } catch (expected: LimitExceededException) {
        }
        assertEquals(Limits.MAX_CHECKLIST_ITEMS, items().size)
        assertEquals(before, revision())
    }

    @Test
    fun tick_rename_andNoOpChanges(): Unit = runBlocking {
        val id = repository.add("t1", "Kem chống nắng")
        val afterAdd = revision()

        repository.setDone("t1", id, done = true)
        repository.setDone("t1", id, done = true) // already done: nothing written
        repository.rename("t1", id, "Kem chống nắng SPF50")
        repository.rename("t1", id, " Kem chống nắng SPF50 ") // same after trimming: nothing written

        assertEquals(ChecklistItem(id, "t1", "Kem chống nắng SPF50", isDone = true), items().single())
        assertEquals(afterAdd + 2, revision())
    }

    @Test
    fun anItemOfAnotherTrip_cannotBeChangedThroughThisTrip(): Unit = runBlocking {
        val id = repository.add("t2", "Hộ chiếu")
        expect(ChecklistProblem.NOT_FOUND) { repository.setDone("t1", id, done = true) }
        expect(ChecklistProblem.NOT_FOUND) { repository.delete("t1", id) }
        assertEquals(false, items("t2").single().isDone)
    }

    @Test
    fun delete_thenRestore_bringsTheSameItemBack_andEachMarksTheTripOnce(): Unit = runBlocking {
        val id = repository.add("t1", "Ô gấp")
        repository.setDone("t1", id, done = true)
        val beforeDelete = revision()

        val removed = repository.delete("t1", id)
        assertTrue(items().isEmpty())
        assertEquals(beforeDelete + 1, revision())

        repository.restore(removed)
        assertEquals(listOf(ChecklistItem(id, "t1", "Ô gấp", isDone = true)), items())
        assertEquals(beforeDelete + 2, revision())
    }

    @Test
    fun undo_putsTheItemBackInItsOldPlace(): Unit = runBlocking {
        repository.add("t1", "Áo khoác")
        val middle = repository.add("t1", "Kem chống nắng")
        repository.add("t1", "Sạc dự phòng")

        repository.restore(repository.delete("t1", middle))

        assertEquals(listOf("Áo khoác", "Kem chống nắng", "Sạc dự phòng"), items().map { it.title })
    }

    @Test
    fun undo_whenANewItemTookThePlace_goesToTheEnd(): Unit = runBlocking {
        repository.add("t1", "Áo khoác")
        val last = repository.add("t1", "Kem chống nắng")
        val removed = repository.delete("t1", last)
        repository.add("t1", "Sạc dự phòng") // may reuse the freed rowid

        repository.restore(removed)

        assertEquals(setOf("Áo khoác", "Sạc dự phòng", "Kem chống nắng"), items().map { it.title }.toSet())
        assertEquals(3, items().size)
    }

    @Test
    fun aQuickTickThenUntick_endsUnticked(): Unit = runBlocking {
        val id = repository.add("t1", "Hộ chiếu")
        coroutineScope {
            launch { repository.setDone("t1", id, done = true) }
            launch { repository.setDone("t1", id, done = false) }
        }
        assertEquals(false, items().single().isDone)
    }
}
