package vn.edu.vlu.trippocket.data.repository

import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.model.TripMember

/**
 * Sample members, itinerary, checklist and expenses from [MockData] for the module screens
 * that are not on Room yet. Each module moves to its own repository (docs/ARCHITECTURE.md)
 * and stops using this class; delete it once nothing does.
 */
class SampleTripRepository {

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
