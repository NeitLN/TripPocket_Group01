package vn.edu.vlu.trippocket.domain

/**
 * Per-trip caps that keep one backup snapshot under Firestore's 1 MiB limit (proposal 6.2).
 * Forms disable "Add" at a cap; repositories re-check inside the same transaction as the insert.
 */
object Limits {
    const val MAX_MEMBERS = 15
    const val MAX_ACTIVITIES = 200
    const val MAX_CHECKLIST_ITEMS = 100
    const val MAX_PLACES = 30
    const val MAX_EXPENSES = 300

    /** Names, titles, destinations, places, countries and expense categories. */
    const val MAX_TEXT = 80
    const val MAX_NOTE = 300

    /** Throws [LimitExceededException] when one more row would go over [max]. */
    fun check(currentCount: Int, max: Int) {
        if (currentCount >= max) throw LimitExceededException(max)
    }
}

class LimitExceededException(val max: Int) : IllegalStateException("Limit of $max reached")
