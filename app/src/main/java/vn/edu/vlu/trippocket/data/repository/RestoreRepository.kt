package vn.edu.vlu.trippocket.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import vn.edu.vlu.trippocket.data.local.AppDatabase
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.remote.firebase.BackupRemote
import vn.edu.vlu.trippocket.data.remote.firebase.InvalidSnapshotException
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotCodec
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotMapper
import vn.edu.vlu.trippocket.work.BackupScheduler

/** A trip that is on the phone and in the backup in two different versions: the user picks one. */
data class RestoreConflict(
    val backup: TripSnapshot,
    /** The phone's revision when the plan was made; the swap is skipped if it moved since. */
    val localRevision: Long,
    val localName: String,
    val localUpdatedAt: Long,
    /** Deleted on this phone, the deletion not backed up yet. */
    val localDeleted: Boolean,
)

/** What "Restore from backup" found, before anything is written. */
data class RestorePlan(
    val ownerUid: String,
    /** Only in the backup: added as they are. */
    val added: List<TripSnapshot>,
    val conflicts: List<RestoreConflict>,
    /** The same version on both sides: nothing to do. */
    val unchanged: Int,
    /** Damaged, over a limit, or naming a trip of another account: never written. */
    val rejected: Int,
) {
    val isEmpty get() = added.isEmpty() && conflicts.isEmpty() && unchanged == 0 && rejected == 0
}

data class RestoreResult(
    /** Added or replaced by the backup version. */
    val restored: Int,
    /** Conflicts where the phone version was kept (and queued to overwrite the backup). */
    val kept: Int,
    /** Edited on the phone while the user was choosing, so not replaced. */
    val changedMeanwhile: Int,
    val rejected: Int,
)

/** Another account is signed in now (or nobody is): restore does not touch anything. */
class AccountChangedException : Exception("The signed-in account changed")

/**
 * A4 (proposal 6.2, docs/tasks/A-auth-trips-backup.md): the manual "Restore from backup".
 *
 * [prepare] reads every backup document of the account, rejects the damaged ones and sorts the
 * rest; nothing is written. [apply] then runs under the account's lock (shared with the backup
 * job) and writes each trip with all its child rows in one transaction, keeping every UUID:
 * - a trip only in the backup is added;
 * - a conflict the user resolved for the backup is replaced, but only if the phone's revision
 *   did not move since [prepare];
 * - a conflict kept on the phone is marked PENDING, so the phone version overwrites the backup.
 * Restored trips are SYNCED at the backup's revision. Afterwards the reminder alarms are rebuilt
 * and [onRestored] gets the restored trip ids (the forecast cache is not in the backup).
 */
