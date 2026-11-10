package vn.edu.vlu.trippocket.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import vn.edu.vlu.trippocket.data.local.dao.ActivityDao
import vn.edu.vlu.trippocket.data.local.dao.ChecklistDao
import vn.edu.vlu.trippocket.data.local.dao.ExpenseDao
import vn.edu.vlu.trippocket.data.local.dao.SavedPlaceDao
import vn.edu.vlu.trippocket.data.local.dao.TripDao
import vn.edu.vlu.trippocket.data.local.dao.TripMemberDao
import vn.edu.vlu.trippocket.data.local.dao.WeatherCacheDao
import vn.edu.vlu.trippocket.data.local.entity.ActivityEntity
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseEntity
import vn.edu.vlu.trippocket.data.local.entity.ExpenseShareEntity
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity
import vn.edu.vlu.trippocket.data.local.entity.TripEntity
import vn.edu.vlu.trippocket.data.local.entity.TripMemberEntity
import vn.edu.vlu.trippocket.data.local.entity.WeatherCacheEntity

/**
 * TripPocket_DB (proposal 4.2). Owner: Tiến; ask before changing an entity.
 * The schema is frozen at version 1 (app/schemas). A change to it bumps [version] and adds a
 * Migration: there is no destructive fallback, so a missing migration fails at start-up
 * instead of silently wiping trips that are not backed up yet.
 */
@Database(
    entities = [
        TripEntity::class,
        TripMemberEntity::class,
        ActivityEntity::class,
        ChecklistItemEntity::class,
        SavedPlaceEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        WeatherCacheEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripMemberDao(): TripMemberDao
    abstract fun activityDao(): ActivityDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun weatherCacheDao(): WeatherCacheDao

    companion object {
        private const val NAME = "TripPocket_DB"
        const val VERSION = 1

        /** Written by one thread, read by all: @Volatile so no thread sees a half-built instance. */
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * The one database of the process. Double-checked locking: the fast path skips the lock,
         * the second check inside it stops two threads from opening TripPocket_DB twice.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /** [name] is a parameter only so a test can open its own file next to the real one. */
        internal fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    }
}
