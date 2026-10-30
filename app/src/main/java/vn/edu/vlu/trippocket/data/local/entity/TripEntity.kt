package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import vn.edu.vlu.trippocket.data.model.SyncStatus

/**
 * The only table with sync fields (proposal 4.2). Every change to a child row bumps
 * [localRevision] and sets [syncStatus] to PENDING through TripWriter.
 */
@Entity(tableName = "trip", indices = [Index("ownerUid")])
data class TripEntity(
    @PrimaryKey val tripId: String,
    /** Firebase uid of the account that owns the trip. */
    val ownerUid: String,
    val name: String,
    val destination: String,
    /** Epoch day (LocalDate.toEpochDay()). */
    val startDate: Long,
    /** Epoch day, inclusive. */
    val endDate: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localRevision: Long = 0,
    /** Soft delete: hidden at once, purged after its backup document is deleted. */
    val isDeleted: Boolean = false,
    /** Epoch millis. */
    val updatedAt: Long,
)
