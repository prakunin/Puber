package com.kino.puber.core.session

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SessionEventBusTest {

    private val blocked = SessionEvent.Unauthorized(SessionEndReason.Blocked)

    @Test
    fun aCollectorThatStartsAfterTheEndStillReceivesIt() = runTest {
        val bus = SessionEventBus()
        bus.emit(blocked)

        val received = withTimeoutOrNull(1.seconds) { bus.events.first() }

        assertEquals(blocked, received)
    }

    @Test
    fun twoEqualEndsReachACollectorTwice() = runTest {
        val bus = SessionEventBus()
        val received = CopyOnWriteArrayList<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect { received += it }
        }

        bus.emit(blocked)
        bus.emit(blocked)

        assertEquals(listOf<SessionEvent>(blocked, blocked), received.toList())
    }

    @Test
    fun anEndCanBeClaimedOnlyOnce() {
        val bus = SessionEventBus()
        bus.emit(blocked)

        assertTrue(bus.claimPendingEnd())
        assertFalse(bus.claimPendingEnd())
    }

    @Test
    fun aNewEndCanBeClaimedAgain() {
        val bus = SessionEventBus()
        bus.emit(blocked)
        bus.claimPendingEnd()

        bus.emit(blocked)

        assertTrue(bus.claimPendingEnd())
    }

    @Test
    fun nothingIsClaimedWithoutAnEnd() {
        assertFalse(SessionEventBus().claimPendingEnd())
    }

    @Test
    fun clearingThePendingEndStopsTheReplay() = runTest {
        val bus = SessionEventBus()
        bus.emit(blocked)

        bus.clearPendingEnd()

        assertFalse(bus.hasPendingEnd)
        assertNull(withTimeoutOrNull(1.seconds) { bus.events.first() })
    }
}
