package vn.edu.vlu.trippocket.data.model

import androidx.annotation.StringRes

/**
 * Exchange rates domain/data model for Milestone 4 (foreign currency conversion to VND).
 *
 * @property lastUpdatedUtc UTC timestamp string provided by the API (e.g. "Tue, 05 Nov 2026 00:02:31 +0000").
 * @property baseCurrency The base currency requested from the API (e.g. "USD").
 * @property items The supported currency items with exchange rates converted to VND.
 */
data class ExchangeRates(
    val lastUpdatedUtc: String,
    val baseCurrency: String,
    val items: List<CurrencyRateItem>,
)

/**
 * One currency row.
 *
 * @property code 3-letter currency code, e.g. "USD", "JPY".
 * @property nameRes String resource for the currency name (English, e.g. R.string.currency_usd).
 * @property flagCountryCode 2-letter ISO country code for FlagCDN, e.g. "us", "jp".
 * @property rateToVnd Exchange rate representing the value of 1 unit of this currency in VND.
 *                     Kept as [Double] to avoid losing precision on fractional/small currencies (e.g. IDR, KRW).
 */
data class CurrencyRateItem(
    val code: String,
    @param:StringRes val nameRes: Int,
    val flagCountryCode: String,
    val rateToVnd: Double,
)
