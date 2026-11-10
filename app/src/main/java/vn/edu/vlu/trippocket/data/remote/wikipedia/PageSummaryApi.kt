package vn.edu.vlu.trippocket.data.remote.wikipedia

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Wikipedia page summary API (Milestone 4, owner: Duy Anh):
 * `https://en.wikipedia.org/api/rest_v1/page/summary/{title}`.
 * [vn.edu.vlu.trippocket.di.AppContainer.wikipediaRetrofit] supplies the base URL and client.
 *
 * Only [vn.edu.vlu.trippocket.data.repository.PlaceInfoRepository] calls it; screens never do.
 * An article that does not exist answers HTTP 404, which the repository turns into "no photo
 * and no introduction", not a screen error.
 */
interface PageSummaryApi {

    /**
     * The summary of the article [title]. Spaces must arrive as underscores, which
     * [vn.edu.vlu.trippocket.data.repository.PlaceInfoRepository] does before calling.
     */
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun summary(@Path("title") title: String): PageSummaryResponse
}
