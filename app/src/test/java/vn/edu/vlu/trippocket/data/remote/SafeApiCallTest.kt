package vn.edu.vlu.trippocket.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import vn.edu.vlu.trippocket.domain.ApiError
import java.util.concurrent.TimeUnit

/**
 * Milestone 4: [safeApiCall] against a real HTTP server on localhost (MockWebServer), so the
 * status codes, the timeout and the broken JSON go through the same Retrofit + OkHttp + Gson
 * stack as the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SafeApiCallTest {

    private data class Item(val id: String, val title: String)

    private interface ItemApi {
        @GET("items")
        suspend fun items(): List<Item>
    }

    private lateinit var server: MockWebServer
    private lateinit var api: ItemApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItemApi::class.java)
    }

    @After
    fun tearDown() {
        // serverGone_isOffline already shut it down.
        runCatching { server.shutdown() }
    }

    private fun call() = runTest {
        // A test dispatcher instead of Dispatchers.IO; the real IO dispatcher is the default.
        lastResult = safeApiCall(UnconfinedTestDispatcher(testScheduler)) { api.items() }
    }

    private var lastResult: ApiResult<List<Item>>? = null

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun ok_isSuccessWithTheParsedList() {
        enqueue(200, """[{"id":"1","title":"Da Lat"},{"id":"2","title":"Hoi An"}]""")
        call()
        assertEquals(
            ApiResult.Success(listOf(Item("1", "Da Lat"), Item("2", "Hoi An"))),
            lastResult,
        )
    }

    @Test
    fun status400_isBadRequest() {
        enqueue(400, """{"error":"bad"}""")
        call()
        assertEquals(ApiResult.Failure(ApiError.BAD_REQUEST), lastResult)
    }

    @Test
    fun status404_isNotFound() {
        enqueue(404, "Not found")
        call()
        assertEquals(ApiResult.Failure(ApiError.NOT_FOUND), lastResult)
    }

    @Test
    fun status429_isRateLimited() {
        enqueue(429)
        call()
        assertEquals(ApiResult.Failure(ApiError.RATE_LIMITED), lastResult)
    }

    @Test
    fun status500and503_areServer() {
        enqueue(500)
        call()
        assertEquals(ApiResult.Failure(ApiError.SERVER), lastResult)
        enqueue(503)
        call()
        assertEquals(ApiResult.Failure(ApiError.SERVER), lastResult)
    }

    @Test
    fun otherStatus_isBadResponse() {
        enqueue(418)
        call()
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), lastResult)
    }

    @Test
    fun brokenJson_isBadResponse() {
        enqueue(200, """{"not":"a list"}""")
        call()
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), lastResult)
    }

    @Test
    fun noAnswerWithinTheReadTimeout_isTimeout() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        call()
        assertEquals(ApiResult.Failure(ApiError.TIMEOUT), lastResult)
    }

    @Test
    fun serverGone_isOffline() {
        // Same as airplane mode for OkHttp: the connection cannot be opened at all.
        server.shutdown()
        call()
        assertEquals(ApiResult.Failure(ApiError.OFFLINE), lastResult)
    }
}
