package vn.edu.vlu.trippocket.domain

/**
 * Why Firebase Auth refused a call, as app-level reasons: the screens map them to their own
 * strings, so a raw Firebase message never reaches the UI (A1). Kept in domain so no layer
 * imports upward from data.
 */
enum class AuthProblem {
    /** Wrong password or unknown email: the same answer for both, so nobody can probe for accounts. */
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    /** Firebase refused the password; shown in the password field, not as a general error. */
    WEAK_PASSWORD,
    NETWORK,
    TOO_MANY_REQUESTS,
    UNKNOWN,
}
