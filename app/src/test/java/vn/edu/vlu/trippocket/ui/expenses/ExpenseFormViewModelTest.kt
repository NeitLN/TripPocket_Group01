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
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

/** E2 form: validation, equal/custom split and saving through the repository. Owner: Minh. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpenseFormViewModelTest {

    private val t = TestDatabase()
    private val repository = ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer)
    private val day = LocalDate.of(2026, 10, 16)

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        val viewModel = ExpenseFormViewModel(SavedStateHandle(args), repository) { day }
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
}
