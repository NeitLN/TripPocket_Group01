package vn.edu.vlu.trippocket.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/** What the phone lets the reminders do right now (I4). */
enum class ReminderStatus {
    /** Notifications are allowed and alarms are exact: the reminder comes on time. */
    ON_TIME,

    /** Notifications are allowed but alarms are inexact: it may come up to about an hour late. */
    MAY_BE_LATE,

    /** Notifications are turned off (or POST_NOTIFICATIONS was denied): nothing will show. */
    BLOCKED,
}

/** Reads the notification and exact alarm permissions for the activity form. Owner: Bảo. */
object ReminderPermissions {

    fun status(context: Context): ReminderStatus = when {
        !NotificationManagerCompat.from(context).areNotificationsEnabled() -> ReminderStatus.BLOCKED
        needsNotificationPermission(context) -> ReminderStatus.BLOCKED
        !canScheduleExact(context) -> ReminderStatus.MAY_BE_LATE
        else -> ReminderStatus.ON_TIME
    }

    /** true on Android 13+ while POST_NOTIFICATIONS has not been granted. */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
}
