package vn.edu.vlu.trippocket.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.edu.vlu.trippocket.TripPocketApp

/**
 * Re-registers the upcoming reminders of the signed-in account after a reboot, a time zone
 * change, a clock change, or when the user allows exact alarms (manifest intent filters).
 * I5, Minh; reminder module of Bảo.
 *
 * Exact alarms: an alarm keeps the mode it was set with, so reminders set while only inexact
 * alarms were allowed stay up to about an hour late until they are set again. Android sends
 * [ACTION_EXACT_ALARMS_ALLOWED] when the user turns the permission on (not when it is turned
 * off: the app is then stopped and its alarms are dropped, and the next start reconciles).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!reschedulesOn(intent.action)) return
        val app = context.applicationContext as? TripPocketApp ?: return
        // Room work cannot run on the main thread: keep the broadcast alive until it is done.
        val pending = goAsync()
        app.appScope.launch {
            try {
                // A receiver has about 10 seconds; give up well before the system does.
                withTimeoutOrNull(TIME_BUDGET_MS) {
                    val container = app.container
                    container.reminderReconciler.reconcile(container.authRepository.currentUid.value)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31). Spelled out
         * because minSdk is 26; older phones never send it and need no exact alarm permission.
         */
        const val ACTION_EXACT_ALARMS_ALLOWED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        private const val TIME_BUDGET_MS = 8_000L

        /** The broadcasts after which every reminder alarm is set again. */
        fun reschedulesOn(action: String?): Boolean = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            ACTION_EXACT_ALARMS_ALLOWED -> true
            else -> false
        }
    }
}
