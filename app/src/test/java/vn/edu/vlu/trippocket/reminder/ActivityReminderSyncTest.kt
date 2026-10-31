package vn.edu.vlu.trippocket.reminder

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.model.ActivityInput
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.domain.ReminderTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * I4: adding, editing and deleting an activity sets, moves and cancels its alarm, through the
 * same path as the app (ItineraryRepository -> ReminderReconciler.syncActivity). Owner: Bảo.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityReminderSyncTest {

    private val t = TestDatabase()
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")

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

    private val startDay = LocalDate.ofEpochDay(t.trip("x").startDate)
    /** "Now" is the first day of the trip at 08:00 in Vietnam. */
    private var now = startDay.atTime(8, 0).atZone(vietnam).toInstant().toEpochMilli()
    private var uid: String? = OWNER
    private val reconciler = ReminderReconciler(t.db.activityDao(), scheduler, { vietnam }, { now })
    private var nextId = 0
    private val repository = ItineraryRepository(t.db.activityDao(), t.writer, { "a${++nextId}" }) { id ->
        reconciler.syncActivity(id, uid)
    }

    private fun input(dayIndex: Int = 1, hour: Int = 9, reminder: Int? = 30, title: String = "Crazy House") =
        ActivityInput(dayIndex, title, "Đà Lạt", LocalTime.of(hour, 0), 0, "", reminder)

    private fun expected(day: Int, hour: Int, minutesBefore: Int) =
        ReminderTime.triggerAtMillis(startDay.toEpochDay(), day, hour * 60, minutesBefore, vietnam)

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.tripDao().insert(t.trip("theirs", owner = "uid-b"))
    }

    @After
    fun tearDown() = t.close()

    @Test
    fun addingWithAReminder_setsTheAlarm() = runBlocking {
        val id = repository.addActivity("t1", input())

        val alarm = scheduler.scheduled.getValue(id)
        assertEquals(expected(1, 9, 30), alarm.triggerAtMillis)
        assertEquals("t1", alarm.tripId)
        assertEquals("Crazy House", alarm.title)
    }

    @Test
    fun addingWithoutAReminder_setsNothing() = runBlocking {
        repository.addActivity("t1", input(reminder = null))

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun editingTheTimeOrLeadTime_movesTheAlarm() = runBlocking {
        val id = repository.addActivity("t1", input())

        repository.updateActivity("t1", id, input(dayIndex = 2, hour = 14, reminder = 60, title = "Lunch"))

        val alarm = scheduler.scheduled.getValue(id)
        assertEquals(expected(2, 14, 60), alarm.triggerAtMillis)
        assertEquals("Lunch", alarm.title)
    }

    @Test
    fun clearingTheReminder_cancelsTheAlarm() = runBlocking {
        val id = repository.addActivity("t1", input())

        repository.updateActivity("t1", id, input(reminder = null))

        assertFalse(id in scheduler.scheduled)
        assertTrue(id in scheduler.cancelled)
    }

    @Test
    fun deletingTheActivity_cancelsTheAlarm() = runBlocking {
        val id = repository.addActivity("t1", input())

        repository.deleteActivity("t1", id)

        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(id in scheduler.cancelled)
    }

    @Test
    fun aTimeAlreadyPast_setsNoAlarm() = runBlocking {
        // Day 1 at 08:15 with 30 minutes lead: 07:45, before "now" (08:00).
        repository.addActivity("t1", ActivityInput(0, "Breakfast", "", LocalTime.of(8, 15), 0, "", 30))

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun anotherAccountsTrip_orSignedOut_setsNoAlarm() = runBlocking {
        repository.addActivity("theirs", input())
        assertTrue(scheduler.scheduled.isEmpty())

        uid = null
        repository.addActivity("t1", input())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun aRejectedWrite_leavesTheAlarmAlone() = runBlocking {
        val id = repository.addActivity("t1", input())
        val before = scheduler.scheduled.getValue(id)

        runCatching { repository.updateActivity("t1", id, input(title = " ")) }

        assertEquals(before, scheduler.scheduled.getValue(id))
    }
}
