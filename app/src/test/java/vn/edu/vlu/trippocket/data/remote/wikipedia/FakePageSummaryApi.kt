package vn.edu.vlu.trippocket.data.remote.wikipedia

import kotlinx.coroutines.CompletableDeferred

/**
 * M4 test double for Wikipedia summaries: records the titles (already with underscores), answers
 * [response] or [responseFor], or fails with [failWith] / [failFor]. [gate] holds a request open
 * so a test can look at the Loading state.
 */
class FakePageSummaryApi : PageSummaryApi {

    val titles = mutableListOf<String>()
    var response: PageSummaryResponse = PageSummaryResponse()

    /** When set, only the titles this returns a response for get one; others use [response]. */
    var responseFor: (String) -> PageSummaryResponse? = { null }

    var failWith: Exception? = null

    /** When set, only the titles this returns an exception for fail (with that exception). */
    var failFor: (String) -> Exception? = { null }

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun summary(title: String): PageSummaryResponse {
        titles += title
        gate?.await()
        failFor(title)?.let { throw it }
        failWith?.let { throw it }
        return responseFor(title) ?: response
    }

    companion object {
        fun page(
            title: String,
            extract: String? = "A city in the Central Highlands of Vietnam.",
            image: String? = "https://upload.wikimedia.org/wikipedia/commons/thumb/dalat.jpg",
        ) = PageSummaryResponse(
            title = title,
            description = "City in Vietnam",
            extract = extract,
            thumbnail = image?.let { PageSummaryResponse.Thumbnail(it) },
        )
    }
}
