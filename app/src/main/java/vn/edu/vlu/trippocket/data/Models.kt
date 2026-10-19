package vn.edu.vlu.trippocket.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import vn.edu.vlu.trippocket.R
import java.time.LocalDate
import java.time.LocalTime

// Milestone 1 models. Field names follow the Room schema in the proposal
// (section 4.2) so they can become @Entity classes in Milestone 2.

enum class SyncStatus(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    SYNCED(R.string.sync_synced, R.drawable.ic_cloud_done),
    PENDING(R.string.sync_pending, R.drawable.ic_cloud_upload),
    FAILED(R.string.sync_failed, R.drawable.ic_cloud),
}

data class Trip(
    val tripId: String,
    val name: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val syncStatus: SyncStatus,
) {
    val dayCount: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
    fun dateOfDay(dayIndex: Int): LocalDate = startDate.plusDays(dayIndex.toLong())
}

data class TripMember(
    val memberId: String,
    val tripId: String,
    val name: String,
    val orderIndex: Int,
) {
    val initial: String get() = name.take(1).uppercase()
}

data class ItineraryActivity(
    val activityId: String,
    val tripId: String,
    val dayIndex: Int,
    val title: String,
    val place: String,
    val startTime: LocalTime,
    val estimatedCost: Long,
    val note: String,
    val reminderMinutesBefore: Int?,
)

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

enum class ExpenseCategory(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    FOOD(R.string.expenses_category_food, R.drawable.ic_restaurant),
    STAY(R.string.expenses_category_stay, R.drawable.ic_hotel),
    TRANSPORT(R.string.expenses_category_transport, R.drawable.ic_car),
}

enum class SplitType { EQUAL, CUSTOM }

data class Expense(
    val expenseId: String,
    val tripId: String,
    val title: String,
    val amount: Long,
    val payerId: String,
    val category: ExpenseCategory,
    val splitType: SplitType,
    val spentAt: LocalDate,
    /** memberId -> share in VND. Always adds up to [amount]. */
    val shares: Map<String, Long>,
)

data class Settlement(val fromId: String, val toId: String, val amount: Long)
