package vn.edu.vlu.trippocket.domain

import androidx.annotation.StringRes
import vn.edu.vlu.trippocket.R

/**
 * Why a REST call failed (Milestone 4). Kept in domain, with no Retrofit type, so a ViewModel and
 * its screen can name the problem without knowing the network library.
 *
 * - [OFFLINE]: no connection (airplane mode, Wi-Fi off, DNS failure, connection refused).
 * - [TIMEOUT]: the server did not answer within the OkHttp timeouts.
 * - [BAD_REQUEST]: HTTP 400, the app sent something the server refused.
 * - [NOT_FOUND]: HTTP 404, the item or endpoint no longer exists.
 * - [RATE_LIMITED]: HTTP 429, too many requests for now.
 * - [SERVER]: HTTP 5xx, the service is having trouble.
 * - [BAD_RESPONSE]: another HTTP status, or a body the app could not read (unexpected JSON).
 */
enum class ApiError(@param:StringRes val messageRes: Int) {
    OFFLINE(R.string.api_error_offline),
    TIMEOUT(R.string.api_error_timeout),
    BAD_REQUEST(R.string.api_error_bad_request),
    NOT_FOUND(R.string.api_error_not_found),
    RATE_LIMITED(R.string.api_error_rate_limited),
    SERVER(R.string.api_error_server),
    BAD_RESPONSE(R.string.api_error_bad_response);

    companion object {
        /** The error for an HTTP status that is not 2xx. */
        fun fromHttpStatus(code: Int): ApiError = when (code) {
            400 -> BAD_REQUEST
            404 -> NOT_FOUND
            429 -> RATE_LIMITED
            in 500..599 -> SERVER
            else -> BAD_RESPONSE
        }
    }
}
