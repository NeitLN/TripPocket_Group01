package vn.edu.vlu.trippocket.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.model.SyncStatus
import java.util.concurrent.Executor

/**
 * Shared helpers for DAO tests: an in-memory database and a TripWriter that records backup requests.
 *
 * [roomExecutor]: optional executor for Room's queries and transactions. A test that tears down
 * Dispatchers.Main passes its own, so it can wait for Room's background work to finish first
 * (see WeatherViewModelTest). Null keeps Room's default executors.
 */
class TestDatabase(roomExecutor: Executor? = null) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).apply {
        if (roomExecutor != null) {
            setQueryExecutor(roomExecutor)
            setTransactionExecutor(roomExecutor)
        }
    }.build()

    /** Owner uids passed to the backup scheduler, in order. */
    val backupRequests = mutableListOf<String>()

    val writer = TripWriter(db, TripChangeTracker(db.tripDao()) { NOW }, backupRequests::add)

    fun trip(id: String, owner: String = OWNER, deleted: Boolean = false) = TripEntity(
        tripId = id,
        ownerUid = owner,
        name = "Đà Lạt",
        destination = "Lâm Đồng",
        startDate = 20_400,
        endDate = 20_403,
        syncStatus = SyncStatus.SYNCED,
        localRevision = 5,
        isDeleted = deleted,
        updatedAt = 0,
    )

    fun close() = db.close()

    companion object {
        const val OWNER = "uid-a"
        const val NOW = 1_000L
    }
}
