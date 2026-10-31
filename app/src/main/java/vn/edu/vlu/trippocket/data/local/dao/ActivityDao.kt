package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity

/** I5: an activity that has a reminder, with what is needed to work out when it fires. */
data class ReminderCandidate(
    val activityId: String,
    val tripId: String,
    val ownerUid: String,
    val isDeleted: Boolean,
    /** The trip's start date, epoch day. */
    val startDate: Long,
    val dayIndex: Int,
    /** Minute of the day, floating local time. */
    val startTime: Int,
    val title: String,
    val reminderMinutesBefore: Int,
)

/**
 * Owner: Bảo. Basic queries from F0; add the ones your screens need.
 * Writes must go through TripWriter.write(tripId) { ... } so the trip is marked for backup.
 */
@Dao
interface ActivityDao {

    @Query("SELECT * FROM activity WHERE tripId = :tripId ORDER BY dayIndex, orderIndex")
    fun observeByTrip(tripId: String): Flow<List<ActivityEntity>>

    @Query("SELECT COUNT(*) FROM activity WHERE tripId = :tripId")
    suspend fun countByTrip(tripId: String): Int

    /** I5: every activity with a reminder, of every account on this phone. */
    @Query(
        "SELECT activity.activityId, activity.tripId, trip.ownerUid, trip.isDeleted, trip.startDate, " +
            "activity.dayIndex, activity.startTime, activity.title, activity.reminderMinutesBefore " +
            "FROM activity JOIN trip ON trip.tripId = activity.tripId " +
            "WHERE activity.reminderMinutesBefore IS NOT NULL"
    )
    suspend fun reminderCandidates(): List<ReminderCandidate>

    @Insert
    suspend fun insert(row: ActivityEntity)

    @Update
    suspend fun update(row: ActivityEntity)

    @Delete
    suspend fun delete(row: ActivityEntity)
}
