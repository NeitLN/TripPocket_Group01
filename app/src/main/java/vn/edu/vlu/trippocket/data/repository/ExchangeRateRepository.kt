package vn.edu.vlu.trippocket.data.repository

import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.dao.ExchangeRateCacheDao
import vn.edu.vlu.trippocket.data.local.entity.ExchangeRateCacheEntity
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
data class SupportedCurrency(
    val code: String,
    @param:StringRes val nameRes: Int,
    val flagCountryCode: String,
)

/**
 * A1: every good answer is saved in [cache]; when the next download fails (Airplane Mode, server
 * error) the saved rates come back marked [ExchangeRates.fromCache], so "Paid in" still converts
 * offline. Only with nothing saved does the failure reach the screen.
 */
class ExchangeRateRepository(
    private val api: ExchangeRateApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cache: ExchangeRateCacheDao? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        private const val BASE_CURRENCY = "USD"
        private const val TARGET_CURRENCY = "VND"

        val SUPPORTED_CURRENCIES: List<SupportedCurrency> = listOf(
            SupportedCurrency("USD", R.string.currency_usd, "us"),
            SupportedCurrency("EUR", R.string.currency_eur, "eu"),
            SupportedCurrency("JPY", R.string.currency_jpy, "jp"),
            SupportedCurrency("KRW", R.string.currency_krw, "kr"),
            SupportedCurrency("THB", R.string.currency_thb, "th"),
            SupportedCurrency("SGD", R.string.currency_sgd, "sg"),
            SupportedCurrency("GBP", R.string.currency_gbp, "gb"),
            SupportedCurrency("AUD", R.string.currency_aud, "au"),
            SupportedCurrency("CNY", R.string.currency_cny, "cn"),
            SupportedCurrency("TWD", R.string.currency_twd, "tw"),
            SupportedCurrency("MYR", R.string.currency_myr, "my"),
            SupportedCurrency("IDR", R.string.currency_idr, "id"),
        )
    }

    suspend fun getExchangeRates(): ApiResult<ExchangeRates> {
        val downloaded = download()
        if (downloaded is ApiResult.Success) return downloaded
        return savedRates() ?: downloaded
    }

    private suspend fun download(): ApiResult<ExchangeRates> {
        val result = safeApiCall(dispatcher) { api.getLatestRates(BASE_CURRENCY) }
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val response = result.data
                // Detect HTTP 200 payload with result="error" or empty rates or missing VND
                if (response.result != "success" || response.rates.isNullOrEmpty()) {
                    return ApiResult.Failure(ApiError.BAD_RESPONSE)
                }
                val rates = toRates(response.rates, response.timeLastUpdateUtc.orEmpty(), fromCache = false)
                    ?: return ApiResult.Failure(ApiError.BAD_RESPONSE)
                save(response.rates, rates.lastUpdatedUtc)
                ApiResult.Success(rates)
            }
        }
    }

    /**
     * Supported currencies against VND from a base-USD map; null without a usable VND rate.
     * Currencies missing from the map are dropped.
     */
    private fun toRates(rates: Map<String, Double>, lastUpdatedUtc: String, fromCache: Boolean): ExchangeRates? {
        val vndRate = rates[TARGET_CURRENCY]
        if (vndRate == null || vndRate <= 0.0) return null
        val items = SUPPORTED_CURRENCIES.mapNotNull { meta ->
            val rateAgainstBase = rates[meta.code]
            if (rateAgainstBase == null || rateAgainstBase <= 0.0) {
                null
            } else {
                // 1 unit of meta.code = (vndRate / rateAgainstBase) VND
                CurrencyRateItem(
                    code = meta.code,
                    nameRes = meta.nameRes,
                    flagCountryCode = meta.flagCountryCode,
                    rateToVnd = vndRate / rateAgainstBase,
                )
            }
        }
        return ExchangeRates(lastUpdatedUtc, BASE_CURRENCY, items, fromCache)
    }

    /** Keeps the rates the app uses (VND and the supported currencies), not all ~160 of them. */
    private suspend fun save(rates: Map<String, Double>, lastUpdatedUtc: String) {
        val cache = cache ?: return
        val kept = JSONObject()
        (SUPPORTED_CURRENCIES.map { it.code } + TARGET_CURRENCY).forEach { code ->
            rates[code]?.let { kept.put(code, it) }
        }
        try {
            withContext(dispatcher) {
                cache.upsert(ExchangeRateCacheEntity(BASE_CURRENCY, kept.toString(), lastUpdatedUtc, clock()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A full disk only costs the offline copy; the fresh rates are still returned.
        }
    }

    /** The last saved rates as a success marked fromCache, or null when there are none or they are unreadable. */
    private suspend fun savedRates(): ApiResult<ExchangeRates>? {
        val cache = cache ?: return null
        return try {
            val row = withContext(dispatcher) { cache.get(BASE_CURRENCY) } ?: return null
            val json = JSONObject(row.ratesJson)
            val rates = json.keys().asSequence().associateWith { json.getDouble(it) }
            toRates(rates, row.lastUpdatedUtc, fromCache = true)
                ?.takeIf { it.items.isNotEmpty() }
                ?.let { ApiResult.Success(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
