package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.model.ActivityInput
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.FakeAuthRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.ActivityProblem
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalTime

/** I1: the add / edit activity form, kept open until the write succeeds. Owner of the module: Bảo. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ItineraryFormViewModelTest {

    private val t = TestDatabase()
    private val repository = ItineraryRepository(t.db.activityDao(), t.writer)
    private val tripRepository = TripRepository(t.db.tripDao(), FakeAuthRepository(TestDatabase.OWNER), t.writer)
    private val trip = MockData.TRIP_DALAT

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        t.db.tripDao().insert(t.trip(trip))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private fun form(activityId: String? = null): ItineraryFormViewModel {
        val args = mutableMapOf<String, Any?>(ARG_TRIP_ID to trip)
        if (activityId != null) args[ARG_ACTIVITY_ID] = activityId
        val viewModel = ItineraryFormViewModel(SavedStateHandle(args), tripRepository, repository)
        // The trip's days are read from Room first (A2); the form ignores Save until then.
        runBlocking { withTimeout(5_000) { viewModel.uiState.first { !it.loading } } }
        return viewModel
    }

    private suspend fun loadedForm(activityId: String): ItineraryFormViewModel {
        val viewModel = form(activityId)
        withTimeout(5_000) { viewModel.uiState.first { !it.loading } }
        return viewModel
    }

    private suspend fun ItineraryFormViewModel.awaitSaved() =
        withTimeout(5_000) { uiState.first { it.saved } }

    private suspend fun revision() =
        t.db.tripDao().observeTrip(trip, TestDatabase.OWNER).first()!!.localRevision

    private fun input(
        dayIndex: Int = 0,
        title: String = "Sunrise at Langbiang",
        startTime: LocalTime = LocalTime.of(5, 0),
        estimatedCost: Long = 210_000,
        reminderMinutesBefore: Int? = null,
    ) = ActivityInput(dayIndex, title, "Langbiang", startTime, estimatedCost, "", reminderMinutesBefore)

    @Test
    fun aNewActivity_savesEveryField_andReportsSavedOnce() = runBlocking {
        val viewModel = form()
        assertFalse(viewModel.uiState.value.editing)
        assertEquals(4, viewModel.uiState.value.dayCount)

        viewModel.onTitleChanged("Sleeper bus")
        viewModel.onPlaceChanged("Bến xe Miền Đông")
        viewModel.onStartTimeChanged(LocalTime.of(6, 30))
        viewModel.onCostChanged("300000")
        viewModel.onNoteChanged("Bring jackets")
        viewModel.onReminderChanged("30")
        viewModel.onDayChanged(1)
        viewModel.save()

        val done = viewModel.awaitSaved()
        assertFalse(done.saving)
        assertTrue(done.problems.isEmpty())

        val saved = repository.observeDay(trip, 1).first().single()
        assertEquals("Sleeper bus", saved.title)
        assertEquals("Bến xe Miền Đông", saved.place)
        assertEquals(LocalTime.of(6, 30), saved.startTime)
        assertEquals(300_000, saved.estimatedCost)
        assertEquals("Bring jackets", saved.note)
        assertEquals(30, saved.reminderMinutesBefore)
        assertEquals(6, revision())

        viewModel.savedShown()
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun anEditedActivity_isLoaded_andSavingReplacesIt() = runBlocking {
        val id = repository.addActivity(trip, input(estimatedCost = 300_000, reminderMinutesBefore = 30))

        val viewModel = loadedForm(id)
        val loaded = viewModel.uiState.value
        assertTrue(loaded.editing)
        assertEquals("Sunrise at Langbiang", loaded.title)
        assertEquals("Langbiang", loaded.place)
        assertEquals(LocalTime.of(5, 0), loaded.startTime)
        assertEquals("300000", loaded.cost)
        assertEquals("30", loaded.reminder)
        assertEquals(0, loaded.dayIndex)

        viewModel.onTitleChanged("Sunrise at Langbiang (jeep)")
        viewModel.onCostChanged("250000")
        viewModel.save()
        viewModel.awaitSaved()

        val saved = repository.getActivity(id)!!
        assertEquals("Sunrise at Langbiang (jeep)", saved.title)
        assertEquals(250_000, saved.estimatedCost)
        assertEquals(7, revision())
    }

    @Test
    fun aFreeActivity_usesAnEmptyCostAndReminderField() = runBlocking {
        val id = repository.addActivity(trip, input(title = "Walk", estimatedCost = 0))
        val viewModel = loadedForm(id)

        assertEquals("", viewModel.uiState.value.cost)
        assertEquals("", viewModel.uiState.value.reminder)

        viewModel.onTitleChanged("Walk around Xuân Hương Lake")
        viewModel.save()
        viewModel.awaitSaved()

        val saved = repository.getActivity(id)!!
        assertEquals(0, saved.estimatedCost)
        assertEquals("Walk around Xuân Hương Lake", saved.title)
    }

    @Test
    fun invalidFields_stayInTheForm_andNothingIsWritten() = runBlocking {
        val viewModel = form()

        viewModel.save()
        assertEquals(setOf(ActivityProblem.BLANK_TITLE), viewModel.uiState.value.problems)

        viewModel.onTitleChanged("Bus")
        viewModel.onCostChanged("12,000")
        viewModel.save()
        assertEquals(setOf(ActivityProblem.COST_INVALID), viewModel.uiState.value.problems)

        viewModel.onCostChanged("-1")
        viewModel.save()
        assertEquals(setOf(ActivityProblem.COST_NEGATIVE), viewModel.uiState.value.problems)

        viewModel.onCostChanged("12000")
        viewModel.onReminderChanged("soon")
        viewModel.save()
        assertEquals(setOf(ActivityProblem.REMINDER_INVALID), viewModel.uiState.value.problems)

        viewModel.onReminderChanged("")
        viewModel.onTitleChanged("a".repeat(Limits.MAX_TEXT + 1))
        viewModel.save()
        assertEquals(setOf(ActivityProblem.TITLE_TOO_LONG), viewModel.uiState.value.problems)

        assertFalse(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.saving)
        assertEquals(0, t.db.activityDao().countByTrip(trip))
        assertEquals(5, revision())
    }

    @Test
    fun the201stActivity_closesTheFormUntilTheTripIsSmaller() = runBlocking {
        repeat(Limits.MAX_ACTIVITIES) { i ->
            t.db.activityDao().insert(
                ActivityEntity("seed$i", trip, 0, "Stop $i", "", 9 * 60, 0, "", i, null),
            )
        }
        val viewModel = form()
        viewModel.onTitleChanged("One more stop")
        viewModel.save()

        val state = withTimeout(5_000) { viewModel.uiState.first { it.limitReached } }
        assertFalse(state.saved)
        assertEquals(Limits.MAX_ACTIVITIES, t.db.activityDao().countByTrip(trip))
        assertEquals(5, revision())

        // Deleting one frees the cap, so the same form can save without being reopened.
        t.db.activityDao().deleteById(trip, "seed0")
        viewModel.save()
        viewModel.awaitSaved()
        assertEquals(Limits.MAX_ACTIVITIES, t.db.activityDao().countByTrip(trip))
    }

    @Test
    fun anActivityOfAnotherTripOrAMissingOne_isNotFound() = runBlocking {
        t.db.tripDao().insert(t.trip("t2"))
        val other = repository.addActivity("t2", input())

        assertTrue(loadedForm(other).uiState.value.notFound)
        assertTrue(loadedForm("missing").uiState.value.notFound)
    }

    @Test
    fun deletingAnActivity_setsDeletedTrue_andRemovesFromRoom() = runBlocking {
        val id = repository.addActivity(trip, input())
        val viewModel = loadedForm(id)

        viewModel.delete()
        val state = withTimeout(5_000) { viewModel.uiState.first { it.deleted } }
        assertTrue(state.deleted)
        assertFalse(state.saving)
        assertEquals(0, t.db.activityDao().countByTrip(trip))
    }

    @Test
    fun deletingAnAlreadyDeletedActivity_setsNotFoundTrue() = runBlocking {
        val id = repository.addActivity(trip, input())
        val viewModel = loadedForm(id)

        repository.deleteActivity(trip, id)
        viewModel.delete()
        val state = withTimeout(5_000) { viewModel.uiState.first { it.notFound } }
        assertTrue(state.notFound)
        assertFalse(state.deleted)
    }
}
