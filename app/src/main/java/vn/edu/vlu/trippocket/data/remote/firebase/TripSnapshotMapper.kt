package vn.edu.vlu.trippocket.data.remote.firebase

import vn.edu.vlu.trippocket.data.local.dao.TripRows

/**
 * Room rows -> the backup document (A3). Units stay as in Room (docs/ARCHITECTURE.md): epoch
 * days, minute of the day, VND, epoch millis; enums as their names. Each expense carries its
 * shares as memberId -> VND. The weather cache is not part of the backup.
 */
object TripSnapshotMapper {

    fun toSnapshot(rows: TripRows): TripSnapshot {
        val trip = rows.trip
        val sharesByExpense = rows.shares.groupBy { it.expenseId }
        return TripSnapshot(
            tripId = trip.tripId,
            ownerUid = trip.ownerUid,
            name = trip.name,
            destination = trip.destination,
            startDate = trip.startDate,
            endDate = trip.endDate,
            localRevision = trip.localRevision,
            updatedAt = trip.updatedAt,
            members = rows.members.map { MemberSnapshot(it.memberId, it.name, it.orderIndex) },
            activities = rows.activities.map {
                ActivitySnapshot(
                    activityId = it.activityId,
                    dayIndex = it.dayIndex,
                    title = it.title,
                    place = it.place,
                    startTime = it.startTime,
                    estimatedCost = it.estimatedCost,
                    note = it.note,
                    orderIndex = it.orderIndex,
                    reminderMinutesBefore = it.reminderMinutesBefore,
                )
            },
            checklist = rows.checklist.map { ChecklistItemSnapshot(it.itemId, it.title, it.isDone) },
            places = rows.places.map {
                PlaceSnapshot(it.placeId, it.name, it.country, it.latitude, it.longitude, it.note)
            },
            expenses = rows.expenses.map { expense ->
                ExpenseSnapshot(
                    expenseId = expense.expenseId,
                    title = expense.title,
                    amount = expense.amount,
                    payerId = expense.payerId,
                    category = expense.category.name,
                    splitType = expense.splitType.name,
                    spentAt = expense.spentAt,
                    createdAt = expense.createdAt,
                    shares = sharesByExpense[expense.expenseId].orEmpty().associate { it.memberId to it.shareAmount },
                )
            },
        )
    }
}
