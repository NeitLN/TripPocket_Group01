package vn.edu.vlu.trippocket.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.remote.firebase.FirestoreDocumentSize
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotMapper
import vn.edu.vlu.trippocket.domain.Limits

/**
 * A5 (proposal 6.2, 6.4): a trip at every limit still fits in one Firestore document.
 * Owner: Minh. The fixture goes into Room and comes back out the way the backup job reads it
 * (BackupDao.rowsOf + TripSnapshotMapper, A3), so the size is the size of the real upload.
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
    fun fullTripSnapshot_fitsInOneFirestoreDocument() = runBlocking {
        FullTripFixture.insertInto(t.db)
        val rows = t.db.backupDao().rowsOf(FullTripFixture.TRIP_ID)!!
        val snapshot = TripSnapshotMapper.toSnapshot(rows)
        // Nothing was dropped on the way: every row of the fixture is in the snapshot.
        assertEquals(Limits.MAX_EXPENSES, snapshot.expenses.size)
        assertTrue(snapshot.expenses.all { it.shares.size == Limits.MAX_MEMBERS })
        assertEquals(Limits.MAX_ACTIVITIES, snapshot.activities.size)

        val size = FirestoreDocumentSize.ofTripBackup(snapshot)
        println("A5: full-trip snapshot = $size bytes, ${FirestoreDocumentSize.MAX_BYTES - size} bytes under 1 MiB")
        assertTrue("Snapshot is $size bytes", size < FirestoreDocumentSize.MAX_BYTES)
    }
}
