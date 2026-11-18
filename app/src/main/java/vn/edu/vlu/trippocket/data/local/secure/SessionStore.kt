package vn.edu.vlu.trippocket.data.local.secure

/**
 * What this phone remembers between launches, encrypted at rest (M5 stage 5).
 *
 * Never a password and never an ID token: the Firebase SDK owns the token and refreshes it, so a
 * copy here would be a second source of truth that expires after an hour.
 *
 * Every implementation touches the disk, so callers use it from `Dispatchers.IO`.
 */
interface SessionStore {

    /** E-mail of the account that signed in last; the login form prefills it. */
    fun lastEmail(): String?

    /** Replaces the remembered account after a successful sign-in or registration. */
    fun remember(email: String, uid: String)

    /** Forgets the account when the user signs out. */
    fun clear()
}
