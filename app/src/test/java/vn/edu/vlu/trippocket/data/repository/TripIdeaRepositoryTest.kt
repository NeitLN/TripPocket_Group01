package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.edu.vlu.trippocket.data.model.TripIdeaInput
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.mockapi.TripIdeaApi
import vn.edu.vlu.trippocket.domain.ApiError
import java.time.Instant

/**
 * Milestone 4: [TripIdeaRepository] against MockWebServer, checking the HTTP method, path and
 * JSON body of every call (GET, POST, PUT, DELETE) and how each status reaches the ViewModel.
 */
class TripIdeaRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TripIdeaRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = tripIdeaApiOn(server)
        repository = TripIdeaRepository(api, signedInAs("uid-1", "tien"), now = { Instant.parse("2026-11-09T08:00:00Z") })
    }

    @After
    fun tearDown() = server.shutdown()

    private val input = TripIdeaInput(
        title = "3 days in Da Lat",
        destination = "Da Lat",
        days = 3,
        budgetVnd = 4_500_000,
        coverUrl = "https://example.com/dalat.jpg",
        summary = "Pine hills",
    )

    @Test
    fun ideas_getsNewestFirst_andDropsRowsWithoutTitle() = runBlocking {
        server.enqueue(json(200, IDEAS_JSON))

        val result = repository.ideas()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/ideas?sortBy=createdAt&order=desc", request.path)
        val ideas = (result as ApiResult.Success).data
        assertEquals(listOf("1", "2"), ideas.map { it.id })
        assertEquals("https://example.com/dalat.jpg", ideas[0].coverUrl)
        assertEquals(null, ideas[1].coverUrl)
        assertEquals("uid-1", ideas[0].authorUid)
    }

    @Test
    fun share_postsTheFormAsTheSignedInAccount() = runBlocking {
        server.enqueue(json(201, """{"id":"9","title":"3 days in Da Lat","days":3,"authorUid":"uid-1"}"""))

        val result = repository.share(input)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/ideas", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"title\":\"3 days in Da Lat\""))
        assertTrue(body, body.contains("\"budgetVnd\":4500000"))
        assertTrue(body, body.contains("\"author\":\"tien\""))
        assertTrue(body, body.contains("\"authorUid\":\"uid-1\""))
        assertTrue(body, body.contains("\"createdAt\":\"2026-11-09T08:00:00Z\""))
        assertEquals("9", (result as ApiResult.Success).data.id)
    }

    @Test
    fun update_putsToTheIdea() = runBlocking {
        server.enqueue(json(200, """{"id":"7","title":"3 days in Da Lat","authorUid":"uid-1"}"""))

        val result = repository.update("7", input)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/ideas/7", request.path)
        // An edit keeps the original date: createdAt is not sent.
        assertTrue(!request.body.readUtf8().contains("createdAt"))
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun update_ofADeletedIdea_isNotFound() = runBlocking {
        server.enqueue(json(404, "\"Not found\""))
        assertEquals(ApiResult.Failure(ApiError.NOT_FOUND), repository.update("7", input))
    }

    @Test
    fun idea_getsOne_and404IsNotFound() = runBlocking {
        server.enqueue(json(200, """{"id":"7","title":"Hoi An","days":2}"""))
        assertEquals("Hoi An", (repository.idea("7") as ApiResult.Success).data.title)
        assertEquals("/ideas/7", server.takeRequest().path)

        server.enqueue(json(404, "\"Not found\""))
        assertEquals(ApiResult.Failure(ApiError.NOT_FOUND), repository.idea("8"))
    }

    @Test
    fun delete_sendsDelete_andAnAlreadyDeletedIdeaCountsAsDone() = runBlocking {
        server.enqueue(json(200, """{"id":"7","title":"x"}"""))
        assertEquals(ApiResult.Success(Unit), repository.delete("7"))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/ideas/7", request.path)

        server.enqueue(json(404, "\"Not found\""))
        assertEquals(ApiResult.Success(Unit), repository.delete("7"))
    }

    @Test
    fun delete_serverError_isReported() = runBlocking {
        server.enqueue(json(500, "{}"))
        assertEquals(ApiResult.Failure(ApiError.SERVER), repository.delete("7"))
    }

    @Test
    fun badRequest_isReported() = runBlocking {
        server.enqueue(json(400, """{"msg":"bad"}"""))
        assertEquals(ApiResult.Failure(ApiError.BAD_REQUEST), repository.share(input))
    }

    companion object {
        val IDEAS_JSON = """
            [
              {"id":"1","createdAt":"2026-11-09T08:00:00.000Z","title":"3 days in Da Lat","destination":"Da Lat",
               "days":3,"budgetVnd":4500000,"coverUrl":"https://example.com/dalat.jpg","summary":"Pine hills",
               "author":"tien","authorUid":"uid-1"},
              {"id":"2","title":"Hoi An by bike","destination":"Hoi An","days":2,"coverUrl":"","author":"bao","authorUid":"uid-2"},
              {"id":"3","title":"  ","destination":"Nowhere"}
            ]
        """.trimIndent()

        fun json(code: Int, body: String) = MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

        fun tripIdeaApiOn(server: MockWebServer): TripIdeaApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TripIdeaApi::class.java)

        /** A signed-in account with a display name, for the author fields. */
        fun signedInAs(uid: String, name: String): AuthRepository =
            object : AuthRepository by FakeAuthRepository(uid) {
                override val displayName: String = name
            }
    }
}
