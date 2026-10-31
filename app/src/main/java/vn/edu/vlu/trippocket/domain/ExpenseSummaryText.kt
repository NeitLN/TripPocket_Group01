package vn.edu.vlu.trippocket.domain

import vn.edu.vlu.trippocket.data.model.Settlement
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.util.Format

/** Plain-text trip summary sent through the Android share sheet (proposal 3.4). */
object ExpenseSummaryText {

    /** [names] maps memberId to the name shown in the suggested payments. */
    fun build(trip: Trip, spent: Long, settlements: List<Settlement>, names: Map<String, String>): String =
        buildString {
            appendLine("${trip.name} - ${Format.dateRange(trip.startDate, trip.endDate)}")
            appendLine("Total spent: ${Format.vnd(spent)}")
            if (settlements.isNotEmpty()) {
                appendLine()
                appendLine("Suggested payments:")
                settlements.forEach {
                    appendLine("- ${names[it.fromId].orEmpty()} pays ${names[it.toId].orEmpty()} ${Format.vnd(it.amount)}")
                }
            }
        }
}
