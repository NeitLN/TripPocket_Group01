package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity

/** Everything one backup document holds, read together (proposal 4.1). */
data class TripRows(
    val trip: TripEntity,
    val members: List<TripMemberEntity>,
    val activities: List<ActivityEntity>,
    val checklist: List<ChecklistItemEntity>,
    val places: List<SavedPlaceEntity>,
    val expenses: List<ExpenseEntity>,
    val shares: List<ExpenseShareEntity>,
)

/**
 * Owner: Tiến (A3). Reads for the backup job and the conditional status updates of the backup
 * protocol in docs/ARCHITECTURE.md: a trip becomes SYNCED or FAILED only if its localRevision is
 * still the one that was uploaded, so an edit made during an upload keeps it PENDING.
 */
@Dao
interface BackupDao {

    /** The account's oldest PENDING trip (deleted ones included: their document must go). */
    @Query("SELECT tripId FROM trip WHERE ownerUid = :ownerUid AND syncStatus = 'PENDING' ORDER BY updatedAt LIMIT 1")
    suspend fun nextPending(ownerUid: String): String?

    /** The trip and all its child rows as one consistent snapshot. null if the trip is gone. */
    @Transaction
    suspend fun rowsOf(tripId: String): TripRows? {
        val trip = trip(tripId) ?: return null
        return TripRows(
            trip = trip,
            members = members(tripId),
            activities = activities(tripId),
            checklist = checklist(tripId),
            places = places(tripId),
            expenses = expenses(tripId),
            shares = shares(tripId),
        )
    }

    @Query("SELECT * FROM trip WHERE tripId = :tripId")
    suspend fun trip(tripId: String): TripEntity?

    @Query("SELECT * FROM trip_member WHERE tripId = :tripId ORDER BY orderIndex")
    suspend fun members(tripId: String): List<TripMemberEntity>

    @Query("SELECT * FROM activity WHERE tripId = :tripId ORDER BY dayIndex, orderIndex")
    suspend fun activities(tripId: String): List<ActivityEntity>

    @Query("SELECT * FROM checklist_item WHERE tripId = :tripId ORDER BY rowid")
    suspend fun checklist(tripId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM saved_place WHERE tripId = :tripId ORDER BY rowid")
    suspend fun places(tripId: String): List<SavedPlaceEntity>

    @Query("SELECT * FROM expense WHERE tripId = :tripId ORDER BY createdAt")
    suspend fun expenses(tripId: String): List<ExpenseEntity>

    @Query(
        "SELECT expense_share.* FROM expense_share " +
            "JOIN expense ON expense.expenseId = expense_share.expenseId WHERE expense.tripId = :tripId"
    )
    suspend fun shares(tripId: String): List<ExpenseShareEntity>

    /** 1 if the uploaded [revision] is still current; 0 means it changed and stays PENDING. */
    @Query(
        "UPDATE trip SET syncStatus = 'SYNCED' " +
            "WHERE tripId = :tripId AND localRevision = :revision AND syncStatus = 'PENDING' AND isDeleted = 0"
    )
    suspend fun markSynced(tripId: String, revision: Long): Int

    /** Too big to back up (>= 1 MiB); only if nothing changed since the check. */
    @Query(
        "UPDATE trip SET syncStatus = 'FAILED' " +
            "WHERE tripId = :tripId AND localRevision = :revision AND syncStatus = 'PENDING'"
    )
    suspend fun markFailed(tripId: String, revision: Long): Int

    /** "Retry backup": FAILED back to PENDING, for the signed-in owner only. */
    @Query("UPDATE trip SET syncStatus = 'PENDING' WHERE tripId = :tripId AND ownerUid = :ownerUid AND syncStatus = 'FAILED'")
    suspend fun retryFailed(tripId: String, ownerUid: String): Int

    /** A deleted trip still at the revision whose document was just removed. */
    @Query("SELECT COUNT(*) FROM trip WHERE tripId = :tripId AND isDeleted = 1 AND localRevision = :revision")
    suspend fun isTombstoneAt(tripId: String, revision: Long): Int
}
