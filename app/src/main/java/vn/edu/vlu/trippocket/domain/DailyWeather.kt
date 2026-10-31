package vn.edu.vlu.trippocket.domain

import vn.edu.vlu.trippocket.data.model.ForecastDay
import java.time.LocalDate

/**
 * Weather of a trip, for the itinerary screen (W5, owner: Tiến).
 *
 * Produced by
 * [vn.edu.vlu.trippocket.data.repository.WeatherRepository.observeDailyWeather]: the forecast of
 * the first saved place of the trip, read only from the Room cache. The screen never calls the
 * API for this; the Weather screen downloads the forecast and the cache is shared.
 *
 * Always one of the two states, so the screen has no case to guess:
 * - [NoSavedPlace] — the trip has no place yet, show "add a place" instead of a forecast;
 * - [Cached] — the place exists; ask [Cached.on] for a date. A date the cache does not cover
 *   (before the first day, after the 16-day range, or no download yet) gives null and the screen
 *   shows "Forecast not available yet", never another day's weather.
 */
sealed interface DailyWeather {

    /** The trip has no saved place. */
    data object NoSavedPlace : DailyWeather

    /**
     * The cached forecast of the first saved place of the trip. [fetchedAt] is epoch millis, or
     * null when the place is saved but its forecast has not been downloaded yet; [days] is
     * empty then.
     */
    data class Cached(
        val days: List<ForecastDay>,
        val fetchedAt: Long? = null,
    ) : DailyWeather {

        /** The forecast of exactly [date], or null when [days] does not contain that day. */
        fun on(date: LocalDate): ForecastDay? = ForecastMatcher.forDate(days, date)
    }
}
