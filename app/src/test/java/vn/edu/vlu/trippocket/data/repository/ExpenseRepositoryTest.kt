package vn.edu.vlu.trippocket.data.repository

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.NOW
import vn.edu.vlu.trippocket.data.local.TestDatabase.Companion.OWNER
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.model.TripTotals
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
import java.time.LocalDate

/** E2: an expense and its shares are saved together, once, or not at all. Owner: Minh. */
@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {

    private val t = TestDatabase()
    private var nextId = 0
    private val repository = ExpenseRepository(
        t.db.tripMemberDao(), t.db.expenseDao(), t.writer, newId = { "e${++nextId}" }, now = { NOW },
    )
    private val members = listOf("a", "b", "c").mapIndexed { i, id -> TripMember(id, "t1", id.uppercase(), i) }

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.tripDao().insert(t.trip("t2"))
        members.forEach { t.db.tripMemberDao().insert(TripMemberEntity(it.memberId, "t1", it.name, it.orderIndex)) }
        t.db.tripMemberDao().insert(TripMemberEntity("x", "t2", "X", 0))
    }

    @After
    fun tearDown() = t.close()

    private fun input(
        amount: Long,
        shares: Map<String, Long>,
        split: SplitType = SplitType.EQUAL,
        payer: String = "a",
        title: String = "Lẩu gà lá é",
    ) = ExpenseInput(title, amount, payer, ExpenseCategory.FOOD, split, LocalDate.of(2026, 10, 15), shares)

    private suspend fun trip(id: String = "t1") = t.db.tripDao().observeTrip(id, OWNER).first()!!

    private suspend inline fun <reified E : Exception> assertRejected(crossinline block: suspend () -> Unit): E {
        try {
            block()
        } catch (e: Exception) {
            if (e is E) return e
            throw e
        }
        fail("Expected ${E::class.simpleName}")
        throw AssertionError()
    }

    @Test
    fun add_savesExpenseAndSharesTogether_andBumpsRevisionOnce() = runBlocking {
        repository.addExpense("t1", input(100_001, ExpenseCalculator.splitEqually(100_001, members)))

        val saved = repository.observeExpenses("t1").first().single()
        assertEquals(mapOf("a" to 33_334L, "b" to 33_334L, "c" to 33_333L), saved.shares)
        assertEquals("Lẩu gà lá é", saved.title)
        assertEquals(SyncStatus.PENDING, trip().syncStatus)
        assertEquals(6, trip().localRevision)
        assertEquals(listOf(OWNER), t.backupRequests)
    }

    @Test
    fun customSplitThatDoesNotAddUp_isNotSaved_andRevisionStays() = runBlocking {
        val e = assertRejected<InvalidExpenseException> {
            repository.addExpense(
                "t1",
                input(300_000, mapOf("a" to 100_000L, "b" to 100_000L, "c" to 99_999L), SplitType.CUSTOM),
            )
        }
        assertEquals(ExpenseProblem.SPLIT, e.problem)
        assertEquals(0, t.db.expenseDao().countByTrip("t1"))
        assertEquals(5, trip().localRevision)
        assertEquals(SyncStatus.SYNCED, trip().syncStatus)
        assertTrue(t.backupRequests.isEmpty())
    }

    @Test
    fun memberOfAnotherTrip_isRejected_asParticipantOrPayer() = runBlocking {
        val asParticipant = assertRejected<InvalidExpenseException> {
            repository.addExpense("t1", input(200_000, mapOf("a" to 100_000L, "x" to 100_000L), SplitType.CUSTOM))
        }
        val asPayer = assertRejected<InvalidExpenseException> {
            repository.addExpense("t1", input(100_000, mapOf("a" to 100_000L), payer = "x"))
        }
        assertEquals(ExpenseProblem.MEMBER_NOT_IN_TRIP, asParticipant.problem)
        assertEquals(ExpenseProblem.MEMBER_NOT_IN_TRIP, asPayer.problem)
        assertEquals(5, trip().localRevision)
    }

    @Test
    fun blankTitleOrZeroAmount_isRejected() = runBlocking {
        assertEquals(
            ExpenseProblem.BLANK_TITLE,
            assertRejected<InvalidExpenseException> {
                repository.addExpense("t1", input(100, mapOf("a" to 100L), title = "  "))
            }.problem,
        )
        assertEquals(
            ExpenseProblem.AMOUNT_NOT_POSITIVE,
            assertRejected<InvalidExpenseException> { repository.addExpense("t1", input(0, mapOf("a" to 0L))) }.problem,
        )
        assertEquals(0, t.db.expenseDao().countByTrip("t1"))
    }

    /** Task sheet E, Sunday: a failure while writing the shares rolls back the expense row too. */
    @Test
    fun failedShareInsert_rollsBackTheExpenseRowAndRevision() = runBlocking {
        val dao = t.db.expenseDao()
        try {
            t.writer.write("t1") {
                dao.insert(ExpenseEntity("e1", "t1", "Taxi", 90_000, "a", ExpenseCategory.TRANSPORT, SplitType.EQUAL, 20_400, NOW))
                // "ghost" is not a member: the foreign key fails half way through the write.
                dao.insertShares(listOf(ExpenseShareEntity("e1", "a", 45_000), ExpenseShareEntity("e1", "ghost", 45_000)))
            }
            fail("Expected a foreign key failure")
        } catch (expected: SQLiteConstraintException) {
        }
        assertEquals(0, dao.countByTrip("t1"))
        assertEquals(0, dao.observeSharesByTrip("t1").first().size)
        assertEquals(5, trip().localRevision)
        assertTrue(t.backupRequests.isEmpty())

        // The connection still works after the failed transaction.
        repository.addExpense("t1", input(90_000, mapOf("a" to 45_000L, "b" to 45_000L), SplitType.CUSTOM))
        assertEquals(1, dao.countByTrip("t1"))
    }

    @Test
    fun update_replacesAllOldShares_andBumpsRevisionOnce() = runBlocking {
        val id = repository.addExpense("t1", input(300_000, ExpenseCalculator.splitEqually(300_000, members)))

        repository.updateExpense(
            "t1", id, input(300_000, mapOf("a" to 50_000L, "b" to 250_000L, "c" to 0L), SplitType.CUSTOM, payer = "b"),
        )

        val saved = repository.getExpense(id)!!
        assertEquals(mapOf("a" to 50_000L, "b" to 250_000L), saved.shares)
        assertEquals("b", saved.payerId)
        assertEquals(SplitType.CUSTOM, saved.splitType)
        assertEquals(7, trip().localRevision)
        // Chi no longer takes part, so Chi could now be removed.
        assertEquals(0, t.db.expenseDao().countUsingMember("c"))
    }

    @Test
    fun update_ofAnExpenseInAnotherTrip_isRejected() = runBlocking {
        val id = repository.addExpense("t1", input(100_000, mapOf("a" to 100_000L)))
        val e = assertRejected<InvalidExpenseException> {
            repository.updateExpense("t2", id, input(100_000, mapOf("x" to 100_000L), payer = "x"))
        }
        assertEquals(ExpenseProblem.NOT_FOUND, e.problem)
        assertEquals(5, trip("t2").localRevision)
    }

    @Test
    fun delete_removesTheExpenseAndItsShares() = runBlocking {
        val id = repository.addExpense("t1", input(300_000, ExpenseCalculator.splitEqually(300_000, members)))
        repository.deleteExpense("t1", id)

        assertTrue(repository.observeExpenses("t1").first().isEmpty())
        assertEquals(0, t.db.expenseDao().countUsingMember("b"))
        assertEquals(7, trip().localRevision)
    }

    @Test
    fun the301stExpense_isRejected_andRevisionStays() = runBlocking {
        val dao = t.db.expenseDao()
        repeat(Limits.MAX_EXPENSES) { i ->
            dao.insert(ExpenseEntity("seed$i", "t1", "Cà phê", 1_000, "a", ExpenseCategory.FOOD, SplitType.EQUAL, 20_400, NOW))
        }
        val e = assertRejected<LimitExceededException> {
            repository.addExpense("t1", input(1_000, mapOf("a" to 1_000L)))
        }
        assertEquals(Limits.MAX_EXPENSES, e.max)
        assertEquals(Limits.MAX_EXPENSES, dao.countByTrip("t1"))
        assertEquals(5, trip().localRevision)
    }

    @Test
    fun tripTotals_countMembersAndMoneyPerTrip() = runBlocking {
        repository.addExpense("t1", input(300_000, ExpenseCalculator.splitEqually(300_000, members)))
        repository.addExpense("t1", input(1, mapOf("b" to 1L), payer = "b"))

        val totals = repository.observeTripTotals().first()
        assertEquals(TripTotals(memberCount = 3, spent = 300_001), totals["t1"])
        assertEquals(TripTotals(memberCount = 1, spent = 0), totals["t2"])
    }
}
