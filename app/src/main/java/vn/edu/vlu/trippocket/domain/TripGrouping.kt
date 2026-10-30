package vn.edu.vlu.trippocket.domain

import java.time.LocalDate

/** The sections of the trip list, in display order. */
enum class TripGroup { HAPPENING_NOW, UPCOMING, PAST }

/**
 * Splits trips into Happening now / Upcoming / Past by the phone's calendar date (inclusive end
 * date). Happening now and Upcoming run by start date (soonest first), Past by end date (newest
 * first). Empty groups are left out.
 */
object TripGrouping {

    fun <T> group(
        trips: List<T>,
        today: LocalDate,
        startDate: (T) -> LocalDate,
        endDate: (T) -> LocalDate,
    ): List<Pair<TripGroup, List<T>>> {
        val byGroup = trips.groupBy { trip ->
            when {
                today.isBefore(startDate(trip)) -> TripGroup.UPCOMING
                today.isAfter(endDate(trip)) -> TripGroup.PAST
                else -> TripGroup.HAPPENING_NOW
            }
        }
        return TripGroup.entries.mapNotNull { group ->
            val members = byGroup[group] ?: return@mapNotNull null
            group to when (group) {
                TripGroup.PAST -> members.sortedByDescending(endDate)
                else -> members.sortedBy(startDate)
            }
        }
    }
}
