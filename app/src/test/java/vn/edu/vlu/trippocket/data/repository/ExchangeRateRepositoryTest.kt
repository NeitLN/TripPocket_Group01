package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateApi
import vn.edu.vlu.trippocket.domain.ApiError
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRateRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ExchangeRateApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun getExchangeRates_callsCorrectMethodAndPath() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(
            200,
            """
            {
                "result": "success",
                "time_last_update_utc": "Tue, 05 Nov 2026 00:02:31 +0000",
                "base_code": "USD",
                "rates": {
                    "USD": 1.0,
                    "VND": 25611.0
                }
            }
            """.trimIndent(),
        )

        repo.getExchangeRates()

        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("GET", recorded?.method)
        assertEquals("/v6/latest/USD", recorded?.path)
    }

    @Test
    fun getExchangeRates_success_mapsAllSupportedCurrencies() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(
            200,
            """
            {
                "result": "success",
                "time_last_update_utc": "Tue, 05 Nov 2026 00:02:31 +0000",
                "base_code": "USD",
                "rates": {
                    "USD": 1.0,
                    "EUR": 0.92,
                    "JPY": 152.5,
                    "KRW": 1380.0,
                    "THB": 34.2,
                    "SGD": 1.32,
                    "GBP": 0.78,
                    "AUD": 1.51,
                    "CNY": 7.15,
                    "TWD": 32.1,
                    "MYR": 4.38,
                    "IDR": 15700.0,
                    "VND": 25611.0
                }
            }
            """.trimIndent(),
        )

        val result = repo.getExchangeRates()
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("Tue, 05 Nov 2026 00:02:31 +0000", data.lastUpdatedUtc)
        assertEquals("USD", data.baseCurrency)
        assertEquals(12, data.items.size)

        val usdItem = data.items.first { it.code == "USD" }
        assertEquals(25611.0, usdItem.rateToVnd, 0.01)
        assertEquals("us", usdItem.flagCountryCode)
    }

    @Test
    fun getExchangeRates_crossRateComputation_checksWithDecimalPrecision() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(
            200,
            """
            {
                "result": "success",
                "time_last_update_utc": "Tue, 05 Nov 2026 00:02:31 +0000",
                "base_code": "USD",
                "rates": {
                    "USD": 1.0,
                    "JPY": 152.5,
                    "VND": 25611.0
                }
            }
            """.trimIndent(),
        )

        val result = repo.getExchangeRates()
        assertTrue(result is ApiResult.Success)
        val items = (result as ApiResult.Success).data.items
        val jpyItem = items.first { it.code == "JPY" }

        val expected = 25611.0 / 152.5
        assertEquals(expected, jpyItem.rateToVnd, 0.01)
    }

    @Test
    fun getExchangeRates_missingOneCurrency_dropsMissingAndKeepsOthers() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        // Response without TWD
        enqueue(
            200,
            """
            {
                "result": "success",
                "time_last_update_utc": "Tue, 05 Nov 2026 00:02:31 +0000",
                "base_code": "USD",
                "rates": {
                    "USD": 1.0,
                    "EUR": 0.92,
                    "JPY": 152.5,
                    "KRW": 1380.0,
                    "THB": 34.2,
                    "SGD": 1.32,
                    "GBP": 0.78,
                    "AUD": 1.51,
                    "CNY": 7.15,
                    "MYR": 4.38,
                    "IDR": 15700.0,
                    "VND": 25611.0
                }
            }
            """.trimIndent(),
        )

        val result = repo.getExchangeRates()
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(11, data.items.size)
        assertNull(data.items.find { it.code == "TWD" })
    }

    @Test
    fun getExchangeRates_http200WithErrorResult_isBadResponse() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        // open.er-api.com returns HTTP 200 with result="error" on bad base
        enqueue(
            200,
            """
            {
                "result": "error",
                "error-type": "unsupported-code"
            }
            """.trimIndent(),
        )

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), result)
    }

    @Test
    fun getExchangeRates_missingVnd_isBadResponse() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(
            200,
            """
            {
                "result": "success",
                "rates": {
                    "USD": 1.0,
                    "EUR": 0.92
                }
            }
            """.trimIndent(),
        )

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), result)
    }

    @Test
    fun getExchangeRates_emptyRates_isBadResponse() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(
            200,
            """
            {
                "result": "success",
                "rates": {}
            }
            """.trimIndent(),
        )

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), result)
    }

    @Test
    fun getExchangeRates_status429_isRateLimited() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(429, "Too Many Requests")

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.RATE_LIMITED), result)
    }

    @Test
    fun getExchangeRates_status500_isServer() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(500, "Internal Server Error")

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.SERVER), result)
    }

    @Test
    fun getExchangeRates_malformedJson_isBadResponse() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        enqueue(200, """{"rates": "not_an_object"}""")

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.BAD_RESPONSE), result)
    }

    @Test
    fun getExchangeRates_serverShutdown_isOffline() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler))
        server.shutdown()

        val result = repo.getExchangeRates()
        assertEquals(ApiResult.Failure(ApiError.OFFLINE), result)
    }
}
