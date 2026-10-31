package vn.edu.vlu.trippocket.data.remote.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [Task] as a suspend call without kotlinx-coroutines-play-services, which is not a project
 * dependency: the listener resumes the coroutine when the task finishes, and a cancelled
 * coroutine simply drops the result. Shared by Firebase Auth (A1) and the backup (A3).
 */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val error = task.exception
        if (error != null) {
            continuation.resumeWithException(error)
        } else {
            continuation.resume(task.result)
        }
    }
}
