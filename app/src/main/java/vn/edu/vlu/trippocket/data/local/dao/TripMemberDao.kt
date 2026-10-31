package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity

/** Number of members of one trip, for the trip cards. */
data class TripMemberCount(val tripId: String, val count: Int)

/**
 * Owner: Minh. Basic queries from F0; add the ones your screens need.
 * Writes must go through TripWriter.write(tripId) { ... } so the trip is marked for backup.
 */
@Dao
interface TripMemberDao {

    @Query("SELECT * FROM trip_member WHERE tripId = :tripId ORDER BY orderIndex")
    fun observeByTrip(tripId: String): Flow<List<TripMemberEntity>>

    @Query("SELECT memberId FROM trip_member WHERE tripId = :tripId")
    suspend fun idsByTrip(tripId: String): List<String>

    @Query("SELECT tripId, COUNT(*) AS count FROM trip_member GROUP BY tripId")
    fun observeCountPerTrip(): Flow<List<TripMemberCount>>

    @Query("SELECT COUNT(*) FROM trip_member WHERE tripId = :tripId")
    suspend fun countByTrip(tripId: String): Int

    /** E1: null when the member is not in that trip. */
    @Query("SELECT * FROM trip_member WHERE memberId = :memberId AND tripId = :tripId")
    suspend fun get(tripId: String, memberId: String): TripMemberEntity?

    /** E1: the highest fixed order in the trip; null when it has no members yet. */
    @Query("SELECT MAX(orderIndex) FROM trip_member WHERE tripId = :tripId")
    suspend fun maxOrderIndex(tripId: String): Int?

    @Insert
    suspend fun insert(row: TripMemberEntity)

    @Update
    suspend fun update(row: TripMemberEntity)

    @Delete
    suspend fun delete(row: TripMemberEntity)
}
