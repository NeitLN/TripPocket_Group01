package vn.edu.vlu.trippocket.reminder

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import java.time.LocalDate
import java.time.ZoneId

/** I5: alarms are rebuilt for the signed-in account only, from the phone's current zone. Owner: Minh. */
@RunWith(RobolectricTestRunner::class)
class ReminderReconcilerTest {

    private val t = TestDatabase()
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    /** Records what the reconciler asked AlarmManager to do. */
    private val scheduler = object : ReminderScheduler {
        val scheduled = mutableMapOf<String, ScheduledReminder>()
        val cancelled = mutableListOf<String>()
        override fun schedule(reminder: ScheduledReminder) {
            scheduled[reminder.activityId] = reminder
        }
        override fun cancel(activityId: String) {
            cancelled += activityId
            scheduled.remove(activityId)
        }
    }

    private var zone = vietnam
    private val startDay = LocalDate.ofEpochDay(t.trip("x").startDate)
    /** "Now" is the first day of the trip at 08:00 in Vietnam. */
    private var now = startDay.atTime(8, 0).atZone(vietnam).toInstant().toEpochMilli()
    private val reconciler = ReminderReconciler(t.db.activityDao(), scheduler, { zone }, { now })

    private fun activity(id: String, tripId: String, day: Int, hour: Int, minutesBefore: Int?) =
        ActivityEntity(id, tripId, day, "Hoạt động $id", "Đà Lạt", hour * 60, 0, "", 0, minutesBefore)

    private fun expected(day: Int, hour: Int, minutesBefore: Int, zone: ZoneId) =
        startDay.plusDays(day.toLong()).atTime(hour, 0).atZone(zone).minusMinutes(minutesBefore.toLong())
            .toInstant().toEpochMilli()

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("mine"))
        t.db.tripDao().insert(t.trip("theirs", owner = "uid-b"))
        t.db.tripDao().insert(t.trip("deleted", deleted = true))
        listOf(
            activity("past", "mine", day = 0, hour = 7, minutesBefore = 30), // 06:30 today: already gone
            activity("soon", "mine", day = 0, hour = 9, minutesBefore = 30), // 08:30 today
            activity("later", "mine", day = 2, hour = 19, minutesBefore = 60),
            activity("noReminder", "mine", day = 1, hour = 10, minutesBefore = null),
            activity("other", "theirs", day = 1, hour = 10, minutesBefore = 15),
            activity("gone", "deleted", day = 1, hour = 10, minutesBefore = 15),
        ).forEach { t.db.activityDao().insert(it) }
    }

    @After
    fun tearDown() = t.close()

    @Test
    fun setsOnlyTheUpcomingRemindersOfTheSignedInAccount() = runBlocking {
        assertEquals(2, reconciler.reconcile(OWNER))

        assertEquals(setOf("soon", "later"), scheduler.scheduled.keys)
        assertEquals(expected(0, 9, 30, vietnam), scheduler.scheduled.getValue("soon").triggerAtMillis)
        assertEquals(expected(2, 19, 60, vietnam), scheduler.scheduled.getValue("later").triggerAtMillis)
        assertEquals("mine", scheduler.scheduled.getValue("later").tripId)
    }

    @Test
    fun everyOldAlarmIsCancelledFirst_soNothingIsLeftFromAnotherAccount() = runBlocking {
        reconciler.reconcile("uid-b")
        assertEquals(setOf("other"), scheduler.scheduled.keys)

        reconciler.reconcile(OWNER)
        assertEquals(setOf("soon", "later"), scheduler.scheduled.keys)
        assertTrue(scheduler.cancelled.containsAll(listOf("past", "soon", "later", "other", "gone")))
    }

    @Test
    fun signingOut_leavesNoAlarm() = runBlocking {
        reconciler.reconcile(OWNER)
        assertEquals(0, reconciler.reconcile(null))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun timeZoneChange_movesAlarmsToTheNewLocalTime() = runBlocking {
        reconciler.reconcile(OWNER)
        val inVietnam = scheduler.scheduled.getValue("later").triggerAtMillis

        // Flew to Tokyo: 18:00 on day 3 is now 18:00 Tokyo time, two hours earlier in absolute time.
        zone = tokyo
        reconciler.reconcile(OWNER)
        val inTokyo = scheduler.scheduled.getValue("later").triggerAtMillis
        assertEquals(expected(2, 19, 60, tokyo), inTokyo)
        assertEquals(2 * 60 * 60 * 1000L, inVietnam - inTokyo)
    }

    @Test
    fun afterTheClockMovesOn_passedRemindersAreNotSetAgain() = runBlocking {
        now = expected(0, 9, 30, vietnam) + 1
        assertEquals(1, reconciler.reconcile(OWNER))
        assertEquals(setOf("later"), scheduler.scheduled.keys)
    }
}
