package vn.edu.vlu.trippocket.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** Where backup documents live: users/{uid}/trips/{tripId} (proposal 4.1). Faked in tests. */
interface BackupRemote {
    /** Overwrites the whole document, so deleted child rows disappear from the backup too. */
    suspend fun upload(snapshot: TripSnapshot)

    /** Removes the document of a deleted trip; succeeds if it was never uploaded. */
    suspend fun delete(ownerUid: String, tripId: String)
}

/**
 * Firestore refused the call for a reason that will not go away by retrying (rules, a bad
 * document): the backup job marks the trip FAILED instead of retrying it forever.
 */
class BackupRejectedException(reason: String, cause: Throwable? = null) :
    Exception("Firestore refused the backup: $reason", cause)

/**
 * Firestore backup (A3). Room is the source of truth, so Firestore keeps no local cache of its
 * own; each call waits for the server and gives up after [timeoutMillis] with an IOException,
 * which the backup job treats as "try again later" instead of hanging on a flaky network.
 */
class FirestoreBackupRemote(
    private val firestore: FirebaseFirestore,
    private val timeoutMillis: Long = 60_000,
) : BackupRemote {

    override suspend fun upload(snapshot: TripSnapshot) {
        call { document(snapshot.ownerUid, snapshot.tripId).set(snapshot).awaitResult() }
    }

    override suspend fun delete(ownerUid: String, tripId: String) {
        call { document(ownerUid, tripId).delete().awaitResult() }
    }

    private fun document(ownerUid: String, tripId: String) =
        firestore.collection("users").document(ownerUid).collection("trips").document(tripId)

    /**
     * No answer in time, or a server error that may pass (UNAVAILABLE, DEADLINE_EXCEEDED, ...):
     * IOException, "try again later". A refusal that will not change by itself
     * (PERMISSION_DENIED, INVALID_ARGUMENT, ...): [BackupRejectedException].
     */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Firestore did not answer in time", e)
    } catch (e: FirebaseFirestoreException) {
        throw if (e.code in TRANSIENT) IOException("Firestore: ${e.code}", e) else BackupRejectedException(e.code.name, e)
    }

    companion object {
        private val TRANSIENT = setOf(
            Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.RESOURCE_EXHAUSTED,
            Code.INTERNAL, Code.UNAUTHENTICATED, Code.CANCELLED, Code.UNKNOWN,
        )

        /** The app's Firestore without its own offline cache; call once, before any other use. */
        fun create(): FirestoreBackupRemote {
            val firestore = FirebaseFirestore.getInstance()
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            return FirestoreBackupRemote(firestore)
        }
    }
}

/** Builds without google-services.json have no Firebase: nothing can be uploaded. */
object NoBackupRemote : BackupRemote {
    override suspend fun upload(snapshot: TripSnapshot) = throw IOException("Firebase is not configured")
    override suspend fun delete(ownerUid: String, tripId: String) = throw IOException("Firebase is not configured")
}
