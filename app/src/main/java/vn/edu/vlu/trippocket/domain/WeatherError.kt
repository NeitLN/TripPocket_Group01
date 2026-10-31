package vn.edu.vlu.trippocket.domain

/**
 * Why a request to Open-Meteo failed (H, proposal 6.4). In domain so the repository, the
 * ViewModel and the screen's strings name the same problems:
 *
 * - [NETWORK]: the phone could not reach Open-Meteo (offline, timeout, DNS).
 * - [RATE_LIMITED]: HTTP 429, too many requests for now.
 * - [SERVER]: HTTP 5xx, the service is having trouble.
 * - [BAD_RESPONSE]: another HTTP error (a bad request, a 404), or an answer the app could not
 *   read (JSON of an unexpected shape).
 */
enum class WeatherError {
    NETWORK,
    RATE_LIMITED,
    SERVER,
    BAD_RESPONSE,
}
