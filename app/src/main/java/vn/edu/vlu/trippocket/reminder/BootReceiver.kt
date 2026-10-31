package vn.edu.vlu.trippocket.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.edu.vlu.trippocket.TripPocketApp

/**
 * Re-registers the upcoming reminders of the signed-in account after a reboot, a time zone
 * change or a clock change (manifest intent filters). I5, Minh; reminder module of Bảo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> Unit
            else -> return
        }
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

    private companion object {
        const val TIME_BUDGET_MS = 8_000L
    }
}
