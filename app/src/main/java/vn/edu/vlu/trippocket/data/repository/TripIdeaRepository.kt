package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.vlu.trippocket.data.local.dao.TripIdeaCacheDao
import vn.edu.vlu.trippocket.data.local.entity.TripIdeaCacheEntity
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
 * The server owns the ideas and the list is reloaded each time the screen opens. A1: the last
 * good list is also copied to [cache], which [savedIdeas] reads when the download fails, so the
 * screen still shows something in Airplane Mode.
 */
class TripIdeaRepository(
    private val api: TripIdeaApi,
    private val auth: AuthRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = Instant::now,
    private val cache: TripIdeaCacheDao? = null,
) {

    /** Uid of the signed-in account, to know which ideas it may edit. */
    val currentUid: String? get() = auth.currentUid.value

    /** GET /ideas, newest first. A good answer replaces the offline copy. */
    suspend fun ideas(): ApiResult<List<TripIdea>> {
        val result = safeApiCall(io) { api.list().toModels() }
        if (result is ApiResult.Success) keep(result.data)
        return result
    }

    /** The list of the last good load, in the same order; empty when there is none or it cannot be read. */
    suspend fun savedIdeas(): List<TripIdea> {
        val cache = cache ?: return emptyList()
        return try {
            withContext(io) { cache.all() }.map { it.toModel() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun keep(ideas: List<TripIdea>) {
        val cache = cache ?: return
        val fetchedAt = now().toEpochMilli()
        try {
            withContext(io) { cache.replaceAll(ideas.mapIndexed { i, idea -> idea.toCacheRow(i, fetchedAt) }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the offline copy is lost; the fresh list is still shown.
        }
    }

    private suspend fun forget(id: String) {
        val cache = cache ?: return
        try {
            withContext(io) { cache.delete(id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next good load rewrites the copy anyway.
        }
    }

    /** GET /ideas/{id}; a row the app cannot read counts as a bad response. */
    suspend fun idea(id: String): ApiResult<TripIdea> = safeApiCall(io) {
        api.get(id).toModel() ?: throw IllegalStateException("Idea $id has no id or title")
    }

    /**
     * POST /ideas as the signed-in account. Without one nothing is sent ([ApiError.FORBIDDEN]):
     * an idea with no owner could never be edited or deleted. The author name is the profile
     * name, never part of the email, since every idea is public.
     */
    suspend fun share(input: TripIdeaInput): ApiResult<TripIdea> {
        val uid = currentUid ?: return ApiResult.Failure(ApiError.FORBIDDEN)
        return safeApiCall(io) {
            api.create(input.toBody(auth.displayName ?: ANONYMOUS, uid, createdAt = now().toString())).toModel()
                ?: throw IllegalStateException("Created idea came back without id or title")
        }
    }

    /**
     * PUT /ideas/{id}, only for the account that shared the idea: MockAPI accepts any PUT, so the
     * owner check happens here, before sending. The original author and uid are kept, so an edit
     * never changes who owns the idea. [original] is the copy the form loaded; without it the
     * idea is read again (GET) first.
     */
    suspend fun update(id: String, input: TripIdeaInput, original: TripIdea? = null): ApiResult<TripIdea> {
        val current = original ?: when (val read = idea(id)) {
            is ApiResult.Success -> read.data
            is ApiResult.Failure -> return read
        }
        val uid = currentUid
        if (uid == null || current.authorUid != uid) return ApiResult.Failure(ApiError.FORBIDDEN)
        return safeApiCall(io) {
            api.update(id, input.toBody(current.author, uid)).toModel()
                ?: throw IllegalStateException("Updated idea came back without id or title")
        }
    }

    /**
     * DELETE /ideas/{id}, only for the account that shared the idea (checked here, as for
     * [update]); [known] is the row the list shows, otherwise the idea is read first. A 404 means
     * someone already removed it, which is what the user wanted.
     */
    suspend fun delete(id: String, known: TripIdea? = null): ApiResult<Unit> {
        val current = known ?: when (val read = idea(id)) {
            is ApiResult.Success -> read.data
            is ApiResult.Failure -> return if (read.error == ApiError.NOT_FOUND) ApiResult.Success(Unit) else read
        }
        val uid = currentUid
        if (uid == null || current.authorUid != uid) return ApiResult.Failure(ApiError.FORBIDDEN)
        val result = when (val sent = safeApiCall(io) { api.delete(id) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> if (sent.error == ApiError.NOT_FOUND) ApiResult.Success(Unit) else sent
        }
        // A deleted idea must not come back in the offline copy.
        if (result is ApiResult.Success) forget(id)
        return result
    }

    private fun TripIdea.toCacheRow(position: Int, fetchedAt: Long) = TripIdeaCacheEntity(
        id = id, position = position, title = title, destination = destination, days = days,
        budgetVnd = budgetVnd, coverUrl = coverUrl, summary = summary, author = author,
        authorUid = authorUid, fetchedAt = fetchedAt,
    )

    private fun TripIdeaCacheEntity.toModel() = TripIdea(
        id = id, title = title, destination = destination, days = days, budgetVnd = budgetVnd,
        coverUrl = coverUrl, summary = summary, author = author, authorUid = authorUid,
    )

    companion object {
        /** Author name when the account has no profile name. */
        const val ANONYMOUS = "Anonymous"
    }
}
