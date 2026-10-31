package vn.edu.vlu.trippocket.data.remote.openmeteo

import kotlinx.coroutines.CompletableDeferred

/**
 * W1 test double for Open-Meteo: records the queries, answers from [results], or fails with
 * [failWith]. [gate] holds a request open so a test can see the Loading state.
 */
class FakeGeocodingApi(
    private val results: (String) -> List<GeocodingResult> = { emptyList() },
) : GeocodingApi {

    val queries = mutableListOf<String>()
    var failWith: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun search(
        name: String,
        count: Int,
        language: String,
        format: String,
    ): GeocodingResponse {
        queries += name
        gate?.await()
        failWith?.let { throw it }
        return GeocodingResponse(results(name))
    }

    companion object {
        fun result(
            name: String,
            latitude: Double = 11.94646,
            longitude: Double = 108.44193,
            admin1: String = "Lam Dong",
            country: String = "Vietnam",
        ) = GeocodingResult(name, latitude, longitude, admin1, country)
    }
}
