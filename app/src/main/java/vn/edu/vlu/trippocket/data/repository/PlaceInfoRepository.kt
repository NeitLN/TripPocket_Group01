package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import vn.edu.vlu.trippocket.data.model.PlaceInfo
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.safeApiCall
import vn.edu.vlu.trippocket.data.remote.wikipedia.PageSummaryApi
import vn.edu.vlu.trippocket.data.remote.wikipedia.toPlaceInfo
import vn.edu.vlu.trippocket.domain.ApiError

/**
 * Photos and a short introduction from Wikipedia (Milestone 4, owner: Duy Anh).
 *
 * [safeApiCall] runs the request on [io] and returns the failure instead of throwing, so this
 * class never throws to the ViewModel (cancellation excepted, which is rethrown). HTTP 404 means
 * "Wikipedia has no article for this place": it becomes [ApiResult.Success] with null, because a
 * place without a photo is not a screen error. Every other failure stays an [ApiError].
 */
class PlaceInfoRepository(
    private val api: PageSummaryApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * The summary of the article [title], or null when there is no article or nothing to show.
     * A blank title answers null without a request.
     */
    suspend fun placeInfo(title: String): ApiResult<PlaceInfo?> {
        // Wikipedia article titles use underscores instead of spaces.
        val page = title.trim().replace(' ', '_')
        if (page.isEmpty()) return ApiResult.Success(null)
        val result = safeApiCall(io) { api.summary(page) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toPlaceInfo())
            // The 404 arrives as a Failure, not as a thrown HttpException: safeApiCall already
            // mapped it. Here it means "no photo, no introduction", not an error for the screen.
            is ApiResult.Failure ->
                if (result.error == ApiError.NOT_FOUND) ApiResult.Success(null) else result
        }
    }
}
