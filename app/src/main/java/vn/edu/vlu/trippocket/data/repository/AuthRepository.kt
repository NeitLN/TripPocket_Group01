package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.edu.vlu.trippocket.domain.AuthProblem

/**
 * The signed-in account. Auth state is the one thing that does not come from Room
 * (proposal 2.2). Owner: Tiến.
 */
interface AuthRepository {
    /** Firebase uid of the signed-in account, or null when signed out. */
    val currentUid: StateFlow<String?>

    /**
     * Signs in an existing account. Returns once Firebase has a session, so [currentUid] is set.
     * @throws AuthException with the reason the screen can show.
     */
    suspend fun signIn(email: String, password: String)

    /**
     * Creates an account and signs it in (Firebase behaviour), so [currentUid] is set.
     * @throws AuthException with the reason the screen can show.
     */
    suspend fun register(email: String, password: String)

    /** Ends the session; trips saved on this phone stay in Room (proposal 6.2). */
    suspend fun signOut()
}

/** A failed auth call, already translated to an app-level [problem]. */
class AuthException(val problem: AuthProblem) : Exception("Auth failed: $problem")

/**
 * Stand-in used when Firebase is not initialised (no `google-services.json`), e.g. debug builds
 * on a machine without the shared config: it is always signed in as [DEV_UID], which owns the
 * sample trips seeded in debug builds. The real account is [vn.edu.vlu.trippocket.data.remote.firebase.FirebaseAuthRepository].
 */
class DevAuthRepository : AuthRepository {

    private val _currentUid = MutableStateFlow<String?>(DEV_UID)
    override val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    override suspend fun signIn(email: String, password: String) {
        _currentUid.value = DEV_UID
    }

    override suspend fun register(email: String, password: String) {
        _currentUid.value = DEV_UID
    }

    override suspend fun signOut() {
        _currentUid.value = null
    }

    companion object {
        const val DEV_UID = "dev-user"
    }
}
