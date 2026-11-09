package vn.edu.vlu.trippocket.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import vn.edu.vlu.trippocket.domain.ApiError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Result of one REST call (Milestone 4): a repository returns this instead of throwing, so a
 * ViewModel turns it into Success or Error state with a plain `when`.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/**
 * Runs [call] on [dispatcher] (Dispatchers.IO by default, never the main thread) and maps every
 * failure to an [ApiError]:
 *
 * - [HttpException] (a non-2xx status) by its code: 400, 404, 429, 5xx, anything else.
 * - [SocketTimeoutException] to TIMEOUT, any other [IOException] (no network, airplane mode,
 *   unknown host) to OFFLINE.
 * - JSON the converter could not read to BAD_RESPONSE.
 *
 * Cancellation is rethrown so leaving the screen still cancels the request.
 */
suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    call: suspend () -> T,
): ApiResult<T> = withContext(dispatcher) {
    try {
        ApiResult.Success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Failure(e.toApiError())
    }
}

/** The [ApiError] for a failure thrown by Retrofit, OkHttp or Gson. */
fun Throwable.toApiError(): ApiError = when (this) {
    is HttpException -> ApiError.fromHttpStatus(code())
    // SocketTimeoutException is an InterruptedIOException; a call timeout is a plain one.
    is SocketTimeoutException, is InterruptedIOException -> ApiError.TIMEOUT
    is IOException -> ApiError.OFFLINE
    // Gson's JsonParseException / IllegalStateException for JSON of an unexpected shape.
    else -> ApiError.BAD_RESPONSE
}
