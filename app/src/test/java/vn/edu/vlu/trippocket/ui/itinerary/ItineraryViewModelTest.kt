package vn.edu.vlu.trippocket.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeGeocodingApi
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** The ViewModels hold no View or Context; Room is in memory. Owner: Bảo. */
@RunWith(RobolectricTestRunner::class)
class ItineraryViewModelTest {

    private val t = TestDatabase()

    @After
    fun tearDown() = t.close()

    private fun dalat() = SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT))

    private fun weather() = WeatherRepository(
        FakeGeocodingApi(),
        FakeForecastApi(),
        t.db.weatherCacheDao(),
        t.db.savedPlaceDao(),
    )

    @Test
    fun itinerary_lastDayIsEmpty_andSelectionIsSaved() {
        val handle = dalat()
        val viewModel = ItineraryViewModel(handle, SampleTripRepository(), weather())
        assertEquals(4, viewModel.uiState.value.days.size)
        assertEquals(4, viewModel.uiState.value.activities.size)

        viewModel.selectDay(3)
        assertTrue(viewModel.uiState.value.activities.isEmpty())
        assertEquals(3, ItineraryViewModel(handle, SampleTripRepository(), weather()).uiState.value.selectedDay)
    }
}
