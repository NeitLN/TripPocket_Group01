package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.edu.vlu.trippocket.domain.AuthProblem

/**
 * A1 test double: never starts Firebase. Set [failWith] to make the next call fail with that
 * reason, or [gate] to hold a call open so the test can see the Loading state.
 */
class FakeAuthRepository(initialUid: String? = null) : AuthRepository {

    private val _currentUid = MutableStateFlow(initialUid)
    override val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    /** When set, signIn/register throw [AuthException] with it instead of signing in. */
    var failWith: AuthProblem? = null

    /** When set, signIn/register wait for it before answering. */
    var gate: CompletableDeferred<Unit>? = null

    /** Sign-in and register calls, so a form error can be checked as "never reached the repository". */
    var calls = 0
        private set

    var lastEmail: String? = null
        private set

    var signOutCalls = 0
        private set

    /** Simulates a session restored by Firebase on app start. */
    fun setUid(uid: String?) {
        _currentUid.value = uid
    }

    override suspend fun signIn(email: String, password: String) = answer(email)

    override suspend fun register(email: String, password: String) = answer(email)

    override suspend fun signOut() {
        signOutCalls++
        _currentUid.value = null
    }

    private suspend fun answer(email: String) {
        calls++
        lastEmail = email
        gate?.await()
        failWith?.let { throw AuthException(it) }
        _currentUid.value = UID
    }

    companion object {
        const val UID = "uid-test"
    }
}
