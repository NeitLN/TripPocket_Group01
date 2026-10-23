package vn.edu.vlu.trippocket.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import vn.edu.vlu.trippocket.ui.expenses.ExpensesViewModel
import vn.edu.vlu.trippocket.ui.itinerary.ItineraryViewModel
import vn.edu.vlu.trippocket.ui.login.LoginViewModel
import vn.edu.vlu.trippocket.ui.trips.TripListViewModel
import vn.edu.vlu.trippocket.ui.weather.WeatherViewModel

/** The ViewModels hold no View or Context, so they run as plain JVM tests. */
class ViewModelTest {

    private fun dalat() = SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT))

    @Test
    fun login_showsErrorsThenSignsIn() {
        val viewModel = LoginViewModel()
        viewModel.signIn("an@gmail", "abc")
        assertEquals(R.string.error_email_invalid, viewModel.uiState.value.emailError)
        assertEquals(R.string.error_password_short, viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.signedIn)

        viewModel.onEmailChanged()
        assertNull(viewModel.uiState.value.emailError)

        viewModel.signIn("an@gmail.com", "dalat2026")
        assertTrue(viewModel.uiState.value.signedIn)
    }

    @Test
    fun tripList_emptyPreviewToggles() {
        val viewModel = TripListViewModel(SavedStateHandle(), TripRepository())
        assertEquals(MockData.trips.size, viewModel.uiState.value.trips.size)
        assertEquals(2, viewModel.uiState.value.unsyncedCount)

        viewModel.toggleEmptyPreview()
        assertTrue(viewModel.uiState.value.trips.isEmpty())
    }

    @Test
    fun itinerary_lastDayIsEmpty_andSelectionIsSaved() {
        val handle = dalat()
        val viewModel = ItineraryViewModel(handle, TripRepository(), WeatherRepository())
        assertEquals(4, viewModel.uiState.value.days.size)
        assertEquals(4, viewModel.uiState.value.activities.size)

        viewModel.selectDay(3)
        assertTrue(viewModel.uiState.value.activities.isEmpty())
        assertEquals(3, ItineraryViewModel(handle, TripRepository(), WeatherRepository()).uiState.value.selectedDay)
    }

    @Test
    fun expenses_balancesSumToZero_andShareTextListsPayments() {
        val viewModel = ExpensesViewModel(dalat(), TripRepository())
        val state = viewModel.uiState.value
        assertEquals(0L, state.balances.sumOf { it.balance })
        assertEquals(state.expenses.sumOf { it.expense.amount }, state.spent)
        assertTrue(state.settlements.isNotEmpty())
        assertTrue(viewModel.shareText().contains("Suggested payments:"))

        viewModel.showBalances(true)
        assertTrue(viewModel.uiState.value.showBalances)
    }

    @Test
    fun weather_offlineSearchFails_andNewPlaceNeedsRetry() {
        val viewModel = WeatherViewModel(dalat(), TripRepository(), WeatherRepository())
        assertTrue(viewModel.forecast.value.loaded)
        assertTrue(viewModel.forecast.value.fromCache)

        viewModel.onQueryChanged("offline")
        assertTrue(viewModel.search.value.networkError)
        viewModel.onQueryChanged("xyz")
        assertEquals(emptyList<Place>(), viewModel.search.value.results)
        viewModel.onQueryChanged("da lat")
        val dalat = viewModel.search.value.results.orEmpty().first { it.name == "Đà Lạt" }

        assertFalse(viewModel.pickPlace(dalat)) // already saved
        val hanoi = MockData.searchPlaces("ha noi").first()
        assertTrue(viewModel.pickPlace(hanoi))
        assertFalse(viewModel.forecast.value.loaded)

        viewModel.retry()
        assertTrue(viewModel.forecast.value.loaded)
        assertFalse(viewModel.forecast.value.fromCache)
    }
}
