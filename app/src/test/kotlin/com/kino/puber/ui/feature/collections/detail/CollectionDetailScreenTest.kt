package com.kino.puber.ui.feature.collections.detail

import com.kino.puber.core.ui.navigation.RootPuberScreen
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class CollectionDetailScreenTest {

    /**
     * The screen has to travel the root flow, not the tab one: only a root navigation captures the
     * caller's lazy-list anchor and only a root return replays it with the focused card, which is
     * what puts the user back on the collection they opened instead of at the top of Home.
     */
    @Test
    fun `is a root screen so returning restores the list it was opened from`() {
        val screen = CollectionDetailScreen(collectionId = 42, collectionTitle = "Collection")

        assertInstanceOf(RootPuberScreen::class.java, screen)
    }
}
