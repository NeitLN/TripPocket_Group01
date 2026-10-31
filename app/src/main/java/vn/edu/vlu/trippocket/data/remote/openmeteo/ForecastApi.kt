package vn.edu.vlu.trippocket.data.remote.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo forecast API (https://api.open-meteo.com/).
 * [vn.edu.vlu.trippocket.di.AppContainer.forecastRetrofit] supplies the base URL and client.
 *
 * Only [vn.edu.vlu.trippocket.data.repository.WeatherRepository] calls it; screens never do.
 */
interface ForecastApi {

    /**
     * Daily forecast for a coordinate. `timezone=auto` makes the API return local dates, the
     * same calendar days the trip uses. Sixteen days is the API maximum and matches
     * [vn.edu.vlu.trippocket.domain.ForecastMatcher].
     */
    @GET("v1/forecast")
    suspend fun daily(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = DEFAULT_FORECAST_DAYS,
    ): ForecastResponse

    companion object {
        const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
        const val DEFAULT_FORECAST_DAYS = 16
    }
}
