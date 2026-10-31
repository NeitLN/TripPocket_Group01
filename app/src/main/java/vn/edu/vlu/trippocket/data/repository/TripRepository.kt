package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.dao.TripDao
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.model.Trip
import java.time.LocalDate

/**
 * Trips of the signed-in account, read from Room. Owner: Tiến.
 * The reference for the other repositories: reads are Flows that follow the account,
 * writes go through TripWriter (create/edit/delete come with A2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepository(
    private val tripDao: TripDao,
    private val auth: AuthRepository,
) {
    fun observeTrips(): Flow<List<Trip>> = auth.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else tripDao.observeTrips(uid).map { rows -> rows.map(::toModel) }
    }

    /** null while signed out, or when the trip is missing, deleted or owned by another account. */
    fun observeTrip(tripId: String): Flow<Trip?> = auth.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else tripDao.observeTrip(tripId, uid).map { it?.let(::toModel) }
    }

    /**
     * Debug builds only, until A2 adds the trip form: copies the sample trips into Room for
     * [ownerUid] so the list and the module screens (still on MockData, same ids) have data.
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

    private fun toModel(row: TripEntity) = Trip(
        tripId = row.tripId,
        name = row.name,
        destination = row.destination,
        startDate = LocalDate.ofEpochDay(row.startDate),
        endDate = LocalDate.ofEpochDay(row.endDate),
        syncStatus = row.syncStatus,
    )
}
