package vn.edu.vlu.trippocket.data.local

import androidx.room.withTransaction
import vn.edu.vlu.trippocket.work.BackupScheduler

/**
 * The one way to change a trip or any of its child rows (docs/ARCHITECTURE.md):
 *
 * ```
 * tripWriter.write(tripId) {
 *     Limits.check(activityDao.countByTrip(tripId), Limits.MAX_ACTIVITIES)  // throws -> rollback
 *     activityDao.insert(row)
 * }
 * ```
 * The block, the revision bump and the ownership lookup share one transaction, so a failed
 * check leaves the revision untouched. The backup job is requested after the commit.
 */
class TripWriter(
    private val db: AppDatabase,
    private val tracker: TripChangeTracker,
    private val backupScheduler: BackupScheduler,
) {
    suspend fun <T> write(tripId: String, block: suspend () -> T): T {
        var ownerUid: String? = null
        val result = db.withTransaction {
            val value = block()
            tracker.touch(tripId)
            ownerUid = db.tripDao().ownerOf(tripId)
            value
        }
        ownerUid?.let(backupScheduler::request)
        return result
    }
}
