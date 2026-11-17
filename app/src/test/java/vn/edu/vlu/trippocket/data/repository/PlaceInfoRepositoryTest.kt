package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.edu.vlu.trippocket.data.model.PlaceInfo
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.wikipedia.PageSummaryApi
import vn.edu.vlu.trippocket.domain.ApiError
import java.util.concurrent.TimeUnit

/**
 * Milestone 4: the Wikipedia page summary on a real HTTP server (MockWebServer). Owner: Duy Anh.
 */
class PlaceInfoRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: PlaceInfoRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PageSummaryApi::class.java)
        repository = PlaceInfoRepository(api)
    }

    @After
    fun tearDown() {
        // A test may have shut the server down already (the offline case).
        runCatching { server.shutdown() }
    }

    @Test
    fun aPageWithAPhoto_returnsItsTextAndImage() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "title": "Da Lat",
                  "description": "City in Vietnam",
                  "extract": "Da Lat is the capital of Lam Dong province.",
                  "thumbnail": { "source": "https://upload.wikimedia.org/dalat.jpg" }
                }
                """.trimIndent()
            )
        )

        val result = repository.placeInfo("Da Lat")

        assertEquals(
            ApiResult.Success(
                PlaceInfo(
                    title = "Da Lat",
                    description = "City in Vietnam",
                    extract = "Da Lat is the capital of Lam Dong province.",
                    imageUrl = "https://upload.wikimedia.org/dalat.jpg",
                )
            ),
            result,
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        // The space became an underscore, as Wikipedia titles ask.
        assertEquals("/api/rest_v1/page/summary/Da_Lat", request.path)
    }

    @Test
    fun aThumbnailWithoutAnExtract_keepsThePhoto() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"title":"Da Lat","thumbnail":{"source":"https://upload.wikimedia.org/dalat.jpg"}}"""
            )
        )

        val result = repository.placeInfo("Da Lat")

        assertEquals(
            ApiResult.Success(
                PlaceInfo(
                    title = "Da Lat",
                    description = null,
                    extract = null,
                    imageUrl = "https://upload.wikimedia.org/dalat.jpg",
                )
            ),
            result,
        )
    }

    @Test
    fun aPageWithNothingToShow_isNoInfo() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"title":"Da Lat"}"""))

        assertEquals(ApiResult.Success(null), repository.placeInfo("Da Lat"))
    }

    @Test
    fun aMissingArticle_isNoInfo_notAnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":404}"""))

        // 404 reaches the repository as Failure(NOT_FOUND) and becomes "no photo, no text".
        assertEquals(ApiResult.Success(null), repository.placeInfo("Nowhere Town"))
        assertEquals("/api/rest_v1/page/summary/Nowhere_Town", server.takeRequest().path)
    }

    @Test
    fun aServerError_isAnApiError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        assertEquals(ApiResult.Failure(ApiError.SERVER), repository.placeInfo("Da Lat"))
    }

    @Test
    fun tooManyRequests_isRateLimited() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(ApiResult.Failure(ApiError.RATE_LIMITED), repository.placeInfo("Da Lat"))
    }

    @Test
    fun anUnreadableAnswer_isAFailure() = runBlocking {
        // Malformed JSON is reported as a failure, never thrown to the ViewModel.
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(repository.placeInfo("Da Lat") is ApiResult.Failure)

        // A field of the wrong shape is an answer the app cannot read either.
        server.enqueue(MockResponse().setBody("""{"title":"Da Lat","thumbnail":"not an object"}"""))
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), repository.placeInfo("Da Lat"))
    }

    @Test
    fun anOfflinePhone_isAnOfflineError() = runBlocking {
        server.shutdown()

        assertEquals(ApiResult.Failure(ApiError.OFFLINE), repository.placeInfo("Da Lat"))
    }

    @Test
    fun aBlankTitle_doesNotCallTheApi() = runBlocking {
        assertEquals(ApiResult.Success(null), repository.placeInfo("   "))
        assertNull(server.takeRequest(1, TimeUnit.MILLISECONDS))
    }
}
