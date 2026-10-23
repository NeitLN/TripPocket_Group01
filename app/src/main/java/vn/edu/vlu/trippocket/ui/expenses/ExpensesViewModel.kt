package vn.edu.vlu.trippocket.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import vn.edu.vlu.trippocket.util.Format

/** One expense card with its payer's name already resolved. */
data class ExpenseRow(val expense: Expense, val payerName: String)

/** One member row in Balances mode. [balance] = [paid] - [share]. */
data class MemberBalance(val member: TripMember, val paid: Long, val share: Long, val balance: Long)

/** One suggested payment: [from] pays [to] [amount]. */
data class SettlementRow(val from: TripMember, val to: TripMember, val amount: Long)

data class ExpensesUiState(
    val tripName: String = "",
    val showBalances: Boolean = false,
    val expenses: List<ExpenseRow> = emptyList(),
    val balances: List<MemberBalance> = emptyList(),
    val settlements: List<SettlementRow> = emptyList(),
    val spent: Long = 0,
    /** Sum of the itinerary's estimated costs. */
    val planned: Long = 0,
    val categoryTotals: Map<ExpenseCategory, Long> = emptyMap(),
) {
    /** Part of the planned budget already spent, 0 to 100. */
    val budgetProgress: Int
        get() = if (planned == 0L) 0 else (spent * 100 / planned).toInt().coerceAtMost(100)
}

/** Screen 5: budget summary, expenses, member balances and suggested payments. */
class ExpensesViewModel(
    private val savedStateHandle: SavedStateHandle,
    repository: TripRepository,
) : ViewModel() {

    private val trip = repository.trip(savedStateHandle[ARG_TRIP_ID] ?: "")

    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    init {
        val trip = trip
        if (trip != null) {
            val members = repository.members(trip.tripId)
            val expenses = repository.expenses(trip.tripId)
            val byId = members.associateBy { it.memberId }
            val balances = ExpenseCalculator.balances(members, expenses)
            _uiState.value = ExpensesUiState(
                tripName = trip.name,
                showBalances = savedStateHandle[KEY_BALANCES] ?: false,
                expenses = expenses.map { ExpenseRow(it, byId[it.payerId]?.name.orEmpty()) },
                balances = members.map { member ->
                    MemberBalance(
                        member = member,
                        paid = ExpenseCalculator.paidBy(member.memberId, expenses),
                        share = ExpenseCalculator.shareOf(member.memberId, expenses),
                        balance = balances[member.memberId] ?: 0L,
                    )
                },
                settlements = ExpenseCalculator.settle(balances).map {
                    SettlementRow(byId.getValue(it.fromId), byId.getValue(it.toId), it.amount)
                },
                spent = expenses.sumOf { it.amount },
                planned = repository.estimatedTotal(trip.tripId),
                categoryTotals = ExpenseCategory.entries.associateWith { category ->
                    expenses.filter { it.category == category }.sumOf { it.amount }
                },
            )
        }
    }

    fun showBalances(show: Boolean) {
        savedStateHandle[KEY_BALANCES] = show
        _uiState.update { it.copy(showBalances = show) }
    }

    /** Plain-text summary sent through the Android share sheet. */
    fun shareText(): String {
        val trip = trip ?: return ""
        val state = _uiState.value
        return buildString {
            appendLine("${trip.name} - ${Format.dateRange(trip.startDate, trip.endDate)}")
            appendLine("Total spent: ${Format.vnd(state.spent)}")
            if (state.settlements.isNotEmpty()) {
                appendLine()
                appendLine("Suggested payments:")
                state.settlements.forEach {
                    appendLine("- ${it.from.name} pays ${it.to.name} ${Format.vnd(it.amount)}")
                }
            }
        }
    }

    companion object {
        private const val KEY_BALANCES = "show_balances"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ExpensesViewModel(createSavedStateHandle(), TripRepository()) }
        }
    }
}
