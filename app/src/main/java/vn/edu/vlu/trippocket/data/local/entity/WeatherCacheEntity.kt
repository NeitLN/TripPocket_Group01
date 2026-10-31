package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Last forecast of a saved place. Not backed up; fetched again after a restore. */
@Entity(
    tableName = "weather_cache",
    foreignKeys = [
        ForeignKey(SavedPlaceEntity::class, ["placeId"], ["placeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class WeatherCacheEntity(
    @PrimaryKey val placeId: String,
    val forecastJson: String,
    /** Epoch millis. */
    val fetchedAt: Long,
)
