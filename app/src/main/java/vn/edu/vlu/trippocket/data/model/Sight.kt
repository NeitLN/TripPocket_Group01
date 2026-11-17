package vn.edu.vlu.trippocket.data.model

/**
 * One nearby sight from Wikipedia (Milestone 4, Nearby sights): a page returned by the geosearch
 * around the trip's saved place. [description] and [imageUrl] are null when the page has none, and
 * the row then hides the text or shows the default image. [distanceMeters] is computed when the
 * answer is read, so the list is already sorted by distance for the screen.
 */
data class Sight(
    val pageId: Long,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val lat: Double,
    val lon: Double,
    /** Metres between the trip's saved place and this sight. */
    val distanceMeters: Double,
)
