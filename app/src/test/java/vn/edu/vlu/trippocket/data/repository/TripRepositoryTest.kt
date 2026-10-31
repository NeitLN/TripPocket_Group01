package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.TripInput
import vn.edu.vlu.trippocket.domain.TripProblem
import java.time.LocalDate

/** A2: create, edit and soft delete trips, only for the account that owns them. Owner: Bảo. */
@RunWith(RobolectricTestRunner::class)
class TripRepositoryTest {

    private val t = TestDatabase()
    private val auth = FakeAuthRepository(OWNER)
    private val itinerary = ItineraryRepository(t.db.activityDao(), t.writer)
    private val reminderSyncs = mutableListOf<String?>()
    private var nextId = 0
    private val repository = TripRepository(
        t.db.tripDao(),
        auth,
        t.writer,
        lastUsedDay = itinerary::lastUsedDayIndex,
        syncReminders = { reminderSyncs += it },
        newId = { "trip${++nextId}" },
        now = { 42L },
    )

    private val nov3 = LocalDate.of(2026, 11, 3)

    private fun input(
        name: String = "Đà Lạt mùa hoa",
        destination: String = "Đà Lạt, Lâm Đồng",
        start: LocalDate = nov3,
        end: LocalDate = nov3.plusDays(3),
    ) = TripInput(name, destination, start, end)

    private suspend fun row(tripId: String) = t.db.tripDao().get(tripId, OWNER)

    private suspend fun assertProblem(expected: TripProblem, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: InvalidTripException) {
            assertEquals(expected, e.problem)
        }
    }

    @After
    fun tearDown() = t.close()

    @Test
    fun create_savesATrimmedPendingTripForTheSignedInAccount_andAsksForABackup() = runBlocking {
        val id = repository.createTrip(input(name = "  Đà Lạt mùa hoa  "))

        val row = row(id)!!
        assertEquals(OWNER, row.ownerUid)
        assertEquals("Đà Lạt mùa hoa", row.name)
        assertEquals(nov3.toEpochDay(), row.startDate)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(listOf(OWNER), t.backupRequests)
        assertEquals(4, repository.observeTrips().first().single().dayCount)
    }

    @Test
    fun aOneDayTrip_isAllowed_reversedDatesAndBlankOrLongTextsAreNot() = runBlocking {
        repository.createTrip(input(end = nov3))

        assertProblem(TripProblem.DATES_REVERSED) { repository.createTrip(input(end = nov3.minusDays(1))) }
        assertProblem(TripProblem.BLANK_NAME) { repository.createTrip(input(name = "   ")) }
        assertProblem(TripProblem.BLANK_DESTINATION) { repository.createTrip(input(destination = "")) }
        assertProblem(TripProblem.DESTINATION_TOO_LONG) { repository.createTrip(input(destination = "x".repeat(81))) }
        assertProblem(TripProblem.NAME_TOO_LONG) { repository.createTrip(input(name = "x".repeat(81))) }
        assertEquals(1, repository.observeTrips().first().size)
    }

    @Test
    fun edit_changesTheFields_bumpsTheRevisionOnce_andRebuildsRemindersOnlyForNewDates() = runBlocking {
        val id = repository.createTrip(input())
        val revision = row(id)!!.localRevision

        repository.updateTrip(id, input(name = "Đà Lạt 2026"))
        assertEquals("Đà Lạt 2026", row(id)!!.name)
        assertEquals(revision + 1, row(id)!!.localRevision)
        assertTrue(reminderSyncs.isEmpty())

        repository.updateTrip(id, input(start = nov3.plusDays(7), end = nov3.plusDays(10)))
        assertEquals(nov3.plusDays(7).toEpochDay(), row(id)!!.startDate)
        assertEquals(listOf<String?>(OWNER), reminderSyncs)
    }

    @Test
    fun edit_cannotDropADayThatStillHasActivities() = runBlocking {
        val id = repository.createTrip(input())
        t.db.activityDao().insert(ActivityEntity("a1", id, 3, "Chợ đêm", "", 19 * 60, 0, "", 0, null))
        val revision = row(id)!!.localRevision

        assertProblem(TripProblem.DAYS_IN_USE) { repository.updateTrip(id, input(end = nov3.plusDays(2))) }
        assertEquals(revision, row(id)!!.localRevision)

        // Moving the trip without making it shorter is fine.
        repository.updateTrip(id, input(start = nov3.plusDays(1), end = nov3.plusDays(4)))
        assertEquals(nov3.plusDays(4).toEpochDay(), row(id)!!.endDate)
    }

    @Test
    fun delete_hidesTheTripAtOnce_keepsTheRowForTheBackupJob_andCancelsItsReminders() = runBlocking {
        val id = repository.createTrip(input())

        repository.deleteTrip(id)

        assertTrue(repository.observeTrips().first().isEmpty())
        assertNull(repository.observeTrip(id).first())
        val stored = t.db.tripDao().ownerOf(id)
        assertEquals(OWNER, stored)
        assertEquals(listOf<String?>(OWNER), reminderSyncs)
        assertProblem(TripProblem.NOT_FOUND) { repository.deleteTrip(id) }
    }

    @Test
    fun anotherAccount_cannotOpenEditOrDeleteTheTrip() = runBlocking {
        val id = repository.createTrip(input())

        auth.setUid("uid-b")
        assertNull(repository.getTrip(id))
        assertNull(repository.observeTrip(id).first())
        assertProblem(TripProblem.NOT_FOUND) { repository.updateTrip(id, input(name = "Hijacked")) }
        assertProblem(TripProblem.NOT_FOUND) { repository.deleteTrip(id) }

        auth.setUid(OWNER)
        assertEquals("Đà Lạt mùa hoa", repository.getTrip(id)!!.name)
    }

    @Test
    fun signedOut_cannotCreate() = runBlocking {
        auth.setUid(null)

        assertProblem(TripProblem.NOT_FOUND) { repository.createTrip(input()) }
    }
}
