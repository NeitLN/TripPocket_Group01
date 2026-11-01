package vn.edu.vlu.trippocket.data.remote.openmeteo

import com.google.gson.annotations.SerializedName
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.domain.WeatherCodes
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * Response of `GET v1/forecast` (owner: Duy Anh). The API sends one array per requested
 * daily field, aligned by position; every value is nullable because an array can be absent,
 * shorter than "time", or hold null (Open-Meteo does that for far days).
 */
data class ForecastResponse(
    @SerializedName("daily") val daily: DailyForecast? = null,
    @SerializedName("hourly") val hourly: HourlyForecast? = null,
)

data class DailyForecast(
    @SerializedName("time") val time: List<String?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
    @SerializedName("temperature_2m_max") val maxTemperature: List<Double?>? = null,
    @SerializedName("temperature_2m_min") val minTemperature: List<Double?>? = null,
    @SerializedName("precipitation_probability_max") val rainChance: List<Int?>? = null,
)

/** Hourly arrays, aligned by position like [DailyForecast]; "time" is local ("2026-11-17T19:00"). */
data class HourlyForecast(
    @SerializedName("time") val time: List<String?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
    @SerializedName("temperature_2m") val temperature: List<Double?>? = null,
    @SerializedName("precipitation_probability") val rainChance: List<Int?>? = null,
)

/**
 * Daily rows as [ForecastDay] models, in the order the API sent them.
 *
 * The arrays are read by index. A required value (weather code, max/min temperature) that the
 * daily arrays leave null is taken from the day's hours when at least [MIN_HOURS_FOR_A_DAY] of
 * them are known: Open-Meteo leaves the last local day null east of UTC (W: 16 days asked, 15
 * cached). A day is skipped when its date cannot be parsed or a required value is still
 * missing, so one broken day cannot hide the rest. A null rain chance counts as 0 %.
 * Temperatures are rounded to whole degrees, as [ForecastDay] stores Int.
 */
fun ForecastResponse.toForecastDays(): List<ForecastDay> {
    val daily = daily ?: return emptyList()
    val dates = daily.time.orEmpty()
    val codes = daily.weatherCode.orEmpty()
    val maxTemps = daily.maxTemperature.orEmpty()
    val minTemps = daily.minTemperature.orEmpty()
    val rainChances = daily.rainChance.orEmpty()
    // Only worked out when a daily value is missing.
    val hoursByDate by lazy { hourly?.daysFromHours().orEmpty() }
    return dates.indices.mapNotNull { index ->
        val date = dates.getOrNull(index)?.let(::parseDateOrNull) ?: return@mapNotNull null
        val code = codes.getOrNull(index) ?: hoursByDate[date]?.code ?: return@mapNotNull null
        val maxTemp = maxTemps.getOrNull(index) ?: hoursByDate[date]?.maxTemp ?: return@mapNotNull null
        val minTemp = minTemps.getOrNull(index) ?: hoursByDate[date]?.minTemp ?: return@mapNotNull null
        ForecastDay(
            date = date,
            condition = WeatherCodes.of(code),
            maxTemp = maxTemp.roundToInt(),
            minTemp = minTemp.roundToInt(),
            rainChance = rainChances.getOrNull(index) ?: hoursByDate[date]?.rainChance ?: 0,
        )
    }
}

/** A day of the forecast is only built from its hours when this many hours are known. */
const val MIN_HOURS_FOR_A_DAY = 18

/** One local day summed up from its hours, the way Open-Meteo builds its daily values. */
private data class DayFromHours(val code: Int, val maxTemp: Double, val minTemp: Double, val rainChance: Int?)

/**
 * Days with at least [MIN_HOURS_FOR_A_DAY] known hours (weather code and temperature both set).
 * The weather code is the highest of the day, as Open-Meteo's daily code is the most severe
 * condition; the rain chance is the highest hourly one.
 */
private fun HourlyForecast.daysFromHours(): Map<LocalDate, DayFromHours> {
    val times = time.orEmpty()
    val codes = weatherCode.orEmpty()
    val temps = temperature.orEmpty()
    val rains = rainChance.orEmpty()
    return times.indices
        .filter { codes.getOrNull(it) != null && temps.getOrNull(it) != null }
        .groupBy { times[it]?.take(DATE_LENGTH)?.let(::parseDateOrNull) }
        .mapNotNull { (date, hours) ->
            if (date == null || hours.size < MIN_HOURS_FOR_A_DAY) return@mapNotNull null
            val dayTemps = hours.map { temps[it]!! }
            date to DayFromHours(
                code = hours.maxOf { codes[it]!! },
                maxTemp = dayTemps.max(),
                minTemp = dayTemps.min(),
                rainChance = hours.mapNotNull { rains.getOrNull(it) }.maxOrNull(),
            )
        }
        .toMap()
}

/** "2026-11-17" out of "2026-11-17T19:00". */
private const val DATE_LENGTH = 10

private fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    null
}
