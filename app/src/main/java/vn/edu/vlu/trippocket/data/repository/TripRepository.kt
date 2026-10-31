package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.local.dao.TripDao
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.model.TripInput
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.domain.TripProblem
import java.time.LocalDate
import java.util.UUID

/** Why a trip was not written; the whole write is rolled back. */
class InvalidTripException(val problem: TripProblem) : IllegalArgumentException("Invalid trip: $problem")

/**
 * Trips of the signed-in account, read from Room. Owner: Tiến.
 * The reference for the other repositories: reads are Flows that follow the account,
 * writes go through TripWriter.
 *
 * A2 (Bảo): create, edit and soft delete. Every write checks that the trip belongs to the account
 * signed in now, so account B can neither open nor change a trip of account A (NOT_FOUND).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepository(
    private val tripDao: TripDao,
    private val auth: AuthRepository,
    private val tripWriter: TripWriter,
    /** The last day index with an activity (ItineraryRepository.lastUsedDayIndex); null when none. */
    private val lastUsedDay: suspend (tripId: String) -> Int? = { null },
    /** Rebuilds the alarms of an account after its trip dates change or a trip is deleted (I5). */
    private val syncReminders: suspend (uid: String?) -> Unit = {},
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeTrips(): Flow<List<Trip>> = auth.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else tripDao.observeTrips(uid).map { rows -> rows.map(::toModel) }
    }

    /** null while signed out, or when the trip is missing, deleted or owned by another account. */
    fun observeTrip(tripId: String): Flow<Trip?> = auth.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else tripDao.observeTrip(tripId, uid).map { it?.let(::toModel) }
    }

    /** The trip for the edit form; null when it cannot be opened by the signed-in account. */
    suspend fun getTrip(tripId: String): Trip? {
        val uid = auth.currentUid.value ?: return null
        return tripDao.get(tripId, uid)?.let(::toModel)
    }

    /**
     * Creates a trip for the signed-in account and returns its id. It starts PENDING so the
     * backup job uploads it. Throws [InvalidTripException].
     */
    suspend fun createTrip(input: TripInput): String {
        val uid = auth.currentUid.value ?: throw InvalidTripException(TripProblem.NOT_FOUND)
        val checked = validate(input)
        val tripId = newId()
        tripWriter.write(tripId) {
            tripDao.insert(
                TripEntity(
                    tripId = tripId,
                    ownerUid = uid,
                    name = checked.name,
                    destination = checked.destination,
                    startDate = checked.startDate.toEpochDay(),
                    endDate = checked.endDate.toEpochDay(),
                    syncStatus = SyncStatus.PENDING,
                    updatedAt = now(),
                )
            )
        }
        return tripId
    }

    /**
     * Changes the name, destination and dates. New dates move every activity with the trip (they
     * are stored per day index) and the reminders are worked out again. A trip cannot become
     * shorter than its last day with activities ([TripProblem.DAYS_IN_USE]).
     */
    suspend fun updateTrip(tripId: String, input: TripInput) {
        val uid = auth.currentUid.value ?: throw InvalidTripException(TripProblem.NOT_FOUND)
        val checked = validate(input)
        var datesChanged = false
        tripWriter.write(tripId) {
            val old = tripDao.get(tripId, uid) ?: throw InvalidTripException(TripProblem.NOT_FOUND)
            val dayCount = checked.endDate.toEpochDay() - checked.startDate.toEpochDay() + 1
            val lastDay = lastUsedDay(tripId)
            if (lastDay != null && lastDay >= dayCount) throw InvalidTripException(TripProblem.DAYS_IN_USE)
            val row = old.copy(
                name = checked.name,
                destination = checked.destination,
                startDate = checked.startDate.toEpochDay(),
                endDate = checked.endDate.toEpochDay(),
            )
            datesChanged = row.startDate != old.startDate || row.endDate != old.endDate
            tripDao.update(row)
        }
        if (datesChanged) syncReminders(uid)
    }

    /**
     * Soft delete: the trip disappears at once and the backup job deletes its document, then
     * purges it (docs/ARCHITECTURE.md). Its reminders are cancelled.
     */
    suspend fun deleteTrip(tripId: String) {
        val uid = auth.currentUid.value ?: throw InvalidTripException(TripProblem.NOT_FOUND)
        tripWriter.write(tripId) {
            val row = tripDao.get(tripId, uid) ?: throw InvalidTripException(TripProblem.NOT_FOUND)
            tripDao.update(row.copy(isDeleted = true))
        }
        syncReminders(uid)
    }

    /**
     * Debug builds only: copies the sample trips into Room for [ownerUid] so the list and the
     * module screens (sample data with the same ids) have data on a fresh install.
     */
    suspend fun seedSampleTrips(ownerUid: String, now: Long) {
        if (MockData.trips.any { tripDao.ownerOf(it.tripId) != null }) return
        MockData.trips.forEach { trip ->
            tripDao.insert(
                TripEntity(
                    tripId = trip.tripId,
                    ownerUid = ownerUid,
                    name = trip.name,
                    destination = trip.destination,
                    startDate = trip.startDate.toEpochDay(),
                    endDate = trip.endDate.toEpochDay(),
                    syncStatus = trip.syncStatus,
                    updatedAt = now,
                )
            )
        }
    }

    /** Same rules as the form; returns the input with trimmed texts. */
    private fun validate(input: TripInput): TripInput {
        val name = input.name.trim()
        val destination = input.destination.trim()
        val problem = when {
            name.isEmpty() -> TripProblem.BLANK_NAME
            name.length > Limits.MAX_TEXT -> TripProblem.NAME_TOO_LONG
            destination.isEmpty() -> TripProblem.BLANK_DESTINATION
            destination.length > Limits.MAX_TEXT -> TripProblem.DESTINATION_TOO_LONG
            input.endDate < input.startDate -> TripProblem.DATES_REVERSED
            else -> null
        }
        if (problem != null) throw InvalidTripException(problem)
        return input.copy(name = name, destination = destination)
    }

    private fun toModel(row: TripEntity) = Trip(
        tripId = row.tripId,
        name = row.name,
        destination = row.destination,
        startDate = LocalDate.ofEpochDay(row.startDate),
        endDate = LocalDate.ofEpochDay(row.endDate),
        syncStatus = row.syncStatus,
    )
}
