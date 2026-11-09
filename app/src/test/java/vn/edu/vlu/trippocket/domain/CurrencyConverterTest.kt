package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CurrencyConverter].
 * Ensures foreign amounts are accurately converted to VND and rounded to whole dong.
 */
class CurrencyConverterTest {

    @Test
    fun toVnd_standardUsdConversion() {
        val amount = 100.0
        val rate = 25611.0
        assertEquals(2_561_100L, CurrencyConverter.toVnd(amount, rate))
    }

    @Test
    fun toVnd_fractionalAmountsAndSmallCurrenciesRoundProperly() {
        // IDR: 1 IDR = 1.58 VND
        assertEquals(1_580_000L, CurrencyConverter.toVnd(1_000_000.0, 1.58))

        // KRW: 1 KRW = 18.25 VND
        assertEquals(182_500L, CurrencyConverter.toVnd(10_000.0, 18.25))

        // JPY: 10.5 JPY * 167.94 = 1763.37 -> 1763 VND
        assertEquals(1_763L, CurrencyConverter.toVnd(10.5, 167.94))
    }

    @Test
    fun toVnd_roundingBoundaries() {
        // 1.5 * 1.5 = 2.25 -> rounds to 2
        assertEquals(2L, CurrencyConverter.toVnd(1.5, 1.5))

        // 1.5 * 1.7 = 2.55 -> rounds to 3
        assertEquals(3L, CurrencyConverter.toVnd(1.5, 1.7))

        // 2.5 * 1.0 = 2.5 -> roundToLong rounds half up (3)
        assertEquals(3L, CurrencyConverter.toVnd(2.5, 1.0))
    }

    @Test
    fun toVnd_zeroOrNegativeAmounts_returnZero() {
        assertEquals(0L, CurrencyConverter.toVnd(0.0, 25000.0))
        assertEquals(0L, CurrencyConverter.toVnd(-50.0, 25000.0))
    }

    @Test
    fun toVnd_invalidRates_returnZero() {
        assertEquals(0L, CurrencyConverter.toVnd(100.0, 0.0))
        assertEquals(0L, CurrencyConverter.toVnd(100.0, -25000.0))
        assertEquals(0L, CurrencyConverter.toVnd(100.0, Double.NaN))
        assertEquals(0L, CurrencyConverter.toVnd(Double.NaN, 25000.0))
    }

    @Test
    fun toVnd_largeAmounts() {
        // 1,000,000 USD
        assertEquals(25_611_000_000L, CurrencyConverter.toVnd(1_000_000.0, 25611.0))
    }
}
