package vn.edu.vlu.trippocket.data.remote.mockapi

import vn.edu.vlu.trippocket.data.model.TripIdea
import vn.edu.vlu.trippocket.data.model.TripIdeaInput

/**
 * One idea as MockAPI.io stores it. Every field is nullable because Gson fills whatever the JSON
 * has: a row edited by hand on mockapi.io may miss a field or carry a wrong type.
 *
 * ```json
 * {"id":"1","createdAt":"2026-11-09T08:00:00.000Z","title":"3 days in Da Lat",
 *  "destination":"Da Lat, Lam Dong","days":3,"budgetVnd":4500000,
 *  "coverUrl":"https://upload.wikimedia.org/...jpg","summary":"Pine hills, night market",
 *  "author":"tien","authorUid":"Xy12..."}
 * ```
 */
data class TripIdeaDto(
    val id: String? = null,
    val createdAt: String? = null,
    val title: String? = null,
    val destination: String? = null,
    val days: Int? = null,
    val budgetVnd: Long? = null,
    val coverUrl: String? = null,
    val summary: String? = null,
    val author: String? = null,
    val authorUid: String? = null,
)

/** Body of POST and PUT: the fields the user typed plus who shared the idea. */
data class TripIdeaBody(
    val title: String,
    val destination: String,
    val days: Int,
    val budgetVnd: Long,
    val coverUrl: String,
    val summary: String,
    val author: String,
    val authorUid: String,
)

/** The idea, or null when the row has no id or no title (nothing the list could show). */
fun TripIdeaDto.toModel(): TripIdea? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val title = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return TripIdea(
        id = id,
        title = title,
        destination = destination?.trim().orEmpty(),
        days = (days ?: 1).coerceAtLeast(1),
        budgetVnd = (budgetVnd ?: 0L).coerceAtLeast(0L),
        coverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() },
        summary = summary?.trim().orEmpty(),
        author = author?.trim().orEmpty(),
        authorUid = authorUid,
    )
}

/** Rows that cannot be shown are dropped; the rest of the list survives. */
fun List<TripIdeaDto>.toModels(): List<TripIdea> = mapNotNull { it.toModel() }

fun TripIdeaInput.toBody(author: String, authorUid: String) = TripIdeaBody(
    title = title,
    destination = destination,
    days = days,
    budgetVnd = budgetVnd,
    coverUrl = coverUrl,
    summary = summary,
    author = author,
    authorUid = authorUid,
)
