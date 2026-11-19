package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType

/**
 * A member who paid for something cannot be deleted (RESTRICT). Purging a whole trip
 * goes through TripDao.purge, which deletes the expenses before the members.
 */
@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["tripId"], ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TripMemberEntity::class, ["memberId"], ["payerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("tripId"), Index("payerId")],
)
data class ExpenseEntity(
    @PrimaryKey val expenseId: String,
    val tripId: String,
    val title: String,
    /** VND. */
    val amount: Long,
    val payerId: String,
    val category: ExpenseCategory,
    val splitType: SplitType,
    /** Epoch day the money was spent. */
    val spentAt: Long,
    /** Epoch millis. */
    val createdAt: Long,
    /**
     * Schema v2: what was actually paid when it was not VND ("Paid in"), so reopening the expense
     * shows "12.50 USD" again. All three are null for a VND expense and for rows from v1.
     */
    val originalCurrency: String? = null,
    val originalAmount: Double? = null,
    /** VND per 1 [originalCurrency] at the time of the expense. */
    val rateToVnd: Double? = null,
)
