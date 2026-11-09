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

    /** I1: one day of the itinerary, in display order. */
    @Query("SELECT * FROM activity WHERE tripId = :tripId AND dayIndex = :dayIndex ORDER BY orderIndex")
    fun observeDay(tripId: String, dayIndex: Int): Flow<List<ActivityEntity>>

    /** I1: the same rows as a list, for renumbering inside a transaction. */
    @Query("SELECT * FROM activity WHERE tripId = :tripId AND dayIndex = :dayIndex ORDER BY orderIndex")
    suspend fun dayRows(tripId: String, dayIndex: Int): List<ActivityEntity>

    /** I1: the planned cost of one day; 0 when the day is empty. */
    @Query("SELECT COALESCE(SUM(estimatedCost), 0) FROM activity WHERE tripId = :tripId AND dayIndex = :dayIndex")
    fun observeDayTotal(tripId: String, dayIndex: Int): Flow<Long>

    /** I1: the planned budget of the trip, for E4 (Tiến/Minh). */
    @Query("SELECT COALESCE(SUM(estimatedCost), 0) FROM activity WHERE tripId = :tripId")
    fun observeTotal(tripId: String): Flow<Long>

    /** I1: null when the activity does not exist. */
    @Query("SELECT * FROM activity WHERE activityId = :activityId")
    suspend fun get(activityId: String): ActivityEntity?

    /** I1: number of days of the trip; null when the trip is missing or deleted. */
    @Query("SELECT endDate - startDate + 1 FROM trip WHERE tripId = :tripId AND isDeleted = 0")
    suspend fun tripDayCount(tripId: String): Int?

    /** A2: the last day that has an activity; null when the trip has none. */
    @Query("SELECT MAX(dayIndex) FROM activity WHERE tripId = :tripId")
    suspend fun lastUsedDayIndex(tripId: String): Int?

    @Query("DELETE FROM activity WHERE tripId = :tripId AND activityId = :activityId")
    suspend fun deleteById(tripId: String, activityId: String): Int

    /** I5: every activity with a reminder, of every account on this phone. */
    @Query(
        "SELECT activity.activityId, activity.tripId, trip.ownerUid, trip.isDeleted, trip.startDate, " +
            "activity.dayIndex, activity.startTime, activity.title, activity.reminderMinutesBefore " +
            "FROM activity JOIN trip ON trip.tripId = activity.tripId " +
            "WHERE activity.reminderMinutesBefore IS NOT NULL"
    )
    suspend fun reminderCandidates(): List<ReminderCandidate>

    /** I4: the reminder of one activity; null when the activity is gone or has no reminder. */
    @Query(
        "SELECT activity.activityId, activity.tripId, trip.ownerUid, trip.isDeleted, trip.startDate, " +
            "activity.dayIndex, activity.startTime, activity.title, activity.reminderMinutesBefore " +
            "FROM activity JOIN trip ON trip.tripId = activity.tripId " +
            "WHERE activity.activityId = :activityId AND activity.reminderMinutesBefore IS NOT NULL"
    )
    suspend fun reminderCandidate(activityId: String): ReminderCandidate?

    @Insert
    suspend fun insert(row: ActivityEntity)

    @Update
    suspend fun update(row: ActivityEntity)

    @Delete
    suspend fun delete(row: ActivityEntity)
}
