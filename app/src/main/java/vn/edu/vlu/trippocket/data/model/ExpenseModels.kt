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
    /** What was actually paid when it was not VND ("Paid in"); null for a VND expense. */
    val paidIn: PaidIn? = null,
)

/**
 * A1 (schema v2): the foreign amount behind a VND expense, e.g. 12.5 USD at 25,704 VND per USD.
 * [amount] of [currency] is what the receipt says; the expense's VND amount stays the one the
 * shares add up to.
 */
data class PaidIn(val currency: String, val amount: Double, val rateToVnd: Double) {
    /** A 3-letter upper-case code and two finite positive numbers. */
    val isValid: Boolean
        get() = CURRENCY_CODE.matches(currency) &&
            amount.isFinite() && amount > 0.0 && rateToVnd.isFinite() && rateToVnd > 0.0

    companion object {
        private val CURRENCY_CODE = Regex("[A-Z]{3}")

        /** From the three nullable columns / backup fields; null unless all three are there. */
        fun of(currency: String?, amount: Double?, rateToVnd: Double?): PaidIn? =
            if (currency != null && amount != null && rateToVnd != null) PaidIn(currency, amount, rateToVnd) else null
    }
}

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
    /** null = paid in VND; editing an expense with null clears an earlier foreign amount. */
    val paidIn: PaidIn? = null,
)

/** What a trip card shows about members and money. */
data class TripTotals(val memberCount: Int = 0, val spent: Long = 0)
