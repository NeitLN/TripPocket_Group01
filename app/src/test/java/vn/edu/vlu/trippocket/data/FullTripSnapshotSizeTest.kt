package vn.edu.vlu.trippocket.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.remote.firebase.ActivitySnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.ChecklistItemSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.ExpenseSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.FirestoreDocumentSize
import vn.edu.vlu.trippocket.data.remote.firebase.MemberSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.PlaceSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot
import vn.edu.vlu.trippocket.domain.Limits

/**
 * A5 (proposal 6.2, 6.4): a trip at every limit still fits in one Firestore document.
 * Owner: Minh. The snapshot is built by [snapshotOf] for now; switch it to the real A3
 * mapper (Tiến) once it lands, so the test measures what is really uploaded.
 */
@RunWith(RobolectricTestRunner::class)
class FullTripSnapshotSizeTest {

    private val t = TestDatabase()

    @After
    fun tearDown() = t.close()

    /** The example from Firestore's storage-size page: 147 bytes. */
    data class Task(val type: String, val isDone: Boolean, val priority: Long, val description: String)

    @Test
    fun sizeRules_matchTheFirestoreDocsExample() {
        val size = FirestoreDocumentSize.ofDocument(
            listOf("users", "jeff", "tasks", "my_task_id"),
            Task("Personal", isDone = false, priority = 1, description = "Learn Cloud Firestore"),
        )
        assertEquals(147, size)
        // Vietnamese letters with two marks take 3 bytes each.
        assertEquals(3 + 1, FirestoreDocumentSize.ofString("ệ"))
    }

    @Test
    fun fixture_isExactlyAtEveryLimit_withMaximumLengthText() {
        val f = FullTripFixture
        assertEquals(Limits.MAX_MEMBERS, f.members.size)
        assertEquals(Limits.MAX_ACTIVITIES, f.activities.size)
        assertEquals(Limits.MAX_CHECKLIST_ITEMS, f.checklist.size)
        assertEquals(Limits.MAX_PLACES, f.places.size)
        assertEquals(Limits.MAX_EXPENSES, f.expenses.size)
        assertEquals(Limits.MAX_EXPENSES * Limits.MAX_MEMBERS, f.shares.size)
        assertTrue(f.activities.all { it.title.length == Limits.MAX_TEXT && it.note.length == Limits.MAX_NOTE })
        assertTrue(f.places.all { it.name.length == Limits.MAX_TEXT && it.note.length == Limits.MAX_NOTE })
        // Every expense's shares add up to its amount.
        val sums = f.shares.groupBy { it.expenseId }.mapValues { (_, s) -> s.sumOf { it.shareAmount } }
        assertTrue(f.expenses.all { sums[it.expenseId] == it.amount })
    }

    @Test
    fun fixture_isValidRoomData() = runBlocking {
        FullTripFixture.insertInto(t.db)
        val id = FullTripFixture.TRIP_ID
        assertEquals(Limits.MAX_MEMBERS, t.db.tripMemberDao().countByTrip(id))
        assertEquals(Limits.MAX_ACTIVITIES, t.db.activityDao().countByTrip(id))
        assertEquals(Limits.MAX_CHECKLIST_ITEMS, t.db.checklistDao().countByTrip(id))
        assertEquals(Limits.MAX_PLACES, t.db.savedPlaceDao().countByTrip(id))
        assertEquals(Limits.MAX_EXPENSES, t.db.expenseDao().countByTrip(id))
    }

    @Test
    fun fullTripSnapshot_fitsInOneFirestoreDocument() {
        val size = FirestoreDocumentSize.ofTripBackup(snapshotOf(FullTripFixture))
        println("A5: full-trip snapshot = $size bytes, ${FirestoreDocumentSize.MAX_BYTES - size} bytes under 1 MiB")
        assertTrue("Snapshot is $size bytes", size < FirestoreDocumentSize.MAX_BYTES)
    }

    /** Stand-in for the A3 mapper: Room rows -> TripSnapshot, field for field. */
    private fun snapshotOf(f: FullTripFixture): TripSnapshot {
        val sharesByExpense = f.shares.groupBy { it.expenseId }
        return TripSnapshot(
            tripId = f.trip.tripId,
            ownerUid = f.trip.ownerUid,
            name = f.trip.name,
            destination = f.trip.destination,
            startDate = f.trip.startDate,
            endDate = f.trip.endDate,
            localRevision = f.trip.localRevision,
            updatedAt = f.trip.updatedAt,
            members = f.members.map { MemberSnapshot(it.memberId, it.name, it.orderIndex) },
            activities = f.activities.map {
                ActivitySnapshot(it.activityId, it.dayIndex, it.title, it.place, it.startTime, it.estimatedCost,
                    it.note, it.orderIndex, it.reminderMinutesBefore)
            },
            checklist = f.checklist.map { ChecklistItemSnapshot(it.itemId, it.title, it.isDone) },
            places = f.places.map { PlaceSnapshot(it.placeId, it.name, it.country, it.latitude, it.longitude, it.note) },
            expenses = f.expenses.map { e ->
                ExpenseSnapshot(e.expenseId, e.title, e.amount, e.payerId, e.category.name, e.splitType.name, e.spentAt,
                    e.createdAt, sharesByExpense.getValue(e.expenseId).associate { it.memberId to it.shareAmount })
            },
        )
    }
}
