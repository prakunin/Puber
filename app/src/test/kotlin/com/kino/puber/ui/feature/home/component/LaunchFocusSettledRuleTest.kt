package com.kino.puber.ui.feature.home.component

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LaunchFocusSettledRuleTest {
    private fun settled(
        heroSettled: Boolean = true,
        heroEmpty: Boolean = false,
        heroFocusResolved: Boolean = false,
        rowsHeldFocus: Boolean = false,
    ) = isLaunchFocusSettled(heroSettled, heroEmpty, heroFocusResolved, rowsHeldFocus)

    @Test
    fun heroThatTookFocusSettlesLaunchFocus() {
        assertTrue(settled(heroFocusResolved = true))
    }

    @Test
    fun anEmptySettledHeroSettlesLaunchFocus() {
        assertTrue(settled(heroEmpty = true))
    }

    @Test
    fun aRecreatedHomeWhoseRowsHeldFocusSettlesWithoutAHeroClaim() {
        assertTrue(settled(rowsHeldFocus = true))
    }

    @Test
    fun anUnsettledHeroNeverSettles() {
        assertFalse(settled(heroSettled = false, heroEmpty = true, heroFocusResolved = true, rowsHeldFocus = true))
    }

    @Test
    fun aPendingHeroClaimKeepsTheSplash() {
        assertFalse(settled())
    }
}
