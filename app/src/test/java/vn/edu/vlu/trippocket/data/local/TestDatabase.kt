package vn.edu.vlu.trippocket.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.model.SyncStatus

/** Shared helpers for DAO tests: an in-memory database and a TripWriter that records backup requests. */
class TestDatabase {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).build()

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
