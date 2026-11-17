package vn.edu.vlu.trippocket.data.remote.mockapi

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Trip Ideas on the team's MockAPI.io project (Milestone 4), base URL
 * `BuildConfig.MOCKAPI_BASE_URL` (`https://<project>.mockapi.io/api/v1/`). Owner: Tiến.
 *
 * Every function is `suspend`: Retrofit runs the request on OkHttp's threads and the repository
 * wraps it in safeApiCall on Dispatchers.IO. A non-2xx status is thrown as HttpException.
 */
interface TripIdeaApi {

    /** GET /ideas: every shared idea, newest first. */
    @GET("ideas")
    suspend fun list(
        @Query("sortBy") sortBy: String = "createdAt",
        @Query("order") order: String = "desc",
    ): List<TripIdeaDto>

    /** GET /ideas/{id}: one idea, 404 when it was deleted. */
    @GET("ideas/{id}")
    suspend fun get(@Path("id") id: String): TripIdeaDto

    /** POST /ideas: shares a new idea; MockAPI answers 201 with the stored idea and its new id. */
    @POST("ideas")
    suspend fun create(@Body body: TripIdeaBody): TripIdeaDto

    /** PUT /ideas/{id}: replaces the fields of an idea; 404 when it was deleted. */
    @PUT("ideas/{id}")
    suspend fun update(@Path("id") id: String, @Body body: TripIdeaBody): TripIdeaDto

    /** DELETE /ideas/{id}: removes an idea; MockAPI answers 200 with the removed idea. */
    @DELETE("ideas/{id}")
    suspend fun delete(@Path("id") id: String): TripIdeaDto
}
