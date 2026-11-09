package vn.edu.vlu.trippocket.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.TripPocketApp
import vn.edu.vlu.trippocket.data.local.dao.ActivityDao
import vn.edu.vlu.trippocket.data.local.dao.ReminderCandidate
import vn.edu.vlu.trippocket.ui.detail.TripDetailActivity
import vn.edu.vlu.trippocket.ui.trips.MainActivity

/**
 * Fired by AlarmManager ([AlarmReminderScheduler]) to show an activity reminder. Owner: Bảo (I4).
 *
 * The alarm only carries ids: the activity is read again from Room, and the notification is shown
 * only when it still has a reminder and its trip belongs to the account signed in now
 * (docs/ARCHITECTURE.md "Tài khoản, phiên và backup"), so a reminder never leaks across accounts.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID) ?: return
        val app = context.applicationContext as? TripPocketApp ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                withTimeoutOrNull(TIME_BUDGET_MS) {
                    val container = app.container
                    val uid = container.authRepository.currentUid.value
                    reminderToShow(container.database.activityDao(), activityId, uid)?.let { reminder ->
                        show(app, activityId, reminder.tripId, reminder.title)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    // Checked just before: when notifications are blocked the notification is skipped.
    @SuppressLint("MissingPermission")
    private fun show(context: Context, activityId: String, tripId: String, title: String) {
        if (ReminderPermissions.status(context) == ReminderStatus.BLOCKED) return
        createChannel(context)
        val open = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .addNextIntent(TripDetailActivity.newIntent(context, tripId))
            .getPendingIntent(
                AlarmReminderScheduler.requestCode(activityId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(activityId, NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_REMIND = "vn.edu.vlu.trippocket.action.REMIND"
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val CHANNEL_ID = "activity_reminders"
        private const val NOTIFICATION_ID = 1
        private const val TIME_BUDGET_MS = 8_000L

        /**
         * The reminder to show for [activityId], or null when the activity has no reminder any
         * more, its trip was deleted, or the trip belongs to another account than [uid] (null when
         * nobody is signed in).
         */
        suspend fun reminderToShow(dao: ActivityDao, activityId: String, uid: String?): ReminderCandidate? =
            dao.reminderCandidate(activityId)?.takeIf { !it.isDeleted && uid != null && it.ownerUid == uid }

        fun newIntent(context: Context, reminder: ScheduledReminder): Intent =
            AlarmReminderScheduler.matchingIntent(context, reminder.activityId)
                .putExtra(EXTRA_ACTIVITY_ID, reminder.activityId)

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
