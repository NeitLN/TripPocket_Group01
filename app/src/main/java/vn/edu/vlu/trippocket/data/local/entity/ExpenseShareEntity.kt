package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** One participant's share of an expense. A member with a share cannot be deleted (RESTRICT). */
@Entity(
    tableName = "expense_share",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [
        ForeignKey(ExpenseEntity::class, ["expenseId"], ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TripMemberEntity::class, ["memberId"], ["memberId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("memberId")],
)
data class ExpenseShareEntity(
    val expenseId: String,
    val memberId: String,
    /** VND. The shares of one expense always add up to its amount. */
    val shareAmount: Long,
)
