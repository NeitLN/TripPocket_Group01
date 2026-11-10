package vn.edu.vlu.trippocket.data.remote.openmeteo

import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate

/**
 * W3 test double for Open-Meteo: answers [response] for every request, records the coordinates
 * (so a test can check which place was refreshed), or fails with [failWith].
 */
class FakeForecastApi : ForecastApi {

    val requests = mutableListOf<Pair<Double, Double>>()
    var failWith: Exception? = null

    /** When set, only the requests this returns true for fail (with [failWith]). */
    var failFor: ((Pair<Double, Double>) -> Boolean)? = null

    /** Holds a request open, so a test can see the screen while a download runs. */
    var gate: CompletableDeferred<Unit>? = null

    var response: ForecastResponse = of(LocalDate.of(2026, 10, 31))

    override suspend fun daily(
        latitude: Double,
        longitude: Double,
        daily: String,
        hourly: String,
        timezone: String,
        forecastDays: Int,
    ): ForecastResponse {
        val request = latitude to longitude
        requests += request
        gate?.await()
        val failure = failWith
        if (failure != null && (failFor?.invoke(request) ?: true)) throw failure
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
