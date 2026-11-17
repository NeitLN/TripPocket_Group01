package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import vn.edu.vlu.trippocket.data.model.Sight
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.safeApiCall
import vn.edu.vlu.trippocket.data.remote.wikipedia.GeoSearchApi
import vn.edu.vlu.trippocket.data.remote.wikipedia.toSights
import java.util.Locale

/**
 * Nearby sights from Wikipedia (Milestone 4, Nearby sights). [safeApiCall] runs the request on
 * [io] and returns the failure instead of throwing, so this class never throws to the ViewModel
 * (cancellation excepted, which is rethrown). An empty answer is [ApiResult.Success] with an empty
 * list: Wikipedia simply has no page within the radius, which the screen shows as Empty.
 */
class SightRepository(
    private val api: GeoSearchApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Pages within [RADIUS_METERS] of ([lat], [lon]), nearest first. */
    suspend fun nearby(lat: Double, lon: Double): ApiResult<List<Sight>> {
        // Wikipedia reads "lat|lon" with a dot decimal separator. The device locale would turn
        // that dot into a comma on a Vietnamese phone, so the format is pinned to Locale.US.
        val coord = String.format(Locale.US, "%.6f|%.6f", lat, lon)
        val result = safeApiCall(io) {
            api.geosearch(
                action = "query",
                generator = "geosearch",
                coord = coord,
                radius = RADIUS_METERS,
                limit = LIMIT,
                prop = "pageimages|description|coordinates",
                piprop = "thumbnail",
                thumbSize = THUMB_SIZE,
                format = "json",
                formatVersion = 2,
            )
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toSights(lat, lon))
            is ApiResult.Failure -> result
        }
    }

    companion object {
        /** GeoSearch radius in metres (Wikipedia allows at most 10000). */
        const val RADIUS_METERS = 10_000

        /** Pages asked for per request (the screen shows a short list). */
        const val LIMIT = 20

        /** Thumbnail width in pixels, as the row's image does not need more. */
        const val THUMB_SIZE = 400
    }
}
