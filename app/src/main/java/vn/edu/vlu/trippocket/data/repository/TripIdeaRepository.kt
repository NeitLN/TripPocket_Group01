package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import vn.edu.vlu.trippocket.data.model.TripIdea
import vn.edu.vlu.trippocket.data.model.TripIdeaInput
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.mockapi.TripIdeaApi
import vn.edu.vlu.trippocket.data.remote.mockapi.toBody
import vn.edu.vlu.trippocket.data.remote.mockapi.toModel
import vn.edu.vlu.trippocket.data.remote.mockapi.toModels
import vn.edu.vlu.trippocket.data.remote.safeApiCall
import vn.edu.vlu.trippocket.domain.ApiError
import java.time.Instant

/**
 * Trip Ideas on MockAPI.io (Milestone 4, owner: Tiến). The single source the ViewModels read:
 * no Activity or Fragment touches [TripIdeaApi].
 *
 * Every call runs through [safeApiCall] on [io] (Dispatchers.IO) and returns an [ApiResult], so
 * a 400 / 404 / 5xx / timeout / offline arrives as data the screen can show, never as a crash.
 * The ideas are not kept in Room: they belong to the server, and the list is reloaded each time
 * the screen opens.
 */
class TripIdeaRepository(
    private val api: TripIdeaApi,
    private val auth: AuthRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = Instant::now,
) {

    /** Uid of the signed-in account, to know which ideas it may edit. */
    val currentUid: String? get() = auth.currentUid.value

    /** GET /ideas, newest first. */
    suspend fun ideas(): ApiResult<List<TripIdea>> = safeApiCall(io) { api.list().toModels() }

    /** GET /ideas/{id}; a row the app cannot read counts as a bad response. */
    suspend fun idea(id: String): ApiResult<TripIdea> = safeApiCall(io) {
        api.get(id).toModel() ?: throw IllegalStateException("Idea $id has no id or title")
    }

    /** POST /ideas as the signed-in account. */
    suspend fun share(input: TripIdeaInput): ApiResult<TripIdea> = safeApiCall(io) {
        api.create(input.toBody(auth.displayName.orEmpty(), currentUid.orEmpty(), createdAt = now().toString())).toModel()
            ?: throw IllegalStateException("Created idea came back without id or title")
    }

    /** PUT /ideas/{id}; the author stays the signed-in account. */
    suspend fun update(id: String, input: TripIdeaInput): ApiResult<TripIdea> = safeApiCall(io) {
        api.update(id, input.toBody(auth.displayName.orEmpty(), currentUid.orEmpty())).toModel()
            ?: throw IllegalStateException("Updated idea came back without id or title")
    }

    /** DELETE /ideas/{id}. A 404 means someone already removed it, which is what the user wanted. */
    suspend fun delete(id: String): ApiResult<Unit> = when (val result = safeApiCall(io) { api.delete(id) }) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Failure -> if (result.error == ApiError.NOT_FOUND) ApiResult.Success(Unit) else result
    }
}
