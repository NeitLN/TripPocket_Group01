package vn.edu.vlu.trippocket.domain

import vn.edu.vlu.trippocket.data.model.WeatherCondition

/**
 * WMO weather codes from Open-Meteo to the three conditions the app can show.
 *
 * The UI has three icons only, so related codes are grouped around the nearest condition.
 * The grouping, copied from the Open-Meteo "WMO Weather interpretation codes" table:
 *
 * | WMO codes | Condition |
 * |---|---|
 * | 0 clear sky, 1 mainly clear | SUNNY |
 * | 2 partly cloudy, 3 overcast | CLOUDY |
 * | 45, 48 fog | CLOUDY |
 * | 51, 53, 55 drizzle; 56, 57 freezing drizzle | RAIN |
 * | 61, 63, 65 rain; 66, 67 freezing rain | RAIN |
 * | 71, 73, 75, 77 snow fall / snow grains | RAIN |
 * | 80, 81, 82 rain showers | RAIN |
 * | 95, 96, 99 thunderstorm | RAIN |
 * | any other code (and negative values) | CLOUDY |
 *
 * Snow and thunderstorms are precipitation, so they go to RAIN because there is no snow or
 * storm condition. Do not add a [WeatherCondition] value here: that changes the UI.
 */
object WeatherCodes {

    fun of(code: Int): WeatherCondition = when (code) {
        0, 1 -> WeatherCondition.SUNNY
        2, 3, 45, 48 -> WeatherCondition.CLOUDY
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        71, 73, 75, 77,
        80, 81, 82,
        95, 96, 99 -> WeatherCondition.RAIN
        else -> WeatherCondition.CLOUDY
    }
}
