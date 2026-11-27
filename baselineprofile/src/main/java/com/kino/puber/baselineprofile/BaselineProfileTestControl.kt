package com.kino.puber.baselineprofile

import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.tls.HeldCertificate

/**
 * Coordinates the generator process (mock backend, journey) with the target application's
 * instrumentation-only control receiver (cache reset, egress journal).
 *
 * Every call is made from the generator process, outside the target's own work: the target is
 * killed between iterations while this control plane and the mock stay alive.
 */
internal object BaselineProfileTestControl {

    fun createBackend(arguments: Bundle): BaselineMockBackend {
        val port = arguments.getString(ARG_MOCK_PORT)
            ?.toIntOrNull()
            ?: error("$ARG_MOCK_PORT instrumentation argument is required")
        val h2Port = arguments.getString(ARG_MOCK_H2_PORT)
            ?.toIntOrNull()
            ?: error("$ARG_MOCK_H2_PORT instrumentation argument is required")
        val context = InstrumentationRegistry.getInstrumentation().context
        val leaf = HeldCertificate.decode(
            context.assets.open(LEAF_ASSET).bufferedReader().use { it.readText() }
        )
        return BaselineMockBackend(
            port = port,
            h2Port = h2Port,
            fixtures = BaselineFixtures.from(context, "https://127.0.0.1:$port"),
            leaf = leaf,
        )
    }

    /** Empties the target's egress journal; once per test, so a late request is never lost. */
    fun resetJournal(targetPackageName: String) {
        val result = broadcast(targetPackageName, ACTION_RESET_JOURNAL)
        check(result.contains("result=-1")) { "Failed to reset the target egress journal: $result" }
    }

    /** Drops the target's databases and caches so the next iteration requests everything again. */
    fun clearTarget(targetPackageName: String) {
        val result = broadcast(targetPackageName, ACTION_CLEAR)
        check(result.contains("result=-1")) { "Failed to clear the target caches: $result" }
    }

    /**
     * Waits for the iteration's required routes and for the mock to go quiet, then fails unless the
     * target's egress journal is empty, no unknown route was hit and every required route was.
     */
    fun checkIteration(targetPackageName: String, backend: BaselineMockBackend) {
        awaitRequiredRoutes(backend)
        awaitQuiescence(backend)
        val journal = broadcast(targetPackageName, ACTION_VERIFY)
        val verification = backend.verify()
        Log.i(
            LOG_TAG,
            "iteration scenario=${verification.scenario} matched=${verification.matchedRoutes} " +
                "missing=${verification.missingRequiredRoutes} unknown=${verification.unknownRequests} " +
                "journal=${journal.trim()}"
        )
        JourneyCheck.check(journal, verification)
    }

    /**
     * Run between tests and after the last one: lets the mock go quiet, then fails if anything was
     * requested that no journey accounts for (unknown route, egress journal line).
     */
    fun checkLate(targetPackageName: String, backend: BaselineMockBackend) {
        awaitQuiescence(backend)
        val journal = broadcast(targetPackageName, ACTION_VERIFY)
        val verification = backend.verify()
        Log.i(
            LOG_TAG,
            "late after=${verification.scenario} unknown=${verification.unknownRequests} " +
                "journal=${journal.trim()}"
        )
        JourneyCheck.checkLate(journal, verification)
    }

    private fun awaitRequiredRoutes(backend: BaselineMockBackend) {
        val deadline = System.nanoTime() + REQUIRED_ROUTES_TIMEOUT_NS
        while (System.nanoTime() < deadline) {
            val verification = backend.verify()
            if (verification.missingRequiredRoutes.isEmpty() || verification.unknownRequests.isNotEmpty()) return
            Thread.sleep(POLL_MS)
        }
    }

    private fun awaitQuiescence(backend: BaselineMockBackend) {
        var previous = backend.requestCount
        var stableSince = System.nanoTime()
        val deadline = System.nanoTime() + QUIESCENCE_TIMEOUT_NS
        while (System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
            val current = backend.requestCount
            val now = System.nanoTime()
            if (current != previous) {
                previous = current
                stableSince = now
            } else if (now - stableSince >= QUIESCENCE_STABLE_NS) {
                return
            }
        }
        error("Target requests did not quiesce within ${QUIESCENCE_TIMEOUT_NS / NANOS_PER_MILLI}ms")
    }

    private fun broadcast(targetPackageName: String, action: String): String =
        try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand(
                    "am broadcast --receiver-foreground --include-stopped-packages " +
                        "-n $targetPackageName/$CONTROL_RECEIVER -a $action"
                )
        } catch (error: java.io.IOException) {
            throw IllegalStateException("Failed to call the target control receiver", error)
        }

    private const val LOG_TAG = "PuberGen"
    private const val ARG_MOCK_PORT = "baselineMockPort"
    private const val ARG_MOCK_H2_PORT = "baselineMockH2Port"
    private const val LEAF_ASSET = "baseline/tls/leaf-test-only.pem"
    private const val CONTROL_RECEIVER = "com.kino.puber.profile.BaselineProfileControlReceiver"
    private const val ACTION_CLEAR = "com.kino.puber.profile.CLEAR"
    private const val ACTION_RESET_JOURNAL = "com.kino.puber.profile.RESET_JOURNAL"
    private const val ACTION_VERIFY = "com.kino.puber.profile.VERIFY"
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val POLL_MS = 100L
    private const val REQUIRED_ROUTES_TIMEOUT_NS = 15_000 * NANOS_PER_MILLI
    private const val QUIESCENCE_STABLE_NS = 500 * NANOS_PER_MILLI
    private const val QUIESCENCE_TIMEOUT_NS = 15_000 * NANOS_PER_MILLI
}
