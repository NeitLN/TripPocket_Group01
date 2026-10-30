package vn.edu.vlu.trippocket.data.remote.firebase

/**
 * One Firestore document at users/{uid}/trips/{tripId}: the whole trip with every child row
 * (proposal 4.1). Each upload overwrites the document, so edits and deletions of child rows
 * reach the backup on their own. The weather cache is not included.
 *
 * Units match the Room entities: dates are epoch days, activity startTime is a minute of the
 * day, money is VND, timestamps are epoch millis. Default values give Firestore the no-arg
 * constructor it needs. The Room <-> snapshot mapper comes with A3. Owner: Tiến.
 */
data class TripSnapshot(
    val tripId: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val destination: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val localRevision: Long = 0,
    val updatedAt: Long = 0,
    val members: List<MemberSnapshot> = emptyList(),
    val activities: List<ActivitySnapshot> = emptyList(),
    val checklist: List<ChecklistItemSnapshot> = emptyList(),
    val places: List<PlaceSnapshot> = emptyList(),
    val expenses: List<ExpenseSnapshot> = emptyList(),
)

data class MemberSnapshot(
    val memberId: String = "",
    val name: String = "",
    val orderIndex: Int = 0,
)

data class ActivitySnapshot(
    val activityId: String = "",
    val dayIndex: Int = 0,
    val title: String = "",
    val place: String = "",
    val startTime: Int = 0,
    val estimatedCost: Long = 0,
    val note: String = "",
    val orderIndex: Int = 0,
    val reminderMinutesBefore: Int? = null,
)

data class ChecklistItemSnapshot(
    val itemId: String = "",
    val title: String = "",
    val isDone: Boolean = false,
)

data class PlaceSnapshot(
    val placeId: String = "",
    val name: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val note: String = "",
)

data class ExpenseSnapshot(
    val expenseId: String = "",
    val title: String = "",
    val amount: Long = 0,
    val payerId: String = "",
    /** ExpenseCategory name. */
    val category: String = "",
    /** SplitType name. */
    val splitType: String = "",
    val spentAt: Long = 0,
    val createdAt: Long = 0,
    /** memberId -> share in VND; adds up to [amount]. */
    val shares: Map<String, Long> = emptyMap(),
)
