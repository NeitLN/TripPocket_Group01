package vn.edu.vlu.trippocket.data.repository

import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import vn.edu.vlu.trippocket.domain.Limits

/**
 * A4: checks a whole backup snapshot before restore writes any of it (proposal 6.2). The rules
 * are the ones the forms and repositories apply when the data is created: the caps and text
 * lengths of [Limits], dates in order, times inside the day and the trip, valid enum names, and
 * expenses whose payer and shares all name members of the same trip and add up to the amount.
 */
object TripSnapshotValidator {

    private const val MINUTES_PER_DAY = 24 * 60

    /** null when [s] can be restored for [ownerUid] from document [documentId]; else why not. */
    fun problem(s: TripSnapshot, documentId: String, ownerUid: String): String? {
        val dayCount = s.endDate - s.startDate + 1
        val memberIds = s.members.map { it.memberId }.toSet()
        val ids = listOf(
            s.members.map { it.memberId }, s.activities.map { it.activityId }, s.checklist.map { it.itemId },
            s.places.map { it.placeId }, s.expenses.map { it.expenseId },
        )
        return when {
            s.tripId != documentId -> "tripId does not match the document"
            s.ownerUid != ownerUid -> "belongs to another account"
            !s.name.isValidText() -> "trip name"
            !s.destination.isValidText() -> "destination"
            s.endDate < s.startDate -> "dates reversed"
            s.members.size > Limits.MAX_MEMBERS -> "too many members"
            s.activities.size > Limits.MAX_ACTIVITIES -> "too many activities"
            s.checklist.size > Limits.MAX_CHECKLIST_ITEMS -> "too many checklist items"
            s.places.size > Limits.MAX_PLACES -> "too many places"
            s.expenses.size > Limits.MAX_EXPENSES -> "too many expenses"
            ids.any { it.any(String::isBlank) || it.toSet().size != it.size } -> "blank or repeated id"
            s.members.any { !it.name.isValidText() } -> "member name"
            s.activities.any {
                !it.title.isValidText() || it.place.length > Limits.MAX_TEXT || it.note.length > Limits.MAX_NOTE ||
                    it.startTime !in 0 until MINUTES_PER_DAY || it.dayIndex !in 0 until dayCount ||
                    it.estimatedCost < 0 || (it.reminderMinutesBefore ?: 0) < 0
            } -> "activity"
            s.checklist.any { !it.title.isValidText() } -> "checklist item"
            s.places.any {
                !it.name.isValidText() || it.country.length > Limits.MAX_TEXT || it.note.length > Limits.MAX_NOTE ||
                    it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0
            } -> "place"
            s.expenses.any {
                !it.title.isValidText() || it.amount <= 0 ||
                    ExpenseCategory.entries.none { c -> c.name == it.category } ||
                    SplitType.entries.none { t -> t.name == it.splitType } ||
                    it.payerId !in memberIds || !memberIds.containsAll(it.shares.keys) ||
                    ExpenseCalculator.validateCustomSplit(it.amount, it.shares) != null
            } -> "expense"
            else -> null
        }
    }

    private fun String.isValidText() = isNotBlank() && length <= Limits.MAX_TEXT
}
