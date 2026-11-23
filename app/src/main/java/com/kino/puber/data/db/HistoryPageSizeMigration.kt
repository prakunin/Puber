package com.kino.puber.data.db

import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Rescales the resume page of an unfinished history walk from 20 to 50 entries a page, once, when
 * the database moves from version 4 to 5. The numbers are literals on purpose: a later edit of the
 * page size must not change what this migration computes.
 */
class HistoryPageSizeMigration : AutoMigrationSpec {

    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE watch_state_sync SET history_resume_page = ((history_resume_page - 1) * 20) / 50 + 1 " +
                "WHERE full_history_walk_done = 0",
        )
    }
}
