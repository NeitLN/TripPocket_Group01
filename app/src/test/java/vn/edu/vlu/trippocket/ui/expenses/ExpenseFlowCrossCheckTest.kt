package vn.edu.vlu.trippocket.ui.expenses

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.FakeAuthRepository
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.MemberProblem
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import vn.edu.vlu.trippocket.ui.members.MembersViewModel
import java.time.LocalDate

/**
 * Cross-check of E1–E4 (docs/tasks/E-members-expenses.md) through the screens' ViewModels on one
 * Room database, the way a user goes: members, then expenses in the form, then the Expenses tab
 * and its shared text. Owner: Minh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpenseFlowCrossCheckTest {

    private val t = TestDatabase()
    private val repository = ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer)
    private val args = SavedStateHandle(mapOf(ARG_TRIP_ID to "t1"))

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        t.db.tripDao().insert(t.trip("t1"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private suspend fun revision() = t.db.tripDao().observeTrip("t1", TestDatabase.OWNER).first()!!.localRevision

    private fun membersScreen() = MembersViewModel(SavedStateHandle(mapOf(ARG_TRIP_ID to "t1")), repository)

    private fun expensesScreen() = ExpensesViewModel(
        args,
        TripRepository(t.db.tripDao(), FakeAuthRepository(TestDatabase.OWNER), t.writer),
        repository,
        ItineraryRepository(t.db.activityDao(), t.writer)::estimatedTotal,
    )

    /** Opens the form, fills it like a user, saves, and waits for it to close. */
    private suspend fun addInForm(
        title: String,
        amount: String,
        payer: String,
        notTaking: Set<String> = emptySet(),
        custom: Map<String, String>? = null,
    ): ExpenseFormViewModel {
        val form = ExpenseFormViewModel(SavedStateHandle(mapOf(ARG_TRIP_ID to "t1")), repository) { LocalDate.of(2026, 10, 15) }
        withTimeout(5_000) { form.state.first { !it.loading } }
        form.setTitle(title)
        form.setAmount(amount)
        form.setPayer(payer)
        if (custom == null) {
            notTaking.forEach { form.setParticipant(it, taking = false) }
        } else {
            form.setSplitType(SplitType.CUSTOM)
            custom.forEach { (memberId, share) -> form.setCustomShare(memberId, share) }
        }
        form.save()
        return form
    }

    private suspend fun ExpenseFormViewModel.awaitDone() = withTimeout(5_000) { state.first { it.done } }

    @Test
    fun membersExpensesBalancesBudgetAndSharedText_agreeEndToEnd(): Unit = runBlocking {
        // E1: four members in a fixed order.
        val members = membersScreen()
        listOf("An", "Bình", "Chi", "Dũng").forEach(members::addMember)
        val ids = withTimeout(5_000) { members.uiState.first { it.count == 4 } }.members.map { it.member.memberId }
        val (an, binh, chi, dung) = ids

        // E3 demo (proposal): An pays 300,000 for a dinner of An, Bình and Chi.
        addInForm("Lẩu gà lá é", "300000", an, notTaking = setOf(dung)).awaitDone()
        val screen = expensesScreen()
        val demo = withTimeout(5_000) { screen.uiState.first { !it.loading && it.expenses.size == 1 } }
        assertEquals(listOf(200_000L, -100_000L, -100_000L, 0L), demo.balances.map { it.balance })
        assertEquals(
            setOf(Triple("Bình", "An", 100_000L), Triple("Chi", "An", 100_000L)),
            demo.settlements.map { Triple(it.from.name, it.to.name, it.amount) }.toSet(),
        )

        // E2: 100,001 split equally by three, leftover dong to the first in member order.
        addInForm("Homestay", "100001", binh, notTaking = setOf(dung)).awaitDone()
        val homestay = repository.observeExpenses("t1").first().first { it.title == "Homestay" }
        assertEquals(mapOf(an to 33_334L, binh to 33_334L, chi to 33_333L), homestay.shares)

        // E2: a custom split that does not add up is not saved and the revision stays.
        val before = revision()
        val wrong = addInForm("Taxi", "90000", dung, custom = mapOf(an to "30000", dung to "50000"))
        assertFalse(wrong.state.value.done)
        assertEquals(before, revision())
        assertEquals(2, t.db.expenseDao().countByTrip("t1"))
        wrong.setCustomShare(chi, "10000")
        wrong.save()
        wrong.awaitDone()

        // E1: Dũng now pays and shares, so cannot be removed; a member never used can.
        members.deleteMember(dung)
        assertEquals(MemberProblem.IN_USE, withTimeout(5_000) { members.uiState.first { it.message != null } }.message)
        members.messageShown()
        members.addMember("Em")
        val em = withTimeout(5_000) { members.uiState.first { it.count == 5 } }.members.last().member.memberId
        members.deleteMember(em)
        assertEquals(4, withTimeout(5_000) { members.uiState.first { it.count == 4 && it.message == null } }.count)

        // E4: the plan comes from the itinerary; screen, balances and shared text agree.
        t.db.activityDao().insert(ActivityEntity("a1", "t1", 0, "Chợ đêm", "", 19 * 60, 250_000, "", 0, null))
        val state = withTimeout(5_000) { screen.uiState.first { it.expenses.size == 3 && it.planned == 250_000L } }
        assertEquals(490_001L, state.spent)
        assertEquals(240_001L, state.overPlan)
        assertEquals(state.spent, state.categoryTotals.values.sum())
        assertEquals(0L, state.balances.sumOf { it.balance })
        assertEquals(state.balances.map { it.paid - it.share }, state.balances.map { it.balance })

        // Making every suggested payment brings every balance back to 0.
        val left = state.balances.associate { it.member.memberId to it.balance }.toMutableMap()
        state.settlements.forEach {
            left[it.from.memberId] = left.getValue(it.from.memberId) + it.amount
            left[it.to.memberId] = left.getValue(it.to.memberId) - it.amount
        }
        assertTrue("Left over: $left", left.values.all { it == 0L })
        assertTrue(state.settlements.size <= state.memberCount - 1)

        val text = screen.shareText()
        assertTrue(text, text.contains("Planned: 250,000 ₫\nSpent: 490,001 ₫ (240,001 ₫ over plan)\n"))
        state.settlements.forEach {
            assertTrue(text, text.contains("- ${it.from.name} pays ${it.to.name} "))
        }
    }
}
