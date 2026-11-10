package vn.edu.vlu.trippocket.data.remote.wikipedia

import com.google.gson.annotations.SerializedName
import vn.edu.vlu.trippocket.data.model.PlaceInfo

/**
 * Response of `GET api/rest_v1/page/summary/{title}` (Milestone 4, owner: Duy Anh). Every field
 * is nullable because Wikipedia leaves out what a page does not have: a stub or a disambiguation
 * page can have a thumbnail and no `extract`, and a page without a picture has no `thumbnail`.
 */
data class PageSummaryResponse(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("extract") val extract: String? = null,
    @SerializedName("thumbnail") val thumbnail: Thumbnail? = null,
) {
    data class Thumbnail(
        @SerializedName("source") val source: String? = null,
    )
}

/**
 * The summary as [PlaceInfo], or null when the page has nothing to show: neither an `extract`
 * nor a thumbnail. A thumbnail without an `extract` still gives the search row its picture, so
 * a stub page never loses the photo it does have.
 */
fun PageSummaryResponse.toPlaceInfo(): PlaceInfo? {
    val imageUrl = thumbnail?.source?.trim()?.takeIf { it.isNotEmpty() }
    val extract = extract?.trim()?.takeIf { it.isNotEmpty() }
    if (extract == null && imageUrl == null) return null
    return PlaceInfo(
        title = title?.trim().orEmpty(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        extract = extract,
        imageUrl = imageUrl,
    )
}
