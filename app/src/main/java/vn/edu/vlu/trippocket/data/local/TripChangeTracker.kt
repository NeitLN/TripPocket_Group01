package vn.edu.vlu.trippocket.data.local

import vn.edu.vlu.trippocket.data.local.dao.TripDao

/** Marks a trip PENDING and bumps its localRevision. Only call it inside a Room transaction. */
class TripChangeTracker(
    private val tripDao: TripDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Throws when the trip does not exist, which rolls the whole transaction back. */
    suspend fun touch(tripId: String) {
        check(tripDao.touch(tripId, now()) == 1) { "Trip $tripId does not exist" }
    }
}
