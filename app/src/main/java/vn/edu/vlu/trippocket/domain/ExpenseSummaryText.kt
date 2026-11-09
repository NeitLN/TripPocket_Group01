package vn.edu.vlu.trippocket.domain

import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.Settlement
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.util.Format

/**
 * Plain-text budget summary sent through the Android share sheet (proposal 3.4, E4): the planned
 * total from the itinerary against the money spent, spending by category, then the suggested
 * payments. Categories with nothing spent are left out.
 */
object ExpenseSummaryText {

    /** [names] maps memberId to the name shown in the suggested payments. */
    fun build(
        trip: Trip,
        planned: Long,
        spent: Long,
        categoryTotals: Map<ExpenseCategory, Long>,
        settlements: List<Settlement>,
        names: Map<String, String>,
    ): String = buildString {
        appendLine("${trip.name} - ${Format.dateRange(trip.startDate, trip.endDate)}")
        appendLine("Planned: ${Format.vnd(planned)}")
        appendLine("Spent: ${Format.vnd(spent)}${overPlan(planned, spent)}")
        ExpenseCategory.entries.forEach { category ->
            val total = categoryTotals[category] ?: 0L
            if (total > 0) appendLine("- ${label(category)}: ${Format.vnd(total)}")
        }
        if (settlements.isNotEmpty()) {
            appendLine()
            appendLine("Suggested payments:")
            settlements.forEach {
                appendLine("- ${names[it.fromId].orEmpty()} pays ${names[it.toId].orEmpty()} ${Format.vnd(it.amount)}")
            }
        }
    }

    /** " (150,000 ₫ over plan)" once the spending passes a plan; nothing without a plan. */
    private fun overPlan(planned: Long, spent: Long): String =
        if (planned > 0 && spent > planned) " (${Format.vnd(spent - planned)} over plan)" else ""

    // Plain Kotlin, no resources: the same English words as the screen's labels.
    private fun label(category: ExpenseCategory) = when (category) {
        ExpenseCategory.FOOD -> "Food"
        ExpenseCategory.STAY -> "Stay"
        ExpenseCategory.TRANSPORT -> "Transport"
    }
}
