package vn.edu.vlu.trippocket.data.remote.exchangerate

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Exchange rate API from open.er-api.com (Milestone 4).
 * Fetches latest foreign exchange rates against a base currency.
 */
interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String): ExchangeRateResponse
}
