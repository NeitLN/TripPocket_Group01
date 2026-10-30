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
