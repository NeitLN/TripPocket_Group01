package vn.edu.vlu.trippocket.data.remote.openmeteo

import java.time.LocalDate

/**
 * W3 test double for Open-Meteo: answers [response] for every request, records the coordinates
 * (so a test can check which place was refreshed), or fails with [failWith].
 */
class FakeForecastApi : ForecastApi {

    val requests = mutableListOf<Pair<Double, Double>>()
    var failWith: Exception? = null
    var response: ForecastResponse = of(LocalDate.of(2026, 10, 31))

    override suspend fun daily(
        latitude: Double,
        longitude: Double,
        daily: String,
        timezone: String,
        forecastDays: Int,
    ): ForecastResponse {
        requests += latitude to longitude
        failWith?.let { throw it }
        return response
    }

    companion object {
        /** Sunny days at [maxTemp] degrees, the shape of a real Open-Meteo answer. */
        fun of(vararg dates: LocalDate, maxTemp: Int = 25): ForecastResponse = ForecastResponse(
            DailyForecast(
                time = dates.map { it.toString() },
                weatherCode = dates.map { 0 },
                maxTemperature = dates.map { maxTemp.toDouble() },
                minTemperature = dates.map { (maxTemp - 8).toDouble() },
                rainChance = dates.map { 10 },
            )
        )
    }
}
