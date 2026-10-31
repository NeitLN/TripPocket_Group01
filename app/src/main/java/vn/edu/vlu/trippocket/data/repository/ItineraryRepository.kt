package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.local.dao.ActivityDao
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.model.ActivityInput
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.domain.ActivityProblem
import vn.edu.vlu.trippocket.domain.ItineraryOrdering
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.domain.TimedActivity
import java.time.LocalTime
import java.util.UUID

/** Why an activity was not written; the whole write is rolled back. */
class InvalidActivityException(val problem: ActivityProblem) :
    IllegalArgumentException("Invalid activity: $problem")

/**
 * The activities of a trip, in Room (I1, proposal 3.2). Owner of the module: Bảo.
 *
 * Every write is one [TripWriter] transaction, so the trip's revision goes up exactly once per
 * add/edit/delete, and a rejected write (limit, invalid field, missing row) leaves nothing
 * behind. The screens read Flows; the form checks first and this class checks again inside the
 * transaction.
 *
 * Inside a day the activities are ordered by `orderIndex`, which follows the start time
 * (docs/ARCHITECTURE.md "Thứ tự hoạt động trong ngày"), see [ItineraryOrdering].
 */
class ItineraryRepository(
    private val activityDao: ActivityDao,
    private val tripWriter: TripWriter,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** The activities of one day, in display order. */
    fun observeDay(tripId: String, dayIndex: Int): Flow<List<ItineraryActivity>> =
        activityDao.observeDay(tripId, dayIndex).map { rows -> rows.map(::toModel) }

    /** Every activity of the trip, grouped by day and in display order; for the "n/200" count. */
    fun observeActivities(tripId: String): Flow<List<ItineraryActivity>> =
        activityDao.observeByTrip(tripId).map { rows -> rows.map(::toModel) }

    /** Planned cost of one day; 0 when the day is empty. */
    fun observeDayTotal(tripId: String, dayIndex: Int): Flow<Long> =
        activityDao.observeDayTotal(tripId, dayIndex)

    /**
     * Planned budget of the whole trip, from Room (the sum of `estimatedCost`). E4 (Tiến/Minh)
     * uses this instead of the sample data; it follows the activities as they change.
     */
    fun estimatedTotal(tripId: String): Flow<Long> = activityDao.observeTotal(tripId)

    /** null when the activity does not exist. */
    suspend fun getActivity(activityId: String): ItineraryActivity? =
        activityDao.get(activityId)?.let(::toModel)

    /**
     * Adds an activity and returns its id. It is appended to its day before the day is sorted,
     * so at an occupied time it lands after the activities already there.
     *
     * Throws [InvalidActivityException] for a bad field and
     * [vn.edu.vlu.trippocket.domain.LimitExceededException] for the 201st activity of the trip.
     */
    suspend fun addActivity(tripId: String, input: ActivityInput): String {
        val activityId = newId()
        tripWriter.write(tripId) {
            Limits.check(activityDao.countByTrip(tripId), Limits.MAX_ACTIVITIES)
            validate(tripId, input)
            activityDao.insert(toEntity(activityId, tripId, input, orderIndex = nextOrderIndex(tripId, input.dayIndex)))
            reorderDay(tripId, input.dayIndex)
        }
        return activityId
    }

    /**
     * Replaces the editable fields of an activity. A new time re-sorts that day; a new day also
     * appends it there and renumbers the day it left; a new title, place, note, cost or
     * reminder keeps the current order.
     */
    suspend fun updateActivity(tripId: String, activityId: String, input: ActivityInput) {
        tripWriter.write(tripId) {
            val old = activityDao.get(activityId)
            if (old == null || old.tripId != tripId) throw InvalidActivityException(ActivityProblem.NOT_FOUND)
            validate(tripId, input)

            val movedDay = old.dayIndex != input.dayIndex
            // A moved activity is appended to its new day; otherwise it keeps its place until
            // the day is renumbered below.
            val orderIndex = if (movedDay) nextOrderIndex(tripId, input.dayIndex) else old.orderIndex
            activityDao.update(toEntity(activityId, tripId, input, orderIndex))
            if (movedDay) reorderDay(tripId, old.dayIndex)
            if (movedDay || old.startTime != input.startTime.toMinuteOfDay()) reorderDay(tripId, input.dayIndex)
        }
        // TODO(I4): reschedule/cancel the reminder of this activity
    }

    /** Deletes the activity and closes the gap in its day's order. */
    suspend fun deleteActivity(tripId: String, activityId: String) {
        tripWriter.write(tripId) {
            val row = activityDao.get(activityId)
            if (row == null || row.tripId != tripId) throw InvalidActivityException(ActivityProblem.NOT_FOUND)
            activityDao.deleteById(tripId, activityId)
            reorderDay(tripId, row.dayIndex)
        }
        // TODO(I4): reschedule/cancel the reminder of this activity
    }

    /**
     * Debug builds only, until A2 adds the activity form: copies the sample activities into
     * Room for the sample trips (same ids as
     * [vn.edu.vlu.trippocket.data.repository.TripRepository.seedSampleTrips]), so a fresh
     * install shows an itinerary. A trip that is not on this phone (or was deleted) and a trip
     * that already has activities are left alone, so calling this on every start never
     * duplicates rows or breaks the foreign key. Seeding is not a user change, so it does not
     * go through [TripWriter] and the revision stays.
     */
    suspend fun seedSampleActivities() {
        MockData.trips.forEach { trip ->
            if (activityDao.tripDayCount(trip.tripId) == null) return@forEach
            if (activityDao.countByTrip(trip.tripId) > 0) return@forEach
            (0 until trip.dayCount).forEach { dayIndex ->
                val day = MockData.activitiesOf(trip.tripId, dayIndex)
                val ordered = ItineraryOrdering.inDisplayOrder(
                    day.map { TimedActivity(it.activityId, it.startTime.toMinuteOfDay()) }
                )
                ordered.forEachIndexed { orderIndex, timed ->
                    val activity = day.first { it.activityId == timed.activityId }
                    activityDao.insert(
                        toEntity(
                            activityId = activity.activityId,
                            tripId = trip.tripId,
                            input = ActivityInput(
                                dayIndex = activity.dayIndex,
                                title = activity.title,
                                place = activity.place,
                                startTime = activity.startTime,
                                estimatedCost = activity.estimatedCost,
                                note = activity.note,
                                reminderMinutesBefore = activity.reminderMinutesBefore,
                            ),
                            orderIndex = orderIndex,
                        )
                    )
                }
            }
        }
    }

    /** Runs inside the transaction, so a failure rolls the whole write back. */
    private suspend fun validate(tripId: String, input: ActivityInput) {
        val dayCount = activityDao.tripDayCount(tripId)
            ?: throw InvalidActivityException(ActivityProblem.NOT_FOUND)
        val title = input.title.trim()
        val problem = when {
            title.isEmpty() -> ActivityProblem.BLANK_TITLE
            title.length > Limits.MAX_TEXT -> ActivityProblem.TITLE_TOO_LONG
            input.place.trim().length > Limits.MAX_TEXT -> ActivityProblem.PLACE_TOO_LONG
            input.note.trim().length > Limits.MAX_NOTE -> ActivityProblem.NOTE_TOO_LONG
            input.estimatedCost < 0 -> ActivityProblem.COST_NEGATIVE
            input.startTime.toMinuteOfDay() !in 0 until MINUTES_PER_DAY -> ActivityProblem.TIME_OUT_OF_RANGE
            input.dayIndex !in 0 until dayCount -> ActivityProblem.DAY_OUT_OF_RANGE
            (input.reminderMinutesBefore ?: 0) < 0 -> ActivityProblem.REMINDER_INVALID
            else -> null
        }
        if (problem != null) throw InvalidActivityException(problem)
    }

    /** Stable sort by start time, then `orderIndex` 0..n-1 for that day. */
    private suspend fun reorderDay(tripId: String, dayIndex: Int) {
        val rows = activityDao.dayRows(tripId, dayIndex)
        if (rows.isEmpty()) return
        val byId = rows.associateBy { it.activityId }
        ItineraryOrdering.inDisplayOrder(rows.map { TimedActivity(it.activityId, it.startTime) })
            .forEachIndexed { index, timed ->
                val row = byId.getValue(timed.activityId)
                if (row.orderIndex != index) activityDao.update(row.copy(orderIndex = index))
            }
    }

    private suspend fun nextOrderIndex(tripId: String, dayIndex: Int): Int =
        (activityDao.dayRows(tripId, dayIndex).maxOfOrNull { it.orderIndex } ?: -1) + 1

    private fun toEntity(activityId: String, tripId: String, input: ActivityInput, orderIndex: Int) =
        ActivityEntity(
            activityId = activityId,
            tripId = tripId,
            dayIndex = input.dayIndex,
            title = input.title.trim(),
            place = input.place.trim(),
            startTime = input.startTime.toMinuteOfDay(),
            estimatedCost = input.estimatedCost,
            note = input.note.trim(),
            orderIndex = orderIndex,
            reminderMinutesBefore = input.reminderMinutesBefore,
        )

    private fun toModel(row: ActivityEntity) = ItineraryActivity(
        activityId = row.activityId,
        tripId = row.tripId,
        dayIndex = row.dayIndex,
        title = row.title,
        place = row.place,
        startTime = LocalTime.of(row.startTime / 60, row.startTime % 60),
        estimatedCost = row.estimatedCost,
        note = row.note,
        reminderMinutesBefore = row.reminderMinutesBefore,
    )

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Minute of the day (0..1439), the way [vn.edu.vlu.trippocket.data.local.entity.ActivityEntity] stores it. */
private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
