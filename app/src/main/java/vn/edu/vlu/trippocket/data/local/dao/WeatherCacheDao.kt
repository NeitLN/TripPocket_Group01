package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.WeatherCacheEntity

/**
 * Owner: Duy Anh. The forecast cache is not part of the backup, so writing it does not
 * go through TripWriter and does not mark the trip PENDING.
 */
@Dao
interface WeatherCacheDao {

    @Query("SELECT * FROM weather_cache WHERE placeId = :placeId")
    fun observe(placeId: String): Flow<WeatherCacheEntity?>

    @Upsert
    suspend fun upsert(row: WeatherCacheEntity)
}
