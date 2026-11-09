package vn.edu.vlu.trippocket.data.remote.openmeteo

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import vn.edu.vlu.trippocket.data.repository.toWeatherError
import vn.edu.vlu.trippocket.domain.WeatherError
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * W-2 (cross-check Bảo -> Weather): a broken answer is BAD_RESPONSE, a lost connection is
 * NETWORK. Real Retrofit, OkHttp and Gson as in AppContainer; only the network is replaced by an
 * interceptor. Owner: Bảo.
 */
class ReadFirstConverterFactoryTest {

    private val json = "application/json".toMediaType()

    /** The geocoding API, answering every request with [answer]. */
    private fun api(answer: (Interceptor.Chain) -> Response): GeocodingApi = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(OkHttpClient.Builder().addInterceptor { answer(it) }.build())
        .addConverterFactory(ReadFirstConverterFactory.gson())
        .build()
        .create(GeocodingApi::class.java)

    private fun Interceptor.Chain.ok(body: ResponseBody) = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

    /** The error the Weather screen would show for this answer. */
    private fun errorFor(answer: (Interceptor.Chain) -> Response): WeatherError? = runBlocking {
        try {
            api(answer).search("Da Lat")
            null
        } catch (e: Exception) {
            e.toWeatherError()
        }
    }

    @Test
    fun aGoodAnswer_isRead() = runBlocking {
        val body = """{"results":[{"name":"Da Lat","latitude":11.94,"longitude":108.44,"country":"Vietnam"}]}"""

        val response = api { it.ok(body.toResponseBody(json)) }.search("Da Lat")

        assertEquals("Da Lat", response.results?.single()?.name)
    }

    @Test
    fun anAnswerThatIsNotJson_isABadResponse_notANetworkError() {
        assertEquals(WeatherError.BAD_RESPONSE, errorFor { it.ok("<html>Bad gateway</html>".toResponseBody(json)) })
    }

    @Test
    fun jsonCutShort_isABadResponse_notANetworkError() {
        assertEquals(WeatherError.BAD_RESPONSE, errorFor { it.ok("""{"results":[{"name":"Da""".toResponseBody(json)) })
    }

    @Test
    fun anEmptyBody_isABadResponse_notANetworkError() {
        assertEquals(WeatherError.BAD_RESPONSE, errorFor { it.ok("".toResponseBody(json)) })
    }

    @Test
    fun noConnection_isStillANetworkError() {
        assertEquals(WeatherError.NETWORK, errorFor { throw IOException("Unable to resolve host") })
        assertEquals(WeatherError.NETWORK, errorFor { throw SocketTimeoutException("timeout") })
    }

    @Test
    fun theConnectionDroppingWhileTheBodyArrives_isStillANetworkError() {
        val dropping = object : ResponseBody() {
            override fun contentType() = json
            override fun contentLength() = -1L
            override fun source(): BufferedSource {
                val start = Buffer().writeUtf8("""{"results":[{"name":"Da""")
                return object : ForwardingSource(start) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = super.read(sink, byteCount)
                        if (read == -1L) throw IOException("unexpected end of stream")
                        return read
                    }
                }.buffer()
            }
        }

        assertEquals(WeatherError.NETWORK, errorFor { it.ok(dropping) })
    }
}
