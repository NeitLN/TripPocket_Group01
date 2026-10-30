package vn.edu.vlu.trippocket.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-registers the upcoming reminders of the signed-in account after a reboot, a time zone
 * change or a clock change (manifest intent filters). Stub from F0; owner: Bảo (I5).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> Unit // I5
            else -> return
        }
    }
}
