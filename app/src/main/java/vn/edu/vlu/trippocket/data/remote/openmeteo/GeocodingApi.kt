package vn.edu.vlu.trippocket.data.remote.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo geocoding API (https://geocoding-api.open-meteo.com/).
 * [vn.edu.vlu.trippocket.di.AppContainer.geocodingRetrofit] supplies the base URL and client.
 *
 * Only [vn.edu.vlu.trippocket.data.repository.WeatherRepository] calls it; screens never do.
 */
interface GeocodingApi {

    /**
     * Searches places by name. The response has no "results" key when nothing matches,
     * which [toPlaces] turns into an empty list.
     */
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = DEFAULT_COUNT,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponse

    companion object {
        const val DEFAULT_COUNT = 10
    }
}
