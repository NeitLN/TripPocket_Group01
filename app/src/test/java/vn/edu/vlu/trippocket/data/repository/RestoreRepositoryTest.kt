package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.remote.firebase.BackupDocument
import vn.edu.vlu.trippocket.data.remote.firebase.BackupRemote
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotCodec
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotCodecTest.Companion.fullSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotMapper
import vn.edu.vlu.trippocket.domain.Limits
import java.io.IOException

/** A4 restore (proposal 6.2, docs/tasks/A-auth-trips-backup.md) on Room in memory and a fake Firestore. */
@RunWith(RobolectricTestRunner::class)
class RestoreRepositoryTest {

    private class FakeRemote : BackupRemote {
        val documents = mutableListOf<BackupDocument>()
        var offline = false

        fun put(snapshot: TripSnapshot) {
            documents += BackupDocument(snapshot.tripId, TripSnapshotCodec.toMap(snapshot))
        }

        override suspend fun upload(snapshot: TripSnapshot) = error("restore never uploads")
        override suspend fun delete(ownerUid: String, tripId: String) = error("restore never deletes")
        override suspend fun fetchAll(ownerUid: String): List<BackupDocument> {
            if (offline) throw IOException("offline")
            return documents.toList()
        }
    }

    private val t = TestDatabase()
    private val remote = FakeRemote()
    private val auth = FakeAuthRepository(OWNER)
    private val backupRequests = mutableListOf<String>()
    private val reconciled = mutableListOf<String?>()
    private val refreshed = mutableListOf<String>()
    private val restore = RestoreRepository(
        t.db, remote, auth, { backupRequests += it },
        reconcileReminders = { reconciled += it },
        onRestored = { refreshed += it },
        now = { 9_999 },
    )

    @After
    fun tearDown() = t.close()

    private suspend fun onPhone(id: String) = t.db.backupDao().rowsOf(id)?.let(TripSnapshotMapper::toSnapshot)

    private suspend fun trip(id: String) = t.db.backupDao().trip(id)

    @Test
    fun aTripOnlyInTheBackup_comesBackWhole_withItsIds_synced(): Unit = runBlocking {
        val backup = fullSnapshot("t1")
        remote.put(backup)

        val plan = restore.prepare()
        val result = restore.apply(plan, emptySet())

        assertEquals(1, plan.added.size)
        assertEquals(RestoreResult(restored = 1, kept = 0, changedMeanwhile = 0, rejected = 0), result)
        assertEquals(backup, onPhone("t1"))
        assertEquals(SyncStatus.SYNCED, trip("t1")!!.syncStatus)
        assertEquals(listOf<String?>(OWNER), reconciled)
        assertEquals(listOf("t1"), refreshed)
        assertTrue(backupRequests.isEmpty())
    }

    @Test
    fun theSameVersionOnBothSides_isNotAskedAbout(): Unit = runBlocking {
        remote.put(fullSnapshot("t1"))
        restore.apply(restore.prepare(), emptySet())

        val again = restore.prepare()

        assertEquals(1, again.unchanged)
        assertTrue(again.added.isEmpty() && again.conflicts.isEmpty())
    }

    @Test
    fun aConflict_resolvedForTheBackup_replacesTheTripAndAllItsRows(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.checklistDao().insert(ChecklistItemEntity("phone-only", "t1", "Sạc dự phòng", isDone = false))
        val backup = fullSnapshot("t1", revision = 3)
        remote.put(backup)

        val plan = restore.prepare()
        assertEquals(listOf("t1"), plan.conflicts.map { it.backup.tripId })

        val result = restore.apply(plan, useBackupFor = setOf("t1"))

        assertEquals(1, result.restored)
        assertEquals(backup, onPhone("t1"))
        assertEquals(SyncStatus.SYNCED, trip("t1")!!.syncStatus)
    }

    @Test
    fun aConflict_keptOnThePhone_staysAsItIs_andIsQueuedToOverwriteTheBackup(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.checklistDao().insert(ChecklistItemEntity("phone-only", "t1", "Sạc dự phòng", isDone = false))
        val before = onPhone("t1")!!
        remote.put(fullSnapshot("t1"))

        val result = restore.apply(restore.prepare(), useBackupFor = emptySet())

        assertEquals(1, result.kept)
        assertEquals(before.checklist, onPhone("t1")!!.checklist)
        assertEquals(SyncStatus.PENDING, trip("t1")!!.syncStatus)
        assertEquals(before.localRevision + 1, trip("t1")!!.localRevision)
        assertEquals(listOf(OWNER), backupRequests)
    }

