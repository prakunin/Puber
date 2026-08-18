package com.kino.puber.core.contentlink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ContentLaunchCoordinatorTest {

    private val coordinator = ContentLaunchCoordinator(ContentUriCodec())

    @Test
    fun authenticatedStart_consumesPendingTargetExactlyOnce() {
        assertTrue(coordinator.accept("puber://content/items/42"))

        assertEquals(ContentTarget.Details(42), coordinator.consumeForAuthenticatedStart())
        assertNull(coordinator.consumeForWarmRouting())
    }

    @Test
    fun signedOutStart_retainsTargetUntilAuthenticationSucceeds() {
        assertTrue(coordinator.accept("puber://content/items/42/seasons/2/episodes/7"))

        coordinator.waitForAuthentication()

        assertNull(coordinator.consumeForWarmRouting())
        assertEquals(
            ContentTarget.EpisodeDetails(42, 2, 7),
            coordinator.consumeAfterAuthentication(),
        )
        assertNull(coordinator.consumeAfterAuthentication())
    }

    @Test
    fun warmTarget_isAvailableOnlyAfterNavigationIsReady() {
        coordinator.waitForAuthentication()
        assertTrue(coordinator.accept("puber://content/items/42?action=play"))
        assertNull(coordinator.consumeForWarmRouting())

        coordinator.consumeAfterAuthentication()
        assertTrue(coordinator.accept("puber://content/items/7?action=play&video=2"))

        assertEquals(
            ContentTarget.Playback(itemId = 7, videoNumber = 2),
            coordinator.consumeForWarmRouting(),
        )
    }

    @Test
    fun invalidUri_doesNotReplaceValidPendingTarget() {
        assertTrue(coordinator.accept("puber://content/items/42"))
        assertFalse(coordinator.accept("puber://content/items/0"))

        assertEquals(ContentTarget.Details(42), coordinator.consumeForAuthenticatedStart())
    }

    @Test
    fun clearSession_discardsPendingTargetAndDisablesWarmRouting() {
        coordinator.consumeForAuthenticatedStart()
        coordinator.accept("puber://content/items/42")

        coordinator.clearSession()

        assertNull(coordinator.consumeForWarmRouting())
        assertNull(coordinator.consumeAfterAuthentication())
    }
}
