package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeSharedPreferences
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import com.kino.puber.util.ManualTelemetryExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

internal class SessionLogControllerTest {
    @TempDir
    lateinit var root: File

    private val clock = FakeTelemetryClock(elapsed = 0)
    private val executor = ManualTelemetryExecutor()
    private val handlerBefore = Thread.getDefaultUncaughtExceptionHandler()

    @AfterEach
    fun tearDown() {
        Timber.forest().filterIsInstance<TelemetryErrorTree>().forEach(Timber::uproot)
        Thread.setDefaultUncaughtExceptionHandler(handlerBefore)
        Telemetry.resetForTests()
    }

    private lateinit var sink: TelemetrySink

    private fun runtime(store: TelemetryStore): TelemetryRuntime {
        sink = TelemetrySink(clock, store, executor, { testSessionInfo }, sid = "deadbeef")
        return TelemetryRuntime(FakeSharedPreferences().sharedPreferences, clock, sink)
    }

    private suspend fun CoroutineScope.share(runtime: TelemetryRuntime): ShareResult {
        val result = async(start = CoroutineStart.UNDISPATCHED) { runtime.share() }
        executor.runAll()
        return result.await()
    }

    private fun dir() = File(root, "telemetry")

    @Test
    fun shareRotatesAndZipsOnlyClosedFiles() = runTest {
        val runtime = runtime(FileTelemetryStore { root })
        runtime.setEnabled(true)
        executor.runAll()
        sink.record(TelemetryEvent.Nav(1, NavCmd.PUSH, null, null))
        executor.runRepeating()

        val result = share(runtime) as ShareResult.Ready

        val jsonl = dir().listFiles().orEmpty().filter { it.name.endsWith(".jsonl") }.map { it.name }.sorted()
        assertTrue(Regex("""puber-telemetry-deadbeef-\d{8}-\d{4}\.zip""").matches(result.archive.name))
        ZipFile(result.archive).use { zip ->
            assertEquals(jsonl, zip.entries().toList().map { it.name }.sorted())
        }
        assertTrue(dir().listFiles().orEmpty().none { it.name.endsWith(".tmp") })

        // The next line goes to a new file that begins with a rotate marker.
        sink.record(TelemetryEvent.Nav(2, NavCmd.BACK, null, null))
        executor.runRepeating()
        val second = dir().listFiles().orEmpty().single { it.name.endsWith("-02.jsonl") }
        assertEquals("rotate", parseLine(second.readLines().first()).str("ev"))
    }

    @Test
    fun shareReplacesPreviousArchive() = runTest {
        val runtime = runtime(FileTelemetryStore { root })
        runtime.setEnabled(true)
        executor.runAll()
        executor.runRepeating()

        val first = share(runtime) as ShareResult.Ready
        clock.advance(120_000)
        val second = share(runtime) as ShareResult.Ready

        assertFalse(first.archive.exists())
        assertEquals(1, dir().listFiles().orEmpty().count { it.name.endsWith(".zip") })
        assertTrue(second.archive.exists())
    }

    @Test
    fun shareWithNoClosedFilesReturnsNothingToShare() = runTest {
        val runtime = runtime(FileTelemetryStore { root })
        assertEquals(ShareResult.NothingToShare, share(runtime))
        assertFalse(dir().exists())
    }

    @Test
    fun shareIoFailureReturnsFailedAndLeavesNoPartialZip() = runTest {
        val store = FakeTelemetryStore()
        val runtime = runtime(store)
        runtime.setEnabled(true)
        executor.runAll()
        store.throwOnZip = true

        assertEquals(ShareResult.Failed, share(runtime))
        assertTrue(store.files.keys.none { it.endsWith(".zip") || it.endsWith(".tmp") })
    }
}
