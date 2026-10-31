package vn.edu.vlu.trippocket.data.remote.firebase

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.edu.vlu.trippocket.domain.AuthProblem

/** A1: the Firebase error code to the reason the screen shows. Plain JVM, no Firebase started. */
class FirebaseAuthErrorMapperTest {

    @Test
    fun everyCredentialCode_becomesTheSameReason() {
        listOf(
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_EMAIL",
        ).forEach { code ->
            assertEquals(code, AuthProblem.INVALID_CREDENTIALS, FirebaseAuthErrorMapper.problem(code))
        }
    }

    @Test
    fun theKnownCodes_mapToTheirOwnReason() {
        assertEquals(AuthProblem.EMAIL_IN_USE, FirebaseAuthErrorMapper.problem("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthProblem.WEAK_PASSWORD, FirebaseAuthErrorMapper.problem("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthProblem.NETWORK, FirebaseAuthErrorMapper.problem("ERROR_NETWORK_REQUEST_FAILED"))
        assertEquals(AuthProblem.TOO_MANY_REQUESTS, FirebaseAuthErrorMapper.problem("ERROR_TOO_MANY_REQUESTS"))
    }

    @Test
    fun anUnknownOrMissingCode_isUnknown() {
        assertEquals(AuthProblem.UNKNOWN, FirebaseAuthErrorMapper.problem("ERROR_API_KEY_INVALID"))
        assertEquals(AuthProblem.UNKNOWN, FirebaseAuthErrorMapper.problem(""))
        assertEquals(AuthProblem.UNKNOWN, FirebaseAuthErrorMapper.problem(null))
    }
}
