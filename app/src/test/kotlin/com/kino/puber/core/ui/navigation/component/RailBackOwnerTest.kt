package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class RailBackOwnerTest {

    @Test
    fun railBackOwner_isRailOpen_whenDrawerOpenOrHandingOff() {
        for (tabCanPop in listOf(false, true)) {
            assertEquals(RailBackOwner.RailOpen, railBackOwner(DrawerValue.Open, tabCanPop))
            assertEquals(RailBackOwner.RailOpen, railBackOwner(DrawerValue.HandingOff, tabCanPop))
        }
    }

    @Test
    fun railBackOwner_isTab_whenDrawerClosedAndTabCanPop() {
        assertEquals(RailBackOwner.Tab, railBackOwner(DrawerValue.Closed, tabCanPop = true))
    }

    @Test
    fun railBackOwner_isRailClosed_whenDrawerClosedAndTabAtRoot() {
        assertEquals(RailBackOwner.RailClosed, railBackOwner(DrawerValue.Closed, tabCanPop = false))
    }

    @Test
    fun railBackOwner_isTabOrNull_withoutRailHost() {
        assertEquals(RailBackOwner.Tab, railBackOwner(drawer = null, tabCanPop = true))
        assertNull(railBackOwner(drawer = null, tabCanPop = false))
    }
}
