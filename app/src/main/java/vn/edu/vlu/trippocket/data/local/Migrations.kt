package vn.edu.vlu.trippocket.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations of TripPocket_DB. Each one is checked by MigrationTest against the schema
 * Room exported to app/schemas, so the SQL here must match those files exactly.
 */
object Migrations {

    /** v1 -> v2 (A1): nothing is removed or rewritten, so every trip, expense and activity stays. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Network caches, never backed up (NetworkCacheEntities.kt).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trip_idea_cache` (`id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `destination` TEXT NOT NULL, `days` INTEGER NOT NULL, " +
                    "`budgetVnd` INTEGER NOT NULL, `coverUrl` TEXT, `summary` TEXT NOT NULL, `author` TEXT NOT NULL, " +
                    "`authorUid` TEXT, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exchange_rate_cache` (`baseCurrency` TEXT NOT NULL, " +
                    "`ratesJson` TEXT NOT NULL, `lastUpdatedUtc` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`baseCurrency`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `place_info_cache` (`page` TEXT NOT NULL, `title` TEXT, " +
                    "`description` TEXT, `extract` TEXT, `imageUrl` TEXT, `fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`page`))"
            )
            // "Paid in": nullable, so every v1 expense simply has no original currency.
            db.execSQL("ALTER TABLE `expense` ADD COLUMN `originalCurrency` TEXT")
            db.execSQL("ALTER TABLE `expense` ADD COLUMN `originalAmount` REAL")
            db.execSQL("ALTER TABLE `expense` ADD COLUMN `rateToVnd` REAL")
            // One day's activities in order; the new index also covers the tripId foreign key.
            db.execSQL("DROP INDEX IF EXISTS `index_activity_tripId`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_activity_tripId_dayIndex_orderIndex` " +
                    "ON `activity` (`tripId`, `dayIndex`, `orderIndex`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
