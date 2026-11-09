package vn.edu.vlu.trippocket.data.model

// Trip Ideas (Milestone 4, owner: Tiến): trips people share on the team's MockAPI.io project.

data class TripIdea(
    val id: String,
    val title: String,
    val destination: String,
    val days: Int,
    val budgetVnd: Long,
    /** Remote picture loaded with Glide; null when the idea has none. */
    val coverUrl: String?,
    val summary: String,
    /** Name shown under the idea ("tien"). */
    val author: String,
    /** Firebase uid of whoever shared it; only that account sees Edit and Delete. */
    val authorUid: String?,
)

/** What the share / edit form sends (POST / PUT), already trimmed and checked by TripIdeaRule. */
data class TripIdeaInput(
    val title: String,
    val destination: String,
    val days: Int,
    val budgetVnd: Long,
    /** "" when the user gave no picture. */
    val coverUrl: String,
    val summary: String,
)
