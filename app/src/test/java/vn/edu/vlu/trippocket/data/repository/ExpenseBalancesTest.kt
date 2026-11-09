package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.Settlement
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.TripBalances
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import java.time.LocalDate

/**
 * E3 (proposal 3.4, 6.4): balances and suggested payments read from Room, following every
 * expense that is added, edited or deleted. Owner: Bảo.
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseBalancesTest {

    private val t = TestDatabase()
    private val repository = ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer)
    private val day = LocalDate.of(2026, 11, 4)

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        // Inserted out of order on purpose: results follow orderIndex, not insertion.
        listOf("C" to 2, "A" to 0, "B" to 1, "D" to 3).forEach { (name, order) ->
            t.db.tripMemberDao().insert(TripMemberEntity(name, "t1", name, order))
        }
    }

    @After
    fun tearDown() = t.close()

    private fun expense(amount: Long, payer: String, shares: Map<String, Long>, split: SplitType = SplitType.CUSTOM) =
        ExpenseInput("Chi phí", amount, payer, ExpenseCategory.FOOD, split, day, shares)

    private fun equally(amount: Long, payer: String, vararg ids: String) = expense(
        amount,
        payer,
        ExpenseCalculator.splitEqually(amount, ids.mapIndexed { i, id -> TripMember(id, "t1", id, i) }),
        SplitType.EQUAL,
    )

    private suspend fun balances(): TripBalances = repository.observeBalances("t1").first()

    private fun TripBalances.balanceOf(id: String) = positions.single { it.member.memberId == id }.balance

    @Test
    fun proposalDemo_aPays300kForThree_bAndCEachOweA100k() = runBlocking {
        repository.addExpense("t1", equally(300_000, "A", "A", "B", "C"))

        val result = balances()
        assertEquals(listOf("A", "B", "C", "D"), result.positions.map { it.member.memberId })
        assertEquals(200_000L, result.balanceOf("A"))
        assertEquals(-100_000L, result.balanceOf("B"))
        assertEquals(-100_000L, result.balanceOf("C"))
        assertEquals(0L, result.balanceOf("D"))
        assertEquals(
            setOf(Settlement("B", "A", 100_000), Settlement("C", "A", 100_000)),
            result.settlements.toSet(),
        )
        val a = result.positions.first()
        assertEquals(300_000L, a.paid)
        assertEquals(100_000L, a.share)
    }

    @Test
    fun editingAnExpense_updatesTheBalances_andDeletingItSettlesEveryone() = runBlocking {
        val id = repository.addExpense("t1", equally(300_000, "A", "A", "B", "C"))

        repository.updateExpense("t1", id, equally(600_000, "A", "A", "B", "C"))
        assertEquals(-200_000L, balances().balanceOf("B"))

        repository.deleteExpense("t1", id)
        val result = balances()
        assertTrue(result.positions.all { it.balance == 0L })
        assertTrue(result.settlements.isEmpty())
    }

    @Test
    fun aPayerWhoIsNotAParticipant_isOwedTheWholeAmount() = runBlocking {
        // A pays 200,000 for B and C only.
        repository.addExpense("t1", expense(200_000, "A", mapOf("B" to 100_000L, "C" to 100_000L)))

        val result = balances()
        assertEquals(200_000L, result.balanceOf("A"))
        assertEquals(0L, result.positions.first().share)
        assertEquals(2, result.settlements.size)
    }

    @Test
    fun twoPayers_netOut_intoOnePayment() = runBlocking {
        repository.addExpense("t1", equally(300_000, "A", "A", "B", "C"))
        repository.addExpense("t1", equally(150_000, "B", "A", "B", "C"))

        // A: 300k - 150k = +150k; B: 150k - 150k = 0; C: -150k.
        assertEquals(listOf(Settlement("C", "A", 150_000)), balances().settlements)
    }

    @Test
    fun oddAmountsAndCustomSplits_alwaysAddUpToZero_inAtMostNMinusOnePayments() = runBlocking {
        repository.addExpense("t1", equally(100_001, "A", "A", "B", "C"))
        repository.addExpense("t1", expense(250_000, "D", mapOf("A" to 10_000L, "B" to 40_000L, "D" to 200_000L)))
        repository.addExpense("t1", equally(99_999, "C", "A", "B", "C", "D"))

        val result = balances()
        assertEquals(0L, result.positions.sumOf { it.balance })
        assertTrue(result.settlements.size <= result.positions.size - 1)
        assertTrue(result.settlements.all { it.amount > 0 })

        // Making every suggested payment leaves nobody owing anything.
        val after = result.positions.associate { it.member.memberId to it.balance }.toMutableMap()
        result.settlements.forEach {
            after[it.fromId] = after.getValue(it.fromId) + it.amount
            after[it.toId] = after.getValue(it.toId) - it.amount
        }
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun theSameData_alwaysGivesTheSamePayments() = runBlocking {
        // B and C owe the same amount: the tie is broken by member order every time.
        repository.addExpense("t1", equally(300_000, "A", "A", "B", "C"))

        val first = balances().settlements
        repeat(3) { assertEquals(first, balances().settlements) }
        assertEquals("B", first.first().fromId)
    }
}
