package com.kino.puber.core.ui.uikit.component.drawer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        state.beginHandoff(expectsNewContent = true)

        state.reveal()

        assertEquals(DrawerValue.HandingOff, state.currentValue)
    }

    @Test
    fun `begin handoff moves an open rail to handing off`() {
        val state = openState()

        val requestId = state.beginHandoff(expectsNewContent = true)

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(requestId, state.pendingHandoffId)
    }

    @Test
    fun `begin handoff is refused unless the rail is open`() {
        val state = DrawerState(DrawerValue.Closed)

        assertNull(state.beginHandoff(expectsNewContent = true))
        assertEquals(DrawerValue.Closed, state.currentValue)
    }

    @Test
    fun `focus exit closes an open rail without a handoff`() {
        val state = openState()

        state.focusExited()

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `settling the active handoff closes the rail`() {
        val state = openState()
        val requestId = requireNotNull(state.beginHandoff(expectsNewContent = true))

        state.settleHandoff(requestId)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `settling a superseded handoff is ignored`() {
        val state = openState()
        val staleId = requireNotNull(state.beginHandoff(expectsNewContent = true))
        state.failHandoff(staleId)
        val freshId = requireNotNull(state.beginHandoff(expectsNewContent = true))

        state.settleHandoff(staleId)

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(freshId, state.pendingHandoffId)
    }

    @Test
    fun `a failed handoff returns the rail to open`() {
        val state = openState()
        val requestId = requireNotNull(state.beginHandoff(expectsNewContent = true))

        state.failHandoff(requestId)

        assertEquals(DrawerValue.Open, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `a handoff records whether it waits on content that does not exist yet`() {
        val state = openState()

        state.beginHandoff(expectsNewContent = true)

        assertTrue(state.handoffExpectsNewContent)
    }

    @Test
    fun `dismissing the rail waits on the content already on screen`() {
        val state = openState()

        state.beginHandoff(expectsNewContent = false)

        assertFalse(state.handoffExpectsNewContent)
    }

    @Test
    fun `handing off is persisted as closed`() {
        val state = openState()
        state.beginHandoff(expectsNewContent = true)

        assertEquals(DrawerValue.Closed, DrawerState.persistedValue(state.currentValue))
    }

    @Test
    fun `a rail restored into handing off starts closed`() {
        val state = DrawerState(DrawerValue.HandingOff)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    private fun openState() = DrawerState(DrawerValue.Open)
}
