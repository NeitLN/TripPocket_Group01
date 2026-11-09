package vn.edu.vlu.trippocket.data.remote.wikipedia

import com.google.gson.annotations.SerializedName
import vn.edu.vlu.trippocket.data.model.Sight
import vn.edu.vlu.trippocket.domain.GeoDistance

/**
 * Response of `GET w/api.php` with `generator=geosearch` (Milestone 4, Nearby sights). Every field
 * is nullable: a page can have no description, no thumbnail and no coordinates, and MediaWiki
 * leaves `query` out completely when nothing is near the point.
 */
data class GeoSearchResponse(
    @SerializedName("query") val query: Query? = null,
) {
    data class Query(
        @SerializedName("pages") val pages: List<Page>? = null,
    )

    data class Page(
        @SerializedName("pageid") val pageId: Long? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("thumbnail") val thumbnail: Thumbnail? = null,
        @SerializedName("coordinates") val coordinates: List<Coordinate>? = null,
    )

    data class Thumbnail(
        @SerializedName("source") val source: String? = null,
    )

    data class Coordinate(
        @SerializedName("lat") val lat: Double? = null,
        @SerializedName("lon") val lon: Double? = null,
    )
}

/**
 * The pages as [Sight]s, nearest first. A page without a title or without coordinates is skipped:
 * the row cannot be labelled or placed on the map without them. No `query` means "nothing within
 * the radius", which is an empty list, not an error.
 */
fun GeoSearchResponse.toSights(fromLat: Double, fromLon: Double): List<Sight> =
    query?.pages.orEmpty().mapNotNull { page ->
        val pageId = page.pageId ?: return@mapNotNull null
        val title = page.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val coordinate = page.coordinates?.firstOrNull() ?: return@mapNotNull null
        val lat = coordinate.lat ?: return@mapNotNull null
        val lon = coordinate.lon ?: return@mapNotNull null
        Sight(
            pageId = pageId,
            title = title,
            description = page.description?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = page.thumbnail?.source?.trim()?.takeIf { it.isNotEmpty() },
            lat = lat,
            lon = lon,
            distanceMeters = GeoDistance.meters(fromLat, fromLon, lat, lon),
        )
    }.sortedBy { it.distanceMeters }
