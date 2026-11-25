package com.kino.puber.core.ui.navigation.component

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TabStackStateTest {

    private val state = TabStackState()

    @Test
    fun canPop_isFalse_whenNothingAttached() {
        assertFalse(state.canPop)
    }

    @Test
    fun canPop_followsAttachedProvider() {
        var providerCanPop = false
        state.attach(owner = "tab") { providerCanPop }
        assertFalse(state.canPop)

        providerCanPop = true
        assertTrue(state.canPop)
    }

    @Test
    fun detach_byPreviousOwner_keepsNewAttachment() {
        state.attach(owner = "first") { true }
        state.attach(owner = "second") { true }

        state.detach(owner = "first")
        assertTrue(state.canPop)

        state.detach(owner = "second")
        assertFalse(state.canPop)
    }
}
