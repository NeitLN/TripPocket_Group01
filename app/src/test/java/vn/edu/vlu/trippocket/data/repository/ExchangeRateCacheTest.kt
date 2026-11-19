package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.edu.vlu.trippocket.data.local.dao.ExchangeRateCacheDao
import vn.edu.vlu.trippocket.data.local.entity.ExchangeRateCacheEntity
import vn.edu.vlu.trippocket.data.model.ExchangeRates
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateApi
import vn.edu.vlu.trippocket.domain.ApiError
import java.util.concurrent.TimeUnit

/**
 * A1: the last good rates are saved and come back, marked fromCache, when the download fails.
 * Robolectric for org.json (the plain JVM only has android.jar's stubs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExchangeRateCacheTest {

    private class FakeCache : ExchangeRateCacheDao {
        val rows = mutableMapOf<String, ExchangeRateCacheEntity>()
        override suspend fun get(baseCurrency: String) = rows[baseCurrency]
        override suspend fun upsert(row: ExchangeRateCacheEntity) {
            rows[row.baseCurrency] = row
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var api: ExchangeRateApi
    private val cache = FakeCache()

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

    private fun enqueueRates() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    "result": "success",
                    "time_last_update_utc": "Fri, 20 Nov 2026 00:02:31 +0000",
                    "base_code": "USD",
                    "rates": { "USD": 1.0, "VND": 25000.0, "EUR": 0.8, "BRL": 5.0 }
                }
                """.trimIndent(),
            )
        )
    }

    private fun enqueueOffline() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
    }

    private fun ExchangeRates.rateOf(code: String) = items.first { it.code == code }.rateToVnd

    @Test
    fun aGoodAnswer_isReturnedFresh_andSavedWithOnlyTheRatesTheAppUses() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler), cache) { 1_000L }
        enqueueRates()

        val rates = (repo.getExchangeRates() as ApiResult.Success).data

        assertFalse(rates.fromCache)
        assertEquals(31_250.0, rates.rateOf("EUR"), 0.001)
        val row = cache.rows.getValue("USD")
        assertEquals("Fri, 20 Nov 2026 00:02:31 +0000", row.lastUpdatedUtc)
        assertEquals(1_000L, row.fetchedAt)
        assertFalse("unsupported currencies are not kept", row.ratesJson.contains("BRL"))
    }

    @Test
    fun offline_theSavedRatesComeBack_markedFromCache() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler), cache)
        enqueueRates()
        repo.getExchangeRates()
        enqueueOffline()

        val rates = (repo.getExchangeRates() as ApiResult.Success).data

        assertTrue(rates.fromCache)
        assertEquals(25_000.0, rates.rateOf("USD"), 0.001)
        assertEquals(31_250.0, rates.rateOf("EUR"), 0.001)
        assertEquals("Fri, 20 Nov 2026 00:02:31 +0000", rates.lastUpdatedUtc)
    }

    @Test
    fun aServerError_alsoFallsBackToTheSavedRates() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler), cache)
        enqueueRates()
        repo.getExchangeRates()
        server.enqueue(MockResponse().setResponseCode(503))

        assertTrue((repo.getExchangeRates() as ApiResult.Success).data.fromCache)
    }

    @Test
    fun offline_withNothingSaved_isStillTheNetworkError() = runTest {
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler), cache)
        enqueueOffline()

        val result = repo.getExchangeRates()

        assertEquals(ApiError.OFFLINE, (result as ApiResult.Failure).error)
        assertNull(cache.rows["USD"])
    }

    @Test
    fun anUnreadableSavedRow_isIgnored_andTheErrorShows() = runTest {
        cache.rows["USD"] = ExchangeRateCacheEntity("USD", "not json", "", 0L)
        val repo = ExchangeRateRepository(api, UnconfinedTestDispatcher(testScheduler), cache)
        enqueueOffline()

        assertTrue(repo.getExchangeRates() is ApiResult.Failure)
    }
}
