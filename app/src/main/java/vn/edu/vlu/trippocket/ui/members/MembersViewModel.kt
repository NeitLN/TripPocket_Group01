package vn.edu.vlu.trippocket.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.InvalidMemberException
import vn.edu.vlu.trippocket.domain.MemberProblem
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

/** One member row. [inUse] means the member paid or shares an expense, so the row cannot be removed. */
data class MemberRow(val member: TripMember, val inUse: Boolean)

data class MembersUiState(
    /** Room loads asynchronously; the list is not shown as empty before the first rows arrive. */
    val loading: Boolean = true,
    val members: List<MemberRow> = emptyList(),
    /** Number of members, for "3 of 15 members" and the "Members (3)" entry on the Expenses tab. */
    val count: Int = 0,
    /** false at [Limits.MAX_MEMBERS]: the Add button is disabled and the count says 15/15. */
    val canAdd: Boolean = false,
    /**
     * One-off error for the next Snackbar: the screen calls [MembersViewModel.messageShown]
     * after showing it. It lives in the state (not in a Channel) so a rotation does not lose
     * it; null when there is nothing to show.
     */
    val message: MemberProblem? = null,
)

/**
 * E1: the members of one trip, read from Room. The screen only adds, renames and removes;
 * the fixed order and the rules (name, 15-member cap, member in use) are enforced again in
 * [ExpenseRepository] inside its transaction.
 */
class MembersViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""
    private val message = MutableStateFlow<MemberProblem?>(null)

    val uiState: StateFlow<MembersUiState> = combine(
        repository.observeMembers(tripId),
        repository.observeExpenses(tripId),
        message,
    ) { members, expenses, pendingMessage ->
        // Same rule as ExpenseDao.countUsingMember: payer or any share, whatever its amount.
        val used = expenses.flatMapTo(mutableSetOf()) { it.shares.keys + it.payerId }
        MembersUiState(
            loading = false,
            members = members.map { MemberRow(it, it.memberId in used) },
            count = members.size,
            canAdd = members.size < Limits.MAX_MEMBERS,
            message = pendingMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembersUiState())

    fun addMember(name: String) {
        viewModelScope.launch {
            try {
                repository.addMember(tripId, name)
                message.value = null
            } catch (e: InvalidMemberException) {
                message.value = e.problem
            }
        }
    }

    fun renameMember(memberId: String, name: String) {
        viewModelScope.launch {
            try {
                repository.renameMember(tripId, memberId, name)
                message.value = null
            } catch (e: InvalidMemberException) {
                message.value = e.problem
            }
        }
    }

    fun deleteMember(memberId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMember(tripId, memberId)
                message.value = null
            } catch (e: InvalidMemberException) {
                message.value = e.problem
            }
        }
    }

    /** Called by the screen once the message is visible, so it is not shown again. */
    fun messageShown() {
        message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MembersViewModel(createSavedStateHandle(), appContainer().expenseRepository)
            }
        }
    }
}
