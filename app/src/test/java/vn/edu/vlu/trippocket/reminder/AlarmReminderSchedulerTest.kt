package vn.edu.vlu.trippocket.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** I4: exact when allowed, inexact otherwise, one alarm per activity, cancel finds it. Owner: Bảo. */
@RunWith(RobolectricTestRunner::class)
class AlarmReminderSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val shadow = shadowOf(alarmManager)
    private val scheduler = AlarmReminderScheduler(context)

    private fun reminder(id: String, at: Long) = ScheduledReminder(id, "t1", "Crazy House", at)

    @After
    fun tearDown() = ShadowAlarmManager.reset()

    @Test
    fun withTheExactPermission_setsAnExactAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule(reminder("a1", 1_000_000))

        val alarm = shadow.peekNextScheduledAlarm()!!
        assertEquals(1_000_000, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals("exact = no delivery window", 0L, alarm.windowLengthMs)
    }

    @Test
    fun withoutTheExactPermission_setsAnInexactAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(reminder("a1", 1_000_000))

        val alarm = shadow.peekNextScheduledAlarm()!!
        assertEquals(1_000_000, alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
        assertNotEquals("inexact = the system picks the window", 0L, alarm.windowLengthMs)
    }

    @Test
    fun schedulingAgain_replacesTheAlarmOfThatActivity() {
        scheduler.schedule(reminder("a1", 1_000_000))
        scheduler.schedule(reminder("a2", 3_000_000))
        scheduler.schedule(reminder("a1", 2_000_000))

        assertEquals(listOf(2_000_000L, 3_000_000L), shadow.scheduledAlarms.map { it.triggerAtMs }.sorted())
    }

    @Test
    fun cancel_removesOnlyThatActivity_andIsANoOpWhenNothingIsSet() {
        scheduler.schedule(reminder("a1", 1_000_000))
        scheduler.schedule(reminder("a2", 2_000_000))

        scheduler.cancel("a1")
        scheduler.cancel("never-set")

        assertEquals(listOf(2_000_000L), shadow.scheduledAlarms.map { it.triggerAtMs })
        scheduler.cancel("a2")
        assertNull(shadow.peekNextScheduledAlarm())
    }

    @Test
    fun theAlarmCarriesTheActivityId_forTheReceiver() {
        scheduler.schedule(reminder("a1", 1_000_000))

        val intent = shadowOf(shadow.peekNextScheduledAlarm()!!.operation).savedIntent
        assertEquals(ReminderReceiver.ACTION_REMIND, intent.action)
        assertEquals("a1", intent.getStringExtra(ReminderReceiver.EXTRA_ACTIVITY_ID))
    }
}
