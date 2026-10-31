package vn.edu.vlu.trippocket.domain

/** Why a member was not saved or removed (E1). */
enum class MemberProblem { BLANK_NAME, NAME_TOO_LONG, LIMIT, IN_USE, NOT_FOUND }

/**
 * The one rule for a member name (proposal 3.4): trimmed, not empty, at most
 * [Limits.MAX_TEXT] characters. Shared by [vn.edu.vlu.trippocket.data.repository.ExpenseRepository],
 * which writes members, and by the members screen, so the rename dialog shows the error in its
 * TextInputLayout without waiting for the database.
 */
object MemberNameRule {

    /** null when [name] can be saved; otherwise the problem to show. Surrounding spaces are ignored. */
    fun validate(name: String): MemberProblem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> MemberProblem.BLANK_NAME
            trimmed.length > Limits.MAX_TEXT -> MemberProblem.NAME_TOO_LONG
            else -> null
        }
    }

    /** The name as stored. Only call it after [validate] returned null. */
    fun clean(name: String): String = name.trim()
}
