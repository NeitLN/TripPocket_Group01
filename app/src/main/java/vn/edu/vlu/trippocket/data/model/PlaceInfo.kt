package vn.edu.vlu.trippocket.data.model

/**
 * What Wikipedia has about a place (Milestone 4): the photo of a search row and the "About this
 * place" card. [imageUrl] is null when the page has no thumbnail, so the screen shows its
 * default image; [extract] is null for a stub page, and the card then hides the introduction.
 * A page with neither is not turned into a [PlaceInfo] at all.
 */
data class PlaceInfo(
    val title: String,
    val description: String?,
    val extract: String?,
    val imageUrl: String?,
)
