package vn.edu.vlu.trippocket.domain

/**
 * Why a trip was not saved (A2). The form and the repository check the same rules: the
 * repository throws [vn.edu.vlu.trippocket.data.repository.InvalidTripException] with one of
 * these, and the form shows it under the matching field.
 */
enum class TripProblem {
    BLANK_NAME,
    NAME_TOO_LONG,
    BLANK_DESTINATION,

    /** Destination longer than `Limits.MAX_TEXT` (80). */
    DESTINATION_TOO_LONG,

    /** The end date is before the start date. */
    DATES_REVERSED,

    /** The new dates leave out a day that still has activities; move or delete them first. */
    DAYS_IN_USE,

    /** Missing, deleted, owned by another account, or signed out. */
    NOT_FOUND,
}
