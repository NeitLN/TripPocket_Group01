package vn.edu.vlu.trippocket.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService

/**
 * I4 (proposal 3.2): the [ReminderScheduler] on AlarmManager. Owner: Bảo.
 *
 * Exact when the app may schedule exact alarms (`canScheduleExactAlarms()`), inexact otherwise:
 * on Android 14+ a fresh install does not have the exact permission, so the inexact path is the
 * usual one and the reminder may come up to about an hour late ([ReminderPermissions]).
 *
 * The PendingIntent is immutable and is found again by the activity id (in the intent data and
 * in the request code), so [cancel] and a later [schedule] replace the same alarm.
 */
class AlarmReminderScheduler(context: Context) : ReminderScheduler {

    private val context = context.applicationContext
    private val alarmManager: AlarmManager = checkNotNull(this.context.getSystemService())

    override fun schedule(reminder: ScheduledReminder) {
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode(reminder.activityId),
            ReminderReceiver.newIntent(context, reminder),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
                return
            } catch (e: SecurityException) {
                // The exact permission was revoked between the check and the call: fall back.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
    }

    override fun cancel(activityId: String) {
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode(activityId),
            matchingIntent(context, activityId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        /** Stable for one activity across app restarts (String.hashCode is specified). */
        fun requestCode(activityId: String): Int = activityId.hashCode()

        /**
         * The part of the intent PendingIntent compares (component + data, not extras), so two
         * activities whose ids share a hash code still get separate alarms.
         */
        fun matchingIntent(context: Context, activityId: String): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .setData(Uri.fromParts("trippocket-reminder", activityId, null))
    }
}
