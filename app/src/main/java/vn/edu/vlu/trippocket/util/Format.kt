package vn.edu.vlu.trippocket.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Display formatting shared by all screens. Money is always a Long in VND. */
object Format {
    private val number = NumberFormat.getIntegerInstance(Locale.US)
    private val decimalRate = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    val dayMonthTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US)
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    val weekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.US)
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    fun vnd(amount: Long): String = "${number.format(amount)} ₫"

    fun amount(amount: Long): String = number.format(amount)

    fun signedVnd(amount: Long): String = when {
        amount > 0 -> "+${vnd(amount)}"
        amount < 0 -> "-${vnd(abs(amount))}"
        else -> vnd(0)
    }

    fun dateRange(start: LocalDate, end: LocalDate): String =
        "${start.format(dayMonth)} - ${end.format(dayMonthYear)}"

    /**
     * Formats an exchange rate to VND string.
     * Rates >= 100 are rounded to whole dong (e.g. "25,611 ₫").
     * Rates < 100 retain up to two decimals so small currencies (IDR, KRW) don't lose precision (e.g. "1.58 ₫").
     */
    fun rateToVnd(rate: Double): String = if (rate >= 100.0) {
        "${number.format(rate.roundToLong())} ₫"
    } else {
        "${decimalRate.format(rate)} ₫"
    }
}
