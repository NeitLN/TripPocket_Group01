package vn.edu.vlu.trippocket.data

import vn.edu.vlu.trippocket.data.local.AppDatabase
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.domain.Limits
import java.time.LocalDate
import java.util.UUID

/**
 * A5 fixture (owner: Minh): the biggest trip the app allows. Every table is at its cap in
 * domain/Limits.kt and every text field is at its maximum length, written in Vietnamese with
 * as many 3-byte UTF-8 letters as a real sentence has, so the backup snapshot is as large
 * as it can get. Every expense is shared by all 15 members.
 *
 * Use [insertInto] to put it in a Room database, then run it through the real backup mapper
 * (A3) and [vn.edu.vlu.trippocket.data.remote.firebase.FirestoreDocumentSize].
 */
object FullTripFixture {

    const val TRIP_ID = "00000000-0000-4000-8000-000000000001"

    /** Firebase uids are 28 characters. */
    const val OWNER_UID = "fixtureOwnerUid0123456789abc"

    private const val NOW = 1_793_000_000_000L
    private val START_DAY = LocalDate.of(2026, 10, 15).toEpochDay()

    /** Dense Vietnamese: most letters carry a diacritic, most of those are 3 bytes in UTF-8. */
    private const val SENTENCE =
        "Đường lên đỉnh Lang Biang nhiều sương, nhớ mang áo ấm, giày leo núi và đồ ăn nhẹ cho cả nhóm. "

    /** [length] characters of Vietnamese text, so the UTF-8 size is close to the worst case. */
    fun text(length: Int, tag: Int = 0): String {
        val prefix = "#$tag "
        val body = SENTENCE.repeat(length / SENTENCE.length + 1).take(length - prefix.length)
        return (prefix + body).also { check(it.length == length) }
    }

    private fun id(kind: String, index: Int) = UUID.nameUUIDFromBytes("$kind-$index".toByteArray()).toString()

    val trip = TripEntity(
        tripId = TRIP_ID,
        ownerUid = OWNER_UID,
        name = text(Limits.MAX_TEXT),
        destination = text(Limits.MAX_TEXT, 1),
        startDate = START_DAY,
        endDate = START_DAY + 29,
        syncStatus = SyncStatus.PENDING,
        localRevision = 9_999,
        updatedAt = NOW,
    )

    val members: List<TripMemberEntity> = (0 until Limits.MAX_MEMBERS).map { i ->
        TripMemberEntity(id("member", i), TRIP_ID, text(Limits.MAX_TEXT, i), i)
    }

    val activities: List<ActivityEntity> = (0 until Limits.MAX_ACTIVITIES).map { i ->
        ActivityEntity(
            activityId = id("activity", i),
            tripId = TRIP_ID,
            dayIndex = i % 30,
            title = text(Limits.MAX_TEXT, i),
            place = text(Limits.MAX_TEXT, i + 1),
            startTime = (i * 7) % 1440,
            estimatedCost = 9_999_999_999,
            note = text(Limits.MAX_NOTE, i),
            orderIndex = i / 30,
            reminderMinutesBefore = 60,
        )
    }

    val checklist: List<ChecklistItemEntity> = (0 until Limits.MAX_CHECKLIST_ITEMS).map { i ->
        ChecklistItemEntity(id("check", i), TRIP_ID, text(Limits.MAX_TEXT, i), isDone = i % 2 == 0)
    }

    val places: List<SavedPlaceEntity> = (0 until Limits.MAX_PLACES).map { i ->
        SavedPlaceEntity(
            placeId = id("place", i),
            tripId = TRIP_ID,
            name = text(Limits.MAX_TEXT, i),
            country = text(Limits.MAX_TEXT, i + 1),
            latitude = 11.940419 + i,
            longitude = 108.458313 - i,
            note = text(Limits.MAX_NOTE, i),
        )
    }

    /** Large amounts with leftover dong, split between all 15 members. */
    val expenses: List<ExpenseEntity> = (0 until Limits.MAX_EXPENSES).map { i ->
        ExpenseEntity(
            expenseId = id("expense", i),
            tripId = TRIP_ID,
            title = text(Limits.MAX_TEXT, i),
            amount = 999_999_999_999 - i,
            payerId = members[i % members.size].memberId,
            category = ExpenseCategory.entries[i % ExpenseCategory.entries.size],
            splitType = SplitType.EQUAL,
            spentAt = START_DAY + i % 30,
            createdAt = NOW + i,
        )
    }

    val shares: List<ExpenseShareEntity> = expenses.flatMap { expense ->
        val base = expense.amount / members.size
        val leftover = expense.amount % members.size
        members.mapIndexed { index, member ->
            ExpenseShareEntity(expense.expenseId, member.memberId, base + if (index < leftover) 1 else 0)
        }
    }

    /** Inserts the whole trip directly through the DAOs (test setup, not a user change). */
    suspend fun insertInto(db: AppDatabase) {
        db.tripDao().insert(trip)
        members.forEach { db.tripMemberDao().insert(it) }
        activities.forEach { db.activityDao().insert(it) }
        checklist.forEach { db.checklistDao().insert(it) }
        places.forEach { db.savedPlaceDao().insert(it) }
        expenses.forEach { db.expenseDao().insert(it) }
        db.expenseDao().insertShares(shares)
    }
}
