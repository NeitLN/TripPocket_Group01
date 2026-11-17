package vn.edu.vlu.trippocket.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * X1 release gate: the demo build has no destructive fallback. A database Room has no migration
 * for must stop the app opening it, never be wiped, because trips on it may not be backed up yet.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun aDatabaseWithoutAMigrationPath_isNotOpened_andItsRowsAreKept() {
        // A file from a version this build has no migration for (here: a newer one).
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE unsynced (tripId TEXT)")
            db.execSQL("INSERT INTO unsynced VALUES ('dalat')")
            db.version = AppDatabase.VERSION + 1
        }

        val room = AppDatabase.build(context, name)
        try {
            room.openHelper.writableDatabase
            fail("Room opened a database it has no migration for")
        } catch (expected: IllegalStateException) {
        } finally {
            room.close()
        }

        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM unsynced", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }
}
