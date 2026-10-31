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
)

data class DailyForecast(
    @SerializedName("time") val time: List<String?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
    @SerializedName("temperature_2m_max") val maxTemperature: List<Double?>? = null,
    @SerializedName("temperature_2m_min") val minTemperature: List<Double?>? = null,
    @SerializedName("precipitation_probability_max") val rainChance: List<Int?>? = null,
)

/**
 * Daily rows as [ForecastDay] models, in the order the API sent them.
 *
 * The arrays are read by index. A day is skipped when its date cannot be parsed or a required
 * value (weather code, max/min temperature) is missing, so one broken day cannot hide the
 * rest. A null rain chance counts as 0 %, because Open-Meteo sends null for days at the far
 * end of the range. Temperatures are rounded to whole degrees, as [ForecastDay] stores Int.
 */
fun ForecastResponse.toForecastDays(): List<ForecastDay> {
    val daily = daily ?: return emptyList()
    val dates = daily.time.orEmpty()
    val codes = daily.weatherCode.orEmpty()
    val maxTemps = daily.maxTemperature.orEmpty()
    val minTemps = daily.minTemperature.orEmpty()
    val rainChances = daily.rainChance.orEmpty()
    return dates.indices.mapNotNull { index ->
        val date = dates.getOrNull(index)?.let(::parseDateOrNull) ?: return@mapNotNull null
        val code = codes.getOrNull(index) ?: return@mapNotNull null
        val maxTemp = maxTemps.getOrNull(index) ?: return@mapNotNull null
        val minTemp = minTemps.getOrNull(index) ?: return@mapNotNull null
        ForecastDay(
            date = date,
            condition = WeatherCodes.of(code),
            maxTemp = maxTemp.roundToInt(),
            minTemp = minTemp.roundToInt(),
            rainChance = rainChances.getOrNull(index) ?: 0,
        )
    }
}

private fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    null
}
