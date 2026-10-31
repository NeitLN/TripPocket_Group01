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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.data.repository.FakeAuthRepository
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

/** Expenses screen on a real (in-memory) Room database. Owner: Minh. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpensesViewModelTest {

    private val t = TestDatabase()
    private val auth: AuthRepository = FakeAuthRepository(TestDatabase.OWNER)
    private val expenses = ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer)

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        t.db.tripDao().insert(t.trip("t1"))
        listOf("An", "Bình", "Chi").forEachIndexed { i, name ->
            t.db.tripMemberDao().insert(TripMemberEntity("m$i", "t1", name, i))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private fun viewModel() = ExpensesViewModel(
        SavedStateHandle(mapOf(ARG_TRIP_ID to "t1")),
        TripRepository(t.db.tripDao(), auth, t.writer),
        expenses,
        SampleTripRepository(),
    )

    private suspend fun ExpensesViewModel.loaded(expenseCount: Int) =
        withTimeout(5_000) { uiState.first { !it.loading && it.trip != null && it.expenses.size == expenseCount } }

    @Test
    fun savedExpense_showsUpWithBalancesAndSuggestedPayments(): Unit = runBlocking {
        val viewModel = viewModel()
        assertEquals(3, viewModel.loaded(expenseCount = 0).memberCount)

        // Proposal demo: An pays 300,000 for a dinner shared by three.
        expenses.addExpense(
            "t1",
            ExpenseInput("Dinner", 300_000, "m0", ExpenseCategory.FOOD, SplitType.EQUAL, LocalDate.of(2026, 10, 15),
                mapOf("m0" to 100_000L, "m1" to 100_000L, "m2" to 100_000L)),
        )

        val state = viewModel.loaded(expenseCount = 1)
        assertEquals("An", state.expenses.single().payerName)
        assertEquals(300_000, state.spent)
        assertEquals(300_000L, state.categoryTotals[ExpenseCategory.FOOD])
        assertEquals(0L, state.balances.sumOf { it.balance })
        assertEquals(setOf("Bình" to "An", "Chi" to "An"), state.settlements.map { it.from.name to it.to.name }.toSet())
        assertTrue(viewModel.shareText().contains("- Bình pays An 100,000 ₫"))
    }

    @Test
    fun balancesMode_isKeptInSavedState(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.loaded(expenseCount = 0)
        viewModel.showBalances(true)
        assertTrue(withTimeout(5_000) { viewModel.uiState.first { it.showBalances } }.showBalances)
    }
}
