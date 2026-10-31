package vn.edu.vlu.trippocket.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.Expense
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.Settlement
import vn.edu.vlu.trippocket.data.model.Trip
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import vn.edu.vlu.trippocket.domain.ExpenseSummaryText
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** One expense card with its payer's name already resolved. */
data class ExpenseRow(val expense: Expense, val payerName: String)

/** One member row in Balances mode. [balance] = [paid] - [share]. */
data class MemberBalance(val member: TripMember, val paid: Long, val share: Long, val balance: Long)

/** One suggested payment: [from] pays [to] [amount]. */
data class SettlementRow(val from: TripMember, val to: TripMember, val amount: Long)

data class ExpensesUiState(
    /** Room loads asynchronously; nothing is shown as empty until the first rows arrive. */
    val loading: Boolean = true,
    val trip: Trip? = null,
    val showBalances: Boolean = false,
    val memberCount: Int = 0,
    val expenses: List<ExpenseRow> = emptyList(),
    val balances: List<MemberBalance> = emptyList(),
    val settlements: List<SettlementRow> = emptyList(),
    val spent: Long = 0,
    /** Sum of the itinerary's estimated costs. */
    val planned: Long = 0,
    val categoryTotals: Map<ExpenseCategory, Long> = emptyMap(),
) {
    val tripName: String get() = trip?.name.orEmpty()

    /** Part of the planned budget already spent, 0 to 100. */
    val budgetProgress: Int
        get() = if (planned == 0L) 0 else (spent * 100 / planned).toInt().coerceAtMost(100)

    /** The form refuses a 301st expense; the list says so before opening it. */
    val atExpenseLimit: Boolean get() = expenses.size >= Limits.MAX_EXPENSES
}

/** Screen 5: budget summary, expenses, member balances and suggested payments, read from Room. */
class ExpensesViewModel(
    private val savedStateHandle: SavedStateHandle,
    tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    // The planned budget moves to ItineraryRepository.estimatedTotal with E4.
    private val sampleRepository: SampleTripRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    val uiState: StateFlow<ExpensesUiState> = combine(
        tripRepository.observeTrip(tripId),
        expenseRepository.observeMembers(tripId),
        expenseRepository.observeExpenses(tripId),
        savedStateHandle.getStateFlow(KEY_BALANCES, false),
    ) { trip, members, expenses, showBalances ->
        buildState(trip, members, expenses, showBalances)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun showBalances(show: Boolean) {
        savedStateHandle[KEY_BALANCES] = show
    }

    /** Plain-text summary sent through the Android share sheet. */
    fun shareText(): String {
        val state = uiState.value
        val trip = state.trip ?: return ""
        val names = state.balances.associate { it.member.memberId to it.member.name }
        val settlements = state.settlements.map {
            Settlement(it.from.memberId, it.to.memberId, it.amount)
        }
        return ExpenseSummaryText.build(trip, state.spent, settlements, names)
    }

    private fun buildState(
        trip: Trip?,
        members: List<TripMember>,
        expenses: List<Expense>,
        showBalances: Boolean,
    ): ExpensesUiState {
        val byId = members.associateBy { it.memberId }
        // E3: computed from the same members and expenses as the list, so they never disagree.
        val balances = ExpenseCalculator.summarize(members, expenses)
        return ExpensesUiState(
            loading = false,
            trip = trip,
            showBalances = showBalances,
            memberCount = members.size,
            expenses = expenses.map { ExpenseRow(it, byId[it.payerId]?.name.orEmpty()) },
            balances = balances.positions.map { MemberBalance(it.member, it.paid, it.share, it.balance) },
            settlements = balances.settlements.mapNotNull {
                val from = byId[it.fromId] ?: return@mapNotNull null
                val to = byId[it.toId] ?: return@mapNotNull null
                SettlementRow(from, to, it.amount)
            },
            spent = expenses.sumOf { it.amount },
            planned = sampleRepository.estimatedTotal(tripId),
            categoryTotals = ExpenseCategory.entries.associateWith { category ->
                expenses.filter { it.category == category }.sumOf { it.amount }
            },
        )
    }

    companion object {
        private const val KEY_BALANCES = "show_balances"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ExpensesViewModel(
                    createSavedStateHandle(),
                    container.tripRepository,
                    container.expenseRepository,
                    container.sampleTripRepository,
                )
            }
        }
    }
}
