package vn.edu.vlu.trippocket.ui.expenses

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ExchangeRates
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.model.TripMember
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.repository.ExchangeRateRepository
import vn.edu.vlu.trippocket.data.repository.ExpenseProblem
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.InvalidExpenseException
import vn.edu.vlu.trippocket.domain.ApiError
import vn.edu.vlu.trippocket.domain.CurrencyConverter
import vn.edu.vlu.trippocket.domain.ExpenseCalculator
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

data class ExpenseFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    /** In the fixed order used for equal splits. */
    val members: List<TripMember> = emptyList(),
    val title: String = "",
    val amountText: String = "",
    val payerId: String? = null,
    val category: ExpenseCategory = ExpenseCategory.FOOD,
    val spentAt: LocalDate = LocalDate.now(),
    val splitType: SplitType = SplitType.EQUAL,
    /** Equal split: the members who take part. */
    val participants: Set<String> = emptySet(),
    /** Custom split: memberId -> share as typed. */
    val customShares: Map<String, String> = emptyMap(),
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val payerError: Int? = null,
    @param:StringRes val splitError: Int? = null,
    val saving: Boolean = false,
    /** Saved or deleted: the form closes. */
    val done: Boolean = false,
    /** One-off message for a snackbar; cleared by [ExpenseFormViewModel.messageShown]. */
    @param:StringRes val message: Int? = null,
    val messageArg: Int? = null,

    // Paid in / foreign currency fields (M4)
    val ratesLoading: Boolean = false,
    val ratesError: ApiError? = null,
    val exchangeRates: ExchangeRates? = null,
    val paidInCurrency: String? = null,
    val paidInAmountText: String = "",
) {
    val amount: Long? get() = amountText.toLongOrNull()?.takeIf { it > 0 }

    /** Equal split preview: memberId -> share, empty until there is an amount and a participant. */
    val equalShares: Map<String, Long>
        get() {
            val amount = amount ?: return emptyMap()
            val taking = members.filter { it.memberId in participants }
            return if (taking.isEmpty()) emptyMap() else ExpenseCalculator.splitEqually(amount, taking)
        }

    /** Custom split: amount minus the typed shares (negative = too much). null if a share is not a number. */
    val customRemaining: Long?
        get() {
            val shares = parsedCustomShares() ?: return null
            return (amount ?: 0) - shares.values.sum()
        }

    /** null when a typed share is not a whole, non-negative number. Blank counts as 0. */
    fun parsedCustomShares(): Map<String, Long>? = members.associate { member ->
        val text = customShares[member.memberId].orEmpty().trim()
        member.memberId to (if (text.isEmpty()) 0L else text.toLongOrNull()?.takeIf { it >= 0 } ?: return null)
    }
}

