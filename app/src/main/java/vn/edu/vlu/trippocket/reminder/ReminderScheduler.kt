package vn.edu.vlu.trippocket.reminder

/** One alarm to set: show the reminder for [activityId] at [triggerAtMillis] (epoch millis). */
data class ScheduledReminder(
    val activityId: String,
    val tripId: String,
    val title: String,
    val triggerAtMillis: Long,
)

/**
 * Turns activity alarms on and off. The AlarmManager version comes with I4 (Bảo): exact when
 * the app may schedule exact alarms, inexact otherwise, immutable PendingIntent with a request
 * code taken from the activityId so [cancel] finds the same alarm. I5 uses it to rebuild every
 * alarm after a reboot, a time zone or clock change, and on sign-in/sign-out. Tests use a fake.
 */
interface ReminderScheduler {
    fun schedule(reminder: ScheduledReminder)

    /** No-op when no alarm is set for [activityId]. */
    fun cancel(activityId: String)
}
