package vn.edu.vlu.trippocket.work

/**
 * Asks for the trips of [ownerUid] to be uploaded. The real version (A3) enqueues the
 * unique work "trip-backup:<uid>" with APPEND_OR_REPLACE; tests use a fake.
 */
fun interface BackupScheduler {
    fun request(ownerUid: String)
}
