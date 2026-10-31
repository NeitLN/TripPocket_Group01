package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_place",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["tripId"], ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class SavedPlaceEntity(
    @PrimaryKey val placeId: String,
    val tripId: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val note: String,
)
