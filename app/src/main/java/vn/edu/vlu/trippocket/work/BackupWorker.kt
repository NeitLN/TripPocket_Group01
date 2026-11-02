package vn.edu.vlu.trippocket.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import vn.edu.vlu.trippocket.TripPocketApp
import vn.edu.vlu.trippocket.data.repository.BackupOutcome
import java.util.concurrent.TimeUnit

/**
 * Uploads the PENDING trips of one account (A3). Runs only with a network; transient failures
 * come back with exponential backoff. All the rules live in BackupRepository.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_OWNER_UID) ?: return Result.success()
        val backup = (applicationContext as TripPocketApp).container.backupRepository
        return when (backup.backUp(uid)) {
            BackupOutcome.DONE, BackupOutcome.ACCOUNT_CHANGED -> Result.success()
            BackupOutcome.RETRY_LATER -> Result.retry()
        }
    }

    companion object {
        const val KEY_OWNER_UID = "owner_uid"
    }
}

/**
 * One unique queue per account, "trip-backup:<uid>", with APPEND_OR_REPLACE: a change made while
 * a run is in progress queues another run after it, and one account retrying never blocks
 * another account's backups (docs/ARCHITECTURE.md).
 */
class WorkManagerBackupScheduler(private val context: Context) : BackupScheduler {

    override fun request(ownerUid: String) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(BackupWorker.KEY_OWNER_UID to ownerUid))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("trip-backup:$ownerUid", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
