package vn.edu.vlu.trippocket.data.remote.openmeteo

import com.google.gson.annotations.SerializedName
import vn.edu.vlu.trippocket.data.model.Place

/**
 * Response of `GET v1/search` (owner: Duy Anh). Every field is nullable because Open-Meteo
 * leaves out what it does not know; a missing "results" key means "no match", not an error.
 */
data class GeocodingResponse(
    @SerializedName("results") val results: List<GeocodingResult>? = null,
)

data class GeocodingResult(
    @SerializedName("name") val name: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("admin1") val admin1: String? = null,
    @SerializedName("country") val country: String? = null,
)

/**
 * Search results as [Place] models. A row without a name or coordinates is dropped so one
 * broken row cannot hide the others. [Place.region] is the first-level administrative area
 * (admin1) and stays empty when the API has none; [Place.country] stays empty as well.
 */
fun GeocodingResponse.toPlaces(): List<Place> = results.orEmpty().mapNotNull { result ->
    val name = result.name?.trim().orEmpty()
    if (name.isEmpty()) return@mapNotNull null
    val latitude = result.latitude ?: return@mapNotNull null
    val longitude = result.longitude ?: return@mapNotNull null
    Place(
        name = name,
        region = result.admin1?.trim().orEmpty(),
        country = result.country?.trim().orEmpty(),
        latitude = latitude,
        longitude = longitude,
    )
}
