package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One itinerary activity. Named ActivityEntity to avoid clashing with android.app.Activity. */
@Entity(
    tableName = "activity",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["tripId"], ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class ActivityEntity(
    @PrimaryKey val activityId: String,
    val tripId: String,
    /** 0 = the trip's start date. */
    val dayIndex: Int,
    val title: String,
    val place: String,
    /** Minute of the day (0..1439), floating device-local time (docs/ARCHITECTURE.md). */
    val startTime: Int,
    /** VND. */
    val estimatedCost: Long,
    val note: String,
    /** Display order inside the day. */
    val orderIndex: Int,
    /** null = no reminder. */
    val reminderMinutesBefore: Int?,
)
