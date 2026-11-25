package com.kino.puber.core.ui.uikit.component.drawer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class DrawerStateTest {

    @Test
    fun `reveal opens a closed rail`() {
        val state = DrawerState(DrawerValue.Closed)

        state.reveal()

        assertEquals(DrawerValue.Open, state.currentValue)
    }

    @Test
    fun `reveal is ignored while handing off`() {
        val state = openState()
        state.beginHandoff(HandoffKind.TabPick)

        state.reveal()

        assertEquals(DrawerValue.HandingOff, state.currentValue)
    }

    @Test
    fun `begin handoff moves an open rail to handing off`() {
        val state = openState()

        val requestId = state.beginHandoff(HandoffKind.TabPick)

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(requestId, state.pendingHandoffId)
    }

    @Test
    fun `begin handoff is refused unless the rail is open`() {
        val state = DrawerState(DrawerValue.Closed)

        assertNull(state.beginHandoff(HandoffKind.TabPick))
        assertEquals(DrawerValue.Closed, state.currentValue)
    }

    @Test
    fun `focus exit closes an open rail without a handoff`() {
        val state = openState()

        state.focusExited()

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @ParameterizedTest
    @EnumSource(HandoffKind::class)
    fun `settling the active handoff closes the rail`(kind: HandoffKind) {
        val (state, requestId) = handingOff(kind)

        state.settleHandoff(requestId)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
        assertNull(state.handoffKind)
    }

    @Test
    fun `settling a superseded handoff is ignored`() {
        val state = openState()
        val staleId = requireNotNull(state.beginHandoff(HandoffKind.TabPick))
        state.failHandoff(staleId)
        val freshId = requireNotNull(state.beginHandoff(HandoffKind.TabPick))

        state.settleHandoff(staleId)

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(freshId, state.pendingHandoffId)
    }

    @Test
    fun `a failed tab pick handoff reopens the rail`() {
        val (state, requestId) = handingOff(HandoffKind.TabPick)

        state.failHandoff(requestId)

        assertEquals(DrawerValue.Open, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `a failed tab pick handoff that must not reopen leaves the rail closed`() {
        val (state, requestId) = handingOff(HandoffKind.TabPick)

        state.failHandoff(requestId, reopen = false)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `a failed dismiss handoff leaves the rail closed even when reopening is allowed`() {
        val (state, requestId) = handingOff(HandoffKind.Dismiss)

        state.failHandoff(requestId, reopen = true)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `a failed initial handoff leaves the rail closed even when reopening is allowed`() {
        val (state, requestId) = handingOff(HandoffKind.Initial)

        state.failHandoff(requestId, reopen = true)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `a failed handoff clears its kind`() {
        val (state, requestId) = handingOff(HandoffKind.TabPick)

        state.failHandoff(requestId)

        assertNull(state.handoffKind)
    }

    @ParameterizedTest
    @EnumSource(HandoffKind::class)
    fun `only a tab pick handoff waits on new content`(kind: HandoffKind) {
        val (state, _) = handingOff(kind)

        assertEquals(kind == HandoffKind.TabPick, state.handoffExpectsNewContent)
    }

    @Test
    fun `a tab pick during the initial handoff restarts it as a tab pick with a new id`() {
        val (state, initialId) = handingOff(HandoffKind.Initial)

        val pickId = state.beginHandoff(HandoffKind.TabPick)

        assertNotNull(pickId)
        assertNotEquals(initialId, pickId)
        assertEquals(pickId, state.pendingHandoffId)
        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(HandoffKind.TabPick, state.handoffKind)
        assertTrue(state.handoffExpectsNewContent)
    }

    @Test
    fun `the superseded initial id can neither settle nor fail the tab pick`() {
        val (state, initialId) = handingOff(HandoffKind.Initial)
        val pickId = requireNotNull(state.beginHandoff(HandoffKind.TabPick))

        state.settleHandoff(initialId)
        state.failHandoff(initialId, reopen = true)

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(pickId, state.pendingHandoffId)
        assertEquals(HandoffKind.TabPick, state.handoffKind)
    }

    @Test
    fun `a tab pick during a tab pick handoff restarts it with a new id`() {
        val (state, mistakenId) = handingOff(HandoffKind.TabPick)

        val correctiveId = requireNotNull(state.beginHandoff(HandoffKind.TabPick))
        state.settleHandoff(mistakenId)

        assertNotEquals(mistakenId, correctiveId)
        assertEquals(correctiveId, state.pendingHandoffId)
        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(HandoffKind.TabPick, state.handoffKind)
    }

    @Test
    fun `a tab pick during a dismiss handoff is refused`() {
        val (state, dismissId) = handingOff(HandoffKind.Dismiss)

        assertNull(state.beginHandoff(HandoffKind.TabPick))
        assertEquals(dismissId, state.pendingHandoffId)
        assertEquals(HandoffKind.Dismiss, state.handoffKind)
    }

    @Test
    fun `a dismiss is refused unless the rail is open`() {
        assertNull(DrawerState(DrawerValue.Closed).beginHandoff(HandoffKind.Dismiss))

        for (pending in HandoffKind.entries) {
            val (state, requestId) = handingOff(pending)

            assertNull(state.beginHandoff(HandoffKind.Dismiss), "during $pending")
            assertEquals(requestId, state.pendingHandoffId)
            assertEquals(pending, state.handoffKind)
        }
    }

    @Test
    fun `an initial handoff cannot be begun as a rail handoff`() {
        val state = openState()

        assertThrows<IllegalArgumentException> { state.beginHandoff(HandoffKind.Initial) }
    }

    @Test
    fun `handing off is persisted as closed`() {
        val state = openState()
        state.beginHandoff(HandoffKind.TabPick)

        assertEquals(DrawerValue.Closed, DrawerState.persistedValue(state.currentValue))
    }

    @Test
    fun `a rail restored into handing off starts closed`() {
        val state = DrawerState(DrawerValue.HandingOff)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    private fun openState() = DrawerState(DrawerValue.Open)

    /** A rail handing off by [kind]: Initial from a closed rail, as at cold start, the rest from an open one. */
    private fun handingOff(kind: HandoffKind): Pair<DrawerState, Long> =
        if (kind == HandoffKind.Initial) {
            val state = DrawerState(DrawerValue.Closed)
            state to requireNotNull(state.beginInitialHandoff())
        } else {
            val state = openState()
            state to requireNotNull(state.beginHandoff(kind))
        }
}
