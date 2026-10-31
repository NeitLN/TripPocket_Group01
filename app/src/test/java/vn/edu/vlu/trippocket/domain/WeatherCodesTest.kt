package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.edu.vlu.trippocket.data.model.WeatherCondition

/** P0: the WMO grouping documented in [WeatherCodes], one case per group. */
class WeatherCodesTest {

    private fun assertCodes(condition: WeatherCondition, vararg codes: Int) {
        for (code in codes) {
            assertEquals("WMO $code", condition, WeatherCodes.of(code))
        }
    }

    @Test
    fun clearAndMainlyClear_areSunny() {
        assertCodes(WeatherCondition.SUNNY, 0, 1)
    }

    @Test
    fun partlyCloudyOvercastAndFog_areCloudy() {
        assertCodes(WeatherCondition.CLOUDY, 2, 3, 45, 48)
    }

    @Test
    fun drizzleFreezingDrizzle_areRain() {
        assertCodes(WeatherCondition.RAIN, 51, 53, 55, 56, 57)
    }

    @Test
    fun rainAndFreezingRain_areRain() {
        assertCodes(WeatherCondition.RAIN, 61, 63, 65, 66, 67)
    }

    @Test
    fun snowFallAndSnowGrains_areRain() {
        // No snow condition exists, so snow is grouped with the other precipitation.
        assertCodes(WeatherCondition.RAIN, 71, 73, 75, 77)
    }

    @Test
    fun rainShowers_areRain() {
        assertCodes(WeatherCondition.RAIN, 80, 81, 82)
    }

    @Test
    fun thunderstorm_areRain() {
        assertCodes(WeatherCondition.RAIN, 95, 96, 99)
    }

    @Test
    fun unknownCodes_areCloudy() {
        assertCodes(WeatherCondition.CLOUDY, -1, 4, 20, 50, 100)
    }
}
