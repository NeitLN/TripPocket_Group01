package vn.edu.vlu.trippocket.domain

import vn.edu.vlu.trippocket.data.model.ForecastDay
import java.time.LocalDate

/**
 * Matches a trip day to the forecast the API returned for exactly that date.
 *
 * W5 (Itinerary, owner: Tiến) uses this to show the weather of each trip day from the cache.
 * A day outside the 16-day range, a day missing from the list or an empty list returns null,
 * and the screen shows "Forecast not available yet". It never falls back to another day
 * (today's forecast, for example), because that would show the wrong weather for the trip.
 */
object ForecastMatcher {

    /** The entry for [date], or null when [forecast] does not cover that day. */
    fun forDate(forecast: List<ForecastDay>, date: LocalDate): ForecastDay? =
        forecast.firstOrNull { it.date == date }
}
