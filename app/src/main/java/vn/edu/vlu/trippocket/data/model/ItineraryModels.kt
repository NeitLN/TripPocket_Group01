package vn.edu.vlu.trippocket.data.model

import java.time.LocalTime

// Models of one module (owner: Bảo). Room entities live in data/local/entity.

data class ItineraryActivity(
    val activityId: String,
    val tripId: String,
    val dayIndex: Int,
    val title: String,
    val place: String,
    val startTime: LocalTime,
    val estimatedCost: Long,
    val note: String,
    val reminderMinutesBefore: Int?,
)

/**
 * What the add/edit form writes (I1). The repository trims the texts, checks the limits and
 * turns [startTime] into the minute of the day stored in Room.
 */
data class ActivityInput(
    val dayIndex: Int,
    val title: String,
    val place: String,
    val startTime: LocalTime,
    /** VND; 0 = free. */
    val estimatedCost: Long,
    val note: String,
    /** null = no reminder; I4 sets the alarm. */
    val reminderMinutesBefore: Int? = null,
)
