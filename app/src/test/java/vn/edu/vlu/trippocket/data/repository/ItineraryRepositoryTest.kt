package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.model.ActivityInput
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.domain.ActivityProblem
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
import java.time.LocalTime

/** I1: the activities of a trip on Room, in order, one transaction per write. Owner: Bảo. */
@RunWith(RobolectricTestRunner::class)
class ItineraryRepositoryTest {

    private val t = TestDatabase()
    private var nextId = 0
    private val repository = ItineraryRepository(t.db.activityDao(), t.writer) { "a${++nextId}" }

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.tripDao().insert(t.trip("t2"))
    }

    @After
    fun tearDown() = t.close()

    private fun input(
        dayIndex: Int = 0,
        title: String = "Sunrise at Langbiang",
        place: String = "Langbiang, Lạc Dương",
        startTime: LocalTime = LocalTime.of(5, 0),
        estimatedCost: Long = 210_000,
        note: String = "",
        reminderMinutesBefore: Int? = null,
    ) = ActivityInput(dayIndex, title, place, startTime, estimatedCost, note, reminderMinutesBefore)

    private suspend fun revision(tripId: String = "t1") =
        t.db.tripDao().observeTrip(tripId, OWNER).first()!!.localRevision

    private suspend fun day(tripId: String = "t1", dayIndex: Int = 0): List<ItineraryActivity> =
        repository.observeDay(tripId, dayIndex).first()

    private suspend fun orders(tripId: String = "t1", dayIndex: Int = 0): List<Int> =
        t.db.activityDao().dayRows(tripId, dayIndex).map { it.orderIndex }

    private fun ids(activities: List<ItineraryActivity>) = activities.map { it.activityId }

    private suspend fun insertRow(
        activityId: String,
        dayIndex: Int = 0,
        startTime: Int = 9 * 60,
        orderIndex: Int,
        tripId: String = "t1",
    ) = t.db.activityDao().insert(
        ActivityEntity(activityId, tripId, dayIndex, "Stop $activityId", "", startTime, 0, "", orderIndex, null),
    )

    private suspend inline fun <reified E : Exception> assertRejected(crossinline block: suspend () -> Unit): E {
        try {
            block()
        } catch (e: Exception) {
            if (e is E) return e
            throw e
        }
        fail("Expected ${E::class.simpleName}")
        throw AssertionError()
    }

    private suspend fun assertProblem(problem: ActivityProblem, block: suspend () -> Unit) {
        assertEquals(problem, assertRejected<InvalidActivityException> { block() }.problem)
    }

    @Test
    fun add_savesEveryField_trimsTheTexts_andBumpsTheRevisionOnce() = runBlocking {
        val id = repository.addActivity(
            "t1",
            input(
                title = "  Sleeper bus to Đà Lạt  ",
                place = " Bến xe Miền Đông ",
                startTime = LocalTime.of(6, 30),
                estimatedCost = 300_000,
                note = "  Bring jackets  ",
                reminderMinutesBefore = 30,
            ),
        )

        val saved = repository.getActivity(id)!!
        assertEquals("t1", saved.tripId)
        assertEquals(0, saved.dayIndex)
        assertEquals("Sleeper bus to Đà Lạt", saved.title)
        assertEquals("Bến xe Miền Đông", saved.place)
        assertEquals(LocalTime.of(6, 30), saved.startTime)
        assertEquals(300_000, saved.estimatedCost)
        assertEquals("Bring jackets", saved.note)
        assertEquals(30, saved.reminderMinutesBefore)
        assertEquals(listOf(0), orders())
        assertEquals(6, revision())
        assertEquals(SyncStatus.PENDING, t.db.tripDao().observeTrip("t1", OWNER).first()!!.syncStatus)
        assertEquals(listOf(OWNER), t.backupRequests)
    }

    @Test
    fun activitiesAtTheSameTime_areAppended_andTheDayIsNumberedFromZero() = runBlocking {
        val breakfast = repository.addActivity("t1", input(title = "Breakfast", startTime = LocalTime.of(9, 0)))
        val dinner = repository.addActivity("t1", input(title = "Dinner", startTime = LocalTime.of(19, 0)))
        val coffee = repository.addActivity("t1", input(title = "Coffee", startTime = LocalTime.of(9, 0)))
        val market = repository.addActivity("t1", input(title = "Market", startTime = LocalTime.of(9, 0)))

        // Same time keeps the order the rows were created in: the new one goes last.
        assertEquals(listOf(breakfast, coffee, market, dinner), ids(day()))
        assertEquals(listOf(0, 1, 2, 3), orders())
    }

    @Test
    fun changingTheTime_reordersTheWholeDay() = runBlocking {
        val a = repository.addActivity("t1", input(title = "A", startTime = LocalTime.of(9, 0)))
        val b = repository.addActivity("t1", input(title = "B", startTime = LocalTime.of(14, 0)))
        val c = repository.addActivity("t1", input(title = "C", startTime = LocalTime.of(9, 0)))
        assertEquals(listOf(a, c, b), ids(day()))

        repository.updateActivity("t1", b, input(title = "B", startTime = LocalTime.of(8, 0)))

        assertEquals(listOf(b, a, c), ids(day()))
        assertEquals(listOf(0, 1, 2), orders())
        assertEquals(LocalTime.of(8, 0), repository.getActivity(b)!!.startTime)
        // Three adds and one edit: one revision per write.
        assertEquals(9, revision())
    }

    @Test
    fun renamingOrChangingTheCost_keepsTheOrder() = runBlocking {
        val a = repository.addActivity("t1", input(title = "A", startTime = LocalTime.of(9, 0)))
        val b = repository.addActivity("t1", input(title = "B", startTime = LocalTime.of(14, 0)))
        val c = repository.addActivity("t1", input(title = "C", startTime = LocalTime.of(9, 0)))
        val before = orders()

        repository.updateActivity(
            "t1", a, input(title = "Renamed", place = "Elsewhere", startTime = LocalTime.of(9, 0), estimatedCost = 1, note = "Note"),
        )

        assertEquals(listOf(a, c, b), ids(day()))
        assertEquals(before, orders())
    }

    @Test
    fun movingToAnotherDay_appendsThere_andRenumbersBothDays() = runBlocking {
        val a = repository.addActivity("t1", input(title = "A", startTime = LocalTime.of(9, 0)))
        val b = repository.addActivity("t1", input(title = "B", startTime = LocalTime.of(10, 0)))
        val c = repository.addActivity("t1", input(title = "C", dayIndex = 1, startTime = LocalTime.of(10, 0)))
        val d = repository.addActivity("t1", input(title = "D", dayIndex = 1, startTime = LocalTime.of(10, 0)))

        repository.updateActivity("t1", b, input(title = "B", dayIndex = 1, startTime = LocalTime.of(10, 0)))

        assertEquals(listOf(a), ids(day(dayIndex = 0)))
        assertEquals(listOf(0), orders(dayIndex = 0))
        // Appended at its new day, then the stable sort keeps it after C and D.
        assertEquals(listOf(c, d, b), ids(day(dayIndex = 1)))
        assertEquals(listOf(0, 1, 2), orders(dayIndex = 1))
        assertEquals(1, repository.getActivity(b)!!.dayIndex)
    }

    @Test
    fun editingOrDeletingAnActivityOfAnotherTrip_isNotFound() = runBlocking {
        val id = repository.addActivity("t1", input())

        assertEquals(
            ActivityProblem.NOT_FOUND,
            assertRejected<InvalidActivityException> { repository.updateActivity("t2", id, input()) }.problem,
        )
        assertEquals(
            ActivityProblem.NOT_FOUND,
            assertRejected<InvalidActivityException> { repository.deleteActivity("t2", id) }.problem,
        )
        assertEquals(5, revision("t2"))
        assertEquals(1, t.db.activityDao().countByTrip("t1"))
    }

    @Test
    fun delete_closesTheGapInTheDay() = runBlocking {
        val a = repository.addActivity("t1", input(title = "A", startTime = LocalTime.of(9, 0)))
        val b = repository.addActivity("t1", input(title = "B", startTime = LocalTime.of(9, 0)))
        val c = repository.addActivity("t1", input(title = "C", startTime = LocalTime.of(14, 0)))

        repository.deleteActivity("t1", a)

        assertNull(repository.getActivity(a))
        assertEquals(listOf(b, c), ids(day()))
        assertEquals(listOf(0, 1), orders())
        assertEquals(9, revision())
    }

    @Test
    fun dayAndTripTotals_followTheActivities() = runBlocking {
        repository.addActivity("t1", input(estimatedCost = 300_000))
        repository.addActivity("t1", input(estimatedCost = 1_500_000))
        repository.addActivity("t1", input(dayIndex = 1, estimatedCost = 200_000))

        assertEquals(1_800_000, repository.observeDayTotal("t1", 0).first())
        assertEquals(200_000, repository.observeDayTotal("t1", 1).first())
        assertEquals(0, repository.observeDayTotal("t1", 3).first())
        assertEquals(2_000_000, repository.estimatedTotal("t1").first())
        assertEquals(listOf(0, 0, 1), repository.observeActivities("t1").first().map { it.dayIndex })
    }

    @Test
    fun invalidFields_areRejectedInsideTheTransaction_andNothingIsWritten() = runBlocking {
        assertProblem(ActivityProblem.BLANK_TITLE) { repository.addActivity("t1", input(title = "  ")) }
        assertProblem(ActivityProblem.TITLE_TOO_LONG) {
            repository.addActivity("t1", input(title = "a".repeat(Limits.MAX_TEXT + 1)))
        }
        assertProblem(ActivityProblem.PLACE_TOO_LONG) {
            repository.addActivity("t1", input(place = "a".repeat(Limits.MAX_TEXT + 1)))
        }
        assertProblem(ActivityProblem.NOTE_TOO_LONG) {
            repository.addActivity("t1", input(note = "a".repeat(Limits.MAX_NOTE + 1)))
        }
        assertProblem(ActivityProblem.COST_NEGATIVE) { repository.addActivity("t1", input(estimatedCost = -1)) }
        assertProblem(ActivityProblem.DAY_OUT_OF_RANGE) { repository.addActivity("t1", input(dayIndex = 4)) }
        assertProblem(ActivityProblem.REMINDER_INVALID) {
            repository.addActivity("t1", input(reminderMinutesBefore = -1))
        }
        // A trip that is not on this phone (or was deleted) has no days to write to.
        assertProblem(ActivityProblem.NOT_FOUND) { repository.addActivity("missing", input()) }

        assertEquals(0, t.db.activityDao().countByTrip("t1"))
        assertEquals(5, revision())
        assertTrue(t.backupRequests.isEmpty())
        // Exactly at the limits is fine.
        repository.addActivity("t1", input(title = "a".repeat(Limits.MAX_TEXT), place = "a".repeat(Limits.MAX_TEXT)))
        repository.updateActivity(
            "t1", repository.observeDay("t1", 0).first().single().activityId,
            input(note = "a".repeat(Limits.MAX_NOTE)),
        )
    }

    @Test
    fun the201stActivity_isRejected_andTheRevisionStays(): Unit = runBlocking {
        repeat(Limits.MAX_ACTIVITIES) { i ->
            t.db.activityDao().insert(ActivityEntity("seed$i", "t1", 0, "Stop $i", "", 60 * 9, 0, "", i, null))
        }

        val e = assertRejected<LimitExceededException> { repository.addActivity("t1", input()) }

        assertEquals(Limits.MAX_ACTIVITIES, e.max)
        assertEquals(Limits.MAX_ACTIVITIES, t.db.activityDao().countByTrip("t1"))
        assertEquals(5, revision())
        assertTrue(t.backupRequests.isEmpty())
        // The cap is per trip.
        repository.addActivity("t2", input())
    }

    @Test
    fun seedSampleActivities_copiesTheSamplesOnce_andIsNotAUserChange() = runBlocking {
        t.db.tripDao().insert(t.trip(MockData.TRIP_DALAT))
        t.db.tripDao().insert(t.trip(MockData.TRIP_HOIAN))

        repository.seedSampleActivities()

        assertEquals(9, t.db.activityDao().countByTrip(MockData.TRIP_DALAT))
        assertEquals(listOf("a1", "a2", "a3", "a4"), ids(day(MockData.TRIP_DALAT)))
        assertEquals(listOf(0, 1, 2, 3), orders(MockData.TRIP_DALAT))
        assertEquals(LocalTime.of(6, 30), day(MockData.TRIP_DALAT).first().startTime)
        assertEquals(5, revision(MockData.TRIP_DALAT))
        assertTrue(t.backupRequests.isEmpty())

        repository.seedSampleActivities()

        assertEquals(9, t.db.activityDao().countByTrip(MockData.TRIP_DALAT))
        assertEquals(5, revision(MockData.TRIP_DALAT))
    }

    @Test
    fun seedSampleActivities_leavesATripThatAlreadyHasActivitiesAlone() = runBlocking {
        t.db.tripDao().insert(t.trip(MockData.TRIP_DALAT))
        t.db.activityDao().insert(ActivityEntity("mine", MockData.TRIP_DALAT, 0, "My stop", "", 600, 0, "", 0, null))

        repository.seedSampleActivities()

        assertEquals(1, t.db.activityDao().countByTrip(MockData.TRIP_DALAT))
        assertEquals("My stop", repository.getActivity("mine")!!.title)
    }

    @Test
    fun seedSampleActivities_ignoresTheSampleTripsThatAreNotOnThisPhone() = runBlocking {
        // No trip rows: seeding must not break the foreign key.
        repository.seedSampleActivities()

        assertEquals(0, t.db.activityDao().countByTrip(MockData.TRIP_DALAT))
    }

    @Test
    fun reorder_renumbersTheDayInOneWrite() = runBlocking {
        // The display order is orderIndex, not the time: the rows are inserted out of time order.
        listOf("a", "b", "c", "d", "e").forEachIndexed { i, id ->
            insertRow(id, startTime = 14 * 60 - i * 60, orderIndex = i)
        }

        repository.reorder("t1", 0, listOf("e", "d", "c", "b", "a"))

        assertEquals(listOf("e", "d", "c", "b", "a"), ids(day()))
        assertEquals(listOf(0, 1, 2, 3, 4), orders())
        assertEquals(6, revision())
        assertEquals(listOf(OWNER), t.backupRequests)
    }

    @Test
    fun reorder_withTheCurrentOrder_writesNothing() = runBlocking {
        listOf("a", "b", "c").forEachIndexed { i, id -> insertRow(id, orderIndex = i) }
        repository.reorder("t1", 0, listOf("c", "a", "b"))
        val revisionAfterDrag = revision()

        // Dropped back where it was: Room keeps the order, the revision and the backup list.
        repository.reorder("t1", 0, listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), ids(day()))
        assertEquals(revisionAfterDrag, revision())
        assertEquals(listOf(OWNER), t.backupRequests)

        // Still validated: a wrong id is refused even when the visible order would not change.
        assertProblem(ActivityProblem.ORDER_MISMATCH) { repository.reorder("t1", 0, listOf("c", "a", "ghost")) }
        assertEquals(revisionAfterDrag, revision())
        assertEquals(listOf(OWNER), t.backupRequests)
    }

    @Test
    fun reorder_withAListThatIsNotTheDay_isRejected() = runBlocking {
        insertRow("a", orderIndex = 0)
        insertRow("b", orderIndex = 1)
        insertRow("c", dayIndex = 1, orderIndex = 0)
        insertRow("other", tripId = "t2", orderIndex = 0)

        assertProblem(ActivityProblem.ORDER_MISMATCH) { repository.reorder("t1", 0, listOf("a", "b", "ghost")) }
        assertProblem(ActivityProblem.ORDER_MISMATCH) { repository.reorder("t1", 0, listOf("a")) }
        assertProblem(ActivityProblem.ORDER_MISMATCH) { repository.reorder("t1", 0, listOf("a", "b", "b")) }
        assertProblem(ActivityProblem.ORDER_MISMATCH) { repository.reorder("t1", 0, listOf("a", "c")) }
        assertProblem(ActivityProblem.ORDER_MISMATCH) { repository.reorder("t1", 0, listOf("a", "other")) }
        assertProblem(ActivityProblem.DAY_OUT_OF_RANGE) { repository.reorder("t1", 4, listOf("a", "b")) }
        assertProblem(ActivityProblem.NOT_FOUND) { repository.reorder("missing", 0, listOf("a", "b")) }

        assertEquals(listOf("a", "b"), ids(day()))
        assertEquals(listOf(0, 1), orders())
        assertEquals(5, revision())
        assertTrue(t.backupRequests.isEmpty())
    }

    @Test
    fun moveUpAndMoveDown_swapWithTheNeighbour_andStopAtTheEnds() = runBlocking {
        listOf("a", "b", "c").forEachIndexed { i, id -> insertRow(id, orderIndex = i) }

        repository.moveUp("t1", "b")
        assertEquals(listOf("b", "a", "c"), ids(day()))
        assertEquals(6, revision())

        repository.moveDown("t1", "b")
        assertEquals(listOf("a", "b", "c"), ids(day()))
        assertEquals(7, revision())

        // The first cannot move up and the last cannot move down: no write at all.
        repository.moveUp("t1", "a")
        repository.moveDown("t1", "c")
        assertEquals(listOf("a", "b", "c"), ids(day()))
        assertEquals(7, revision())
        assertEquals(listOf(OWNER, OWNER), t.backupRequests)
    }

    @Test
    fun movingAnActivityOfAnotherTripOrAMissingOne_isNotFound() = runBlocking {
        insertRow("a", orderIndex = 0)

        assertProblem(ActivityProblem.NOT_FOUND) { repository.moveUp("t2", "a") }
        assertProblem(ActivityProblem.NOT_FOUND) { repository.moveDown("t1", "ghost") }

        assertEquals(5, revision())
        assertEquals(5, revision("t2"))
        assertTrue(t.backupRequests.isEmpty())
    }

    @Test
    fun afterAReorder_changingTheTime_stillSortsByTime() = runBlocking {
        insertRow("a", startTime = 9 * 60, orderIndex = 0)
        insertRow("b", startTime = 14 * 60, orderIndex = 1)
        insertRow("c", startTime = 9 * 60, orderIndex = 2)
        repository.reorder("t1", 0, listOf("c", "b", "a"))
        assertEquals(listOf("c", "b", "a"), ids(day()))

        // The I1 rule is untouched by I3: a new time re-sorts that day.
        repository.updateActivity("t1", "a", input(title = "Stop a", startTime = LocalTime.of(7, 0)))

        assertEquals(listOf("a", "c", "b"), ids(day()))
        assertEquals(listOf(0, 1, 2), orders())
    }
}
