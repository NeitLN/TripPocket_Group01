package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.Settlement
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.TripMember

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
}
