package vn.edu.vlu.trippocket.ui.members

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
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.ExpenseInput
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.domain.MemberProblem
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID
import java.time.LocalDate

/** E1: the members screen on a real (in-memory) Room database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MembersViewModelTest {

    private val t = TestDatabase()
    private val repository = ExpenseRepository(t.db.tripMemberDao(), t.db.expenseDao(), t.writer)

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

    private fun viewModel() = MembersViewModel(SavedStateHandle(mapOf(ARG_TRIP_ID to "t1")), repository)

    private suspend fun MembersViewModel.loaded(count: Int) =
        withTimeout(5_000) { uiState.first { !it.loading && it.count == count } }

    private suspend fun MembersViewModel.awaitMessage(problem: MemberProblem) =
        withTimeout(5_000) { uiState.first { it.message == problem } }

    @Test
    fun loadsMembers_withCountAndAddEnabled(): Unit = runBlocking {
        val state = viewModel().loaded(3)

        assertEquals(listOf("An", "Bình", "Chi"), state.members.map { it.member.name })
        assertEquals(3, state.count)
        assertTrue(state.canAdd)
        assertTrue(state.members.none { it.inUse })
        assertNull(state.message)
    }

    @Test
    fun addAndRename_updateTheList_andClearPreviousErrors(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.loaded(3)

        viewModel.addMember("Dũng")
        val added = viewModel.loaded(4)
        assertEquals("Dũng", added.members.last().member.name)
        assertEquals(3, added.members.last().member.orderIndex)
        assertTrue(added.canAdd)

        val id = added.members.last().member.memberId
        viewModel.renameMember(id, "  Dũng Phan  ")
        val renamed = withTimeout(5_000) {
            viewModel.uiState.first { it.members.last().member.name == "Dũng Phan" }
        }
        assertEquals("Dũng Phan", renamed.members.last().member.name)
        assertNull(renamed.message)
    }

    @Test
    fun invalidName_showsOneMessage_untilItIsShown(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.loaded(3)

        viewModel.renameMember("m0", "   ")
        val state = viewModel.awaitMessage(MemberProblem.BLANK_NAME)
        assertEquals(3, state.count)

        viewModel.messageShown()
        withTimeout(5_000) { viewModel.uiState.first { it.message == null } }
    }

    @Test
    fun deletingAMemberInUse_isBlocked_withTheReason(): Unit = runBlocking {
        repository.addExpense(
            "t1",
            ExpenseInput(
                "Dinner", 300_000, "m0", ExpenseCategory.FOOD, SplitType.EQUAL, LocalDate.of(2026, 10, 15),
                mapOf("m0" to 100_000L, "m1" to 100_000L, "m2" to 100_000L),
            ),
        )
        val viewModel = viewModel()
        assertTrue(viewModel.loaded(3).members.all { it.inUse })

        viewModel.deleteMember("m1")
        val state = viewModel.awaitMessage(MemberProblem.IN_USE)
        assertEquals(3, state.count)
    }

    @Test
    fun deletingAnUnusedMember_removesIt(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.loaded(3)
        val id = repository.addMember("t1", "Dũng")
        assertEquals(4, viewModel.loaded(4).count)

        viewModel.deleteMember(id)
        val state = viewModel.loaded(3)
        assertEquals(listOf("m0", "m1", "m2"), state.members.map { it.member.memberId })
        assertNull(state.message)
    }

    @Test
    fun atFifteenMembers_addIsDisabled_andTheSixteenthIsBlocked(): Unit = runBlocking {
        val dao = t.db.tripMemberDao()
        repeat(Limits.MAX_MEMBERS - 3) { i ->
            dao.insert(TripMemberEntity("seed$i", "t1", "Member $i", 3 + i))
        }
        val viewModel = viewModel()
        assertFalse(viewModel.loaded(15).canAdd)

        viewModel.addMember("One too many")
        val state = viewModel.awaitMessage(MemberProblem.LIMIT)
        assertEquals(15, state.count)
        assertFalse(state.canAdd)
    }
}
