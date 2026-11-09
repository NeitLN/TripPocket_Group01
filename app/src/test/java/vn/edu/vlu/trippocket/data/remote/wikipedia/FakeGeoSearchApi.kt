package vn.edu.vlu.trippocket.data.remote.wikipedia

import kotlinx.coroutines.CompletableDeferred

/**
 * Milestone 4 test double for Wikipedia GeoSearch: records the coordinates asked for, answers
 * with [response], or fails with [failWith]. [gate] holds a request open so a test can see the
 * Loading state.
 */
class FakeGeoSearchApi(
    private val response: GeoSearchResponse = GeoSearchResponse(),
) : GeoSearchApi {

    val requests = mutableListOf<Request>()
    var failWith: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    data class Request(val coord: String, val radius: Int, val limit: Int)

    override suspend fun geosearch(
        action: String,
        generator: String,
        coord: String,
        radius: Int,
        limit: Int,
        prop: String,
        piprop: String,
        thumbSize: Int,
        format: String,
        formatVersion: Int,
    ): GeoSearchResponse {
        requests += Request(coord, radius, limit)
        gate?.await()
        failWith?.let { throw it }
        return response
    }

    companion object {
        fun page(
            pageId: Long,
            title: String,
            lat: Double = 11.9406,
            lon: Double = 108.4585,
            description: String? = "Lake in Da Lat",
            imageUrl: String? = "https://img/$pageId.jpg",
        ) = GeoSearchResponse.Page(
            pageId = pageId,
            title = title,
            description = description,
            thumbnail = imageUrl?.let { GeoSearchResponse.Thumbnail(it) },
            coordinates = listOf(GeoSearchResponse.Coordinate(lat, lon)),
        )

        fun response(vararg pages: GeoSearchResponse.Page) =
            GeoSearchResponse(GeoSearchResponse.Query(pages.toList()))
    }
}
