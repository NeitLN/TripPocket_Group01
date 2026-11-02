package vn.edu.vlu.trippocket.data.remote.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.edu.vlu.trippocket.data.repository.AuthException
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.domain.AuthProblem

/**
 * A1: the real account, on Firebase Auth. The session survives an app restart because
 * [currentUid] starts from the user Firebase cached and follows [FirebaseAuth.AuthStateListener],
 * instead of being written by hand after each call.
 *
 * Only built when Firebase is initialised (see [vn.edu.vlu.trippocket.di.AppContainer], snippet
 * in the A1-data handoff), so a machine without `google-services.json` keeps DevAuthRepository.
 */
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    private val _currentUid = MutableStateFlow(auth.currentUser?.uid)
    override val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUid.value = firebaseAuth.currentUser?.uid
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override suspend fun signIn(email: String, password: String) {
        call { auth.signInWithEmailAndPassword(email.trim(), password).awaitResult() }
    }

    override suspend fun register(email: String, password: String) {
        call { auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult() }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    /** One Firebase call, with its error translated; a cancelled screen is not an auth error. */
    private suspend fun call(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthException(problemOf(e))
        }
    }

    /**
     * Type first, code second: the network and rate-limit failures are not
     * [FirebaseAuthException] and carry no useful auth code.
     */
    private fun problemOf(e: Throwable): AuthProblem = when (e) {
        is FirebaseNetworkException -> AuthProblem.NETWORK
        is FirebaseTooManyRequestsException -> AuthProblem.TOO_MANY_REQUESTS
        is FirebaseAuthException -> FirebaseAuthErrorMapper.problem(e.errorCode)
        else -> AuthProblem.UNKNOWN
    }
}
