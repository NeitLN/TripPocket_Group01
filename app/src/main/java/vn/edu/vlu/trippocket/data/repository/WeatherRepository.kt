package vn.edu.vlu.trippocket.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import vn.edu.vlu.trippocket.data.local.dao.SavedPlaceDao
import vn.edu.vlu.trippocket.data.local.dao.WeatherCacheDao
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity
import vn.edu.vlu.trippocket.data.local.entity.WeatherCacheEntity
import vn.edu.vlu.trippocket.data.model.CachedForecast
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.RefreshSummary
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.model.WeatherCondition
import vn.edu.vlu.trippocket.data.remote.openmeteo.ForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.GeocodingApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.toForecastDays
import vn.edu.vlu.trippocket.data.remote.openmeteo.toPlaces
import vn.edu.vlu.trippocket.domain.DailyWeather
import vn.edu.vlu.trippocket.domain.WeatherError
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Place search (W1), the forecast cache of a saved place (W3) and the daily contract of the
 * itinerary (W5).
 *
 * The APIs, the DAOs, the [Gson] and the clock are constructor parameters, so tests use fakes
 * and an in-memory database and never touch the network or the real clock.
 *
 * The forecast cache is not part of the backup: [refreshForecast] writes straight to
 * [WeatherCacheDao] and never goes through TripWriter, so downloading weather does not mark the
 * trip PENDING.
 */
class WeatherRepository(
    private val geocodingApi: GeocodingApi,
    private val forecastApi: ForecastApi,
    private val cacheDao: WeatherCacheDao,
    private val savedPlaceDao: SavedPlaceDao,
    private val gson: Gson = Gson(),
    private val now: () -> Long = System::currentTimeMillis,
) {

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

    /**
     * Downloads the 16-day forecast of [place] and replaces its cached copy.
     *
     * A network, HTTP or JSON failure is thrown before anything is written, so the previous
     * cache is kept and the screen keeps showing the forecast from the last sync.
     */
    suspend fun refreshForecast(place: SavedPlace) {
        val days = forecastApi.daily(place.latitude, place.longitude).toForecastDays()
        cacheDao.upsert(
            WeatherCacheEntity(
                placeId = place.placeId,
                forecastJson = gson.toJson(days.map { CachedDay.of(it) }),
                fetchedAt = now(),
            )
        )
    }

    /**
     * The cached forecast of [placeId], or null when that place has never been refreshed.
     * The screen reads the forecast only from here (Room), never from the API.
     */
    fun observeForecast(placeId: String): Flow<CachedForecast?> =
        cacheDao.observe(placeId).map { row ->
            row?.let { CachedForecast(it.placeId, decode(it.forecastJson), it.fetchedAt) }
        }

    /**
     * Reloads the forecast of every place saved to [tripId], one after another, and reports how
     * many were refreshed and how many kept their previous cache. A failing place does not stop
     * the places after it.
     *
     * For A4 (Tiến): call this after a Restore, because the forecast cache is not part of the
     * backup (docs/tasks/W-places-weather.md, W3), so a restored trip starts with no forecast.
     */
    suspend fun refreshTripForecasts(tripId: String): RefreshSummary {
        val places = savedPlaceDao.observeByTrip(tripId).first().map { it.toSavedPlace() }
        var refreshed = 0
        var failed = 0
        for (place in places) {
            try {
                refreshForecast(place)
                refreshed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // This place keeps its cache; the next place is still worth trying.
                failed++
            }
        }
        return RefreshSummary(refreshed, failed)
    }

    /**
     * Contract for W5 (Itinerary, Tiến): the weather of a trip, from the cache.
     *
     * The first saved place of the trip is the place whose forecast the itinerary shows (the
     * places are in the order they were saved). The trip having no place is a state of its own,
     * [DailyWeather.NoSavedPlace], never a crash. A place without a downloaded forecast gives
     * [DailyWeather.Cached] with an empty [DailyWeather.Cached.days] and a null `fetchedAt`;
     * [DailyWeather.Cached.on] then returns null for every date.
     *
     * Emits again when the places of the trip change (saved, deleted, reordered) or when the
     * cache of that first place is written.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDailyWeather(tripId: String): Flow<DailyWeather> =
        savedPlaceDao.observeByTrip(tripId).flatMapLatest { places ->
            val placeId = places.firstOrNull()?.placeId
            if (placeId == null) {
                flowOf(DailyWeather.NoSavedPlace)
            } else {
                cacheDao.observe(placeId).map { row ->
                    DailyWeather.Cached(
                        days = row?.let { decode(it.forecastJson) }.orEmpty(),
                        fetchedAt = row?.fetchedAt,
                    )
                }
            }
        }

    /**
     * Reads the JSON written by [refreshForecast]. A row with broken JSON (an older format, a
     * write cut short) gives an empty list instead of an exception, so the screen shows
     * "Forecast not available yet" and the next refresh replaces the row.
     */
    private fun decode(json: String): List<ForecastDay> = try {
        val type = object : TypeToken<List<CachedDay>>() {}.type
        gson.fromJson<List<CachedDay>>(json, type).orEmpty().mapNotNull(CachedDay::toForecastDay)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * One day of the cache JSON: the date is an ISO string and the condition is the enum name,
     * so the stored rows never depend on how Gson handles [LocalDate] or enums.
     */
    private data class CachedDay(
        val date: String = "",
        val condition: String = "",
        val maxTemp: Int = 0,
        val minTemp: Int = 0,
        val rainChance: Int = 0,
    ) {
        fun toForecastDay(): ForecastDay? {
            val day = try {
                LocalDate.parse(date)
            } catch (e: DateTimeParseException) {
                return null
            }
            val weather = WeatherCondition.entries.firstOrNull { it.name == condition } ?: return null
            return ForecastDay(day, weather, maxTemp, minTemp, rainChance)
        }

        companion object {
            fun of(day: ForecastDay) = CachedDay(
                date = day.date.toString(),
                condition = day.condition.name,
                maxTemp = day.maxTemp,
                minTemp = day.minTemp,
                rainChance = day.rainChance,
            )
        }
    }
}

/** The stored row as the model the rest of the app uses; only the fields a refresh needs. */
private fun SavedPlaceEntity.toSavedPlace() = SavedPlace(
    placeId = placeId,
    tripId = tripId,
    name = name,
    country = country,
    latitude = latitude,
    longitude = longitude,
    note = note,
)

/**
 * The reason a request failed, as the screen shows it (H). Called where the request was made,
 * because only this layer knows Retrofit: [HttpException] for an HTTP error, [IOException] when
 * the phone could not reach the service, anything else (JSON of an unexpected shape) as
 * [WeatherError.BAD_RESPONSE].
 */
internal fun Throwable.toWeatherError(): WeatherError = when {
    this is IOException -> WeatherError.NETWORK
    this is HttpException -> when {
        code() == 429 -> WeatherError.RATE_LIMITED
        code() in 500..599 -> WeatherError.SERVER
        else -> WeatherError.BAD_RESPONSE
    }
    else -> WeatherError.BAD_RESPONSE
}

