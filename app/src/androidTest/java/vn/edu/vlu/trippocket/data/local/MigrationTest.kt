package vn.edu.vlu.trippocket.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A1: a phone that ran v1.1.1 (schema 1) gets v2 without losing anything. The v1 database is
 * built from app/schemas/1.json, filled with SQL as v1.1.1 wrote it, migrated, then checked
 * against 2.json (runMigrationsAndValidate) and opened by the real AppDatabase.
 * Instrumented on purpose: MigrationTestHelper reads the schemas from the test APK's assets,
 * which the Room plugin fills for androidTest only (CI job ui-tests runs it).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    /** One trip as v1.1.1 stored it: a member, an activity, an expense with its share. */
    private fun createV1() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO trip (tripId, ownerUid, name, destination, startDate, endDate, syncStatus, " +
                    "localRevision, isDeleted, updatedAt) VALUES ('t1', 'uid-a', 'Đà Lạt', 'Lâm Đồng', " +
                    "20500, 20502, 'SYNCED', 7, 0, 1700000000000)"
            )
            db.execSQL("INSERT INTO trip_member (memberId, tripId, name, orderIndex) VALUES ('m1', 't1', 'An', 0)")
            db.execSQL(
                "INSERT INTO activity (activityId, tripId, dayIndex, title, place, startTime, estimatedCost, " +
                    "note, orderIndex, reminderMinutesBefore) VALUES ('a1', 't1', 0, 'Chợ đêm', 'Đà Lạt', " +
                    "1170, 150000, '', 0, 30)"
            )
            db.execSQL(
                "INSERT INTO expense (expenseId, tripId, title, amount, payerId, category, splitType, spentAt, " +
                    "createdAt) VALUES ('e1', 't1', 'Lẩu gà', 300001, 'm1', 'FOOD', 'EQUAL', 20500, 1700000000000)"
            )
            db.execSQL("INSERT INTO expense_share (expenseId, memberId, shareAmount) VALUES ('e1', 'm1', 300001)")
        }
    }

    @Test
    fun v1Rows_surviveTheMigration_andTheSchemaMatches2Json() {
        createV1()

        // validateDroppedTables = true: the result must equal 2.json, nothing more, nothing less.
        helper.runMigrationsAndValidate(DB, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT name, localRevision, syncStatus FROM trip WHERE tripId = 't1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Đà Lạt", it.getString(0))
                assertEquals(7, it.getInt(1)) // not marked changed: a migration is not a user edit
                assertEquals("SYNCED", it.getString(2))
            }
            db.query("SELECT amount, originalCurrency, originalAmount, rateToVnd FROM expense").use {
                assertTrue(it.moveToFirst())
                assertEquals(300001L, it.getLong(0))
                assertTrue(it.isNull(1) && it.isNull(2) && it.isNull(3))
            }
            db.query("SELECT title, reminderMinutesBefore FROM activity").use {
                assertTrue(it.moveToFirst())
                assertEquals("Chợ đêm", it.getString(0))
                assertEquals(30, it.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM trip_idea_cache").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun theOldActivityIndex_isReplacedByTheCompositeOne() {
        createV1()

        helper.runMigrationsAndValidate(DB, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'activity'").use {
                val names = buildList { while (it.moveToNext()) add(it.getString(0)) }
                assertTrue(names.toString(), "index_activity_tripId_dayIndex_orderIndex" in names)
                assertFalse(names.toString(), "index_activity_tripId" in names)
            }
        }
    }

    @Test
    fun theAppDatabase_opensTheMigratedFile_andReadsTheTrip() = runBlocking {
        createV1()

        // What the app itself does on the first start after the update.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Đà Lạt", db.tripDao().get("t1", "uid-a")?.name)
            assertEquals(listOf("Chợ đêm"), db.activityDao().dayRows("t1", 0).map { it.title })

            val expense = db.expenseDao().getWithShares("e1")!!
            assertEquals(300001L, expense.expense.amount)
            assertNull(expense.expense.originalCurrency)
            assertEquals(listOf(300001L), expense.shares.map { it.shareAmount })
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
