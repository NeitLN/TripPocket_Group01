package vn.edu.vlu.trippocket.data.model

import androidx.annotation.DrawableRes
import vn.edu.vlu.trippocket.R
import java.time.LocalDate

// Models of one module (owner: Duy Anh). Room entities live in data/local/entity.

data class Place(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
)

enum class WeatherCondition(@param:DrawableRes val iconRes: Int, val label: String) {
    SUNNY(R.drawable.ic_sunny, "Sunny"),
    CLOUDY(R.drawable.ic_cloud, "Partly cloudy"),
    RAIN(R.drawable.ic_rain, "Showers"),
}

data class ForecastDay(
    val date: LocalDate,
    val condition: WeatherCondition,
    val maxTemp: Int,
    val minTemp: Int,
    val rainChance: Int,
)
