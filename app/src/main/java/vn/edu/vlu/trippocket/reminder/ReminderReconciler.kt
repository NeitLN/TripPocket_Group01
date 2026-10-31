package vn.edu.vlu.trippocket.reminder

import vn.edu.vlu.trippocket.data.local.dao.ActivityDao
import vn.edu.vlu.trippocket.data.local.dao.ReminderCandidate
import vn.edu.vlu.trippocket.domain.ReminderTime
import java.time.ZoneId

/**
 * I5 (proposal 3.2, docs/ARCHITECTURE.md "Thời gian"): rebuilds every reminder alarm from Room.
 *
 * Called after a reboot, a time zone change or a clock change (BootReceiver), and by the
 * Auth/Trips module on sign-in, sign-out, restore and when a trip's dates change.
 * Alarms do not survive a reboot, and times are floating local time ("09:00 on day 2" is
 * 09:00 wherever the phone is now), so every alarm is worked out again from the current zone.
 */
class ReminderReconciler(
    private val activityDao: ActivityDao,
    private val scheduler: ReminderScheduler,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Cancels the alarm of every activity with a reminder, then sets only the upcoming ones of
     * [uid]: no alarms for another account, for deleted trips, or for times already past.
     * [uid] null (signed out) leaves no alarm at all. Returns the number of alarms set.
     */
    suspend fun reconcile(uid: String?): Int {
        val candidates = activityDao.reminderCandidates()
        candidates.forEach { scheduler.cancel(it.activityId) }
        if (uid == null) return 0

        val zone = zone()
        val now = now()
        val upcoming = candidates
            .filter { it.ownerUid == uid && !it.isDeleted }
            .map { it.toReminder(zone) }
            .filter { it.triggerAtMillis > now }
        upcoming.forEach(scheduler::schedule)
        return upcoming.size
    }

    /**
     * I4: brings the alarm of one activity in line with Room after it was added, edited or
     * deleted. The old alarm is always cancelled; a new one is set only when the activity still
     * has a reminder, belongs to [uid], its trip is not deleted and the time is still ahead.
     * Returns true when an alarm is set.
     */
    suspend fun syncActivity(activityId: String, uid: String?): Boolean {
        scheduler.cancel(activityId)
        val candidate = activityDao.reminderCandidate(activityId) ?: return false
        if (uid == null || candidate.ownerUid != uid || candidate.isDeleted) return false
        val reminder = candidate.toReminder(zone())
        if (reminder.triggerAtMillis <= now()) return false
        scheduler.schedule(reminder)
        return true
    }

    private fun ReminderCandidate.toReminder(zone: ZoneId) = ScheduledReminder(
        activityId = activityId,
        tripId = tripId,
        title = title,
        triggerAtMillis = ReminderTime.triggerAtMillis(startDate, dayIndex, startTime, reminderMinutesBefore, zone),
    )
}
