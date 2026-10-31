package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.TripEntity

/** Owner: Tiến. Every read is filtered by ownerUid (proposal 6.2, switching accounts). */
@Dao
interface TripDao {

    @Query("SELECT * FROM trip WHERE ownerUid = :ownerUid AND isDeleted = 0 ORDER BY startDate DESC")
    fun observeTrips(ownerUid: String): Flow<List<TripEntity>>

    /** null when the trip does not exist, belongs to another account or is deleted. */
    @Query("SELECT * FROM trip WHERE tripId = :tripId AND ownerUid = :ownerUid AND isDeleted = 0")
    fun observeTrip(tripId: String, ownerUid: String): Flow<TripEntity?>

    /** A2: the trip of [ownerUid] for the form; null when missing, deleted or not theirs. */
    @Query("SELECT * FROM trip WHERE tripId = :tripId AND ownerUid = :ownerUid AND isDeleted = 0")
    suspend fun get(tripId: String, ownerUid: String): TripEntity?

    @Query("SELECT ownerUid FROM trip WHERE tripId = :tripId")
    suspend fun ownerOf(tripId: String): String?

    @Insert
    suspend fun insert(trip: TripEntity)

    @Update
    suspend fun update(trip: TripEntity)

    /**
     * Marks the trip as changed: PENDING, revision + 1. Called by TripChangeTracker inside
     * the same transaction as the change. Returns the number of rows updated (0 = no such trip).
     */
    @Query(
        "UPDATE trip SET syncStatus = 'PENDING', localRevision = localRevision + 1, " +
            "updatedAt = :now WHERE tripId = :tripId"
    )
    suspend fun touch(tripId: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM trip WHERE ownerUid = :ownerUid AND syncStatus = 'PENDING'")
    suspend fun countPending(ownerUid: String): Int

    /**
     * Removes a trip and every child row for good (after its backup document is deleted).
     * Expenses go first: members are RESTRICTed while an expense still names them.
     */
    @Transaction
    suspend fun purge(tripId: String) {
        deleteExpensesOf(tripId)
        deleteTrip(tripId)
    }

    @Query("DELETE FROM expense WHERE tripId = :tripId")
    suspend fun deleteExpensesOf(tripId: String)

    @Query("DELETE FROM trip WHERE tripId = :tripId")
    suspend fun deleteTrip(tripId: String)
}
