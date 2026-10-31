package vn.edu.vlu.trippocket.domain

/**
 * One activity and when it starts, the only two things the ordering needs. Kept free of Room,
 * so the rule can be tested on plain Kotlin.
 */
data class TimedActivity(
    val activityId: String,
    /** Minute of the day (0..1439), floating device-local time. */
    val startTime: Int,
)

/**
 * Order of the activities inside one day (docs/ARCHITECTURE.md "Thứ tự hoạt động trong ngày").
 *
 * `orderIndex` is the display order. Creating an activity or changing its time or day sorts
 * that day by start time and numbers `orderIndex` 0..n-1 again; renaming, editing the note or
 * the cost keeps the order. Dragging (I3) only rewrites `orderIndex`.
 */
object ItineraryOrdering {

    /**
     * [activities] in display order: the list must already be in the current order
     * (`orderIndex` ascending), which the repository gets from Room.
     *
     * The sort is stable, so activities that start at the same minute keep their relative
     * order. That is why a new activity is appended at the end of its day before sorting: at an
     * occupied time it stays after the ones already there, and editing a time never shuffles
     * the other activities of that slot.
     */
    fun inDisplayOrder(activities: List<TimedActivity>): List<TimedActivity> =
        activities.sortedBy { it.startTime }
}
