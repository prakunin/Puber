package com.kino.puber.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [WatchStateEntity::class, WatchStateSyncEntity::class, CachedPayloadEntity::class],
    version = 5,
    exportSchema = true,
    // Adding a column is a migration Room writes itself, and this one is worth writing: dropping
    // the tables would cost a walk of the whole history to get back what is already there.
    // 3 -> 4 adds the updated_at index the cache sweep orders by and deletes the legacy cache rows once.
    // 4 -> 5 changes no schema: it rescales the resume page of an unfinished walk to 50 entries a page.
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = LegacyPayloadCleanup::class),
        AutoMigration(from = 4, to = 5, spec = HistoryPageSizeMigration::class),
    ],
)
abstract class PuberDatabase : RoomDatabase() {

    abstract fun watchStateDao(): WatchStateDao

    abstract fun watchStateSyncDao(): WatchStateSyncDao

    abstract fun cachedPayloadDao(): CachedPayloadDao

    companion object {
        private const val NAME = "puber.db"

        fun create(context: Context, name: String = NAME): PuberDatabase {
            return Room.databaseBuilder(context, PuberDatabase::class.java, name)
                // Room 3 requires an explicit driver. The platform one keeps the on-device SQLite
                // this database has always run on, so the migration changes no query behaviour.
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                // The whole database is a rebuildable index over server state, so throwing away a
                // schema we cannot migrate costs one sync, not user data.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
