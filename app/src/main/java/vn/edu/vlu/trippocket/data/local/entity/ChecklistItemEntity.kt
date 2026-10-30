package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_item",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["tripId"], ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class ChecklistItemEntity(
    @PrimaryKey val itemId: String,
    val tripId: String,
    val title: String,
    val isDone: Boolean,
)