    @Test
    fun aTripDeletedOnThePhone_isAConflictToo_andTheBackupVersionBringsItBack(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1", deleted = true).copy(syncStatus = SyncStatus.PENDING))
        remote.put(fullSnapshot("t1"))

        val plan = restore.prepare()
        assertTrue(plan.conflicts.single().localDeleted)

        restore.apply(plan, useBackupFor = setOf("t1"))

        assertFalse(trip("t1")!!.isDeleted)
        assertEquals(fullSnapshot("t1"), onPhone("t1"))
    }

    @Test
    fun aTripEditedWhileTheUserChooses_isNotReplaced(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        remote.put(fullSnapshot("t1"))
        val plan = restore.prepare()

        t.writer.write("t1") { t.db.checklistDao().insert(ChecklistItemEntity("new", "t1", "Kem chống nắng", false)) }
        val result = restore.apply(plan, useBackupFor = setOf("t1"))

        assertEquals(1, result.changedMeanwhile)
        assertEquals(0, result.restored)
        assertEquals(listOf("Kem chống nắng"), onPhone("t1")!!.checklist.map { it.title })
    }

    @Test
    fun aSnapshotOverALimit_isRejected_thePhoneKeepsItsTrip_andTheOthersStillCome(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("big"))
        val before = onPhone("big")
        val tooMany = List(Limits.MAX_CHECKLIST_ITEMS + 1) {
            fullSnapshot("big").checklist.single().copy(itemId = "c$it")
        }
        remote.put(fullSnapshot("big").copy(checklist = tooMany))
        remote.put(fullSnapshot("ok"))

        val plan = restore.prepare()
        val result = restore.apply(plan, useBackupFor = setOf("big"))

        assertEquals(1, plan.rejected)
        assertEquals(RestoreResult(restored = 1, kept = 0, changedMeanwhile = 0, rejected = 1), result)
        assertEquals(before, onPhone("big"))
        assertEquals(fullSnapshot("ok"), onPhone("ok"))
    }

    @Test
    fun anUnreadableDocument_isRejected(): Unit = runBlocking {
        remote.documents += BackupDocument("t1", TripSnapshotCodec.toMap(fullSnapshot("t1")) - "members")

        val plan = restore.prepare()

        assertEquals(1, plan.rejected)
        assertTrue(plan.added.isEmpty())
    }

    @Test
    fun aTripIdUsedByAnotherAccountOnThisPhone_isNeverOverwritten(): Unit = runBlocking {
        t.db.tripDao().insert(t.trip("t1", owner = "uid-b"))
        remote.put(fullSnapshot("t1"))

        val plan = restore.prepare()
        restore.apply(plan, useBackupFor = setOf("t1"))

        assertEquals(1, plan.rejected)
        assertEquals("uid-b", trip("t1")!!.ownerUid)
    }

    @Test
    fun aRowRoomRefuses_rollsBackOnlyThatTrip(): Unit = runBlocking {
        // Another trip on the phone already uses the checklist id of the backup.
        t.db.tripDao().insert(t.trip("other"))
        t.db.checklistDao().insert(ChecklistItemEntity("t1-c1", "other", "Ô", isDone = false))
        remote.put(fullSnapshot("t1"))

        val result = restore.apply(restore.prepare(), emptySet())

        assertEquals(1, result.rejected)
        assertEquals(null, trip("t1"))
    }

    @Test(expected = IOException::class)
    fun offline_throws_andNothingIsWritten(): Unit = runBlocking {
        remote.offline = true
        try {
            restore.prepare()
        } finally {
            assertTrue(reconciled.isEmpty())
        }
    }

    @Test
    fun anotherAccountSignedInBeforeTheChoice_writesNothing(): Unit = runBlocking {
        remote.put(fullSnapshot("t1"))
        val plan = restore.prepare()
        auth.setUid("uid-b")

        val thrown = runCatching { restore.apply(plan, emptySet()) }.exceptionOrNull()

        assertTrue(thrown is AccountChangedException)
        assertEquals(null, trip("t1"))
    }
}
