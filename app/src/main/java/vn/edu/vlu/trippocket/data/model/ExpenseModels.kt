package vn.edu.vlu.trippocket.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import vn.edu.vlu.trippocket.R
import java.time.LocalDate

// Models of one module (owner: Minh). Room entities live in data/local/entity.

data class TripMember(
    val memberId: String,
    val tripId: String,
    val name: String,
    val orderIndex: Int,
) {
    val initial: String get() = name.take(1).uppercase()
}

enum class ExpenseCategory(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    FOOD(R.string.expenses_category_food, R.drawable.ic_restaurant),
    STAY(R.string.expenses_category_stay, R.drawable.ic_hotel),
    TRANSPORT(R.string.expenses_category_transport, R.drawable.ic_car),
}

enum class SplitType { EQUAL, CUSTOM }

data class Expense(
    val expenseId: String,
    val tripId: String,
    val title: String,
    val amount: Long,
    val payerId: String,
    val category: ExpenseCategory,
    val splitType: SplitType,
    val spentAt: LocalDate,
    /** memberId -> share in VND. Always adds up to [amount]. */
    val shares: Map<String, Long>,
)

data class Settlement(val fromId: String, val toId: String, val amount: Long)

/** E3: what one member paid, their own share, and paid - share (positive = gets money back). */
data class MemberPosition(val member: TripMember, val paid: Long, val share: Long, val balance: Long)

/**
 * E3 (proposal 3.4): every member's position in member order, and the suggested payments that
 * bring every balance back to 0. The balances always add up to 0.
 */
data class TripBalances(
    val positions: List<MemberPosition> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
)

/** A new or edited expense as the form sends it; [shares] are already split. */
data class ExpenseInput(
    val title: String,
    val amount: Long,
    val payerId: String,
    val category: ExpenseCategory,
    val splitType: SplitType,
    val spentAt: LocalDate,
    /** memberId -> share in VND; must add up to [amount]. */
    val shares: Map<String, Long>,
)

/** What a trip card shows about members and money. */
data class TripTotals(val memberCount: Int = 0, val spent: Long = 0)
