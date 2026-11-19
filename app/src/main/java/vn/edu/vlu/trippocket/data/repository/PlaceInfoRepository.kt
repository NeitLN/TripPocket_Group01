package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.vlu.trippocket.data.local.dao.PlaceInfoCacheDao
import vn.edu.vlu.trippocket.data.local.entity.PlaceInfoCacheEntity
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
 *
 * A1: each answer (also "no article") is saved in [cache]; when the next request fails, the
 * saved answer comes back instead, so a saved place keeps its photo and introduction offline.
 */
class PlaceInfoRepository(
    private val api: PageSummaryApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cache: PlaceInfoCacheDao? = null,
    private val clock: () -> Long = System::currentTimeMillis,
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
        val answer = when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toPlaceInfo())
            // The 404 arrives as a Failure, not as a thrown HttpException: safeApiCall already
            // mapped it. Here it means "no photo, no introduction", not an error for the screen.
            is ApiResult.Failure ->
                if (result.error == ApiError.NOT_FOUND) ApiResult.Success(null) else result
        }
        return when (answer) {
            is ApiResult.Success -> answer.also { save(page, it.data) }
            is ApiResult.Failure -> saved(page) ?: answer
        }
    }

    private suspend fun save(page: String, info: PlaceInfo?) {
        val cache = cache ?: return
        val row = PlaceInfoCacheEntity(page, info?.title, info?.description, info?.extract, info?.imageUrl, clock())
        try {
            withContext(io) { cache.upsert(row) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the offline copy is lost.
        }
    }

    /** The saved answer for [page] (null inside = "no article"), or null when nothing is saved. */
    private suspend fun saved(page: String): ApiResult<PlaceInfo?>? {
        val cache = cache ?: return null
        val row = try {
            withContext(io) { cache.get(page) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val title = row.title ?: return ApiResult.Success(null)
        return ApiResult.Success(PlaceInfo(title, row.description, row.extract, row.imageUrl))
    }
}
