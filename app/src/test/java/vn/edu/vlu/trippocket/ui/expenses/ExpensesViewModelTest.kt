package vn.edu.vlu.trippocket.ui.expenses

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** The ViewModels hold no View or Context, so they run as plain JVM tests. Owner: Minh. */
class ExpensesViewModelTest {

    private fun dalat() = SavedStateHandle(mapOf(ARG_TRIP_ID to MockData.TRIP_DALAT))

    @Test
    fun expenses_balancesSumToZero_andShareTextListsPayments() {
        val viewModel = ExpensesViewModel(dalat(), SampleTripRepository())
        val state = viewModel.uiState.value
        assertEquals(0L, state.balances.sumOf { it.balance })
        assertEquals(state.expenses.sumOf { it.expense.amount }, state.spent)
        assertTrue(state.settlements.isNotEmpty())
        assertTrue(viewModel.shareText().contains("Suggested payments:"))

        viewModel.showBalances(true)
        assertTrue(viewModel.uiState.value.showBalances)
    }
}
