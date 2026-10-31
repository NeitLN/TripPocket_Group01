package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** I1: the ordering rule of a day (docs/ARCHITECTURE.md "Thứ tự hoạt động trong ngày"). */
class ItineraryOrderingTest {

    private fun at(id: String, minute: Int) = TimedActivity(id, minute)

    @Test
    fun activitiesAtDifferentTimes_areSortedAscending() {
        val ordered = ItineraryOrdering.inDisplayOrder(
            listOf(at("lunch", 12 * 60), at("bus", 6 * 60 + 30), at("dinner", 19 * 60))
        )

        assertEquals(listOf("bus", "lunch", "dinner"), ordered.map { it.activityId })
    }

    @Test
    fun activitiesAtTheSameTime_keepTheirCurrentOrder() {
        // The input is already in display order; a new activity was appended at the end.
        val ordered = ItineraryOrdering.inDisplayOrder(
            listOf(at("first", 9 * 60), at("second", 14 * 60), at("third", 9 * 60), at("fourth", 9 * 60))
        )

        assertEquals(listOf("first", "third", "fourth", "second"), ordered.map { it.activityId })
    }

    @Test
    fun anEmptyOrSingleActivityDay_isUnchanged() {
        assertEquals(emptyList<TimedActivity>(), ItineraryOrdering.inDisplayOrder(emptyList()))

        val one = listOf(at("only", 9 * 60))
        assertEquals(one, ItineraryOrdering.inDisplayOrder(one))
    }
}
