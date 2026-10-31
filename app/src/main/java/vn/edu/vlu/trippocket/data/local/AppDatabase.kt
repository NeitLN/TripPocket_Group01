package vn.edu.vlu.trippocket.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import vn.edu.vlu.trippocket.BuildConfig
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
 * Every schema change bumps [version]. The destructive fallback is for debug builds
 * with throw-away data only; the schema is frozen before demo data is created.
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
    version = 1,
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
                .build()
    }
}
