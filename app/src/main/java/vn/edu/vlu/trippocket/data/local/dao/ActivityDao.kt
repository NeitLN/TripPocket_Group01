package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity

/**
 * Owner: Bảo. Basic queries from F0; add the ones your screens need.
 * Writes must go through TripWriter.write(tripId) { ... } so the trip is marked for backup.
 */
@Dao
interface ActivityDao {

    @Query("SELECT * FROM activity WHERE tripId = :tripId ORDER BY dayIndex, orderIndex")
    fun observeByTrip(tripId: String): Flow<List<ActivityEntity>>

    @Query("SELECT COUNT(*) FROM activity WHERE tripId = :tripId")
    suspend fun countByTrip(tripId: String): Int

    @Insert
    suspend fun insert(row: ActivityEntity)

    @Update
    suspend fun update(row: ActivityEntity)

    @Delete
    suspend fun delete(row: ActivityEntity)
}
