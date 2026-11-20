package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import vn.edu.vlu.trippocket.data.local.entity.PlaceInfoCacheEntity

/** The last Wikipedia summary of each page title (A1). Public data: not backed up, not per account. */
@Dao
interface PlaceInfoCacheDao {
    @Query("SELECT * FROM place_info_cache WHERE page = :page")
    suspend fun get(page: String): PlaceInfoCacheEntity?

    @Upsert
    suspend fun upsert(row: PlaceInfoCacheEntity)
}