class RestoreRepository(
    private val db: AppDatabase,
    private val remote: BackupRemote,
    private val auth: AuthRepository,
    private val scheduler: BackupScheduler,
    private val reconcileReminders: suspend (uid: String?) -> Unit,
    private val onRestored: (tripIds: List<String>) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.backupDao()

    /** Throws an IOException when the backup cannot be read; the phone is never changed here. */
    suspend fun prepare(): RestorePlan {
        val uid = auth.currentUid.value ?: throw AccountChangedException()
        val added = mutableListOf<TripSnapshot>()
        val conflicts = mutableListOf<RestoreConflict>()
        var unchanged = 0
        var rejected = 0
        for (document in remote.fetchAll(uid)) {
            val snapshot = try {
                TripSnapshotCodec.fromMap(document.data)
            } catch (e: InvalidSnapshotException) {
                null
            }
            if (snapshot == null || TripSnapshotValidator.problem(snapshot, document.id, uid) != null) {
                rejected++
                continue
            }
            val local = dao.rowsOf(snapshot.tripId)
            when {
                local == null -> added += snapshot
                local.trip.ownerUid != uid -> rejected++
                !local.trip.isDeleted && TripSnapshotMapper.toSnapshot(local) == snapshot -> unchanged++
                else -> conflicts += RestoreConflict(
                    backup = snapshot,
                    localRevision = local.trip.localRevision,
                    localName = local.trip.name,
                    localUpdatedAt = local.trip.updatedAt,
                    localDeleted = local.trip.isDeleted,
                )
            }
        }
        return RestorePlan(uid, added, conflicts, unchanged, rejected)
    }

    /** [useBackupFor]: the trip ids of the conflicts to replace with the backup version. */
    suspend fun apply(plan: RestorePlan, useBackupFor: Set<String>): RestoreResult {
        val uid = plan.ownerUid
        val result = AccountLocks.withLock(uid) {
            if (auth.currentUid.value != uid) throw AccountChangedException()
            val restoredIds = mutableListOf<String>()
            var kept = 0
            var changed = 0
            var rejected = plan.rejected
            val toWrite = plan.added.map { it to null } +
                plan.conflicts.filter { it.backup.tripId in useBackupFor }.map { it.backup to it.localRevision }
            for ((snapshot, expectedRevision) in toWrite) {
                when (write(snapshot, expectedRevision)) {
                    Write.DONE -> restoredIds += snapshot.tripId
                    Write.CHANGED -> changed++
                    Write.FAILED -> rejected++
                }
            }
            for (conflict in plan.conflicts.filter { it.backup.tripId !in useBackupFor }) {
                keepPhoneVersion(conflict, uid)
                kept++
            }
            if (kept > 0) scheduler.request(uid)
            restoredIds to RestoreResult(restoredIds.size, kept, changed, rejected)
        }
        reconcileReminders(uid)
        onRestored(result.first)
        return result.second
    }

    private enum class Write { DONE, CHANGED, FAILED }

    /**
     * One transaction: the old rows go, the backup's rows come in. [expectedRevision] null means
     * the trip must still be missing from the phone. A row Room refuses (an id already used by
     * another trip) rolls the whole trip back; failures are caught outside the transaction.
     */
    private suspend fun write(s: TripSnapshot, expectedRevision: Long?): Write = try {
        db.withTransaction {
            val local = dao.trip(s.tripId)
            val stillAsPlanned = if (expectedRevision == null) {
                local == null
            } else {
                local != null && local.ownerUid == s.ownerUid && local.localRevision == expectedRevision
            }
            if (!stillAsPlanned) return@withTransaction Write.CHANGED
            if (local != null) db.tripDao().purge(s.tripId)
            insert(s)
            Write.DONE
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLException) {
        Write.FAILED
    }

    private suspend fun insert(s: TripSnapshot) {
        db.tripDao().insert(
            TripEntity(
                tripId = s.tripId,
                ownerUid = s.ownerUid,
                name = s.name,
                destination = s.destination,
                startDate = s.startDate,
                endDate = s.endDate,
                syncStatus = SyncStatus.SYNCED,
                localRevision = s.localRevision,
                isDeleted = false,
                updatedAt = s.updatedAt,
            )
        )
        s.members.forEach { db.tripMemberDao().insert(TripMemberEntity(it.memberId, s.tripId, it.name, it.orderIndex)) }
        s.activities.forEach {
            db.activityDao().insert(
                ActivityEntity(
                    it.activityId, s.tripId, it.dayIndex, it.title, it.place, it.startTime, it.estimatedCost,
                    it.note, it.orderIndex, it.reminderMinutesBefore,
                )
            )
        }
        // Checklist and places are listed in insertion order, so they come back in the same order.
        s.checklist.forEach { db.checklistDao().insert(ChecklistItemEntity(it.itemId, s.tripId, it.title, it.isDone)) }
        s.places.forEach {
            db.savedPlaceDao().insert(
                SavedPlaceEntity(it.placeId, s.tripId, it.name, it.country, it.latitude, it.longitude, it.note)
            )
        }
        s.expenses.forEach { expense ->
            db.expenseDao().insert(
                ExpenseEntity(
                    expense.expenseId, s.tripId, expense.title, expense.amount, expense.payerId,
                    ExpenseCategory.valueOf(expense.category), SplitType.valueOf(expense.splitType),
                    expense.spentAt, expense.createdAt,
                )
            )
            db.expenseDao().insertShares(expense.shares.map { (memberId, amount) -> ExpenseShareEntity(expense.expenseId, memberId, amount) })
        }
    }

    /** The phone version wins: PENDING with a new revision, unless it was edited (so PENDING) since. */
    private suspend fun keepPhoneVersion(conflict: RestoreConflict, uid: String) {
        db.withTransaction {
            val local = dao.trip(conflict.backup.tripId)
            if (local != null && local.ownerUid == uid && local.localRevision == conflict.localRevision) {
                db.tripDao().touch(local.tripId, now())
            }
        }
    }
}
