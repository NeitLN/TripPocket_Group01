package vn.edu.vlu.trippocket.data.local

import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ForecastDay
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.model.WeatherCondition
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * In-memory sample data, the only static data source of the app.
 * Only the repositories in data/repository read it; the screens get it
 * through their ViewModels. Later milestones replace it with Room DAOs
 * and the Open-Meteo API.
 *
 * The Đà Lạt trip follows the demo scenario in the proposal: three members,
 * An pays 300,000 ₫ for a dinner shared by three, so each share is 100,000 ₫.
 */
object MockData {

    const val TRIP_DALAT = "trip-dalat"
    const val TRIP_HOIAN = "trip-hoian"
    const val TRIP_PHUQUOC = "trip-phuquoc"

    const val FORECAST_DAYS = 16

    /** Time the cached forecast was "fetched" (shown as "Last updated"). */
    val forecastFetchedAt: LocalDateTime = LocalDateTime.of(2026, 10, 16, 14, 5)
    private val forecastStart: LocalDate = forecastFetchedAt.toLocalDate()

    val trips = listOf(
        Trip(TRIP_DALAT, "Đà Lạt Getaway", "Đà Lạt, Lâm Đồng",
            LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18), SyncStatus.PENDING),
        Trip(TRIP_PHUQUOC, "Phú Quốc Summer", "Phú Quốc, Kiên Giang",
            LocalDate.of(2027, 6, 20), LocalDate.of(2027, 6, 24), SyncStatus.FAILED),
        Trip(TRIP_HOIAN, "Hội An Weekend", "Hội An, Quảng Nam",
            LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7), SyncStatus.SYNCED),
    )

    private val members = listOf(
        TripMember("m-an", TRIP_DALAT, "An", 0),
        TripMember("m-binh", TRIP_DALAT, "Bình", 1),
        TripMember("m-chi", TRIP_DALAT, "Chi", 2),
        TripMember("m-dung", TRIP_HOIAN, "Dũng", 0),
        TripMember("m-giang", TRIP_HOIAN, "Giang", 1),
        TripMember("m-hoa", TRIP_HOIAN, "Hoa", 2),
        TripMember("m-khanh", TRIP_HOIAN, "Khánh", 3),
        TripMember("m-lan", TRIP_PHUQUOC, "Lan", 0),
        TripMember("m-minh", TRIP_PHUQUOC, "Minh", 1),
    )

    private val activities = listOf(
        // Day 1
        ItineraryActivity("a1", TRIP_DALAT, 0, "Sleeper bus to Đà Lạt", "Bến xe Miền Đông",
            LocalTime.of(6, 30), 300_000, "Bring jackets, it gets cold at night.", 30),
        ItineraryActivity("a2", TRIP_DALAT, 0, "Check in at the homestay", "Lacasa, Phường 3",
            LocalTime.of(13, 30), 1_500_000, "Pay on arrival, 3 nights.", null),
        ItineraryActivity("a3", TRIP_DALAT, 0, "Walk around Xuân Hương Lake", "Hồ Xuân Hương",
            LocalTime.of(16, 30), 0, "", null),
        ItineraryActivity("a4", TRIP_DALAT, 0, "Dinner: lẩu gà lá é", "Tao Ngộ, Đường 3/4",
            LocalTime.of(19, 0), 300_000, "Book a table for 3 before 18:00.", 60),
        // Day 2
        ItineraryActivity("a5", TRIP_DALAT, 1, "Sunrise at Langbiang", "Langbiang, Lạc Dương",
            LocalTime.of(5, 0), 210_000, "Jeep from the foot of the mountain.", 30),
        ItineraryActivity("a6", TRIP_DALAT, 1, "Coffee with a view", "Túi Mơ To, Phường 12",
            LocalTime.of(10, 30), 150_000, "", null),
        ItineraryActivity("a7", TRIP_DALAT, 1, "City flower gardens", "Vườn hoa thành phố",
            LocalTime.of(14, 0), 150_000, "", null),
        // Day 3
        ItineraryActivity("a8", TRIP_DALAT, 2, "Datanla waterfall and alpine coaster", "Thác Datanla",
            LocalTime.of(8, 0), 600_000, "Wear shoes with grip.", 45),
        ItineraryActivity("a9", TRIP_DALAT, 2, "Night market", "Chợ Đà Lạt",
            LocalTime.of(18, 0), 200_000, "", null),
        // Day 4 left empty on purpose to show the empty state.
    )

    private val expenses: List<Expense> by lazy {
        val dalat = membersOf(TRIP_DALAT)
        val hoian = membersOf(TRIP_HOIAN)
        listOf(
            Expense("e1", TRIP_DALAT, "Dinner: lẩu gà lá é", 300_000, "m-an",
                ExpenseCategory.FOOD, SplitType.EQUAL, LocalDate.of(2026, 10, 15),
                ExpenseCalculator.splitEqually(300_000, dalat)),
            Expense("e2", TRIP_DALAT, "Homestay, 3 nights", 1_500_000, "m-binh",
                ExpenseCategory.STAY, SplitType.EQUAL, LocalDate.of(2026, 10, 15),
                ExpenseCalculator.splitEqually(1_500_000, dalat)),
            Expense("e3", TRIP_DALAT, "Jeep to Langbiang", 210_000, "m-chi",
                ExpenseCategory.TRANSPORT, SplitType.EQUAL, LocalDate.of(2026, 10, 16),
                ExpenseCalculator.splitEqually(210_000, dalat)),
            Expense("e4", TRIP_DALAT, "Coffee at Túi Mơ To", 140_000, "m-an",
                ExpenseCategory.FOOD, SplitType.CUSTOM, LocalDate.of(2026, 10, 16),
                mapOf("m-an" to 40_000L, "m-binh" to 50_000L, "m-chi" to 50_000L)),
            Expense("e5", TRIP_HOIAN, "Cao lầu lunch", 360_000, "m-dung",
                ExpenseCategory.FOOD, SplitType.EQUAL, LocalDate.of(2026, 9, 5),
                ExpenseCalculator.splitEqually(360_000, hoian)),
            Expense("e6", TRIP_HOIAN, "Bike rental", 400_001, "m-hoa",
                ExpenseCategory.TRANSPORT, SplitType.EQUAL, LocalDate.of(2026, 9, 6),
                ExpenseCalculator.splitEqually(400_001, hoian)),
        )
    }

    /** Done / total packing items per trip. */
    private val checklist = mapOf(
        TRIP_DALAT to (5 to 12),
        TRIP_HOIAN to (9 to 9),
        TRIP_PHUQUOC to (0 to 4),
    )

    private val searchablePlaces = listOf(
        Place("Đà Lạt", "Lâm Đồng", "Vietnam", 11.94, 108.44),
        Place("Langbiang", "Lạc Dương, Lâm Đồng", "Vietnam", 12.05, 108.44),
        Place("Đà Nẵng", "Đà Nẵng", "Vietnam", 16.07, 108.22),
        Place("Hà Nội", "Hà Nội", "Vietnam", 21.03, 105.85),
        Place("Hồ Chí Minh City", "Hồ Chí Minh", "Vietnam", 10.82, 106.63),
        Place("Huế", "Thừa Thiên Huế", "Vietnam", 16.46, 107.59),
        Place("Hội An", "Quảng Nam", "Vietnam", 15.88, 108.33),
        Place("Nha Trang", "Khánh Hòa", "Vietnam", 12.24, 109.20),
        Place("Phú Quốc", "Kiên Giang", "Vietnam", 10.22, 103.96),
        Place("Sa Pa", "Lào Cai", "Vietnam", 22.34, 103.84),
        Place("Dalat", "Oregon", "United States", 45.52, -122.98),
    )

    private val savedPlaces = mapOf(
        TRIP_DALAT to listOf(searchablePlaces[0], searchablePlaces[1]),
    )

    fun trip(tripId: String): Trip? = trips.firstOrNull { it.tripId == tripId }

    fun membersOf(tripId: String) = members.filter { it.tripId == tripId }.sortedBy { it.orderIndex }

    fun activitiesOf(tripId: String, dayIndex: Int) =
        activities.filter { it.tripId == tripId && it.dayIndex == dayIndex }.sortedBy { it.startTime }

    fun estimatedTotal(tripId: String) = activities.filter { it.tripId == tripId }.sumOf { it.estimatedCost }

    fun expensesOf(tripId: String) = expenses.filter { it.tripId == tripId }.sortedByDescending { it.spentAt }

    fun spentTotal(tripId: String) = expensesOf(tripId).sumOf { it.amount }

    fun checklistOf(tripId: String): Pair<Int, Int> = checklist[tripId] ?: (0 to 0)

    fun savedPlacesOf(tripId: String): List<Place> = savedPlaces[tripId].orEmpty()

    /** Accent-insensitive search, like typing "da lat" to find "Đà Lạt". */
    fun searchPlaces(query: String): List<Place> {
        val key = query.normalizeForSearch()
        if (key.isBlank()) return emptyList()
        return searchablePlaces.filter { it.name.normalizeForSearch().contains(key) }.take(5)
    }

    /** Deterministic fake 16-day forecast so every run shows the same values. */
    fun forecastFor(place: Place): List<ForecastDay> {
        val seed = place.name.hashCode()
        val conditions = WeatherCondition.entries
        return (0 until FORECAST_DAYS).map { day ->
            val wobble = ((seed shr (day % 8)) and 3)
            val base = if (place.latitude > 11.5 && place.latitude < 12.5) 22 else 30
            ForecastDay(
                date = forecastStart.plusDays(day.toLong()),
                condition = conditions[(wobble + day) % conditions.size],
                maxTemp = base + wobble,
                minTemp = base - 8 + wobble / 2,
                rainChance = (wobble * 20 + day * 3) % 90,
            )
        }
    }

    private fun String.normalizeForSearch(): String =
        Normalizer.normalize(trim().lowercase().replace('đ', 'd'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(" ", "")
}
