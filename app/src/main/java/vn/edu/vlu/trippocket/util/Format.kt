package vn.edu.vlu.trippocket.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Display formatting shared by all screens. Money is always a Long in VND. */
object Format {
    private val number = NumberFormat.getIntegerInstance(Locale.US)

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
}
