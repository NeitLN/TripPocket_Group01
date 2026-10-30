package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity

/**
 * Owner: Duy Anh. Basic queries from F0; add the ones your screens need.
 * Writes must go through TripWriter.write(tripId) { ... } so the trip is marked for backup.
 */
@Dao
interface SavedPlaceDao {

    @Query("SELECT * FROM saved_place WHERE tripId = :tripId ORDER BY rowid")
    fun observeByTrip(tripId: String): Flow<List<SavedPlaceEntity>>

    @Query("SELECT COUNT(*) FROM saved_place WHERE tripId = :tripId")
    suspend fun countByTrip(tripId: String): Int

    @Insert
    suspend fun insert(row: SavedPlaceEntity)

    @Update
    suspend fun update(row: SavedPlaceEntity)

    @Delete
    suspend fun delete(row: SavedPlaceEntity)
}
