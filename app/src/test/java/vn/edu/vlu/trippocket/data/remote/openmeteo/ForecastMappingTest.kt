package vn.edu.vlu.trippocket.data.remote.openmeteo

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.data.model.WeatherCondition
import java.time.LocalDate

/**
 * P0: the real Open-Meteo forecast JSON maps to
 * [vn.edu.vlu.trippocket.data.model.ForecastDay]; broken days are skipped, never thrown.
 */
class ForecastMappingTest {

    private val gson = Gson()

    private fun parse(file: String): ForecastResponse =
        gson.fromJson(readResource(file), ForecastResponse::class.java)

    @Test
    fun realForecast_mapsAllSixteenDays() {
        val days = parse("forecast_da_lat_16d.json").toForecastDays()

        assertEquals(16, days.size)
        val first = days.first()
        assertEquals(LocalDate.of(2026, 10, 31), first.date)
        assertEquals(WeatherCondition.RAIN, first.condition) // 95, thunderstorm
        assertEquals(26, first.maxTemp) // 26.3 rounded
        assertEquals(15, first.minTemp) // 15.4 rounded
        assertEquals(100, first.rainChance)

        val last = days.last()
        assertEquals(LocalDate.of(2026, 11, 15), last.date)
        assertEquals(WeatherCondition.CLOUDY, last.condition) // 3, overcast
        assertEquals(24, last.maxTemp) // 23.7 rounded
        assertEquals(14, last.minTemp) // 14.4 rounded
        assertEquals(95, last.rainChance)

        // The API sends local dates (timezone=auto); they stay in order.
        assertEquals(days.map { it.date }.sorted(), days.map { it.date })
    }

    @Test
    fun missingField_andNullValues_skipOnlyTheBrokenDays() {
        val days = parse("forecast_missing_field.json").toForecastDays()

        // precipitation_probability_max is absent and rows 2, 3, 4 and 8 are broken.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5)),
            days.map { it.date },
        )
        assertEquals(
            listOf(WeatherCondition.RAIN, WeatherCondition.RAIN, WeatherCondition.CLOUDY),
            days.map { it.condition },
        )
        assertEquals(listOf(0, 0, 0), days.map { it.rainChance })
        assertEquals(24, days.last().maxTemp) // 24.4 rounded
        assertEquals(15, days.last().minTemp) // 14.7 rounded
    }

    @Test
    fun nullRainChanceInsideTheArray_countsAsZero() {
        val json = """
            {
              "daily": {
                "time": ["2026-11-01", "2026-11-02"],
                "weather_code": [0, 61],
                "temperature_2m_max": [20.4, 19.6],
                "temperature_2m_min": [10.4, 9.6],
                "precipitation_probability_max": [null, 40]
              }
            }
        """.trimIndent()

        val days = gson.fromJson(json, ForecastResponse::class.java).toForecastDays()

        assertEquals(2, days.size)
        assertEquals(0, days[0].rainChance)
        assertEquals(40, days[1].rainChance)
    }

    @Test
    fun missingDailyBlock_isEmpty() {
        assertTrue(ForecastResponse().toForecastDays().isEmpty())
        assertTrue(ForecastResponse(DailyForecast()).toForecastDays().isEmpty())
        assertTrue(ForecastResponse(DailyForecast(time = emptyList())).toForecastDays().isEmpty())
    }

    private fun readResource(file: String): String =
        requireNotNull(javaClass.getResource("/openmeteo/$file")) { "Missing test resource $file" }.readText()
}
