package vn.edu.vlu.trippocket.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** One stored document, still raw: restore checks it before it becomes a TripSnapshot. */
data class BackupDocument(val id: String, val data: Map<String, Any?>)

/** Where backup documents live: users/{uid}/trips/{tripId} (proposal 4.1). Faked in tests. */
interface BackupRemote {
    /** Overwrites the whole document, so deleted child rows disappear from the backup too. */
    suspend fun upload(snapshot: TripSnapshot)

    /** Removes the document of a deleted trip; succeeds if it was never uploaded. */
    suspend fun delete(ownerUid: String, tripId: String)

    /** Every backup document of the account, read from the server (A4 restore). */
    suspend fun fetchAll(ownerUid: String): List<BackupDocument>
}

/**
 * Firestore backup (A3) and restore (A4). Room is the source of truth, so Firestore keeps no
 * local cache of its own; each call waits for the server and gives up after [timeoutMillis] with
 * an IOException, which the backup job treats as "try again later" instead of hanging on a flaky
 * network. Documents are written and read as maps through TripSnapshotCodec.
 */
class FirestoreBackupRemote(
    private val firestore: FirebaseFirestore,
    private val timeoutMillis: Long = 60_000,
) : BackupRemote {

    override suspend fun upload(snapshot: TripSnapshot) {
        call { document(snapshot.ownerUid, snapshot.tripId).set(TripSnapshotCodec.toMap(snapshot)).awaitResult() }
    }

    override suspend fun delete(ownerUid: String, tripId: String) {
        call { document(ownerUid, tripId).delete().awaitResult() }
    }

    override suspend fun fetchAll(ownerUid: String): List<BackupDocument> = call {
        trips(ownerUid).get(Source.SERVER).awaitResult().documents.map { BackupDocument(it.id, it.data.orEmpty()) }
    }

    private fun trips(ownerUid: String) = firestore.collection("users").document(ownerUid).collection("trips")

    private fun document(ownerUid: String, tripId: String) = trips(ownerUid).document(tripId)

    /** No answer in time, or no connection to the server: an IOException, "try again later". */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Firestore did not answer in time", e)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw IOException("Firestore is unreachable", e)
        throw e
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
    override suspend fun fetchAll(ownerUid: String): List<BackupDocument> = throw IOException("Firebase is not configured")
}
