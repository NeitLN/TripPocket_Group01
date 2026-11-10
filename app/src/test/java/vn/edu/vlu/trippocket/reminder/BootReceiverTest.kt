package vn.edu.vlu.trippocket.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * I4/I5: the reminders are set again after a reboot, a time zone or clock change, and when the
 * user allows exact alarms, so reminders set while only inexact alarms were allowed become
 * exact. Owner: Bảo.
 */
@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theExactAlarmAction_isTheAndroidOne() {
        assertEquals(
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            BootReceiver.ACTION_EXACT_ALARMS_ALLOWED,
        )
    }

    @Test
    fun allowingExactAlarms_setsTheRemindersAgain() {
        assertTrue(BootReceiver.reschedulesOn(BootReceiver.ACTION_EXACT_ALARMS_ALLOWED))
        assertTrue(BootReceiver.reschedulesOn(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootReceiver.reschedulesOn(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(BootReceiver.reschedulesOn(Intent.ACTION_TIME_CHANGED))
        assertFalse(BootReceiver.reschedulesOn(Intent.ACTION_SCREEN_ON))
        assertFalse(BootReceiver.reschedulesOn(null))
    }

    @Test
    fun theManifest_routesEveryActionToTheReceiver() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            BootReceiver.ACTION_EXACT_ALARMS_ALLOWED,
        ).forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                PackageManager.MATCH_ALL,
            )
            assertTrue(action, receivers.any { it.activityInfo.name == BootReceiver::class.java.name })
        }
    }
}
