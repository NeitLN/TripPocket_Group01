package vn.edu.vlu.trippocket.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

/**
 * Pure Kotlin utility for currency conversion (Milestone 4).
 * Converts foreign currency amounts to Vietnamese Dong (VND) and rounds
 * the final amount to the nearest whole dong (Long), matching Room storage.
 */
object CurrencyConverter {

    /**
     * Converts [amount] in foreign currency to VND using [rateToVnd]
     * (the value of 1 unit of foreign currency in VND).
     *
     * The result is rounded to the nearest whole dong.
     * Returns 0L if either [amount] or [rateToVnd] is NaN or non-positive.
     */
    fun toVnd(amount: Double, rateToVnd: Double): Long {
        if (amount.isNaN() || rateToVnd.isNaN() || rateToVnd <= 0.0 || amount <= 0.0) return 0L
        return (amount * rateToVnd).roundToLong()
    }

    /**
     * The foreign amount worth [vnd] at [rateToVnd], rounded to 2 decimals and written the way the
     * "Paid in" field accepts it ("12", "10.5", "29.99"). "" when the rate is not usable.
     */
    fun fromVnd(vnd: Long, rateToVnd: Double): String {
        if (rateToVnd.isNaN() || rateToVnd.isInfinite() || rateToVnd <= 0.0 || vnd <= 0L) return ""
        return BigDecimal.valueOf(vnd)
            .divide(BigDecimal.valueOf(rateToVnd), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
