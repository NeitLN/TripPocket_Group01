package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** The ViewModels hold no View or Context, so they run as plain JVM tests. Owner: Bảo. */
class ItineraryViewModelTest {

    private fun dalat() = SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT))

    @Test
    fun itinerary_lastDayIsEmpty_andSelectionIsSaved() {
        val handle = dalat()
        val viewModel = ItineraryViewModel(handle, SampleTripRepository(), WeatherRepository())
        assertEquals(4, viewModel.uiState.value.days.size)
        assertEquals(4, viewModel.uiState.value.activities.size)

        viewModel.selectDay(3)
        assertTrue(viewModel.uiState.value.activities.isEmpty())
        assertEquals(3, ItineraryViewModel(handle, SampleTripRepository(), WeatherRepository()).uiState.value.selectedDay)
    }
}
