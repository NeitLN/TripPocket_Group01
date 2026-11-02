package vn.edu.vlu.trippocket.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Where backup documents live: users/{uid}/trips/{tripId} (proposal 4.1). Faked in tests. */
interface BackupRemote {
    /** Overwrites the whole document, so deleted child rows disappear from the backup too. */
    suspend fun upload(snapshot: TripSnapshot)

    /** Removes the document of a deleted trip; succeeds if it was never uploaded. */
    suspend fun delete(ownerUid: String, tripId: String)
}

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

    private suspend fun call(block: suspend () -> Any?) {
        withTimeoutOrNull(timeoutMillis) { block(); Unit } ?: throw IOException("Firestore did not answer in time")
    }

    companion object {
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
