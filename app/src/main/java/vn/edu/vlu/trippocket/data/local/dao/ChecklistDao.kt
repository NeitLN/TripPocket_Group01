package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity

/**
 * Owner: Bảo. Basic queries from F0; add the ones your screens need.
 * Writes must go through TripWriter.write(tripId) { ... } so the trip is marked for backup.
 */
@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_item WHERE tripId = :tripId ORDER BY rowid")
    fun observeByTrip(tripId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_item WHERE itemId = :itemId")
    suspend fun get(itemId: String): ChecklistItemEntity?

    @Query("SELECT COUNT(*) FROM checklist_item WHERE tripId = :tripId")
    suspend fun countByTrip(tripId: String): Int

    @Insert
    suspend fun insert(row: ChecklistItemEntity)

    @Update
    suspend fun update(row: ChecklistItemEntity)

    @Delete
    suspend fun delete(row: ChecklistItemEntity)
}
