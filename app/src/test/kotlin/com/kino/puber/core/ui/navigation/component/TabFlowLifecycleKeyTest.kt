package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.ui.feature.history.component.HistoryScreen
import com.kino.puber.domain.model.TabType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class TabFlowLifecycleKeyTest {

    @Test
    fun refreshedHistoryTabNamespacesScreenLifecycleAndReusesTheLogicalNavigatorSlot() {
        val historyScreen = HistoryScreen()
        val historyKey = historyScreen.key
        val initialTab = PuberTab(
            screen = historyScreen,
            tag = TabType.History,
        )
        val refreshedTab = PuberTab(
            screen = HistoryScreen(),
            tag = TabType.History,
            instanceKey = "refresh_2",
        )

        assertEquals(initialTab.key, refreshedTab.key)
        assertNotEquals(initialTab.contentInstanceKey, refreshedTab.contentInstanceKey)
        assertNotEquals(
            tabRootScreenKey(initialTab.contentInstanceKey, historyKey),
            tabRootScreenKey(refreshedTab.contentInstanceKey, historyKey),
        )
        assertEquals(
            tabFlowNavigatorKey(initialTab.navigationSlotKey),
            tabFlowNavigatorKey(refreshedTab.navigationSlotKey),
        )
        assertEquals(
            "TabRoot:${refreshedTab.contentInstanceKey}:$historyKey",
            tabRootScreenKey(refreshedTab.contentInstanceKey, historyKey),
        )
    }

}
