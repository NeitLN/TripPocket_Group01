package vn.edu.vlu.trippocket.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.edu.vlu.trippocket.data.local.AppDatabase
import vn.edu.vlu.trippocket.data.remote.firebase.BackupRejectedException
import vn.edu.vlu.trippocket.data.remote.firebase.BackupRemote
import vn.edu.vlu.trippocket.data.remote.firebase.FirestoreDocumentSize
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshotMapper
import vn.edu.vlu.trippocket.work.BackupScheduler
import java.util.concurrent.ConcurrentHashMap

enum class BackupOutcome {
    /** Nothing PENDING is left for the account. */
    DONE,

    /** A network or server error, or too many changes in one run: run again with backoff. */
    RETRY_LATER,

    /** Another account is signed in now: stop, its trips stay PENDING for when it returns. */
    ACCOUNT_CHANGED,
}

/**
 * One lock per account, shared by the backup job and the restore (A4), so an older snapshot
 * can never overwrite a newer one and a restore never races an upload (proposal 6.2).
 */
object AccountLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(ownerUid: String, block: suspend () -> T): T =
        locks.getOrPut(ownerUid) { Mutex() }.withLock { block() }
}

/**
 * The backup protocol of docs/ARCHITECTURE.md (A3), run by BackupWorker for one account:
 * take the oldest PENDING trip, read it and its revision in one transaction, upload it, and mark
 * it SYNCED only if the revision did not move meanwhile; repeat until nothing is PENDING.
 * - A deleted trip: its document is deleted first, then the rows are purged.
 * - A snapshot of 1 MiB or more becomes FAILED (the user trims it, then "Retry backup").
 * - Firestore refusing it for good (BackupRejectedException: PERMISSION_DENIED,
 *   INVALID_ARGUMENT, ...) also makes it FAILED, and the next trip goes on; unless the account
 *   signed out meanwhile, then it stays PENDING. A deleted trip that FAILED is retried at the
 *   next start-up or sign-in ([resumePending]).
 * - Any other error (network, timeout) leaves the trip PENDING; the job retries with
 *   exponential backoff.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val remote: BackupRemote,
    private val auth: AuthRepository,
    private val scheduler: BackupScheduler,
    private val maxTripsPerRun: Int = 50,
) {
    private val dao = db.backupDao()

    suspend fun backUp(ownerUid: String): BackupOutcome = AccountLocks.withLock(ownerUid) {
        repeat(maxTripsPerRun) {
            // Checked before every remote call: never upload or delete for a signed-out account.
            if (auth.currentUid.value != ownerUid) return@withLock BackupOutcome.ACCOUNT_CHANGED
            val tripId = dao.nextPending(ownerUid) ?: return@withLock BackupOutcome.DONE
            val rows = dao.rowsOf(tripId) ?: return@repeat
            val revision = rows.trip.localRevision
            try {
                if (rows.trip.isDeleted) {
                    remote.delete(ownerUid, tripId)
                    db.withTransaction {
                        if (dao.isTombstoneAt(tripId, revision) > 0) db.tripDao().purge(tripId)
                    }
                } else {
                    val snapshot = TripSnapshotMapper.toSnapshot(rows)
                    if (FirestoreDocumentSize.ofTripBackup(snapshot) >= FirestoreDocumentSize.MAX_BYTES) {
                        dao.markFailed(tripId, revision)
                    } else {
                        remote.upload(snapshot)
                        dao.markSynced(tripId, revision)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupRejectedException) {
                // Signed out (or switched) while the request was on its way: the rules refuse it
                // for that reason only, so the trip stays PENDING for the next sign-in.
                if (auth.currentUid.value != ownerUid) return@withLock BackupOutcome.ACCOUNT_CHANGED
                // Refused for good (rules, a bad document): FAILED, so it stops blocking the
                // account's other trips; "Retry backup" sends it again once the cause is fixed.
                dao.markFailed(tripId, revision)
            } catch (e: Exception) {
                return@withLock BackupOutcome.RETRY_LATER
            }
        }
        BackupOutcome.RETRY_LATER
    }

    /** "Retry backup" on a FAILED trip: back to PENDING and schedule the job. */
    suspend fun retry(tripId: String) {
        val uid = auth.currentUid.value ?: return
        if (dao.retryFailed(tripId, uid) > 0) scheduler.request(uid)
    }

    /**
     * At start-up and after sign-in: a trip left PENDING (app killed before upload) is resent.
     * A deleted trip that FAILED is tried again too: it has no card, so no "Retry backup" button,
     * and without this its document would stay in Firestore for good.
     */
    suspend fun resumePending(ownerUid: String) {
        dao.retryFailedDeletes(ownerUid)
        if (db.tripDao().countPending(ownerUid) > 0) scheduler.request(ownerUid)
    }
}
