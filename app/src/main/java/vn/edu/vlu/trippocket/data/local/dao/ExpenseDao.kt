package vn.edu.vlu.trippocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity

/** An expense row with all of its shares, read in one query so the two always match. */
data class ExpenseWithShares(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "expenseId", entityColumn = "expenseId")
    val shares: List<ExpenseShareEntity>,
)

/** Money spent on one trip, for the trip cards. */
data class TripSpent(val tripId: String, val spent: Long)

/**
 * Owner: Minh. Basic queries from F0; add the ones your screens need.
 * An expense and its shares are written in one TripWriter.write(tripId) { ... } call.
 */
@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expense WHERE tripId = :tripId ORDER BY spentAt DESC, createdAt DESC")
    fun observeByTrip(tripId: String): Flow<List<ExpenseEntity>>

    /** Newest first; each expense comes with its shares. */
    @Transaction
    @Query("SELECT * FROM expense WHERE tripId = :tripId ORDER BY spentAt DESC, createdAt DESC")
    fun observeWithShares(tripId: String): Flow<List<ExpenseWithShares>>

    @Transaction
    @Query("SELECT * FROM expense WHERE expenseId = :expenseId")
    suspend fun getWithShares(expenseId: String): ExpenseWithShares?

    @Query(
        "SELECT expense_share.* FROM expense_share " +
            "JOIN expense ON expense.expenseId = expense_share.expenseId WHERE expense.tripId = :tripId"
    )
    fun observeSharesByTrip(tripId: String): Flow<List<ExpenseShareEntity>>

    /** Total spent per trip; trips without expenses are left out. */
    @Query("SELECT tripId, SUM(amount) AS spent FROM expense GROUP BY tripId")
    fun observeSpentPerTrip(): Flow<List<TripSpent>>

    @Query("SELECT COUNT(*) FROM expense WHERE tripId = :tripId")
    suspend fun countByTrip(tripId: String): Int

    /** Expenses that name the member as payer or participant; a member in use cannot be removed. */
    @Query(
        "SELECT COUNT(*) FROM expense WHERE payerId = :memberId OR expenseId IN " +
            "(SELECT expenseId FROM expense_share WHERE memberId = :memberId)"
    )
    suspend fun countUsingMember(memberId: String): Int

    @Insert
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    /** Shares go with it (CASCADE). Returns 0 when the expense is not in that trip. */
    @Query("DELETE FROM expense WHERE expenseId = :expenseId AND tripId = :tripId")
    suspend fun deleteById(tripId: String, expenseId: String): Int

    @Insert
    suspend fun insertShares(shares: List<ExpenseShareEntity>)

    @Query("DELETE FROM expense_share WHERE expenseId = :expenseId")
    suspend fun deleteShares(expenseId: String)
}
