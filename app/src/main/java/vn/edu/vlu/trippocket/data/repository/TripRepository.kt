package vn.edu.vlu.trippocket.data.repository

import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.model.TripMember

/**
 * Trips, members, itinerary and expenses for the ViewModels.
 * Reads [MockData] for now; Room DAOs replace it later without changing the ViewModels.
 */
class TripRepository {

    fun trips(): List<Trip> = MockData.trips

    fun trip(tripId: String): Trip? = MockData.trip(tripId)

    fun members(tripId: String): List<TripMember> = MockData.membersOf(tripId)

    fun activities(tripId: String, dayIndex: Int): List<ItineraryActivity> =
        MockData.activitiesOf(tripId, dayIndex)

    /** Sum of the estimated costs of every activity in the trip (the planned budget). */
    fun estimatedTotal(tripId: String): Long = MockData.estimatedTotal(tripId)

    fun expenses(tripId: String): List<Expense> = MockData.expensesOf(tripId)

    fun spentTotal(tripId: String): Long = MockData.spentTotal(tripId)

    /** Done / total packing items. */
    fun checklist(tripId: String): Pair<Int, Int> = MockData.checklistOf(tripId)
}
