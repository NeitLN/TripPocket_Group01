package vn.edu.vlu.trippocket.data.model

import androidx.annotation.DrawableRes
import vn.edu.vlu.trippocket.R
import java.time.LocalDate

// Models of one module (owner: Duy Anh). Room entities live in data/local/entity.

data class Place(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * A place saved to one trip (W2). Unlike a search result ([Place]) it has its own id, so two
 * places with the same name but different coordinates stay separate, and a user note.
 */
data class SavedPlace(
    val placeId: String,
    val tripId: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val note: String,
)

enum class WeatherCondition(@param:DrawableRes val iconRes: Int, val label: String) {
    SUNNY(R.drawable.ic_sunny, "Sunny"),
    CLOUDY(R.drawable.ic_cloud, "Partly cloudy"),
    RAIN(R.drawable.ic_rain, "Showers"),
}

data class ForecastDay(
    val date: LocalDate,
    val condition: WeatherCondition,
    val maxTemp: Int,
    val minTemp: Int,
    val rainChance: Int,
)

/**
 * The forecast stored in Room for one saved place (W3). [fetchedAt] is epoch millis, the time
 * Open-Meteo answered; [days] is empty when the stored JSON could not be read.
 */
data class CachedForecast(
    val placeId: String,
    val days: List<ForecastDay>,
    val fetchedAt: Long,
) {
    /**
     * true when the forecast was fetched more than [maxAgeMillis] before [now] (both epoch
     * millis). The screen refreshes its own accord at that point; a cache exactly at the limit
     * is still fresh, and a fetch time in the future (the clock was changed) is not stale.
     */
    fun isStale(now: Long, maxAgeMillis: Long): Boolean = now - fetchedAt > maxAgeMillis
}

/**
 * How a trip-wide refresh went (H): [refreshed] places now have a new forecast, [failed]
 * places kept their previous cache. One failure never stops the other places.
 */
data class RefreshSummary(
    val refreshed: Int,
    val failed: Int,
)
