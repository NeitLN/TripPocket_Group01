package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.WeatherCondition
import java.time.LocalDate

/** P0: matching a trip day to the forecast of that exact day, for W5 (Itinerary). */
class ForecastMatcherTest {

    private fun day(date: String) = ForecastDay(
        date = LocalDate.parse(date),
        condition = WeatherCondition.SUNNY,
        maxTemp = 25,
        minTemp = 16,
        rainChance = 10,
    )

    private val forecast = listOf(
        day("2026-11-01"),
        day("2026-11-02"),
        // 2026-11-03 is missing from the API answer.
        day("2026-11-04"),
    )

    @Test
    fun dayInTheForecast_returnsThatDay() {
        assertSame(forecast[1], ForecastMatcher.forDate(forecast, LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun emptyForecast_returnsNull() {
        assertNull(ForecastMatcher.forDate(emptyList(), LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun dayBeforeOrAfterTheRange_returnsNull() {
        assertNull(ForecastMatcher.forDate(forecast, LocalDate.of(2026, 10, 31)))
        assertNull(ForecastMatcher.forDate(forecast, LocalDate.of(2026, 11, 5)))
        assertNull(ForecastMatcher.forDate(forecast, LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun missingDayInTheMiddle_returnsNull() {
        assertNull(ForecastMatcher.forDate(forecast, LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun anotherDayIsNeverSubstituted() {
        // A trip day far beyond the 16-day range must not borrow the first or today's row.
        assertNull(ForecastMatcher.forDate(forecast, LocalDate.of(2026, 12, 25)))
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-04"), forecast.map { it.date.toString() })
    }
}
