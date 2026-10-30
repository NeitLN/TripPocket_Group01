package vn.edu.vlu.trippocket.data.local

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.NOW
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits

/** The F0 gate: child writes mark the trip for backup exactly once, or not at all. */
@RunWith(RobolectricTestRunner::class)
class TripWriterTest {

    private val t = TestDatabase()
    private val tripDao = t.db.tripDao()

    @After
    fun tearDown() = t.close()

    private fun activity(id: String, tripId: String = "t1") =
        ActivityEntity(id, tripId, 0, "Chợ Đà Lạt", "Chợ", 9 * 60, 50_000, "", 0, null)

    private fun member(id: String) = TripMemberEntity(id, "t1", id, 0)

    @Test
    fun childWrites_markTripPendingAndBumpRevisionOnce() = runBlocking {
        tripDao.insert(t.trip("t1"))

        t.writer.write("t1") {
            t.db.activityDao().insert(activity("a1"))
            t.db.checklistDao().insert(ChecklistItemEntity("c1", "t1", "Áo khoác", false))
        }

        val trip = tripDao.observeTrip("t1", OWNER).first()!!
        assertEquals(SyncStatus.PENDING, trip.syncStatus)
        assertEquals(6, trip.localRevision)
        assertEquals(NOW, trip.updatedAt)
        assertEquals(listOf(OWNER), t.backupRequests)
    }

    @Test
    fun failedLimitCheck_rollsBackRowsAndRevision() = runBlocking {
        tripDao.insert(t.trip("t1"))

        try {
            t.writer.write("t1") {
                t.db.activityDao().insert(activity("a1"))
                Limits.check(currentCount = 1, max = 1)
            }
            fail("Expected LimitExceededException")
        } catch (expected: LimitExceededException) {
        }

        assertEquals(0, t.db.activityDao().countByTrip("t1"))
        val trip = tripDao.observeTrip("t1", OWNER).first()!!
        assertEquals(SyncStatus.SYNCED, trip.syncStatus)
        assertEquals(5, trip.localRevision)
        assertTrue(t.backupRequests.isEmpty())
    }

    @Test
    fun writeToMissingTrip_rollsBack() = runBlocking {
        tripDao.insert(t.trip("t1"))
        try {
            t.writer.write("missing") { t.db.activityDao().insert(activity("a1")) }
            fail("Expected the write to fail")
        } catch (expected: Exception) {
        }
        assertEquals(0, t.db.activityDao().countByTrip("t1"))
        assertTrue(t.backupRequests.isEmpty())
    }

    @Test
    fun tripQueries_hideOtherOwnersAndDeletedTrips() = runBlocking {
        tripDao.insert(t.trip("mine"))
        tripDao.insert(t.trip("theirs", owner = "uid-b"))
        tripDao.insert(t.trip("deleted", deleted = true))

        assertEquals(listOf("mine"), tripDao.observeTrips(OWNER).first().map { it.tripId })
        assertNull(tripDao.observeTrip("theirs", OWNER).first())
        assertNull(tripDao.observeTrip("deleted", OWNER).first())
    }

    @Test
    fun memberUsedByExpense_cannotBeDeleted_butWholeTripCanBePurged() = runBlocking {
        tripDao.insert(t.trip("t1"))
        val dao = t.db.expenseDao()
        t.writer.write("t1") {
            listOf("an", "binh", "chi").forEach { t.db.tripMemberDao().insert(member(it)) }
            dao.insert(
                ExpenseEntity("e1", "t1", "Lẩu", 300_000, "an", ExpenseCategory.FOOD, SplitType.EQUAL, 20_400, NOW)
            )
            dao.insertShares(listOf("an", "binh", "chi").map { ExpenseShareEntity("e1", it, 100_000) })
        }
        assertEquals(1, dao.countUsingMember("chi"))

        // Participant and payer are both protected.
        for (id in listOf("chi", "an")) {
            try {
                t.db.tripMemberDao().delete(member(id))
                fail("Deleting $id should violate the foreign key")
            } catch (expected: SQLiteConstraintException) {
            }
        }

        // A failed delete must not leave the connection unusable.
        t.writer.write("t1") { t.db.checklistDao().insert(ChecklistItemEntity("c1", "t1", "Ô", false)) }

        // Purging the trip removes expenses, shares and members in one transaction.
        tripDao.purge("t1")
        assertEquals(0, t.db.tripMemberDao().countByTrip("t1"))
        assertEquals(0, dao.countByTrip("t1"))
        assertNull(tripDao.ownerOf("t1"))
    }
}
