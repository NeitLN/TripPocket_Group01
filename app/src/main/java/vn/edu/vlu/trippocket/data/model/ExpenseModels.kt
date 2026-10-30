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
