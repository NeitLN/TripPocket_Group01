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
import vn.edu.vlu.trippocket.data.model.Sight
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.wikipedia.GeoSearchApi
import vn.edu.vlu.trippocket.domain.ApiError
import java.util.Locale

/**
 * Milestone 4: the Wikipedia GeoSearch on a real HTTP server (MockWebServer), checking the query
 * the repository sends and how its answer is mapped to [Sight]s.
 */
class SightRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: SightRepository
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoSearchApi::class.java)
        repository = SightRepository(api)
    }

    @After
    fun tearDown() {
        // The locale case changes the default; the next test must start from the phone's own.
        Locale.setDefault(originalLocale)
        // A test may have shut the server down already (the offline case).
        runCatching { server.shutdown() }
    }

    @Test
    fun aPageList_keepsEveryField_andSortsByDistance() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "batchcomplete": true,
                  "query": {
                    "pages": [
                      {
                        "pageid": 2,
                        "title": "Xuan Huong Lake viewpoint",
                        "description": "Viewpoint in Da Lat",
                        "thumbnail": { "source": "https://img/far.jpg" },
                        "coordinates": [ { "lat": 12.0, "lon": 108.5 } ]
                      },
                      {
                        "pageid": 1,
                        "title": "Xuan Huong Lake",
                        "coordinates": [ { "lat": 11.9405, "lon": 108.4584 } ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.nearby(11.9404, 108.4583)

        assertTrue(result is ApiResult.Success)
        val sights = (result as ApiResult.Success<List<Sight>>).data
        // Wikipedia lists pages by its own order; the nearest one comes first.
        assertEquals(listOf("Xuan Huong Lake", "Xuan Huong Lake viewpoint"), sights.map { it.title })
        val near = sights.first()
        assertEquals(1L, near.pageId)
        assertNull(near.description)
        assertNull(near.imageUrl)
        assertTrue(near.distanceMeters < 50.0)
        val far = sights.last()
        assertEquals("Viewpoint in Da Lat", far.description)
        assertEquals("https://img/far.jpg", far.imageUrl)
        assertTrue(far.distanceMeters > near.distanceMeters)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/w/api.php", request.requestUrl!!.encodedPath)
        val url = request.requestUrl!!
        assertEquals("query", url.queryParameter("action"))
        assertEquals("geosearch", url.queryParameter("generator"))
        // Pinned to Locale.US: a dot, six decimals, and the "|" Retrofit sends as %7C.
        assertEquals("11.940400|108.458300", url.queryParameter("ggscoord"))
        assertEquals("10000", url.queryParameter("ggsradius"))
        assertEquals("20", url.queryParameter("ggslimit"))
        assertEquals("pageimages|description|coordinates", url.queryParameter("prop"))
        assertEquals("thumbnail", url.queryParameter("piprop"))
        assertEquals("400", url.queryParameter("pithumbsize"))
        assertEquals("json", url.queryParameter("format"))
        assertEquals("2", url.queryParameter("formatversion"))
    }

    @Test
    fun aVietnamesePhone_stillSendsTheCoordinatesWithADot() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("vi-VN"))
        server.enqueue(MockResponse().setBody("""{"query":{"pages":[]}}"""))

        repository.nearby(11.9404, 108.4583)

        // A comma decimal separator would make Wikipedia answer with nothing.
        assertEquals("11.940400|108.458300", server.takeRequest().requestUrl!!.queryParameter("ggscoord"))
    }

    @Test
    fun noQuery_isAnEmptyList_notAnError() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"batchcomplete":true}"""))

        assertEquals(ApiResult.Success(emptyList<Sight>()), repository.nearby(11.9404, 108.4583))
    }

    @Test
    fun pagesWithoutATitle_orWithoutCoordinates_areSkipped() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "query": {
                    "pages": [
                      { "pageid": 1, "title": "No coordinates" },
                      { "pageid": 2, "coordinates": [ { "lat": 11.94, "lon": 108.46 } ] },
                      { "title": "No id", "coordinates": [ { "lat": 11.94, "lon": 108.46 } ] },
                      {
                        "pageid": 4,
                        "title": "Complete",
                        "coordinates": [ { "lat": 11.9405, "lon": 108.4584 } ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.nearby(11.9404, 108.4583) as ApiResult.Success<List<Sight>>

        assertEquals(listOf("Complete"), result.data.map { it.title })
    }

    @Test
    fun aServerError_isAnApiError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        assertEquals(ApiResult.Failure(ApiError.SERVER), repository.nearby(11.9404, 108.4583))
    }

    @Test
    fun anUnreadableAnswer_isAFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(repository.nearby(11.9404, 108.4583) is ApiResult.Failure)

        // A field of the wrong shape is an answer the app cannot read either.
        server.enqueue(MockResponse().setBody("""{"query":"not an object"}"""))
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), repository.nearby(11.9404, 108.4583))
    }

    @Test
    fun anOfflinePhone_isAnOfflineError() = runBlocking {
        server.shutdown()

        assertEquals(ApiResult.Failure(ApiError.OFFLINE), repository.nearby(11.9404, 108.4583))
    }
}
