package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.local.dao.ExpenseDao
import vn.edu.vlu.trippocket.data.local.dao.ExpenseWithShares
import vn.edu.vlu.trippocket.data.local.dao.TripMemberDao
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.model.TripTotals
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import vn.edu.vlu.trippocket.domain.Limits
import java.time.LocalDate
import java.util.UUID

/** Why the repository refused to save an expense; the whole write is rolled back. */
class InvalidExpenseException(val problem: ExpenseProblem) :
    IllegalArgumentException("Invalid expense: $problem")

enum class ExpenseProblem { BLANK_TITLE, TITLE_TOO_LONG, AMOUNT_NOT_POSITIVE, SPLIT, MEMBER_NOT_IN_TRIP, NOT_FOUND }

/**
 * Members and expenses of a trip (proposal 3.4), read from Room. Owner: Minh.
 *
 * An expense and all of its shares are written in one TripWriter transaction, so the trip's
 * revision goes up exactly once per save and a rejected expense leaves nothing behind.
 * The form validates first; the checks here run again inside the transaction.
 */
class ExpenseRepository(
    private val memberDao: TripMemberDao,
    private val expenseDao: ExpenseDao,
    private val tripWriter: TripWriter,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** In the fixed member order used for equal splits. */
    fun observeMembers(tripId: String): Flow<List<TripMember>> =
        memberDao.observeByTrip(tripId).map { rows -> rows.map(::toModel) }

    /** Newest first, each with its shares. */
    fun observeExpenses(tripId: String): Flow<List<Expense>> =
        expenseDao.observeWithShares(tripId).map { rows -> rows.map(::toModel) }

    /** tripId -> member count and total spent, for the trip cards (Tiến). Missing trips have neither. */
    fun observeTripTotals(): Flow<Map<String, TripTotals>> = combine(
        memberDao.observeCountPerTrip(),
        expenseDao.observeSpentPerTrip(),
    ) { counts, spent ->
        val spentById = spent.associate { it.tripId to it.spent }
        (counts.map { it.tripId } + spentById.keys).toSet().associateWith { tripId ->
            TripTotals(counts.firstOrNull { it.tripId == tripId }?.count ?: 0, spentById[tripId] ?: 0)
        }
    }

    suspend fun getExpense(expenseId: String): Expense? = expenseDao.getWithShares(expenseId)?.let(::toModel)

    /** Returns the new expense id. Throws [InvalidExpenseException] or [vn.edu.vlu.trippocket.domain.LimitExceededException]. */
    suspend fun addExpense(tripId: String, input: ExpenseInput): String {
        val expenseId = newId()
        tripWriter.write(tripId) {
            Limits.check(expenseDao.countByTrip(tripId), Limits.MAX_EXPENSES)
            validate(tripId, input)
            expenseDao.insert(toEntity(expenseId, tripId, input, createdAt = now()))
            expenseDao.insertShares(toShares(expenseId, input))
        }
        return expenseId
    }

    /** Replaces the expense and all of its old shares. */
    suspend fun updateExpense(tripId: String, expenseId: String, input: ExpenseInput) {
        tripWriter.write(tripId) {
            val old = expenseDao.getWithShares(expenseId)?.expense
            if (old == null || old.tripId != tripId) throw InvalidExpenseException(ExpenseProblem.NOT_FOUND)
            validate(tripId, input)
            expenseDao.update(toEntity(expenseId, tripId, input, createdAt = old.createdAt))
            expenseDao.deleteShares(expenseId)
            expenseDao.insertShares(toShares(expenseId, input))
        }
    }

    suspend fun deleteExpense(tripId: String, expenseId: String) {
        tripWriter.write(tripId) {
            if (expenseDao.deleteById(tripId, expenseId) == 0) throw InvalidExpenseException(ExpenseProblem.NOT_FOUND)
        }
    }

    /**
     * Debug builds only, until E1 adds the members screen: copies the sample members and
     * expenses of the sample trips into Room (same ids as TripRepository.seedSampleTrips).
     * Not a user change, so it does not go through TripWriter.
     */
    suspend fun seedSampleExpenses(now: Long) {
        MockData.trips.forEach { trip ->
            if (memberDao.countByTrip(trip.tripId) > 0) return@forEach
            MockData.membersOf(trip.tripId).forEach {
                memberDao.insert(TripMemberEntity(it.memberId, it.tripId, it.name, it.orderIndex))
            }
            MockData.expensesOf(trip.tripId).forEach { expense ->
                val input = ExpenseInput(
                    expense.title, expense.amount, expense.payerId, expense.category,
                    expense.splitType, expense.spentAt, expense.shares,
                )
                expenseDao.insert(toEntity(expense.expenseId, trip.tripId, input, createdAt = now))
                expenseDao.insertShares(toShares(expense.expenseId, input))
            }
        }
    }

    /** Runs inside the transaction, so a failure rolls the whole save back. */
    private suspend fun validate(tripId: String, input: ExpenseInput) {
        val problem = when {
            input.title.isBlank() -> ExpenseProblem.BLANK_TITLE
            input.title.trim().length > Limits.MAX_TEXT -> ExpenseProblem.TITLE_TOO_LONG
            input.amount <= 0 -> ExpenseProblem.AMOUNT_NOT_POSITIVE
            ExpenseCalculator.validateCustomSplit(input.amount, input.shares) != null -> ExpenseProblem.SPLIT
            else -> {
                // Payer and participants must all be members of this trip.
                val tripMembers = memberDao.idsByTrip(tripId).toSet()
                val used = input.shares.keys + input.payerId
                if (!tripMembers.containsAll(used)) ExpenseProblem.MEMBER_NOT_IN_TRIP else null
            }
        }
        if (problem != null) throw InvalidExpenseException(problem)
    }

    private fun toEntity(expenseId: String, tripId: String, input: ExpenseInput, createdAt: Long) = ExpenseEntity(
        expenseId = expenseId,
        tripId = tripId,
        title = input.title.trim(),
        amount = input.amount,
        payerId = input.payerId,
        category = input.category,
        splitType = input.splitType,
        spentAt = input.spentAt.toEpochDay(),
        createdAt = createdAt,
    )

    /** Members with a 0 share took no part, so they get no row. */
    private fun toShares(expenseId: String, input: ExpenseInput) = input.shares
        .filterValues { it > 0 }
        .map { (memberId, share) -> ExpenseShareEntity(expenseId, memberId, share) }

    private fun toModel(row: TripMemberEntity) = TripMember(row.memberId, row.tripId, row.name, row.orderIndex)

    private fun toModel(row: ExpenseWithShares) = Expense(
        expenseId = row.expense.expenseId,
        tripId = row.expense.tripId,
        title = row.expense.title,
        amount = row.expense.amount,
        payerId = row.expense.payerId,
        category = row.expense.category,
        splitType = row.expense.splitType,
        spentAt = LocalDate.ofEpochDay(row.expense.spentAt),
        shares = row.shares.associate { it.memberId to it.shareAmount },
    )
}
