package vn.edu.vlu.trippocket.data.repository

import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import vn.edu.vlu.trippocket.data.local.TestDatabase
import vn.edu.vlu.trippocket.data.local.entity.WeatherCacheEntity
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.RefreshSummary
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.model.WeatherCondition
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeForecastApi.Companion.of
import vn.edu.vlu.trippocket.data.remote.openmeteo.FakeGeocodingApi
import vn.edu.vlu.trippocket.domain.DailyWeather
import vn.edu.vlu.trippocket.domain.WeatherError
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate

/**
 * W3: the forecast cache of a saved place on Room, and the daily contract for W5.
 * Owner of the feature: Duy Anh.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherRepositoryTest {

    private val t = TestDatabase()
    private val forecastApi = FakeForecastApi()
    private var clock = 100L
    private var nextId = 0

    private val weather = WeatherRepository(
        FakeGeocodingApi(),
        forecastApi,
        t.db.weatherCacheDao(),
        t.db.savedPlaceDao(),
        now = { clock },
    )
    private val places = SavedPlaceRepository(t.db.savedPlaceDao(), t.writer) { "p${++nextId}" }

    private val vietnam = 11.94 to 108.44
    private val oregon = 45.52 to -122.98
    private val day1 = LocalDate.of(2026, 10, 31)
    private val day2 = LocalDate.of(2026, 11, 1)

    @Before
    fun setUp() = runBlocking {
        t.db.tripDao().insert(t.trip("t1"))
        t.db.tripDao().insert(t.trip("t2"))
    }

    @After
    fun tearDown() = t.close()

    private suspend fun saved(tripId: String = "t1", at: Pair<Double, Double> = vietnam): SavedPlace {
        val id = places.savePlace(tripId, Place("Dalat", "Lâm Đồng", "Vietnam", at.first, at.second))
        return places.observePlaces(tripId).first().first { it.placeId == id }
    }

    @Test
    fun refreshForecast_storesTheDaysAndTheFetchTime() = runBlocking {
        val place = saved()
        forecastApi.response = of(day1, day2)
        clock = 1_700_000_000_000L

        weather.refreshForecast(place)

        val cached = weather.observeForecast(place.placeId).first()!!
        assertEquals(listOf(day1, day2), cached.days.map { it.date })
        assertEquals(WeatherCondition.SUNNY, cached.days.first().condition)
        assertEquals(25, cached.days.first().maxTemp)
        assertEquals(1_700_000_000_000L, cached.fetchedAt)
        assertEquals(listOf(11.94 to 108.44), forecastApi.requests)
    }

    @Test
    fun aFailedRefresh_keepsThePreviousCache() = runBlocking {
        val place = saved()
        forecastApi.response = of(day1, maxTemp = 20)
        clock = 5L
        weather.refreshForecast(place)

        forecastApi.failWith = IOException("offline")
        clock = 9L
        try {
            weather.refreshForecast(place)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }

        val cached = weather.observeForecast(place.placeId).first()!!
        assertEquals(5L, cached.fetchedAt)
        assertEquals(20, cached.days.single().maxTemp)
    }

    @Test
    fun aPlaceWithoutCache_hasNoForecast() = runBlocking {
        val place = saved()

        assertNull(weather.observeForecast(place.placeId).first())
    }

    @Test
    fun brokenJson_givesNoDays_andKeepsTheFetchTime() = runBlocking {
        val place = saved()
        t.db.weatherCacheDao().upsert(WeatherCacheEntity(place.placeId, "{\"days\":", 42L))

        val cached = weather.observeForecast(place.placeId).first()!!

        assertTrue(cached.days.isEmpty())
        assertEquals(42L, cached.fetchedAt)
    }

    @Test
    fun deletingAPlace_removesItsCache() = runBlocking {
        val place = saved()
        weather.refreshForecast(place)

        places.deletePlace("t1", place.placeId)

        assertNull(weather.observeForecast(place.placeId).first())
    }

    @Test
    fun dailyWeather_usesTheForecastOfTheFirstSavedPlace() = runBlocking {
        val first = saved(at = vietnam)
        val second = saved(at = oregon)
        forecastApi.response = of(day1, maxTemp = 21)
        weather.refreshForecast(first)
        forecastApi.response = of(day1, maxTemp = 33)
        weather.refreshForecast(second)

        val daily = weather.observeDailyWeather("t1").first()

        assertTrue(daily is DailyWeather.Cached)
        assertEquals(21, (daily as DailyWeather.Cached).on(day1)?.maxTemp)
    }

    @Test
    fun dailyWeather_withoutAPlace_isNoSavedPlace() = runBlocking {
        assertEquals(DailyWeather.NoSavedPlace, weather.observeDailyWeather("t1").first())
    }

    @Test
    fun dailyWeather_beforeTheFirstRefresh_hasNoDays_andMatchesExactDatesOnly() = runBlocking {
        val place = saved()

        val empty = weather.observeDailyWeather("t1").first() as DailyWeather.Cached
        assertTrue(empty.days.isEmpty())
        assertNull(empty.fetchedAt)
        assertNull(empty.on(day1))

        forecastApi.response = of(day1, day2)
        weather.refreshForecast(place)

        val cached = weather.observeDailyWeather("t1").first() as DailyWeather.Cached
        assertEquals(day2, cached.on(day2)?.date)
        // Never falls back to another day.
        assertNull(cached.on(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun refreshTripForecasts_refreshesTheOthers_whenOnePlaceFails() = runBlocking {
        val vn = saved(at = vietnam)
        val us = saved(at = oregon)
        forecastApi.response = of(day1, maxTemp = 20)
        clock = 100L
        weather.refreshForecast(vn)
        weather.refreshForecast(us)

        clock = 1_000L + 7 * 60 * 60 * 1000
        forecastApi.response = of(day1, maxTemp = 30)
        forecastApi.failWith = IOException("offline")
        forecastApi.failFor = { request -> request == oregon }

        val summary = weather.refreshTripForecasts("t1")

        assertEquals(RefreshSummary(refreshed = 1, failed = 1), summary)
        // The place that failed keeps the cache from the last sync.
        assertEquals(30, weather.observeForecast(vn.placeId).first()!!.days.single().maxTemp)
        assertEquals(clock, weather.observeForecast(vn.placeId).first()!!.fetchedAt)
        assertEquals(20, weather.observeForecast(us.placeId).first()!!.days.single().maxTemp)
        assertEquals(100L, weather.observeForecast(us.placeId).first()!!.fetchedAt)
        // Sequential, in the order the places were saved.
        assertEquals(listOf(vietnam, oregon), forecastApi.requests.takeLast(2))
    }

    @Test
    fun refreshTripForecasts_withoutPlaces_isZeroAndZero() = runBlocking {
        val summary = weather.refreshTripForecasts("t1")

        assertEquals(RefreshSummary(refreshed = 0, failed = 0), summary)
        assertTrue(forecastApi.requests.isEmpty())
    }

    @Test
    fun aLostConnection_isANetworkError() {
        assertEquals(WeatherError.NETWORK, IOException("offline").toWeatherError())
        assertEquals(WeatherError.NETWORK, SocketTimeoutException("timeout").toWeatherError())
    }

    @Test
    fun tooManyRequests_isRateLimited() {
        assertEquals(WeatherError.RATE_LIMITED, httpError(429).toWeatherError())
    }

    @Test
    fun aServerError_isANamedKind() {
        assertEquals(WeatherError.SERVER, httpError(500).toWeatherError())
        assertEquals(WeatherError.SERVER, httpError(503).toWeatherError())
    }

    @Test
    fun anotherHttpErrorOrAnUnreadableAnswer_isABadResponse() {
        assertEquals(WeatherError.BAD_RESPONSE, httpError(400).toWeatherError())
        assertEquals(WeatherError.BAD_RESPONSE, httpError(404).toWeatherError())
        assertEquals(WeatherError.BAD_RESPONSE, JsonSyntaxException("not json").toWeatherError())
        assertEquals(WeatherError.BAD_RESPONSE, IllegalStateException("boom").toWeatherError())
    }

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody()))
}
