package vn.edu.vlu.trippocket.domain

/**
 * Why an activity was not saved. In domain, so the form, the repository and the tests name the
 * same problem: the repository throws
 * [vn.edu.vlu.trippocket.data.repository.InvalidActivityException] with one of these values,
 * and the form shows the matching error under the field.
 */
enum class ActivityProblem {
    BLANK_TITLE,
    TITLE_TOO_LONG,

    /** Place longer than `Limits.MAX_TEXT`; a blank place is allowed. */
    PLACE_TOO_LONG,
    NOTE_TOO_LONG,

    /** The cost is negative: money is a positive `Long` VND, 0 meaning free. */
    COST_NEGATIVE,

    /** The form could not read the cost as a number; the repository never throws this. */
    COST_INVALID,

    /** The start time is outside 0..1439 minutes of the day. */
    TIME_OUT_OF_RANGE,

    /** The day is not one of the trip's days. */
    DAY_OUT_OF_RANGE,

    /** The reminder is not a number of minutes, or it is negative. */
    REMINDER_INVALID,

    /** The activity does not exist, or it belongs to another trip (a missing trip also ends here). */
    NOT_FOUND,

    /** A drag list that is not exactly the activities of that day (missing, extra or duplicate id). */
    ORDER_MISMATCH,
}
