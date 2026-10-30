package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in account. Auth state is the one thing that does not come from Room
 * (proposal 2.2). Owner: Tiến.
 */
interface AuthRepository {
    /** Firebase uid of the signed-in account, or null when signed out. */
    val currentUid: StateFlow<String?>
}

/**
 * Stand-in until A1 brings Firebase Auth: always signed in as [DEV_UID], which owns the
 * sample trips seeded in debug builds.
 */
class DevAuthRepository : AuthRepository {
    override val currentUid: StateFlow<String?> = MutableStateFlow<String?>(DEV_UID).asStateFlow()

    companion object {
        const val DEV_UID = "dev-user"
    }
}
