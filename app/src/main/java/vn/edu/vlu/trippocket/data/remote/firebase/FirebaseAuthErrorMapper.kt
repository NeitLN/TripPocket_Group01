package vn.edu.vlu.trippocket.data.remote.firebase

import vn.edu.vlu.trippocket.domain.AuthProblem

/**
 * A1: the Firebase error code to the reason the screen shows. Codes are compared as strings, so
 * this mapper is a plain JVM class with no Firebase on its dependencies and is unit-tested
 * without an emulator. Unknown codes fall back to [AuthProblem.UNKNOWN]; the raw message is
 * never passed on.
 */
object FirebaseAuthErrorMapper {

    fun problem(errorCode: String?): AuthProblem = when (errorCode) {
        // Per-field codes from older SDKs and the combined one: never say which field was wrong,
        // otherwise the screen tells an attacker whether an email has an account.
        ERROR_INVALID_CREDENTIAL,
        ERROR_INVALID_LOGIN_CREDENTIALS,
        ERROR_WRONG_PASSWORD,
        ERROR_USER_NOT_FOUND,
        ERROR_INVALID_EMAIL,
        -> AuthProblem.INVALID_CREDENTIALS

        ERROR_EMAIL_ALREADY_IN_USE -> AuthProblem.EMAIL_IN_USE
        ERROR_WEAK_PASSWORD -> AuthProblem.WEAK_PASSWORD
        ERROR_NETWORK_REQUEST_FAILED -> AuthProblem.NETWORK
        ERROR_TOO_MANY_REQUESTS -> AuthProblem.TOO_MANY_REQUESTS
        else -> AuthProblem.UNKNOWN
    }

    private const val ERROR_INVALID_CREDENTIAL = "ERROR_INVALID_CREDENTIAL"
    private const val ERROR_INVALID_LOGIN_CREDENTIALS = "ERROR_INVALID_LOGIN_CREDENTIALS"
    private const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
    private const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
    private const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"
    private const val ERROR_EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
    private const val ERROR_WEAK_PASSWORD = "ERROR_WEAK_PASSWORD"
    private const val ERROR_NETWORK_REQUEST_FAILED = "ERROR_NETWORK_REQUEST_FAILED"
    private const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
}
