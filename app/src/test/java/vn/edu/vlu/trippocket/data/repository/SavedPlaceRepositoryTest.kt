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
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.WeatherCacheEntity
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits

/** W2: saved places on Room. Owner of the feature: Minh (Weather module: Duy Anh). */
@RunWith(RobolectricTestRunner::class)
class SavedPlaceRepositoryTest {

    private val t = TestDatabase()
    private var nextId = 0
    private val repository = SavedPlaceRepository(t.db.savedPlaceDao(), t.writer) { "p${++nextId}" }

    private val dalatVn = Place("Dalat", "Lâm Đồng", "Vietnam", 11.94, 108.44)
    private val dalatUs = Place("Dalat", "Oregon", "United States", 45.52, -122.98)

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.tripDao().insert(t.trip("t2"))
    }

    @After
    fun tearDown() = t.close()

    private suspend fun revision(tripId: String = "t1") = t.db.tripDao().observeTrip(tripId, OWNER).first()!!.localRevision

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

    @Test
    fun sameNameAtDifferentCoordinates_staysTwoSeparatePlaces() = runBlocking {
        val vn = repository.savePlace("t1", dalatVn)
        val us = repository.savePlace("t1", dalatUs)

        val places = repository.observePlaces("t1").first()
        assertEquals(listOf(vn, us), places.map { it.placeId })
        assertEquals(listOf("Vietnam", "United States"), places.map { it.country })
        assertEquals(7, revision())
        assertEquals(SyncStatus.PENDING, t.db.tripDao().observeTrip("t1", OWNER).first()!!.syncStatus)
    }

    @Test
    fun savingTheSamePlaceAgain_returnsItWithoutACopyOrABackup() = runBlocking {
        val first = repository.savePlace("t1", dalatVn)
        val again = repository.savePlace("t1", dalatVn.copy(name = "Đà Lạt"))

        assertEquals(first, again)
        assertEquals(1, t.db.savedPlaceDao().countByTrip("t1"))
        assertEquals(6, revision())
        assertEquals(listOf(OWNER), t.backupRequests)
    }

    @Test
    fun the31stPlace_isBlocked_andRevisionStays(): Unit = runBlocking {
        repeat(Limits.MAX_PLACES) { i -> repository.savePlace("t1", Place("P$i", "", "Vietnam", 10.0 + i, 106.0)) }
        val before = revision()

        val e = assertRejected<LimitExceededException> { repository.savePlace("t1", dalatVn) }
        assertEquals(Limits.MAX_PLACES, e.max)
        assertEquals(Limits.MAX_PLACES, t.db.savedPlaceDao().countByTrip("t1"))
        assertEquals(before, revision())
        // The cap is per trip.
        repository.savePlace("t2", dalatVn)
    }

    @Test
    fun renameAndNote_areSaved_withinTheirLengthLimits() = runBlocking {
        val id = repository.savePlace("t1", dalatVn)
        repository.renamePlace("t1", id, "  Đà Lạt, phố sương mù  ")
        repository.updateNote("t1", id, "Mang áo ấm")

        val place = repository.observePlaces("t1").first().single()
        assertEquals("Đà Lạt, phố sương mù", place.name)
        assertEquals("Mang áo ấm", place.note)
        assertEquals(8, revision())

        assertEquals(PlaceProblem.BLANK_NAME, assertRejected<InvalidPlaceException> { repository.renamePlace("t1", id, " ") }.problem)
        assertEquals(
            PlaceProblem.NAME_TOO_LONG,
            assertRejected<InvalidPlaceException> { repository.renamePlace("t1", id, "a".repeat(Limits.MAX_TEXT + 1)) }.problem,
        )
        assertEquals(
            PlaceProblem.NOTE_TOO_LONG,
            assertRejected<InvalidPlaceException> { repository.updateNote("t1", id, "a".repeat(Limits.MAX_NOTE + 1)) }.problem,
        )
        assertEquals(8, revision())
        // Exactly at the limits is fine.
        repository.renamePlace("t1", id, "a".repeat(Limits.MAX_TEXT))
        repository.updateNote("t1", id, "a".repeat(Limits.MAX_NOTE))
    }

    @Test
    fun apiNamesLongerThan80_areShortened() = runBlocking {
        repository.savePlace("t1", dalatVn.copy(name = "x".repeat(120)))
        assertEquals(Limits.MAX_TEXT, repository.observePlaces("t1").first().single().name.length)
    }

    @Test
    fun delete_removesThePlaceAndItsCachedForecast() = runBlocking {
        val id = repository.savePlace("t1", dalatVn)
        t.db.weatherCacheDao().upsert(WeatherCacheEntity(id, "{}", 1L))

        repository.deletePlace("t1", id)

        assertTrue(repository.observePlaces("t1").first().isEmpty())
        assertNull(t.db.weatherCacheDao().observe(id).first())
        assertEquals(7, revision())
    }

    @Test
    fun editingAPlaceOfAnotherTrip_isRejected() = runBlocking {
        val id = repository.savePlace("t1", dalatVn)
        assertEquals(
            PlaceProblem.NOT_FOUND,
            assertRejected<InvalidPlaceException> { repository.renamePlace("t2", id, "Mine now") }.problem,
        )
        assertEquals(
            PlaceProblem.NOT_FOUND,
            assertRejected<InvalidPlaceException> { repository.deletePlace("t2", id) }.problem,
        )
        assertEquals(5, revision("t2"))
        assertEquals("Dalat", repository.observePlaces("t1").first().single().name)
    }
}
