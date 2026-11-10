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

    @Test
    fun realForecastEastOfUtc_buildsTheLastDayFromItsHours() {
        // Asked on 02/11 for Da Lat: the 16th local day has 20 of 24 hours, so its daily
        // code and temperatures are null.
        val days = parse("forecast_da_lat_16d_hourly.json").toForecastDays()

        assertEquals(16, days.size)
        val first = days.first()
        assertEquals(LocalDate.of(2026, 11, 2), first.date)
        assertEquals(WeatherCondition.RAIN, first.condition) // 55, drizzle
        assertEquals(23, first.maxTemp) // 22.9 rounded, from the daily values

        val last = days.last()
        assertEquals(LocalDate.of(2026, 11, 17), last.date)
        assertEquals(WeatherCondition.RAIN, last.condition) // 80, the highest hourly code
        assertEquals(25, last.maxTemp) // 24.8, the warmest of its 20 hours
        assertEquals(15, last.minTemp) // 14.9, the coolest of its 20 hours
        assertEquals(90, last.rainChance) // the daily value was there
    }

    @Test
    fun aDayWithTooFewKnownHours_isStillSkipped() {
        assertEquals(1, partlyCovered(knownHours = MIN_HOURS_FOR_A_DAY - 1).toForecastDays().size)

        val days = partlyCovered(knownHours = MIN_HOURS_FOR_A_DAY).toForecastDays()
        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 11, 17), days.last().date)
        assertEquals(WeatherCondition.CLOUDY, days.last().condition) // 3, the highest hourly code
        assertEquals(20 + MIN_HOURS_FOR_A_DAY - 1, days.last().maxTemp)
        assertEquals(20, days.last().minTemp)
        assertEquals(40, days.last().rainChance)
    }

    @Test
    fun dailyValues_winOverTheHours() {
        val response = partlyCovered(knownHours = 24).copy(
            daily = DailyForecast(
                time = listOf("2026-11-16", "2026-11-17"),
                weatherCode = listOf(0, 0),
                maxTemperature = listOf(30.0, 31.0),
                minTemperature = listOf(20.0, 21.0),
                rainChance = listOf(5, 6),
            )
        )

        val last = response.toForecastDays().last()
        assertEquals(WeatherCondition.SUNNY, last.condition)
        assertEquals(31, last.maxTemp)
        assertEquals(21, last.minTemp)
        assertEquals(6, last.rainChance)
    }

    /**
     * Two days: 16/11 complete in the daily arrays, 17/11 null there and known for
     * [knownHours] hours (20, 21, 22 ... degrees; code 1 then 3 at the last hour).
     */
    private fun partlyCovered(knownHours: Int) = ForecastResponse(
        daily = DailyForecast(
            time = listOf("2026-11-16", "2026-11-17"),
            weatherCode = listOf(0, null),
            maxTemperature = listOf(30.0, null),
            minTemperature = listOf(20.0, null),
            rainChance = listOf(10, null),
        ),
        hourly = HourlyForecast(
            time = (0 until 24).map { "2026-11-17T%02d:00".format(it) },
            weatherCode = (0 until 24).map { if (it < knownHours) (if (it == knownHours - 1) 3 else 1) else null },
            temperature = (0 until 24).map { if (it < knownHours) 20.0 + it else null },
            rainChance = (0 until 24).map { if (it == 5) 40 else 0 },
        ),
    )

    private fun readResource(file: String): String =
        requireNotNull(javaClass.getResource("/openmeteo/$file")) { "Missing test resource $file" }.readText()
}
