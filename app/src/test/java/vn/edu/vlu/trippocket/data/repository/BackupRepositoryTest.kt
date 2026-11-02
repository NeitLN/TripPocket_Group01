package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.remote.firebase.BackupRemote
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot
import java.io.IOException

/** The backup protocol of docs/ARCHITECTURE.md (A3), on Room in memory and a fake Firestore. */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private class FakeRemote : BackupRemote {
        val documents = mutableMapOf<String, TripSnapshot>()
        val uploads = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        var failNext = false

        /** Runs inside the next upload, before it "reaches the server". */
        var duringUpload: (suspend () -> Unit)? = null

        override suspend fun upload(snapshot: TripSnapshot) {
            if (failNext) {
                failNext = false
                throw IOException("offline")
            }
            duringUpload?.let { duringUpload = null; it() }
            uploads += snapshot.tripId
            documents["${snapshot.ownerUid}/${snapshot.tripId}"] = snapshot
        }

        override suspend fun delete(ownerUid: String, tripId: String) {
            if (failNext) {
                failNext = false
                throw IOException("offline")
            }
            deletes += tripId
            documents.remove("$ownerUid/$tripId")
        }
    }

    private val t = TestDatabase()
    private val remote = FakeRemote()
    private val auth = FakeAuthRepository(OWNER)
    private val requested = mutableListOf<String>()
    private val backup = BackupRepository(t.db, remote, auth, { requested += it })

    @After
    fun tearDown() = t.close()

    private suspend fun pendingTrip(id: String, owner: String = OWNER) =
        t.db.tripDao().insert(t.trip(id, owner).copy(syncStatus = SyncStatus.PENDING))

    private suspend fun status(id: String) = t.db.backupDao().trip(id)?.syncStatus

    @Test
    fun aPendingTrip_isUploadedWithItsChildRows_andBecomesSynced(): Unit = runBlocking {
        pendingTrip("t1")
        t.writer.write("t1") {
            t.db.tripMemberDao().insert(TripMemberEntity("an", "t1", "An", 0))
            t.db.tripMemberDao().insert(TripMemberEntity("binh", "t1", "Bình", 1))
            t.db.expenseDao().insert(
                ExpenseEntity("e1", "t1", "Lẩu", 300_000, "an", ExpenseCategory.FOOD, SplitType.EQUAL, 20_400, 1)
            )
            t.db.expenseDao().insertShares(listOf(ExpenseShareEntity("e1", "an", 150_000), ExpenseShareEntity("e1", "binh", 150_000)))
            t.db.checklistDao().insert(ChecklistItemEntity("c1", "t1", "Áo khoác", isDone = true))
        }

        assertEquals(BackupOutcome.DONE, backup.backUp(OWNER))

        val document = remote.documents.getValue("$OWNER/t1")
        assertEquals(listOf("An", "Bình"), document.members.map { it.name })
        assertEquals(mapOf("an" to 150_000L, "binh" to 150_000L), document.expenses.single().shares)
        assertEquals("FOOD", document.expenses.single().category)
        assertEquals(true, document.checklist.single().isDone)
        assertEquals(t.db.backupDao().trip("t1")!!.localRevision, document.localRevision)
        assertEquals(SyncStatus.SYNCED, status("t1"))
    }

    @Test
    fun anEditDuringTheUpload_keepsThePendingState_andTheNewVersionIsUploadedToo(): Unit = runBlocking {
        pendingTrip("t1")
        remote.duringUpload = {
            t.writer.write("t1") { t.db.checklistDao().insert(ChecklistItemEntity("c1", "t1", "Sạc", isDone = false)) }
        }

        assertEquals(BackupOutcome.DONE, backup.backUp(OWNER))

        assertEquals(listOf("t1", "t1"), remote.uploads)
        assertEquals("Sạc", remote.documents.getValue("$OWNER/t1").checklist.single().title)
        assertEquals(SyncStatus.SYNCED, status("t1"))
    }

    @Test
    fun aNetworkError_leavesTheTripPending_andAsksToRetry(): Unit = runBlocking {
        pendingTrip("t1")
        remote.failNext = true

        assertEquals(BackupOutcome.RETRY_LATER, backup.backUp(OWNER))
        assertEquals(SyncStatus.PENDING, status("t1"))

        assertEquals(BackupOutcome.DONE, backup.backUp(OWNER))
        assertEquals(SyncStatus.SYNCED, status("t1"))
    }

    @Test
    fun aSnapshotOfOneMiBOrMore_isFailed_withoutUpload_andTheNextTripStillGoes(): Unit = runBlocking {
        pendingTrip("big")
        t.writer.write("big") {
            repeat(15) { i ->
                t.db.checklistDao().insert(ChecklistItemEntity("c$i", "big", "x".repeat(80_000), isDone = false))
            }
        }
        pendingTrip("small")

        assertEquals(BackupOutcome.DONE, backup.backUp(OWNER))

        assertEquals(SyncStatus.FAILED, status("big"))
        assertEquals(listOf("small"), remote.uploads)
        assertEquals(SyncStatus.SYNCED, status("small"))
    }

    @Test
    fun retry_movesAFailedTripBackToPending_andQueuesTheJob(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1").copy(syncStatus = SyncStatus.FAILED))
        t.db.tripDao().insert(t.trip("other", owner = "uid-b").copy(syncStatus = SyncStatus.FAILED))

        backup.retry("t1")
        backup.retry("other") // not the signed-in account's trip

        assertEquals(SyncStatus.PENDING, status("t1"))
        assertEquals(SyncStatus.FAILED, status("other"))
        assertEquals(listOf(OWNER), requested)
    }

    @Test
    fun aDeletedTrip_hasItsDocumentRemovedFirst_thenIsPurged(): Unit = runBlocking {
        pendingTrip("t1")
        backup.backUp(OWNER)
        t.writer.write("t1") {
            t.db.tripDao().update(t.db.backupDao().trip("t1")!!.copy(isDeleted = true))
        }

        assertEquals(BackupOutcome.DONE, backup.backUp(OWNER))

        assertEquals(listOf("t1"), remote.deletes)
        assertTrue(remote.documents.isEmpty())
        assertNull(t.db.backupDao().trip("t1"))
    }

    @Test
    fun aDeletedTrip_whoseRemoteDeleteFails_staysOnThePhone(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1", deleted = true).copy(syncStatus = SyncStatus.PENDING))
        remote.failNext = true

        assertEquals(BackupOutcome.RETRY_LATER, backup.backUp(OWNER))
        assertEquals(SyncStatus.PENDING, status("t1"))
    }

    @Test
    fun anotherAccountSignedIn_stopsTheRun_andLeavesItsTripsPending(): Unit = runBlocking {
        pendingTrip("t1")
        auth.setUid("uid-b")

        assertEquals(BackupOutcome.ACCOUNT_CHANGED, backup.backUp(OWNER))

        assertTrue(remote.uploads.isEmpty())
        assertEquals(SyncStatus.PENDING, status("t1"))
    }

    @Test
    fun onlyTheAccountsOwnPendingTripsAreUploaded_andSyncedOnesAreLeftAlone(): Unit = runBlocking {
        pendingTrip("mine")
        pendingTrip("theirs", owner = "uid-b")
        t.db.tripDao().insert(t.trip("alreadySynced"))

        backup.backUp(OWNER)

        assertEquals(listOf("mine"), remote.uploads)
        assertEquals(SyncStatus.PENDING, status("theirs"))
    }

    @Test
    fun resumePending_queuesTheJobOnlyWhenSomethingIsPending(): Unit = runBlocking {
        backup.resumePending(OWNER)
        assertTrue(requested.isEmpty())

        pendingTrip("t1")
        backup.resumePending(OWNER)
        assertEquals(listOf(OWNER), requested)
    }
}
