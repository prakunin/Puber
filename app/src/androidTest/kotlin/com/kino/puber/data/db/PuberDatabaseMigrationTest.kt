package com.kino.puber.data.db

import android.content.Context
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Version 4 deletes the legacy cache rows once. A migration that throws would not fail the app: the
 * destructive fallback in [PuberDatabase.create] would drop the watch history instead, so both the
 * migration and the production builder are exercised here.
 */
@RunWith(AndroidJUnit4::class)
class PuberDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = context.getDatabasePath(TEST_DB)

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = AndroidSQLiteDriver(),
        databaseClass = PuberDatabase::class,
        autoMigrationSpecs = listOf(LegacyPayloadCleanup(), HistoryPageSizeMigration()),
    )

    @Before
    fun setUp() {
        deleteFiles()
    }

    @After
    fun tearDown() {
        deleteFiles()
    }

    @Test
    fun migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow() = runTest {
        helper.createDatabase(3).use { connection ->
            LEGACY_KEYS.forEach { insertPayload(connection, it, "legacy", 10L) }
            insertPayload(connection, "content:v1:item:7", "item-payload", 111L)
            insertPayload(connection, "content:v1:query:home", "query-payload", 222L)
            insertWatchState(connection, itemId = 7)
            connection.execSQL(
                "INSERT INTO watch_state_sync (id, last_sync_at, history_newest_seen, " +
                    "full_history_walk_done, history_resume_page, generation, last_reconciled_at, " +
                    "history_total_items) VALUES (1, 5, 0, 1, 0, 0, NULL, NULL)",
            )
        }

        helper.runMigrationsAndValidate(4).use { connection ->
            val keys = connection.prepare("SELECT key FROM cached_payload ORDER BY key").use { st ->
                buildList { while (st.step()) add(st.getText(0)) }
            }
            assertEquals(listOf("content:v1:item:7", "content:v1:query:home"), keys)
            connection.prepare(
                "SELECT payload, updated_at FROM cached_payload WHERE key = 'content:v1:item:7'",
            ).use { st ->
                assertEquals(true, st.step())
                assertEquals("item-payload", st.getText(0))
                assertEquals(111L, st.getLong(1))
            }
            connection.prepare(
                "SELECT payload, updated_at FROM cached_payload WHERE key = 'content:v1:query:home'",
            ).use { st ->
                assertEquals(true, st.step())
                assertEquals("query-payload", st.getText(0))
                assertEquals(222L, st.getLong(1))
            }
            assertEquals(1, count(connection, "watch_state"))
            assertEquals(1, count(connection, "watch_state_sync"))
        }
    }

    @Test
    fun migrate4To5RescalesTheResumePageOfAnUnfinishedWalk() = runTest {
        helper.createDatabase(4).use { connection ->
            insertSyncRow(connection, walkDone = 0, resumePage = 26)
        }

        helper.runMigrationsAndValidate(5).use { connection ->
            connection.prepare(
                "SELECT history_resume_page, last_sync_at, history_newest_seen, generation, " +
                    "history_total_items FROM watch_state_sync WHERE id = 1",
            ).use { st ->
                assertEquals(true, st.step())
                assertEquals(11L, st.getLong(0))
                assertEquals(5L, st.getLong(1))
                assertEquals(900L, st.getLong(2))
                assertEquals(2L, st.getLong(3))
                assertEquals(777L, st.getLong(4))
            }
        }
    }

    @Test
    fun migrate4To5LeavesAFinishedWalkAlone() = runTest {
        helper.createDatabase(4).use { connection ->
            insertSyncRow(connection, walkDone = 1, resumePage = 26)
        }

        helper.runMigrationsAndValidate(5).use { connection ->
            connection.prepare("SELECT history_resume_page FROM watch_state_sync WHERE id = 1").use { st ->
                assertEquals(true, st.step())
                assertEquals(26L, st.getLong(0))
            }
        }
    }

    @Test
    fun productionBuilderOpensAV3DatabaseWithoutDroppingWatchState() = runTest {
        helper.createDatabase(3).use { connection -> insertWatchState(connection, itemId = 42) }

        val database = PuberDatabase.create(context, name = TEST_DB)
        try {
            assertNotNull(database.watchStateDao().get(42))
        } finally {
            database.close()
        }
    }

    @Test
    fun productionBuilderOpensTheFileInWalWithNormalSync() = runTest {
        val database = PuberDatabase.create(context, name = TEST_DB)
        try {
            val (mode, sync) = database.useWriterConnection { connection ->
                val mode = connection.usePrepared("PRAGMA journal_mode") { st ->
                    st.step()
                    st.getText(0)
                }
                val sync = connection.usePrepared("PRAGMA synchronous") { st ->
                    st.step()
                    st.getLong(0)
                }
                mode to sync
            }
            assertEquals("wal", mode.lowercase())
            assertEquals(SYNCHRONOUS_NORMAL, sync)
        } finally {
            database.close()
        }
    }

    @Test
    fun productionBuilderSwitchesARollbackJournalFileToWal() = runTest {
        helper.createDatabase(5).close()
        // The helper's Room pool may keep WAL connections open, so the file is rewritten on a fresh
        // connection of its own, then the header proves the file really is a rollback-journal one.
        AndroidSQLiteDriver().open(file.path).use { it.execSQL("PRAGMA journal_mode = TRUNCATE") }
        assertEquals(ROLLBACK_HEADER_VERSION to ROLLBACK_HEADER_VERSION, headerVersions())

        val database = PuberDatabase.create(context, name = TEST_DB)
        try {
            val mode = database.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA journal_mode") { st ->
                    st.step()
                    st.getText(0)
                }
            }
            assertEquals("wal", mode.lowercase())
        } finally {
            database.close()
        }

        // Header bytes 18 and 19 are the file format write and read versions: 2 means WAL.
        assertEquals(WAL_HEADER_VERSION to WAL_HEADER_VERSION, headerVersions())
    }

    private fun headerVersions(): Pair<Int, Int> = file.inputStream().use { stream ->
        stream.skip(HEADER_VERSIONS_OFFSET)
        stream.read() to stream.read()
    }

    private fun insertSyncRow(connection: SQLiteConnection, walkDone: Int, resumePage: Int) {
        connection.execSQL(
            "INSERT INTO watch_state_sync (id, last_sync_at, history_newest_seen, " +
                "full_history_walk_done, history_resume_page, generation, last_reconciled_at, " +
                "history_total_items) VALUES (1, 5, 900, $walkDone, $resumePage, 2, NULL, 777)",
        )
    }

    private fun insertPayload(connection: SQLiteConnection, key: String, payload: String, at: Long) {
        connection.execSQL(
            "INSERT INTO cached_payload (key, payload, updated_at) VALUES ('$key', '$payload', $at)",
        )
    }

    private fun insertWatchState(connection: SQLiteConnection, itemId: Int) {
        connection.execSQL(
            "INSERT INTO watch_state (item_id, is_series_like, is_fully_watched, watched_episodes, " +
                "total_episodes, progress_time, progress_duration, updated_at, history_seen_at, " +
                "is_local_pending, generation) VALUES ($itemId, 0, 0, NULL, NULL, 10, 100, 1, 1, 0, 0)",
        )
    }

    private fun count(connection: SQLiteConnection, table: String): Int =
        connection.prepare("SELECT COUNT(*) FROM $table").use { st ->
            st.step()
            st.getLong(0).toInt()
        }

    private fun deleteFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val SYNCHRONOUS_NORMAL = 1L
        const val HEADER_VERSIONS_OFFSET = 18L
        const val WAL_HEADER_VERSION = 2
        const val ROLLBACK_HEADER_VERSION = 1
        val LEGACY_KEYS = listOf(
            "home:hot", "item:7", "similar:7", "section:popular", "watchlist:subscribed", "history:1",
        )
    }
}
