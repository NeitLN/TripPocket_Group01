package vn.edu.vlu.trippocket.data.remote.wikipedia

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Wikipedia GeoSearch (Milestone 4, Nearby sights): `GET w/api.php` with the MediaWiki query
 * parameters for pages around a point.
 *
 * [vn.edu.vlu.trippocket.di.AppContainer.wikipediaRetrofit] supplies the base URL and client.
 * Only [vn.edu.vlu.trippocket.data.repository.SightRepository] calls it; screens never do.
 *
 * Every value is a parameter: the interface has no default arguments, so the repository is the
 * one place that decides what is asked. `ggscoord` in particular must be formatted with
 * [java.util.Locale.US], or a Vietnamese device would send `11,940400|108,458300`.
 */
interface GeoSearchApi {

    /**
     * Pages within [radius] metres (maximum 10000) of [coord] (`"lat|lon"`), at most
     * [limit] of them (maximum 500, 20 used here), with their thumbnail and description.
     */
    @GET("w/api.php")
    suspend fun geosearch(
        @Query("action") action: String,
        @Query("generator") generator: String,
        @Query("ggscoord") coord: String,
        @Query("ggsradius") radius: Int,
        @Query("ggslimit") limit: Int,
        @Query("prop") prop: String,
        @Query("piprop") piprop: String,
        @Query("pithumbsize") thumbSize: Int,
        @Query("format") format: String,
        @Query("formatversion") formatVersion: Int,
    ): GeoSearchResponse
}
