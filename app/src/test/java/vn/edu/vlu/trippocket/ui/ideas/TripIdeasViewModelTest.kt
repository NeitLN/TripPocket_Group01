package vn.edu.vlu.trippocket.ui.ideas

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepository
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepositoryTest.Companion.IDEAS_JSON
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepositoryTest.Companion.json
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepositoryTest.Companion.signedInAs
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepositoryTest.Companion.tripIdeaApiOn
import vn.edu.vlu.trippocket.domain.ApiError
import vn.edu.vlu.trippocket.domain.TripIdeaProblem
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher

/**
 * Milestone 4: the Loading / Success / Error states of the Trip Ideas list and form, with the
 * real repository and Retrofit talking to MockWebServer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripIdeasViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TripIdeaRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        repository = TripIdeaRepository(tripIdeaApiOn(server), signedInAs("uid-1", "tien"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
        withTimeout(5_000) { first(predicate) }

    @Test
    fun list_startsLoading_thenShowsIdeas_andMarksMine() = runBlocking {
        server.enqueue(json(200, IDEAS_JSON))

        val vm = TripIdeasViewModel(repository)

        val state = vm.uiState.await { !it.loading }
        assertNull(state.error)
        assertEquals(listOf("1", "2"), state.ideas.map { it.idea.id })
        assertEquals(listOf(true, false), state.ideas.map { it.mine })
        assertFalse(state.showEmpty)
    }

    @Test
    fun list_emptyServer_showsEmptyState() = runBlocking {
        server.enqueue(json(200, "[]"))

        val state = TripIdeasViewModel(repository).uiState.await { !it.loading }

        assertTrue(state.showEmpty)
    }

    @Test
    fun list_serverError_showsBanner_thenRetryLoads() = runBlocking {
        server.enqueue(json(500, "{}"))
        server.enqueue(json(200, IDEAS_JSON))
        val vm = TripIdeasViewModel(repository)

        val failed = vm.uiState.await { !it.loading }
        assertEquals(ApiError.SERVER, failed.error)
        assertFalse(failed.showEmpty)

        vm.retry()
        val loaded = vm.uiState.await { !it.loading && it.ideas.isNotEmpty() }
        assertNull(loaded.error)
    }

    @Test
    fun list_failedRefresh_keepsTheRows() = runBlocking {
        server.enqueue(json(200, IDEAS_JSON))
        server.enqueue(json(404, "\"Not found\""))
        val vm = TripIdeasViewModel(repository)
        vm.uiState.await { !it.loading }

        vm.refresh()

        val state = vm.uiState.await { !it.refreshing && it.error != null }
        assertEquals(ApiError.NOT_FOUND, state.error)
        assertEquals(2, state.ideas.size)
    }

    @Test
    fun delete_removesTheRowOnceTheServerConfirms() = runBlocking {
        server.enqueue(json(200, IDEAS_JSON))
        server.enqueue(json(200, """{"id":"1","title":"x"}"""))
        val vm = TripIdeasViewModel(repository)
        vm.uiState.await { !it.loading }

        vm.delete("1")

        val state = vm.uiState.await { it.message != null }
        assertEquals(TripIdeasMessage.Deleted, state.message)
        assertEquals(listOf("2"), state.ideas.map { it.idea.id })
    }

    @Test
    fun delete_failure_keepsTheRow_andSaysWhy() = runBlocking {
        server.enqueue(json(200, IDEAS_JSON))
        server.enqueue(json(503, "{}"))
        val vm = TripIdeasViewModel(repository)
        vm.uiState.await { !it.loading }

        vm.delete("1")

        val state = vm.uiState.await { it.message != null }
        assertEquals(TripIdeasMessage.DeleteFailed(ApiError.SERVER), state.message)
        assertEquals(2, state.ideas.size)
    }

    // ---- Form (POST / PUT) ----

    private fun form(ideaId: String? = null) = TripIdeaFormViewModel(
        SavedStateHandle(if (ideaId == null) emptyMap() else mapOf(ARG_IDEA_ID to ideaId)),
        repository,
    )

    private val valid = TripIdeaFields(
        title = "3 days in Da Lat", destination = "Da Lat", days = "3",
        budget = "4.500.000", coverUrl = "", summary = "Pine hills",
    )

    @Test
    fun form_invalidFields_showErrors_andSendNothing() {
        val vm = form()
        vm.onFieldsChanged(valid.copy(title = " ", days = "0", coverUrl = "http://plain.example"))

        vm.save()

        assertEquals(
            setOf(TripIdeaProblem.BLANK_TITLE, TripIdeaProblem.BAD_DAYS, TripIdeaProblem.BAD_COVER_URL),
            vm.uiState.value.problems,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun form_new_postsAndCloses() = runBlocking {
        server.enqueue(json(201, """{"id":"9","title":"3 days in Da Lat"}"""))
        val vm = form()
        assertEquals("3", vm.uiState.value.fillFields?.days)
        vm.fieldsShown()
        vm.onFieldsChanged(valid)

        vm.save()

        vm.uiState.await { it.saved }
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun form_saveOffline_keepsTheFormWithTheError() = runBlocking {
        server.shutdown()
        val vm = form()
        vm.onFieldsChanged(valid)

        vm.save()

        val state = vm.uiState.await { !it.saving && it.saveError != null }
        assertEquals(ApiError.OFFLINE, state.saveError)
        assertFalse(state.saved)
    }

    @Test
    fun form_edit_loadsTheIdea_thenPuts() = runBlocking {
        server.enqueue(json(200, """{"id":"7","title":"Hoi An","destination":"Quang Nam","days":2,"budgetVnd":0,"authorUid":"uid-1"}"""))
        server.enqueue(json(200, """{"id":"7","title":"Hoi An by night"}"""))
        val vm = form("7")

        val loaded = vm.uiState.await { !it.loading }
        assertEquals("Hoi An", loaded.fillFields?.title)
        assertEquals("", loaded.fillFields?.budget)
        vm.fieldsShown()
        vm.onFieldsChanged(loaded.fillFields!!.copy(title = "Hoi An by night"))
        vm.save()

        vm.uiState.await { it.saved }
        assertEquals("GET", server.takeRequest().method)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/ideas/7", put.path)
    }

    @Test
    fun form_edit_ofADeletedIdea_isNotFound() = runBlocking {
        server.enqueue(json(404, "\"Not found\""))

        val state = form("7").uiState.await { !it.loading }

        assertTrue(state.notFound)
    }

    @Test
    fun form_edit_ofAnotherAccountsIdea_isRefused_withoutAPut() = runBlocking {
        server.enqueue(json(200, """{"id":"7","title":"Hoi An","destination":"Quang Nam","days":2,"authorUid":"uid-2"}"""))
        val vm = form("7")
        val loaded = vm.uiState.await { !it.loading }
        vm.fieldsShown()
        vm.onFieldsChanged(loaded.fillFields!!)

        vm.save()

        val state = vm.uiState.await { !it.saving && it.saveError != null }
        assertEquals(ApiError.FORBIDDEN, state.saveError)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun delete_duringAReload_theOldListDoesNotBringTheRowBack() = runBlocking {
        server.enqueue(json(200, IDEAS_JSON))
        val vm = TripIdeasViewModel(repository)
        vm.uiState.await { !it.loading }
        // From now on a GET answers late with the old list (row "1" still in it), a DELETE at once.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "GET") json(200, IDEAS_JSON).setBodyDelay(500, TimeUnit.MILLISECONDS)
                else json(200, """{"id":"1","title":"x"}""")
        }

        vm.refresh()
        vm.delete("1")

        vm.uiState.await { it.message == TripIdeasMessage.Deleted }
        val state = vm.uiState.await { !it.refreshing }
        assertEquals(listOf("2"), state.ideas.map { it.idea.id })
    }
}