/** E2: add or edit one expense with an equal or custom split (proposal 3.4). */
class ExpenseFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""
    private val expenseId: String? = savedStateHandle[ARG_EXPENSE_ID]

    private val _state = MutableStateFlow(ExpenseFormState(isEdit = expenseId != null, spentAt = today()))
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val members = repository.observeMembers(tripId).first()
            val existing = expenseId?.let { repository.getExpense(it) }
            _state.update { state ->
                if (existing == null) {
                    state.copy(
                        loading = false,
                        members = members,
                        payerId = members.firstOrNull()?.memberId,
                        participants = members.map { it.memberId }.toSet(),
                        message = if (expenseId != null) R.string.expense_error_not_found else null,
                    )
                } else {
                    state.copy(
                        loading = false,
                        members = members,
                        title = existing.title,
                        amountText = existing.amount.toString(),
                        payerId = existing.payerId,
                        category = existing.category,
                        spentAt = existing.spentAt,
                        splitType = existing.splitType,
                        participants = existing.shares.filterValues { it > 0 }.keys,
                        customShares = existing.shares.mapValues { it.value.toString() },
                    )
                }
            }
        }
    }

    fun setTitle(text: String) = _state.update { it.copy(title = text, titleError = null) }

    /**
     * The VND amount as typed. The split errors depend on the amount too, so a new amount clears
     * them. The field also echoes back what [recalculateVndFromPaidIn] wrote; that echo changes
     * nothing. A real edit makes VND the source: the "Paid in" amount follows it
     * ([syncPaidInFromVnd]), so a later currency change converts what the form shows.
     */
    fun setAmount(text: String) {
        val typed = text.trim()
        if (typed == _state.value.amountText) return
        _state.update { it.copy(amountText = typed, amountError = null, splitError = null) }
        syncPaidInFromVnd()
    }

    fun setPayer(memberId: String) = _state.update { it.copy(payerId = memberId, payerError = null) }

    fun setCategory(category: ExpenseCategory) = _state.update { it.copy(category = category) }

    fun setDate(date: LocalDate) = _state.update { it.copy(spentAt = date) }

    fun setSplitType(type: SplitType) = _state.update { it.copy(splitType = type, splitError = null) }

    fun setParticipant(memberId: String, taking: Boolean) = _state.update {
        it.copy(participants = if (taking) it.participants + memberId else it.participants - memberId, splitError = null)
    }

    fun setCustomShare(memberId: String, text: String) = _state.update {
        it.copy(customShares = it.customShares + (memberId to text), splitError = null)
    }

    fun setPaidInCurrency(currency: String?) {
        _state.update { it.copy(paidInCurrency = currency) }
        if (currency != null && _state.value.exchangeRates == null && !_state.value.ratesLoading) {
            fetchExchangeRates()
        } else {
            recalculateVndFromPaidIn()
        }
    }

    fun setPaidInAmount(text: String) {
        // Echo of a value [syncPaidInFromVnd] wrote: converting it back would undo the VND edit.
        if (text == _state.value.paidInAmountText) return
        _state.update { it.copy(paidInAmountText = text) }
        recalculateVndFromPaidIn()
    }

    fun retryRates() {
        fetchExchangeRates()
    }

    private fun fetchExchangeRates() {
        _state.update { it.copy(ratesLoading = true, ratesError = null) }
        viewModelScope.launch {
            when (val result = exchangeRateRepository.getExchangeRates()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(ratesLoading = false, exchangeRates = result.data, ratesError = null) }
                    recalculateVndFromPaidIn()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(ratesLoading = false, ratesError = result.error) }
                }
            }
        }
    }

    private fun recalculateVndFromPaidIn() {
        val state = _state.value
        val currency = state.paidInCurrency ?: return
        val foreignAmount = parseForeignAmount(state.paidInAmountText) ?: return
        val rate = state.exchangeRates?.items?.find { it.code == currency }?.rateToVnd ?: return
        if (rate <= 0.0) return
        val vnd = CurrencyConverter.toVnd(foreignAmount, rate)
        if (vnd > 0L) {
            _state.update { it.copy(amountText = vnd.toString(), amountError = null, splitError = null) }
        }
    }

    /**
     * After the user typed VND: the "Paid in" amount becomes VND / rate (2 decimals), so both
     * fields agree. Without a rate yet the old foreign amount is cleared instead; otherwise the
     * rates arriving later would convert it and overwrite the VND just typed.
     */
    private fun syncPaidInFromVnd() {
        val state = _state.value
        val currency = state.paidInCurrency ?: return
        if (state.paidInAmountText.isBlank() && state.amount == null) return
        val rate = state.exchangeRates?.items?.find { it.code == currency }?.rateToVnd?.takeIf { it > 0.0 }
        val vnd = state.amount
        val foreign = if (rate == null || vnd == null) "" else CurrencyConverter.fromVnd(vnd, rate)
        _state.update { it.copy(paidInAmountText = foreign) }
    }

    private fun parseForeignAmount(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() }
    }

    fun messageShown() = _state.update { it.copy(message = null, messageArg = null) }

    fun save() {
        val state = _state.value
        if (state.loading || state.saving) return
        val amount = state.amount
        val shares: Map<String, Long>? = when {
            amount == null -> null
            state.splitType == SplitType.EQUAL -> state.equalShares.takeIf { it.isNotEmpty() }
            else -> state.parsedCustomShares()
        }
        val splitError = when {
            amount == null -> null
            state.splitType == SplitType.EQUAL && shares == null -> R.string.expense_error_participants
            // A 0 dong share is not stored, so editing later would drop that person from the split.
            state.splitType == SplitType.EQUAL && shares != null && shares.values.any { it == 0L } ->
                R.string.expense_error_equal_too_small
            shares == null -> R.string.expense_error_custom_number
            ExpenseCalculator.validateCustomSplit(amount, shares) != null -> R.string.expense_error_custom_sum
            else -> null
        }
        val checked = state.copy(
            titleError = R.string.expense_error_title.takeIf { state.title.isBlank() },
            amountError = R.string.expense_error_amount.takeIf { amount == null },
            payerError = R.string.expense_error_payer.takeIf { state.payerId == null },
            splitError = splitError,
        )
        val payerId = state.payerId
        if (checked.titleError != null || checked.amountError != null || checked.payerError != null ||
            splitError != null || amount == null || shares == null || payerId == null
        ) {
            _state.value = checked
            return
        }

        val input = ExpenseInput(state.title.trim(), amount, payerId, state.category, state.splitType, state.spentAt, shares)
        _state.value = checked.copy(saving = true)
        viewModelScope.launch {
            try {
                if (expenseId == null) repository.addExpense(tripId, input)
                else repository.updateExpense(tripId, expenseId, input)
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: LimitExceededException) {
                _state.update { it.copy(saving = false, message = R.string.expenses_limit_reached, messageArg = e.max) }
            } catch (e: InvalidExpenseException) {
                _state.update { it.copy(saving = false, message = messageFor(e.problem)) }
            }
        }
    }

    fun delete() {
        val id = expenseId ?: return
        viewModelScope.launch {
            try {
                repository.deleteExpense(tripId, id)
                _state.update { it.copy(done = true) }
            } catch (e: InvalidExpenseException) {
                _state.update { it.copy(message = messageFor(e.problem)) }
            }
        }
    }

    private fun messageFor(problem: ExpenseProblem) = when (problem) {
        ExpenseProblem.NOT_FOUND -> R.string.expense_error_not_found
        ExpenseProblem.MEMBER_NOT_IN_TRIP -> R.string.expense_error_member
        ExpenseProblem.SPLIT -> R.string.expense_error_custom_sum
        ExpenseProblem.AMOUNT_NOT_POSITIVE -> R.string.expense_error_amount
        ExpenseProblem.BLANK_TITLE, ExpenseProblem.TITLE_TOO_LONG -> R.string.expense_error_title
    }

    companion object {
        /** Nav argument of expenseFormFragment; absent when adding. */
        const val ARG_EXPENSE_ID = "expenseId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExpenseFormViewModel(
                    createSavedStateHandle(),
                    appContainer().expenseRepository,
                    appContainer().exchangeRateRepository,
                )
            }
        }
    }
}
