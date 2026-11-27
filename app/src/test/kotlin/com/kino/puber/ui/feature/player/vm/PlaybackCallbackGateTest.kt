package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlaybackCallbackGateTest {

    private val gate = PlaybackCallbackGate()
    private val reported = mutableListOf<String>()
    private var reprepares = 0

    private fun route(session: PlaybackCallbackGate.Session) =
        gate.routeError(session, reprepare = { reprepares++ }, report = { reported += it })

    @Test
    fun dispatch_runsActionWithSessionUrl_whenSessionIsCurrent() {
        val session = gate.beginSession("A", fromSwitch = false)
        val urls = mutableListOf<String>()

        gate.dispatch(session) { urls += it }

        assertEquals(listOf("A"), urls)
    }

    @Test
    fun dispatch_skipsAction_whenNewerSessionBegan() {
        val old = gate.beginSession("A", fromSwitch = false)
        gate.beginSession("B", fromSwitch = true)
        var ran = false

        gate.dispatch(old) { ran = true }

        assertTrue(!ran)
    }

    @Test
    fun dispatch_skipsAction_afterInvalidate() {
        val session = gate.beginSession("A", fromSwitch = false)
        gate.invalidate()
        var ran = false

        gate.dispatch(session) { ran = true }

        assertTrue(!ran)
    }

    @Test
    fun routeError_reportsNothingAndRepreparesOnce_whenSwitchSessionHasNotLoadedItsUrl() {
        val session = gate.beginSession("B", fromSwitch = true)

        route(session)

        assertEquals(1, reprepares)
        assertEquals(emptyList<String>(), reported)
    }

    @Test
    fun routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry() {
        val session = gate.beginSession("B", fromSwitch = true)

        route(session)
        route(session)

        assertEquals(1, reprepares)
        assertEquals(listOf("B"), reported)
    }

    @Test
    fun routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives() {
        val session = gate.beginSession("B", fromSwitch = true)
        gate.onLoadStarted("A")

        route(session)

        assertEquals(1, reprepares)
        assertEquals(emptyList<String>(), reported)
    }

    @Test
    fun routeError_reportsWithSessionUrl_whenSwitchSessionLoadedItsUrl() {
        val session = gate.beginSession("B", fromSwitch = true)
        gate.onLoadStarted("B")

        route(session)

        assertEquals(0, reprepares)
        assertEquals(listOf("B"), reported)
    }

    @Test
    fun routeError_reportsFirstError_whenSessionComesFromPrepare() {
        val session = gate.beginSession("A", fromSwitch = false)

        route(session)

        assertEquals(0, reprepares)
        assertEquals(listOf("A"), reported)
    }

    @Test
    fun routeError_doesNothing_whenSessionIsNotCurrent() {
        val old = gate.beginSession("A", fromSwitch = true)
        val current = gate.beginSession("B", fromSwitch = true)

        route(old)
        gate.invalidate()
        route(current)

        assertEquals(0, reprepares)
        assertEquals(emptyList<String>(), reported)
    }

    @Test
    fun routeError_allowsOneRetryPerSession_whenAnotherSwitchBegins() {
        route(gate.beginSession("B", fromSwitch = true))
        route(gate.beginSession("C", fromSwitch = true))

        assertEquals(2, reprepares)
        assertEquals(emptyList<String>(), reported)
    }
}
