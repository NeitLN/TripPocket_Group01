package vn.edu.vlu.trippocket.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H: when the forecast cache is old enough to download again. */
class CachedForecastTest {

    private val fetchedAt = 1_000_000L
    private val maxAge = 6L * 60 * 60 * 1000

    private fun cache(fetchedAt: Long = this.fetchedAt) =
        CachedForecast(placeId = "p1", days = emptyList(), fetchedAt = fetchedAt)

    @Test
    fun exactlyAtTheLimit_isStillFresh() {
        assertFalse(cache().isStale(fetchedAt + maxAge, maxAge))
    }

    @Test
    fun oneMillisecondPastTheLimit_isStale() {
        assertTrue(cache().isStale(fetchedAt + maxAge + 1, maxAge))
    }

    @Test
    fun aNewerCache_isFresh() {
        assertFalse(cache().isStale(fetchedAt + 1, maxAge))
        assertFalse(cache().isStale(fetchedAt, maxAge))
    }

    @Test
    fun aFetchTimeInTheFuture_isNotStale() {
        // The clock was set back after the download; do not loop on refreshing.
        assertFalse(cache(fetchedAt + 10_000).isStale(fetchedAt, maxAge))
    }
}
