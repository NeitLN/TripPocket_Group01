package vn.edu.vlu.trippocket.domain

import java.math.BigDecimal
import java.math.RoundingMode

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
     * The result is rounded to the nearest whole dong, on the decimal values: multiplying the
     * binary doubles first can land just below a .5 tie (0.29 * 50 = 14.499999999999998), which
     * used to round a dong too low.
     * Returns 0L if either value is not finite, or is NaN or non-positive.
     */
    fun toVnd(amount: Double, rateToVnd: Double): Long {
        if (!amount.isFinite() || !rateToVnd.isFinite()) return 0L
        if (amount <= 0.0 || rateToVnd <= 0.0) return 0L
        val vnd = BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(rateToVnd))
            .setScale(0, RoundingMode.HALF_UP)
        // An absurd amount must not overflow the Long the rest of the app stores.
        return if (vnd > LONG_MAX) Long.MAX_VALUE else vnd.longValueExact()
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

    private val LONG_MAX: BigDecimal = BigDecimal.valueOf(Long.MAX_VALUE)
}
