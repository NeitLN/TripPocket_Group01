package vn.edu.vlu.trippocket.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import vn.edu.vlu.trippocket.data.remote.firebase.ChecklistItemSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotCodecTest.Companion.fullSnapshot
import vn.edu.vlu.trippocket.domain.Limits

/** A4: a backup snapshot is checked whole before restore writes any of it (proposal 6.2). */
class TripSnapshotValidatorTest {

    private fun problem(s: TripSnapshot, documentId: String = s.tripId, owner: String = "uid-a") =
        TripSnapshotValidator.problem(s, documentId, owner)

    private val ok = fullSnapshot()

    @Test
    fun aFullValidTrip_passes() = assertNull(problem(ok))

    @Test
    fun theTripAtEveryCap_passes() {
        val atCap = ok.copy(checklist = List(Limits.MAX_CHECKLIST_ITEMS) { ChecklistItemSnapshot("c$it", "x".repeat(80), false) })
        assertNull(problem(atCap))
    }

    @Test
    fun oneOverACap_isRejected() {
        val over = ok.copy(checklist = List(Limits.MAX_CHECKLIST_ITEMS + 1) { ChecklistItemSnapshot("c$it", "x", false) })
        assertEquals("too many checklist items", problem(over))
    }

    @Test
    fun anotherAccountsDocument_orAMovedDocument_isRejected() {
        assertEquals("belongs to another account", problem(ok, owner = "uid-b"))
        assertEquals("tripId does not match the document", problem(ok, documentId = "other"))
    }

    @Test
    fun textOverItsLength_isRejected() {
        assertNotNull(problem(ok.copy(name = "x".repeat(Limits.MAX_TEXT + 1))))
        assertNotNull(problem(ok.copy(name = "  ")))
        val longNote = ok.activities.map { it.copy(note = "x".repeat(Limits.MAX_NOTE + 1)) }
        assertEquals("activity", problem(ok.copy(activities = longNote)))
    }

    @Test
    fun datesAndTimes_outsideTheTrip_areRejected() {
        assertEquals("dates reversed", problem(ok.copy(endDate = ok.startDate - 1)))
        // 3-day trip: day indexes 0..2, minutes 0..1439.
        assertEquals("activity", problem(ok.copy(activities = ok.activities.map { it.copy(dayIndex = 3) })))
        assertEquals("activity", problem(ok.copy(activities = ok.activities.map { it.copy(startTime = 1440) })))
    }

    @Test
    fun anExpense_naming_aStranger_orNotAddingUp_isRejected() {
        val expense = ok.expenses.single()
        assertEquals("expense", problem(ok.copy(expenses = listOf(expense.copy(payerId = "nobody")))))
        assertEquals("expense", problem(ok.copy(expenses = listOf(expense.copy(shares = expense.shares + ("nobody" to 0L))))))
        assertEquals("expense", problem(ok.copy(expenses = listOf(expense.copy(amount = expense.amount + 1)))))
        assertEquals("expense", problem(ok.copy(expenses = listOf(expense.copy(category = "CASINO")))))
    }

    @Test
    fun aRepeatedId_isRejected() {
        val twice = ok.copy(checklist = ok.checklist + ok.checklist)
        assertEquals("blank or repeated id", problem(twice))
    }
}
