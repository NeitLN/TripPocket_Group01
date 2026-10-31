package vn.edu.vlu.trippocket.ui.weather

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** The ViewModels hold no View or Context, so they run as plain JVM tests. Owner: Duy Anh. */
class WeatherViewModelTest {

    private fun dalat() = SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT))

    @Test
    fun weather_offlineSearchFails_andNewPlaceNeedsRetry() {
        val viewModel = WeatherViewModel(dalat(), SampleTripRepository(), WeatherRepository())
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
