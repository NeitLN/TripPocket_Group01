package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import vn.edu.vlu.trippocket.data.local.entity.ExchangeRateCacheEntity

/**
 * The last rates ExchangeRate-API sent (A1). Not part of the backup, so writing it does not go
 * through TripWriter and never marks a trip PENDING.
 */
@Dao
interface ExchangeRateCacheDao {
    @Query("SELECT * FROM exchange_rate_cache WHERE baseCurrency = :baseCurrency")
    suspend fun get(baseCurrency: String): ExchangeRateCacheEntity?

    @Upsert
    suspend fun upsert(row: ExchangeRateCacheEntity)
}
