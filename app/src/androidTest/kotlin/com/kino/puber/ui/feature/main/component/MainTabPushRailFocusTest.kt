package com.kino.puber.ui.feature.main.component

import androidx.compose.ui.input.key.Key
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** A closed rail must not take focus it did not ask for when a screen is pushed into a tab. */
internal class MainTabPushRailFocusTest {

    @get:Rule
    val rule = createMainHostRule()

    private val host = MainHostHarness(rule)

    @Before
    fun setUp() = host.setUp()

    @After
    fun tearDown() = host.tearDown()

    @Test
    fun pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded() {
        host.startMain(childLoading = true)
        host.startSampling()

        host.pushChildFromRoot()
        Thread.sleep(SETTLE_MILLIS)
        host.endChildLoading()
        host.awaitFocused(CHILD_CARD_FIRST)
        host.stopSampling()

        assertRailNeverOpen()
        host.assertDrawer(DrawerValue.Closed)
    }

    @Test
    fun pushWithContentReady_railStaysClosed() {
        host.startMain()
        host.startSampling()

        host.pushChildFromRoot()
        host.awaitFocused(CHILD_CARD_FIRST)
        Thread.sleep(SETTLE_MILLIS)
        host.stopSampling()

        assertRailNeverOpen()
    }

    @Test
    fun pushAfterDetailsRoundTrip_railStaysClosed() {
        host.startMain()
        host.detailsRoundTripFromRoot()
        host.startSampling()

        host.pushChildFromRoot()
        host.awaitFocused(CHILD_CARD_FIRST)
        Thread.sleep(SETTLE_MILLIS)
        host.stopSampling()

        assertRailNeverOpen()
    }

    @Test
    fun leftFromPushedGrid_opensRail() {
        host.startMain()
        host.pushChildFromRoot()
        host.awaitFocused(CHILD_CARD_FIRST)

        host.focusAndPress(CHILD_CARD_FIRST, Key.DirectionLeft)

        host.awaitDrawer(DrawerValue.Open)
        host.awaitRailItemFocused()
    }

    @Test
    fun leftWithNothingFocused_opensRailAndBackReturnsToContent() {
        host.startMain(childLoading = true)
        host.pushChildFromRoot()
        Thread.sleep(SETTLE_MILLIS)
        // On base the rail is already Open here, so the real Left below would prove nothing.
        host.assertDrawer(DrawerValue.Closed)
        host.assertNothingFocused()

        host.sendLeftThroughInputPipeline()

        host.awaitDrawer(DrawerValue.Open)
        host.awaitRailItemFocused()

        // Back hands focus to the content, which has nothing focusable while Loading: the rail waits
        // in HandingOff until the content lands. A failed Dismiss would also end Closed, with focus
        // cleared, so the focused child card below is what proves the handoff landed.
        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        host.endChildLoading()
        host.awaitDrawer(DrawerValue.Closed)
        host.awaitFocused(CHILD_CARD_FIRST)
        host.assertDrawer(DrawerValue.Closed)
        host.assertShown(CHILD_SCREEN)
    }

    private fun assertRailNeverOpen() {
        assertFalse(
            "rail values seen: ${host.sampledDrawerValues.distinct()}",
            DrawerValue.Open in host.sampledDrawerValues,
        )
    }
}

private const val SETTLE_MILLIS = 1_500L
