package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import vn.edu.vlu.trippocket.data.local.entity.TripIdeaCacheEntity

/**
 * The last Trip Ideas list MockAPI.io sent (A1). Public data, not part of any trip, so it is
 * neither backed up nor tied to an account.
 */
@Dao
interface TripIdeaCacheDao {
    @Query("SELECT * FROM trip_idea_cache ORDER BY position")
    suspend fun all(): List<TripIdeaCacheEntity>

    @Query("DELETE FROM trip_idea_cache")
    suspend fun clear()

    @Insert
    suspend fun insertAll(rows: List<TripIdeaCacheEntity>)

    /** The new list replaces the old one at once: an idea removed on the server leaves the copy too. */
    @Transaction
    suspend fun replaceAll(rows: List<TripIdeaCacheEntity>) {
        clear()
        insertAll(rows)
    }

    @Query("DELETE FROM trip_idea_cache WHERE id = :id")
    suspend fun delete(id: String)
}
