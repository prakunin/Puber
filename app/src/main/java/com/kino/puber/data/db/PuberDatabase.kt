package com.kino.puber.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WatchStateEntity::class, WatchStateSyncEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PuberDatabase : RoomDatabase() {

    abstract fun watchStateDao(): WatchStateDao

    abstract fun watchStateSyncDao(): WatchStateSyncDao

    companion object {
        private const val NAME = "puber.db"

        fun create(context: Context): PuberDatabase {
            return Room.databaseBuilder(context, PuberDatabase::class.java, NAME)
                // The whole database is a rebuildable index over server state, so throwing away a
                // schema we cannot migrate costs one sync, not user data.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
