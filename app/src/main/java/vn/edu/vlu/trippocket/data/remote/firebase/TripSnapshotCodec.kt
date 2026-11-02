package vn.edu.vlu.trippocket.data.remote.firebase

/**
 * The backup document as Firestore stores it (A3 writes it, A4 reads it back). Firestore cannot
 * fill the `val`s of a Kotlin data class, so both directions go through plain maps.
 *
 * Field names are the ones Firestore gives the TripSnapshot classes, so FirestoreDocumentSize
 * measures exactly this map and documents uploaded before the codec still read: `isDone` is
 * stored as "done". Firestore returns whole numbers as Long and decimals as Double.
 *
 * Reading is strict: a missing field or a value of the wrong type throws
 * [InvalidSnapshotException], and restore skips that trip (proposal 6.2).
 */
object TripSnapshotCodec {

    fun toMap(s: TripSnapshot): Map<String, Any?> = mapOf(
        "tripId" to s.tripId,
        "ownerUid" to s.ownerUid,
        "name" to s.name,
        "destination" to s.destination,
        "startDate" to s.startDate,
        "endDate" to s.endDate,
        "localRevision" to s.localRevision,
        "updatedAt" to s.updatedAt,
        "members" to s.members.map { mapOf("memberId" to it.memberId, "name" to it.name, "orderIndex" to it.orderIndex) },
        "activities" to s.activities.map {
            mapOf(
                "activityId" to it.activityId,
                "dayIndex" to it.dayIndex,
                "title" to it.title,
                "place" to it.place,
                "startTime" to it.startTime,
                "estimatedCost" to it.estimatedCost,
                "note" to it.note,
                "orderIndex" to it.orderIndex,
                "reminderMinutesBefore" to it.reminderMinutesBefore,
            )
        },
        "checklist" to s.checklist.map { mapOf("itemId" to it.itemId, "title" to it.title, "done" to it.isDone) },
        "places" to s.places.map {
            mapOf(
                "placeId" to it.placeId,
                "name" to it.name,
                "country" to it.country,
                "latitude" to it.latitude,
                "longitude" to it.longitude,
                "note" to it.note,
            )
        },
        "expenses" to s.expenses.map {
            mapOf(
                "expenseId" to it.expenseId,
                "title" to it.title,
                "amount" to it.amount,
                "payerId" to it.payerId,
                "category" to it.category,
                "splitType" to it.splitType,
                "spentAt" to it.spentAt,
                "createdAt" to it.createdAt,
                "shares" to it.shares,
            )
        },
    )

    fun fromMap(m: Map<String, Any?>): TripSnapshot = TripSnapshot(
        tripId = m.string("tripId"),
        ownerUid = m.string("ownerUid"),
        name = m.string("name"),
        destination = m.string("destination"),
        startDate = m.long("startDate"),
        endDate = m.long("endDate"),
        localRevision = m.long("localRevision"),
        updatedAt = m.long("updatedAt"),
        members = m.list("members") { MemberSnapshot(it.string("memberId"), it.string("name"), it.int("orderIndex")) },
        activities = m.list("activities") {
            ActivitySnapshot(
                activityId = it.string("activityId"),
                dayIndex = it.int("dayIndex"),
                title = it.string("title"),
                place = it.string("place"),
                startTime = it.int("startTime"),
                estimatedCost = it.long("estimatedCost"),
                note = it.string("note"),
                orderIndex = it.int("orderIndex"),
                reminderMinutesBefore = it.intOrNull("reminderMinutesBefore"),
            )
        },
        checklist = m.list("checklist") {
            ChecklistItemSnapshot(it.string("itemId"), it.string("title"), it.value<Boolean>("done"))
        },
        places = m.list("places") {
            PlaceSnapshot(
                placeId = it.string("placeId"),
                name = it.string("name"),
                country = it.string("country"),
                latitude = it.value<Number>("latitude").toDouble(),
                longitude = it.value<Number>("longitude").toDouble(),
                note = it.string("note"),
            )
        },
        expenses = m.list("expenses") {
            ExpenseSnapshot(
                expenseId = it.string("expenseId"),
                title = it.string("title"),
                amount = it.long("amount"),
                payerId = it.string("payerId"),
                category = it.string("category"),
                splitType = it.string("splitType"),
                spentAt = it.long("spentAt"),
                createdAt = it.long("createdAt"),
                shares = it.value<Map<*, *>>("shares").entries.associate { (key, amount) ->
                    (key as? String ?: bad("shares")) to (amount as? Long ?: bad("shares"))
                },
            )
        },
    )

    private inline fun <reified T> Map<*, *>.value(key: String): T = this[key] as? T ?: bad(key)

    private fun Map<*, *>.string(key: String): String = value(key)

    /** A whole number: Long from Firestore, Int straight from [toMap]; never a Double. */
    private fun Map<*, *>.long(key: String): Long = this[key].wholeNumber(key) ?: bad(key)

    private fun Map<*, *>.int(key: String): Int = long(key).toIntExact(key)

    private fun Map<*, *>.intOrNull(key: String): Int? = this[key].wholeNumber(key)?.toIntExact(key)

    private fun Any?.wholeNumber(key: String): Long? = when (this) {
        null -> null
        is Long -> this
        is Int -> toLong()
        else -> bad(key)
    }

    private fun <T> Map<*, *>.list(key: String, item: (Map<*, *>) -> T): List<T> =
        value<List<*>>(key).map { item(it as? Map<*, *> ?: bad(key)) }

    private fun Long.toIntExact(key: String): Int = if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else bad(key)

    private fun bad(key: String): Nothing = throw InvalidSnapshotException("Bad or missing field \"$key\"")
}

/** A backup document that cannot be read as a trip. */
class InvalidSnapshotException(message: String) : Exception(message)
