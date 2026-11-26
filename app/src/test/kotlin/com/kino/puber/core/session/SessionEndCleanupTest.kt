package com.kino.puber.core.session

import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.ContentTarget
import com.kino.puber.core.contentlink.ContentUriCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SessionEndCleanupTest {

    private val bus = SessionEventBus()
    private val coordinator = ContentLaunchCoordinator(ContentUriCodec())
    private var linkResets = 0
    private var clears = 0
    private val gate = CompletableDeferred<Unit>()

    private fun cleanup(suspending: Boolean = false) = SessionEndCleanup(
        bus = bus,
        resetDeepLink = { linkResets++; coordinator.clearSession() },
        clears = listOf({ clears++; if (suspending) gate.await() }),
    )

    @Test
    fun aReplayedEndKeepsALinkAcceptedSinceAndClearsOnce() = runTest {
        bus.emit(SessionEvent.Unauthorized(SessionEndReason.Blocked))
        val cleanup = cleanup()
        cleanup.run()
        coordinator.accept("puber://content/items/42")

        cleanup.run()

        assertEquals(ContentTarget.Details(42), coordinator.consumeForAuthenticatedStart())
        assertEquals(1, linkResets)
        assertEquals(1, clears)
    }

    @Test
    fun aHandlerCancelledMidClearLetsTheReplayClearAgain() = runTest {
        bus.emit(SessionEvent.Unauthorized(SessionEndReason.Blocked))
        val cleanup = cleanup(suspending = true)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { cleanup.run() }
        job.cancel(CancellationException("handler left"))
        job.join()
        gate.complete(Unit)

        cleanup.run()

        assertEquals(2, clears)
        assertEquals(1, linkResets)
    }

    @Test
    fun noEndMeansNoClears() = runTest {
        cleanup().run()

        assertEquals(0, clears)
        assertNull(coordinator.consumeForAuthenticatedStart())
    }
}
