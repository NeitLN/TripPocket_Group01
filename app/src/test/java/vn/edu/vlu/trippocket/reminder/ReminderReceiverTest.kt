package vn.edu.vlu.trippocket.reminder

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity

/**
 * I4: when an alarm fires, the notification is shown only for an activity that still has a
 * reminder, on a trip that is not deleted and belongs to the account signed in now. Owner: Bảo.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {

    private val t = TestDatabase()
    private val dao = t.db.activityDao()

    private fun activity(id: String, tripId: String, reminder: Int? = 30) =
        ActivityEntity(id, tripId, 0, "Crazy House", "Đà Lạt", 9 * 60, 0, "", 0, reminder)

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("mine"))
        t.db.tripDao().insert(t.trip("theirs", owner = "uid-b"))
        t.db.tripDao().insert(t.trip("gone", deleted = true))
        dao.insert(activity("a-mine", "mine"))
        dao.insert(activity("a-theirs", "theirs"))
        dao.insert(activity("a-gone", "gone"))
        dao.insert(activity("a-none", "mine", reminder = null))
    }

    @After
    fun tearDown() = t.close()

    @Test
    fun theOwnersReminder_isShown() = runBlocking {
        val reminder = ReminderReceiver.reminderToShow(dao, "a-mine", OWNER)

        assertEquals("mine", reminder?.tripId)
        assertEquals("Crazy House", reminder?.title)
    }

    @Test
    fun aTripOfAnotherAccount_showsNothing() = runBlocking {
        assertNull(ReminderReceiver.reminderToShow(dao, "a-theirs", OWNER))
        // The other account cannot see the signed-in owner's reminder either.
        assertNull(ReminderReceiver.reminderToShow(dao, "a-mine", "uid-b"))
    }

    @Test
    fun aDeletedTrip_showsNothing() = runBlocking {
        assertNull(ReminderReceiver.reminderToShow(dao, "a-gone", OWNER))
    }

    @Test
    fun signedOut_showsNothing() = runBlocking {
        assertNull(ReminderReceiver.reminderToShow(dao, "a-mine", null))
    }

    @Test
    fun aRemovedReminderOrActivity_showsNothing() = runBlocking {
        assertNull(ReminderReceiver.reminderToShow(dao, "a-none", OWNER))
        assertNull(ReminderReceiver.reminderToShow(dao, "missing", OWNER))
    }
}
