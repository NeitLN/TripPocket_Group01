package vn.edu.vlu.trippocket.data.repository

import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.Place
import java.io.IOException
import java.time.LocalDateTime

/**
 * Place search, saved places and forecasts. Simulates the Open-Meteo API with
 * [MockData] so the ViewModel can show every state, including a network error.
 */
class WeatherRepository {

    /** Time the cached forecast was fetched (shown as "Last updated"). */
    val forecastFetchedAt: LocalDateTime get() = MockData.forecastFetchedAt

    fun savedPlaces(tripId: String): List<Place> = MockData.savedPlacesOf(tripId)

    /** Accent-insensitive search. The query "offline" fails like a lost connection. */
    fun searchPlaces(query: String): Result<List<Place>> =
        if (query.trim().equals(OFFLINE_QUERY, ignoreCase = true)) {
            Result.failure(IOException("Simulated network error"))
        } else {
            Result.success(MockData.searchPlaces(query))
        }

    fun forecast(place: Place): List<ForecastDay> = MockData.forecastFor(place)

    private companion object {
        const val OFFLINE_QUERY = "offline"
    }
}
