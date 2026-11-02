package vn.edu.vlu.trippocket.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** AppDatabase.getInstance: one TripPocket_DB per process, even when threads race to open it. */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseSingletonTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun getInstance_returnsTheSameDatabaseEveryTime() {
        assertSame(AppDatabase.getInstance(context), AppDatabase.getInstance(context))
    }

    @Test
    fun getInstance_racingThreadsShareOneDatabase() {
        val threads = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val results = (1..threads).map {
            pool.submit<AppDatabase> {
                start.await()
                AppDatabase.getInstance(context)
            }
        }
        start.countDown()
        val instances = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, instances.distinctBy { System.identityHashCode(it) }.size)
    }
}
