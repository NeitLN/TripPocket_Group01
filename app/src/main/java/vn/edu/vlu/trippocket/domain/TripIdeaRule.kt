package vn.edu.vlu.trippocket.domain

import vn.edu.vlu.trippocket.data.model.TripIdeaInput

/** A field of the trip idea form that cannot be sent as typed. */
enum class TripIdeaProblem {
    BLANK_TITLE,
    TITLE_TOO_LONG,
    BLANK_DESTINATION,
    DESTINATION_TOO_LONG,
    BAD_DAYS,
    BAD_BUDGET,
    BAD_COVER_URL,
    SUMMARY_TOO_LONG,
}

/**
 * Checks the trip idea form before any request (Milestone 4), so the server only ever gets
 * clean data and a 400 means the server changed, not that the user typed something odd.
 */
object TripIdeaRule {
    const val MAX_TITLE = 60
    const val MAX_DESTINATION = 60
    const val MAX_SUMMARY = 280
    const val MAX_DAYS = 30
    const val MAX_BUDGET_VND = 1_000_000_000L

    /** Problems of the raw form fields; empty = [toInput] succeeds. */
    fun problems(title: String, destination: String, days: String, budget: String, coverUrl: String, summary: String): Set<TripIdeaProblem> {
        val result = mutableSetOf<TripIdeaProblem>()
        val t = title.trim()
        val d = destination.trim()
        if (t.isEmpty()) result += TripIdeaProblem.BLANK_TITLE
        if (t.length > MAX_TITLE) result += TripIdeaProblem.TITLE_TOO_LONG
        if (d.isEmpty()) result += TripIdeaProblem.BLANK_DESTINATION
        if (d.length > MAX_DESTINATION) result += TripIdeaProblem.DESTINATION_TOO_LONG
        if (days.trim().toIntOrNull()?.takeIf { it in 1..MAX_DAYS } == null) result += TripIdeaProblem.BAD_DAYS
        if (parseBudget(budget) == null) result += TripIdeaProblem.BAD_BUDGET
        if (!isCoverUrl(coverUrl.trim())) result += TripIdeaProblem.BAD_COVER_URL
        if (summary.trim().length > MAX_SUMMARY) result += TripIdeaProblem.SUMMARY_TOO_LONG
        return result
    }

    /** The cleaned input, or null when [problems] is not empty. */
    fun toInput(title: String, destination: String, days: String, budget: String, coverUrl: String, summary: String): TripIdeaInput? {
        if (problems(title, destination, days, budget, coverUrl, summary).isNotEmpty()) return null
        return TripIdeaInput(
            title = title.trim(),
            destination = destination.trim(),
            days = days.trim().toInt(),
            budgetVnd = parseBudget(budget)!!,
            coverUrl = coverUrl.trim(),
            summary = summary.trim(),
        )
    }

    /** "4.500.000", "4,500,000" or "4500000" -> 4500000; blank -> 0; anything else -> null. */
    fun parseBudget(text: String): Long? {
        val digits = text.trim().replace(".", "").replace(",", "").replace(" ", "")
        if (digits.isEmpty()) return 0L
        if (!digits.all { it.isDigit() } || digits.length > 10) return null
        return digits.toLong().takeIf { it <= MAX_BUDGET_VND }
    }

    /** Blank (no picture) or an https:// address; Android blocks plain http by default. */
    private fun isCoverUrl(url: String): Boolean =
        url.isEmpty() || (url.startsWith("https://") && url.length > "https://".length + 3 && ' ' !in url)
}
