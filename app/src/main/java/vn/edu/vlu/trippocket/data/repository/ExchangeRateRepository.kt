package vn.edu.vlu.trippocket.data.repository

import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.CurrencyRateItem
import vn.edu.vlu.trippocket.data.model.ExchangeRates
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateApi
import vn.edu.vlu.trippocket.data.remote.safeApiCall
import vn.edu.vlu.trippocket.domain.ApiError

/**
 * Repository for foreign exchange rates (Milestone 4).
 * Fetches latest exchange rates using base currency USD from open.er-api.com,
 * then computes the cross-rate to VND for common travel currencies.
 *
 * Why base USD instead of latest/VND:
 * 1. open.er-api.com caches and updates primarily on USD.
 * 2. 1 VND is ~0.000039 USD; using VND as base yields tiny fractional rates
 *    prone to floating-point precision loss when converting between currencies.
 * 3. Consistent endpoint contracts for testing (GET /v6/latest/USD).
 */
class ExchangeRateRepository(
    private val api: ExchangeRateApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private data class CurrencyMeta(
        val code: String,
        @param:StringRes val nameRes: Int,
        val flagCountryCode: String,
    )

    companion object {
        private const val BASE_CURRENCY = "USD"
        private const val TARGET_CURRENCY = "VND"

        private val SUPPORTED_CURRENCIES = listOf(
            CurrencyMeta("USD", R.string.currency_usd, "us"),
            CurrencyMeta("EUR", R.string.currency_eur, "eu"),
            CurrencyMeta("JPY", R.string.currency_jpy, "jp"),
            CurrencyMeta("KRW", R.string.currency_krw, "kr"),
            CurrencyMeta("THB", R.string.currency_thb, "th"),
            CurrencyMeta("SGD", R.string.currency_sgd, "sg"),
            CurrencyMeta("GBP", R.string.currency_gbp, "gb"),
            CurrencyMeta("AUD", R.string.currency_aud, "au"),
            CurrencyMeta("CNY", R.string.currency_cny, "cn"),
            CurrencyMeta("TWD", R.string.currency_twd, "tw"),
            CurrencyMeta("MYR", R.string.currency_myr, "my"),
            CurrencyMeta("IDR", R.string.currency_idr, "id"),
        )
    }

    suspend fun getExchangeRates(): ApiResult<ExchangeRates> {
        val result = safeApiCall(dispatcher) { api.getLatestRates(BASE_CURRENCY) }
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val response = result.data
                // Detect HTTP 200 payload with result="error" or empty rates or missing VND
                if (response.result != "success" || response.rates.isNullOrEmpty()) {
                    return ApiResult.Failure(ApiError.BAD_RESPONSE)
                }
                val vndRate = response.rates[TARGET_CURRENCY]
                if (vndRate == null || vndRate <= 0.0) {
                    return ApiResult.Failure(ApiError.BAD_RESPONSE)
                }

                // Map supported currencies; drop currencies missing from response
                val items = SUPPORTED_CURRENCIES.mapNotNull { meta ->
                    val rateAgainstBase = response.rates[meta.code]
                    if (rateAgainstBase == null || rateAgainstBase <= 0.0) {
                        null
                    } else {
                        // 1 unit of meta.code = (vndRate / rateAgainstBase) VND
                        val rateToVnd = vndRate / rateAgainstBase
                        CurrencyRateItem(
                            code = meta.code,
                            nameRes = meta.nameRes,
                            flagCountryCode = meta.flagCountryCode,
                            rateToVnd = rateToVnd,
                        )
                    }
                }

                val lastUpdated = response.timeLastUpdateUtc.orEmpty()
                ApiResult.Success(
                    ExchangeRates(
                        lastUpdatedUtc = lastUpdated,
                        baseCurrency = response.baseCode ?: BASE_CURRENCY,
                        items = items,
                    )
                )
            }
        }
    }
}
