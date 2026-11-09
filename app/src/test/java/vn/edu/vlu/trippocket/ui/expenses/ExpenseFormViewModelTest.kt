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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateApi
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateResponse
import vn.edu.vlu.trippocket.data.repository.ExchangeRateRepository
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.domain.ApiError
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.io.IOException
import java.time.LocalDate

/** E2 form: validation, equal/custom split and saving through the repository. Owner: Minh. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpenseFormViewModelTest {

    private class FakeExchangeRateApi : ExchangeRateApi {
        var callCount = 0
        var response: ExchangeRateResponse? = null
        var error: Exception? = null

        override suspend fun getLatestRates(base: String): ExchangeRateResponse {
            callCount++
            error?.let { throw it }
            return response ?: ExchangeRateResponse(
                result = "success",
                timeLastUpdateUtc = "Fri, 08 Nov 2026 00:00:00 +0000",
                rates = mapOf(
                    "USD" to 1.0,
                    "VND" to 25_000.0,
                    "EUR" to 0.9,
                    "JPY" to 150.0,
                    "KRW" to 1350.0,
                    "THB" to 35.0,
                    "SGD" to 1.35,
                    "GBP" to 0.8,
                    "AUD" to 1.5,
                    "CNY" to 7.2,
                    "TWD" to 32.0,
                    "MYR" to 4.5,
                    "IDR" to 16_000.0,
                ),
            )
        }
    }

    private val t = TestDatabase()
    private val repository = ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer)
    private val fakeExchangeRateApi = FakeExchangeRateApi()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val exchangeRateRepository = ExchangeRateRepository(fakeExchangeRateApi, testDispatcher)
    private val day = LocalDate.of(2026, 10, 16)

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        fakeExchangeRateApi.callCount = 0
        fakeExchangeRateApi.error = null
        fakeExchangeRateApi.response = null
        t.db.tripDao().insert(t.trip("t1"))
        listOf("a", "b", "c").forEachIndexed { i, id ->
            t.db.tripMemberDao().insert(TripMemberEntity(id, "t1", id.uppercase(), i))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        t.close()
    }

    private suspend fun form(expenseId: String? = null): ExpenseFormViewModel {
        val args = mutableMapOf<String, Any?>(ARG_TRIP_ID to "t1")
        if (expenseId != null) args[ExpenseFormViewModel.ARG_EXPENSE_ID] = expenseId
        val viewModel = ExpenseFormViewModel(SavedStateHandle(args), repository, exchangeRateRepository) { day }
        withTimeout(5_000) { viewModel.state.first { !it.loading } }
        return viewModel
    }

    private suspend fun ExpenseFormViewModel.awaitDone() = withTimeout(5_000) { state.first { it.done } }

    private suspend fun revision() = t.db.tripDao().observeTrip("t1", TestDatabase.OWNER).first()!!.localRevision

    @Test
    fun newExpense_defaultsToEveryoneAndSplitsLeftoverDongInMemberOrder() = runBlocking {
        val viewModel = form()
        val state = viewModel.state.value
        assertEquals(setOf("a", "b", "c"), state.participants)
        assertEquals("a", state.payerId)
        assertEquals(day, state.spentAt)

        viewModel.setTitle("Bánh căn")
        viewModel.setAmount("100001")
        viewModel.setCategory(ExpenseCategory.FOOD)
        viewModel.save()
        viewModel.awaitDone()

        val saved = repository.observeExpenses("t1").first().single()
        assertEquals(mapOf("a" to 33_334L, "b" to 33_334L, "c" to 33_333L), saved.shares)
        assertEquals(day, saved.spentAt)
        assertEquals(6, revision())
    }

    @Test
    fun equalSplit_onlyBetweenTheTickedMembers() = runBlocking {
        val viewModel = form()
        viewModel.setTitle("Taxi")
        viewModel.setAmount("90000")
        viewModel.setParticipant("c", taking = false)
        assertEquals(mapOf("a" to 45_000L, "b" to 45_000L), viewModel.state.value.equalShares)

        viewModel.save()
        viewModel.awaitDone()
        assertEquals(mapOf("a" to 45_000L, "b" to 45_000L), repository.observeExpenses("t1").first().single().shares)
    }

    @Test
    fun equalSplitSmallerThanThePeople_isRefused_soNobodyIsDroppedFromTheSplit() = runBlocking {
        val viewModel = form()
        viewModel.setTitle("Kẹo")
        // 2 dong for three: C would get 0, and a 0 share is not stored.
        viewModel.setAmount("2")
        assertEquals(mapOf("a" to 1L, "b" to 1L, "c" to 0L), viewModel.state.value.equalShares)

        viewModel.save()
        assertEquals(R.string.expense_error_equal_too_small, viewModel.state.value.splitError)
        assertFalse(viewModel.state.value.done)
        assertEquals(0, t.db.expenseDao().countByTrip("t1"))
        assertEquals(5, revision())

        viewModel.setAmount("3")
        assertNull(viewModel.state.value.splitError)
        viewModel.save()
        viewModel.awaitDone()
        assertEquals(mapOf("a" to 1L, "b" to 1L, "c" to 1L), repository.observeExpenses("t1").first().single().shares)
    }

    @Test
    fun customSplitThatDoesNotAddUp_staysOpenAndSavesNothing() = runBlocking {
        val viewModel = form()
        viewModel.setTitle("Homestay")
        viewModel.setAmount("300000")
        viewModel.setSplitType(SplitType.CUSTOM)
        viewModel.setCustomShare("a", "100000")
        viewModel.setCustomShare("b", "100000")
        assertEquals(100_000L, viewModel.state.value.customRemaining)

        viewModel.save()
        val state = viewModel.state.value
        assertEquals(R.string.expense_error_custom_sum, state.splitError)
        assertFalse(state.done)
        assertEquals(0, t.db.expenseDao().countByTrip("t1"))
        assertEquals(5, revision())

        viewModel.setCustomShare("c", "100000")
        assertEquals(0L, viewModel.state.value.customRemaining)
        viewModel.save()
        viewModel.awaitDone()
        assertEquals(1, t.db.expenseDao().countByTrip("t1"))
    }

    @Test
    fun missingTitleAmountOrParticipants_showFieldErrors() = runBlocking {
        val viewModel = form()
        listOf("a", "b", "c").forEach { viewModel.setParticipant(it, taking = false) }
        viewModel.save()
        assertEquals(R.string.expense_error_title, viewModel.state.value.titleError)
        assertEquals(R.string.expense_error_amount, viewModel.state.value.amountError)

        viewModel.setTitle("Vé")
        viewModel.setAmount("50000")
        viewModel.save()
        assertEquals(R.string.expense_error_participants, viewModel.state.value.splitError)
        assertNull(viewModel.state.value.titleError)
        assertEquals(0, t.db.expenseDao().countByTrip("t1"))
    }

    @Test
    fun editingLoadsTheExpense_andSavingReplacesIt() = runBlocking {
        val id = repository.addExpense(
            "t1",
            ExpenseInput("Cà phê", 140_000, "a", ExpenseCategory.FOOD, SplitType.CUSTOM, day,
                mapOf("a" to 40_000L, "b" to 50_000L, "c" to 50_000L)),
        )
        val viewModel = form(id)
        val loaded = viewModel.state.value
        assertTrue(loaded.isEdit)
        assertEquals("Cà phê", loaded.title)
        assertEquals("140000", loaded.amountText)
        assertEquals(SplitType.CUSTOM, loaded.splitType)
        assertEquals("40000", loaded.customShares["a"])

        viewModel.setAmount("150000")
        viewModel.setCustomShare("a", "50000")
        viewModel.save()
        viewModel.awaitDone()

        val saved = repository.getExpense(id)!!
        assertEquals(150_000, saved.amount)
        assertEquals(mapOf("a" to 50_000L, "b" to 50_000L, "c" to 50_000L), saved.shares)
    }

    @Test
    fun deleteRemovesTheExpense() = runBlocking {
        val id = repository.addExpense(
            "t1",
            ExpenseInput("Vé", 30_000, "b", ExpenseCategory.TRANSPORT, SplitType.EQUAL, day, mapOf("b" to 30_000L)),
        )
        val viewModel = form(id)
        viewModel.delete()
        viewModel.awaitDone()
        assertEquals(0, t.db.expenseDao().countByTrip("t1"))
    }

    @Test
    fun init_doesNotCallExchangeRates() = runBlocking {
        form()
        assertEquals(0, fakeExchangeRateApi.callCount)
    }

    @Test
    fun editExpense_preservesAmountTextAndDoesNotCallExchangeRates() = runBlocking {
        val id = repository.addExpense(
            "t1",
            ExpenseInput("Cà phê", 140_000, "a", ExpenseCategory.FOOD, SplitType.CUSTOM, day,
                mapOf("a" to 40_000L, "b" to 50_000L, "c" to 50_000L)),
        )
        val viewModel = form(id)
        val loaded = viewModel.state.value
        assertEquals("140000", loaded.amountText)
        assertNull(loaded.paidInCurrency)
        assertEquals(0, fakeExchangeRateApi.callCount)
    }

    @Test
    fun paidIn_commaAndDotBothConvert() = runBlocking {
        val viewModel = form()
        viewModel.setPaidInCurrency("USD")
        assertEquals(1, fakeExchangeRateApi.callCount)

        // 1 USD = 25,000 VND -> 10.5 USD = 262,500 VND
        viewModel.setPaidInAmount("10.5")
        assertEquals("262500", viewModel.state.value.amountText)

        // Comma decimal separator: 10,5 USD = 262,500 VND
        viewModel.setPaidInAmount("10,5")
        assertEquals("262500", viewModel.state.value.amountText)

        // Invalid or non-positive strings do not overwrite amountText
        viewModel.setPaidInAmount("invalid")
        assertEquals("262500", viewModel.state.value.amountText)

        viewModel.setPaidInAmount("-5")
        assertEquals("262500", viewModel.state.value.amountText)

        // Switching currency recalculates using already fetched rates without calling API again
        // 9 EUR: EUR rate is 25000 / 0.9 = 27777.777... -> 9 * rate = 250,000 VND
        viewModel.setPaidInAmount("9")
        viewModel.setPaidInCurrency("EUR")
        assertEquals(1, fakeExchangeRateApi.callCount)
        assertEquals("250000", viewModel.state.value.amountText)
    }

    @Test
    fun paidIn_manualVndEditWins() = runBlocking {
        val viewModel = form()
        viewModel.setPaidInCurrency("USD")
        viewModel.setPaidInAmount("10")
        assertEquals("250000", viewModel.state.value.amountText)

        // User manually edits VND: VND stays as edited
        viewModel.setAmount("300000")
        assertEquals("300000", viewModel.state.value.amountText)

        // Modifying foreign amount again recalculates and overwrites VND
        viewModel.setPaidInAmount("20")
        assertEquals("500000", viewModel.state.value.amountText)
    }

    @Test
    fun paidIn_ratesError_allowsRetryAndDoesNotBlockSave() = runBlocking {
        fakeExchangeRateApi.error = IOException("Offline")
        val viewModel = form()

        viewModel.setPaidInCurrency("USD")
        assertEquals(1, fakeExchangeRateApi.callCount)
        assertEquals(ApiError.OFFLINE, viewModel.state.value.ratesError)
        assertFalse(viewModel.state.value.ratesLoading)

        // Retrying triggers network call again
        viewModel.retryRates()
        assertEquals(2, fakeExchangeRateApi.callCount)

        // Network error does not block entering VND and saving to Room
        viewModel.setTitle("Bữa tối")
        viewModel.setAmount("200000")
        viewModel.save()
        viewModel.awaitDone()

        assertEquals(1, t.db.expenseDao().countByTrip("t1"))
        val saved = repository.observeExpenses("t1").first().first()
        assertEquals("Bữa tối", saved.title)
        assertEquals(200_000L, saved.amount)
    }
}
