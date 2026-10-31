package vn.edu.vlu.trippocket.data.repository

import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.remote.openmeteo.GeocodingApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.toPlaces
import java.time.LocalDateTime

/**
 * Place search on Open-Meteo; saved places and forecasts still come from [MockData] until
 * W2 (saved places) and W3 (forecast cache) replace them.
 *
 * The API is a constructor parameter so tests use a fake and never touch the network.
 */
class WeatherRepository(private val geocodingApi: GeocodingApi) {

    /** Time the cached forecast was fetched (shown as "Last updated"). */
    val forecastFetchedAt: LocalDateTime get() = MockData.forecastFetchedAt

    fun savedPlaces(tripId: String): List<Place> = MockData.savedPlacesOf(tripId)

    /**
     * Search results for [query] (W1). A blank query returns no places without a request;
     * network, HTTP and JSON failures are thrown to the caller, which shows the error state.
     * Retrofit already runs the request off the main thread.
     */
    suspend fun searchPlaces(query: String): List<Place> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return geocodingApi.search(trimmed).toPlaces()
    }

    fun forecast(place: Place): List<ForecastDay> = MockData.forecastFor(place)
}
