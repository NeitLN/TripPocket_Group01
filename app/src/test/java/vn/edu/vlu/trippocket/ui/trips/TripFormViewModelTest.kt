package vn.edu.vlu.trippocket.ui.trips

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.model.TripInput
import vn.edu.vlu.trippocket.data.repository.FakeAuthRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.TripProblem
import java.time.LocalDate

/** A2: the new / edit trip form. Owner: Bảo. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripFormViewModelTest {

    private val t = TestDatabase()
    private val auth = FakeAuthRepository(OWNER)
    private val repository = TripRepository(t.db.tripDao(), auth, t.writer)
    private val nov3 = LocalDate.of(2026, 11, 3)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private fun form(tripId: String? = null) =
        TripFormViewModel(SavedStateHandle(mapOf(ARG_EDIT_TRIP_ID to tripId)), repository)

    private suspend fun TripFormViewModel.awaitSaved() = withTimeout(5_000) { uiState.first { it.savedTripId != null } }

    @Test
    fun newTrip_savesAndReportsItsId() = runBlocking {
        val form = form()
        form.onNameChanged("Đà Lạt")
        form.onDestinationChanged("Lâm Đồng")
        form.onDatesChanged(nov3, nov3.plusDays(2))

        form.save()

        val id = form.awaitSaved().savedTripId!!
        assertEquals("Đà Lạt", repository.getTrip(id)!!.name)
        assertEquals(3, repository.getTrip(id)!!.dayCount)
    }

    @Test
    fun missingFields_showErrors_andWriteNothing() = runBlocking {
        val form = form()

        form.save()
        assertEquals(setOf(TripProblem.BLANK_NAME), form.uiState.value.problems)
        assertTrue(form.uiState.value.datesMissing)

        form.onNameChanged("Đà Lạt")
        form.onDestinationChanged("Lâm Đồng")
        form.save()
        assertTrue(form.uiState.value.datesMissing)
        assertTrue(repository.observeTrips().first().isEmpty())
    }

    @Test
    fun editTrip_loadsTheFields_andSavesTheChange() = runBlocking {
        val id = repository.createTrip(
            TripInput("Huế", "Thừa Thiên Huế", nov3, nov3.plusDays(1))
        )
        val form = form(id)
        val loaded = withTimeout(5_000) { form.uiState.first { !it.loading } }
        assertTrue(loaded.editing)
        assertEquals("Huế", loaded.name)
        assertEquals(nov3.plusDays(1), loaded.endDate)

        form.onNameChanged("Huế cổ kính")
        form.save()

        assertEquals(id, form.awaitSaved().savedTripId)
        assertEquals("Huế cổ kính", repository.getTrip(id)!!.name)
    }

    @Test
    fun editingAnotherAccountsTrip_isNotFound() = runBlocking {
        val id = repository.createTrip(
            TripInput("Huế", "Thừa Thiên Huế", nov3, nov3)
        )
        auth.setUid("uid-b")

        val state = withTimeout(5_000) { form(id).uiState.first { !it.loading } }

        assertTrue(state.notFound)
    }
}
