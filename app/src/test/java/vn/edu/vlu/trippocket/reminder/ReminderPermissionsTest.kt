package vn.edu.vlu.trippocket.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** I4: what the reminder field says, from the notification and alarm settings. Owner: Bảo. */
@RunWith(RobolectricTestRunner::class)
class ReminderPermissionsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notifications).setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    private fun channel(importance: Int) {
        notifications.createNotificationChannel(
            NotificationChannel(ReminderReceiver.CHANNEL_ID, "Activity reminders", importance)
        )
    }

    @Test
    fun everythingAllowed_isOnTime() {
        channel(NotificationManager.IMPORTANCE_HIGH)

        assertEquals(ReminderStatus.ON_TIME, ReminderPermissions.status(app))
    }

    @Test
    fun noChannelYet_countsAsOn() {
        assertEquals(ReminderStatus.ON_TIME, ReminderPermissions.status(app))
    }

    @Test
    fun onlyTheReminderChannelTurnedOff_isBlocked() {
        channel(NotificationManager.IMPORTANCE_NONE)

        assertEquals(ReminderStatus.BLOCKED, ReminderPermissions.status(app))
    }

    @Test
    fun theWholeAppTurnedOff_isBlocked() {
        shadowOf(notifications).setNotificationsEnabled(false)

        assertEquals(ReminderStatus.BLOCKED, ReminderPermissions.status(app))
    }

    @Test
    fun theNotificationPermissionDenied_isBlocked() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(ReminderStatus.BLOCKED, ReminderPermissions.status(app))
    }

    @Test
    fun inexactAlarmsOnly_mayBeLate() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertEquals(ReminderStatus.MAY_BE_LATE, ReminderPermissions.status(app))
    }
}
