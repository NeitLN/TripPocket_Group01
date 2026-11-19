package vn.edu.vlu.trippocket.ui.expenses

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
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
    private val savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""
    private val expenseId: String? = savedStateHandle[ARG_EXPENSE_ID]

    /**
     * A restored draft holds the VND amount as the user left it. The "Paid in" amount next to it
     * may be only the 2-decimal echo of that VND, so converting it back when the rates arrive
     * could change the amount (300,001 -> 300,000). Until the user edits the amount, the
     * currency or the "Paid in" amount, the rates must not rewrite it.
     */
    private var keepRestoredAmount = false

    private val _state = MutableStateFlow(ExpenseFormState(isEdit = expenseId != null, spentAt = today()))
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val members = repository.observeMembers(tripId).first()
            val draft = savedStateHandle.get<Boolean>(KEY_DRAFT) == true
            val existing = if (draft) null else expenseId?.let { repository.getExpense(it) }
            _state.update { state ->
                if (draft) {
                    // Android killed the app while the form was open: show what was typed.
                    restoreDraft(state.copy(loading = false, members = members))
                } else if (existing == null) {
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
            if (draft && _state.value.paidInCurrency != null) {
                keepRestoredAmount = true
                fetchExchangeRates()
            }
            // From now on every change is copied into the saved state (see [keepDraft]).
            _state.collect { if (!it.loading && !it.done) keepDraft(it) }
        }
    }

    /**
     * Copies the fields into the saved state, so they survive Android killing the app in the
     * background. Rates are not kept: they are downloaded again for a restored "Paid in" currency.
     */
    private fun keepDraft(state: ExpenseFormState) {
        savedStateHandle[KEY_DRAFT] = true
        savedStateHandle[KEY_TITLE] = state.title
        savedStateHandle[KEY_AMOUNT] = state.amountText
        savedStateHandle[KEY_PAYER] = state.payerId
        savedStateHandle[KEY_CATEGORY] = state.category.name
        savedStateHandle[KEY_SPENT_AT] = state.spentAt.toEpochDay()
        savedStateHandle[KEY_SPLIT] = state.splitType.name
        savedStateHandle[KEY_PARTICIPANTS] = ArrayList(state.participants)
        savedStateHandle[KEY_SHARE_IDS] = ArrayList(state.customShares.keys)
        savedStateHandle[KEY_SHARE_TEXTS] = ArrayList(state.customShares.values)
        savedStateHandle[KEY_PAID_IN] = state.paidInCurrency
        savedStateHandle[KEY_PAID_IN_AMOUNT] = state.paidInAmountText
    }

    /** [state] with the saved draft; members removed since then are dropped from it. */
    private fun restoreDraft(state: ExpenseFormState): ExpenseFormState {
        val ids = state.members.map { it.memberId }.toSet()
        val shareIds = savedStateHandle.get<ArrayList<String>>(KEY_SHARE_IDS).orEmpty()
        val shareTexts = savedStateHandle.get<ArrayList<String>>(KEY_SHARE_TEXTS).orEmpty()
        return state.copy(
            title = savedStateHandle[KEY_TITLE] ?: "",
            amountText = savedStateHandle[KEY_AMOUNT] ?: "",
            payerId = savedStateHandle.get<String>(KEY_PAYER)?.takeIf { it in ids },
            category = enumOrNull<ExpenseCategory>(savedStateHandle[KEY_CATEGORY]) ?: state.category,
            spentAt = savedStateHandle.get<Long>(KEY_SPENT_AT)?.let(LocalDate::ofEpochDay) ?: state.spentAt,
            splitType = enumOrNull<SplitType>(savedStateHandle[KEY_SPLIT]) ?: state.splitType,
            participants = savedStateHandle.get<ArrayList<String>>(KEY_PARTICIPANTS).orEmpty().filter { it in ids }.toSet(),
            customShares = shareIds.zip(shareTexts).toMap().filterKeys { it in ids },
            paidInCurrency = savedStateHandle[KEY_PAID_IN],
            paidInAmountText = savedStateHandle[KEY_PAID_IN_AMOUNT] ?: "",
        )
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
        keepRestoredAmount = false
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
        keepRestoredAmount = false
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
        keepRestoredAmount = false
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
                    if (!keepRestoredAmount) recalculateVndFromPaidIn()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(ratesLoading = false, ratesError = result.error) }
                }
            }
        }
    }

    /** The "Paid in" amount in VND, or null without a currency, a usable amount or a rate. */
    private fun paidInVnd(state: ExpenseFormState): Long? {
        val currency = state.paidInCurrency ?: return null
        val foreignAmount = parseForeignAmount(state.paidInAmountText) ?: return null
        val rate = state.exchangeRates?.items?.find { it.code == currency }?.rateToVnd ?: return null
        if (rate <= 0.0) return null
        return CurrencyConverter.toVnd(foreignAmount, rate)
    }

    private fun recalculateVndFromPaidIn() {
        val vnd = paidInVnd(_state.value) ?: return
        if (vnd > MAX_AMOUNT_VND) {
            // The amount field holds 12 digits: keep the old amount and say why instead.
            _state.update { it.copy(amountError = R.string.expense_error_amount_too_large) }
        } else if (vnd > 0L) {
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
            // A refused "Paid in" conversion leaves the old VND amount in the field: saving it
            // would store an amount that does not match what "Paid in" shows.
            amountError = when {
                (paidInVnd(state) ?: 0L) > MAX_AMOUNT_VND -> R.string.expense_error_amount_too_large
                amount == null -> R.string.expense_error_amount
                else -> null
            },
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A database failure: keep the form open, the user can try again.
                _state.update { it.copy(saving = false, message = R.string.expense_error_save_failed) }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = R.string.expense_error_save_failed) }
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
        private const val KEY_DRAFT = "draft"
        private const val KEY_TITLE = "draft_title"
        private const val KEY_AMOUNT = "draft_amount"
        private const val KEY_PAYER = "draft_payer"
        private const val KEY_CATEGORY = "draft_category"
        private const val KEY_SPENT_AT = "draft_spent_at"
        private const val KEY_SPLIT = "draft_split"
        private const val KEY_PARTICIPANTS = "draft_participants"
        private const val KEY_SHARE_IDS = "draft_share_ids"
        private const val KEY_SHARE_TEXTS = "draft_share_texts"
        private const val KEY_PAID_IN = "draft_paid_in"
        private const val KEY_PAID_IN_AMOUNT = "draft_paid_in_amount"

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }

        /** Nav argument of expenseFormFragment; absent when adding. */
        const val ARG_EXPENSE_ID = "expenseId"

        /** etAmount has maxLength 12 (fragment_expense_form.xml): the largest amount it can show. */
        const val MAX_AMOUNT_VND = 999_999_999_999L

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
