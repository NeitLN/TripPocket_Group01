package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.Settlement
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.model.TripMember
import java.time.LocalDate

class ExpenseCalculatorTest {

    private val a = TripMember("a", "t", "A", 0)
    private val b = TripMember("b", "t", "B", 1)
    private val c = TripMember("c", "t", "C", 2)

    private fun expense(amount: Long, payer: TripMember, shares: Map<String, Long>) =
        Expense("e$amount", "t", "x", amount, payer.memberId, ExpenseCategory.FOOD,
            SplitType.EQUAL, java.time.LocalDate.of(2026, 10, 15), shares)

    @Test
    fun equalSplit_leftoverDongGoToFirstMembers() {
        val shares = ExpenseCalculator.splitEqually(100_001, listOf(c, a, b))
        assertEquals(mapOf("a" to 33_334L, "b" to 33_334L, "c" to 33_333L), shares)
    }

    @Test
    fun proposalDemo_bAndCEachOweA100k() {
        val dinner = expense(300_000, a, ExpenseCalculator.splitEqually(300_000, listOf(a, b, c)))
        val balances = ExpenseCalculator.balances(listOf(a, b, c), listOf(dinner))
        assertEquals(mapOf("a" to 200_000L, "b" to -100_000L, "c" to -100_000L), balances)

        val payments = ExpenseCalculator.settle(balances).toSet()
        assertEquals(setOf(Settlement("b", "a", 100_000), Settlement("c", "a", 100_000)), payments)
    }

    @Test
    fun mockTrip_balancesSumToZero_andSettleInAtMostNMinusOnePayments() {
        val members = MockData.membersOf(MockData.TRIP_DALAT)
        val balances = ExpenseCalculator.balances(members, MockData.expensesOf(MockData.TRIP_DALAT))
        assertEquals(0L, balances.values.sum())
        assert(ExpenseCalculator.settle(balances).size <= members.size - 1)
    }

    @Test
    fun customSplit_mustAddUpToTheAmount() {
        assertNull(ExpenseCalculator.validateCustomSplit(300_000, mapOf("a" to 100_000L, "b" to 200_000L)))
        // A member with 0 takes no part, which is fine as long as someone else pays the rest.
        assertNull(ExpenseCalculator.validateCustomSplit(50_000, mapOf("a" to 50_000L, "b" to 0L)))

        assertEquals(
            SplitError.SUM_MISMATCH,
            ExpenseCalculator.validateCustomSplit(300_000, mapOf("a" to 100_000L, "b" to 199_999L)),
        )
        assertEquals(
            SplitError.SUM_MISMATCH,
            ExpenseCalculator.validateCustomSplit(300_000, mapOf("a" to 200_000L, "b" to 200_000L)),
        )
        assertEquals(
            SplitError.NEGATIVE_SHARE,
            ExpenseCalculator.validateCustomSplit(100_000, mapOf("a" to 150_000L, "b" to -50_000L)),
        )
        assertEquals(SplitError.NO_PARTICIPANTS, ExpenseCalculator.validateCustomSplit(0, mapOf("a" to 0L)))
        assertEquals(SplitError.NO_PARTICIPANTS, ExpenseCalculator.validateCustomSplit(100, emptyMap()))
    }

    @Test
    fun equalSplit_alwaysPassesTheCustomSplitCheck() {
        for (amount in listOf(1L, 2L, 100_001L, 999_999_999L)) {
            val shares = ExpenseCalculator.splitEqually(amount, listOf(a, b, c))
            assertNull(ExpenseCalculator.validateCustomSplit(amount, shares))
        }
    }

    /** Applying every suggested payment must bring every balance back to 0 (proposal 6.4). */
    private fun assertSettlesToZero(members: List<TripMember>, expenses: List<Expense>) {
        val balances = ExpenseCalculator.balances(members, expenses).toMutableMap()
        val payments = ExpenseCalculator.settle(balances)
        for (payment in payments) {
            balances[payment.fromId] = balances.getValue(payment.fromId) + payment.amount
            balances[payment.toId] = balances.getValue(payment.toId) - payment.amount
        }
        assertTrue("Left over: $balances", balances.values.all { it == 0L })
        assertTrue(payments.size <= (members.size - 1).coerceAtLeast(0))
    }

    @Test
    fun settlement_onePerson_needsNoPayment() {
        val solo = expense(250_000, a, mapOf("a" to 250_000L))
        assertEquals(emptyList<Settlement>(), ExpenseCalculator.settle(ExpenseCalculator.balances(listOf(a), listOf(solo))))
        assertSettlesToZero(listOf(a), listOf(solo))
    }

    @Test
    fun settlement_manyPeopleAndOddAmounts_bringsEveryBalanceToZero() {
        val members = listOf(a, b, c)
        assertSettlesToZero(
            members,
            listOf(
                expense(100_001, a, ExpenseCalculator.splitEqually(100_001, members)),
                expense(77_777, b, ExpenseCalculator.splitEqually(77_777, listOf(b, c))),
                expense(1, c, mapOf("a" to 1L)),
                expense(333_334, c, mapOf("a" to 111_111L, "b" to 222_223L)),
            ),
        )
    }

    @Test
    fun settlement_fifteenMembers_bringsEveryBalanceToZero() {
        val members = (0 until 15).map { TripMember("m$it", "t", "M$it", it) }
        val expenses = members.mapIndexed { index, payer ->
            val amount = 10_007L * (index + 1)
            expense(amount, payer, ExpenseCalculator.splitEqually(amount, members.drop(index % 4)))
        }
        assertSettlesToZero(members, expenses)
    }

    @Test
    fun summaryText_listsTotalAndSuggestedPayments() {
        val trip = Trip("t", "Đà Lạt Getaway", "Đà Lạt", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18),
            SyncStatus.SYNCED)
        val text = ExpenseSummaryText.build(
            trip = trip,
            spent = 300_000,
            settlements = listOf(Settlement("b", "a", 100_000), Settlement("c", "a", 100_000)),
            names = mapOf("a" to "An", "b" to "Bình", "c" to "Chi"),
        )
        assertEquals(
            "Đà Lạt Getaway - 15 Oct - 18 Oct 2026\n" +
                "Total spent: 300,000 ₫\n\n" +
                "Suggested payments:\n" +
                "- Bình pays An 100,000 ₫\n" +
                "- Chi pays An 100,000 ₫\n",
            text,
        )

        val settled = ExpenseSummaryText.build(trip, 0, emptyList(), emptyMap())
        assertFalse(settled.contains("Suggested payments"))
    }
}
