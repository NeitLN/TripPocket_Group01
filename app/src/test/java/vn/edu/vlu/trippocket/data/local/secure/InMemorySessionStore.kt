package vn.edu.vlu.trippocket.data.local.secure

/**
 * Test double for [SessionStore].
 *
 * The real [EncryptedSessionStore] opens the AndroidKeyStore in its constructor and Robolectric
 * has no keystore, so unit tests never touch it (M5 stage 5): they assert on this class instead.
 */
class InMemorySessionStore(initialEmail: String? = null) : SessionStore {

    var email: String? = initialEmail
    var uid: String? = null

    /** How many times the ViewModel asked to remember an account. */
    var reminders = 0
        private set

    /** How many times the ViewModel asked to forget it. */
    var clears = 0
        private set

    override fun lastEmail(): String? = email

    override fun remember(email: String, uid: String) {
        this.email = email
        this.uid = uid
        reminders++
    }

    override fun clear() {
        email = null
        uid = null
        clears++
    }
}
