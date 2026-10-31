package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_member",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["tripId"], ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class TripMemberEntity(
    @PrimaryKey val memberId: String,
    val tripId: String,
    val name: String,
    /** Fixed order used to hand out leftover dong in an equal split. */
    val orderIndex: Int,
)
