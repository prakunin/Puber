package com.kino.puber.ui.feature.main.component

import androidx.compose.ui.input.key.Key
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val FIRST_TAB_ROOT = "first"
private const val OTHER_TAB_ROOT = "other"
private const val THIRD_TAB_ROOT = "third"

/**
 * What a rail handoff does by its kind: a dismiss (Back, Right, re-pick of the selected tab), the
 * initial handoff of a cold start, and a tab pick. The never-focusable root is the probe root held
 * in Loading, so the handoff can only end by its 5 s timeout.
 */
internal class MainRailHandoffKindTest {

    @get:Rule
    val rule = createMainHostRule()

    private val host = MainHostHarness(rule)

    @Before
    fun setUp() = host.setUp()

    @After
    fun tearDown() = host.tearDown()

    @Test
    fun backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff() {
        openRailOverNeverFocusableRoot()

        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        assertTrue(host.isExitArmed)

        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        assertTrue(host.isFinishing)
    }

    @Test
    fun backDuringDismissHandoffAfterArmExpired_isSwallowed() {
        openRailOverNeverFocusableRoot()

        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        host.advanceExitClock(PAST_EXIT_WINDOW_MILLIS)
        host.back()

        assertFalse(host.isFinishing)
        host.assertDrawer(DrawerValue.HandingOff)
    }

    @Test
    fun failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt() {
        failBackHandoffAtNeverFocusableRoot()

        host.assertNoRailItemFocused()
        host.sendLeftThroughInputPipeline()
        host.awaitDrawer(DrawerValue.Open)
    }

    @Test
    fun failedBackHandoffAtNeverFocusableRoot_expiredArmBackReopensRail() {
        failBackHandoffAtNeverFocusableRoot()

        host.back()

        host.awaitDrawer(DrawerValue.Open)
        assertFalse(host.isFinishing)
    }

    @Test
    fun rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed() {
        openRailOverNeverFocusableRoot()

        host.press(Key.DirectionRight)
        host.assertDrawer(DrawerValue.HandingOff)
        assertFalse(host.isExitArmed)
        host.startSampling()

        awaitHandoffEnd()
        host.assertDrawer(DrawerValue.Closed)
        assertNoOpenSampled()
    }

    @Test
    fun pickDuringInitialHandoff_switchesToPickedTab() {
        host.startMain(loadingRoot = FIRST_TAB_ROOT)
        host.assertDrawer(DrawerValue.HandingOff)

        host.focusSelectedRailItem()
        host.press(Key.DirectionDown)
        host.press(Key.DirectionCenter)

        assertEquals(1, host.railPicks)
        host.awaitRoot(OTHER_TAB_ROOT)
        host.assertRootAbsent(FIRST_TAB_ROOT)
        host.awaitFocused(ROOT_CARD_FIRST)
        host.awaitDrawer(DrawerValue.Closed)
    }

    @Test
    fun pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle() {
        // A pick puts the arriving tab in Loading and lets the outgoing one compose its cards. The
        // swap is held until the outgoing tab holds focus and has had time to settle, so only a
        // handoff that ignores the outgoing tab stays pending.
        host.startMain(loadingRoot = FIRST_TAB_ROOT, loadingFollowsPick = true, holdTabSwap = true)
        // Well into the cold-start budget, so a kept budget would run out before the pick's.
        Thread.sleep(INTO_INITIAL_HANDOFF_MILLIS)
        host.startSampling()

        host.focusSelectedRailItem()
        host.press(Key.DirectionDown)
        val pickedAt = System.currentTimeMillis()
        host.press(Key.DirectionCenter)

        assertEquals(1, host.railPicks)
        host.awaitFocused(ROOT_CARD_FIRST)
        host.assertRootAbsent(OTHER_TAB_ROOT)
        Thread.sleep(OUTGOING_SETTLE_WINDOW_MILLIS)
        host.assertDrawer(DrawerValue.HandingOff)

        host.releaseTabSwap()
        host.awaitRoot(OTHER_TAB_ROOT)
        Thread.sleep((pickedAt + PAST_INITIAL_BUDGET_MILLIS - System.currentTimeMillis()).coerceAtLeast(0))
        host.assertDrawer(DrawerValue.HandingOff)
        host.awaitDrawer(DrawerValue.Open)
        val beforeOpen = host.sampledDrawerValues.takeWhile { it != DrawerValue.Open }
        assertFalse("rail closed before the pick's handoff failed: $beforeOpen", DrawerValue.Closed in beforeOpen)
    }

    @Test
    fun centerOnSelectedTabDuringInitialHandoff_switchesNothing() {
        host.startMain(loadingRoot = FIRST_TAB_ROOT)

        host.focusSelectedRailItem()
        host.press(Key.DirectionCenter)

        assertEquals(0, host.railPicks)
        host.awaitRoot(FIRST_TAB_ROOT)
        host.assertRootAbsent(OTHER_TAB_ROOT)
        host.assertDrawer(DrawerValue.HandingOff)
    }

    @Test
    fun coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus() {
        host.startSampling()
        host.startMain(loadingRoot = FIRST_TAB_ROOT)
        host.awaitDrawer(DrawerValue.HandingOff)

        awaitHandoffEnd()
        host.assertDrawer(DrawerValue.Closed)

        host.setLoadingRoot(null)
        host.awaitFocused(ROOT_CARD_FIRST)
        host.assertDrawer(DrawerValue.Closed)
        assertNoOpenSampled()
    }

    @Test
    fun centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches() {
        host.startMain(loadingRoot = OTHER_TAB_ROOT)
        host.awaitFocused(ROOT_CARD_FIRST)
        host.openRailFrom(ROOT_CARD_FIRST)
        host.pickRailTabBelow(R.string.main_tabs_history)
        host.awaitRoot(OTHER_TAB_ROOT)
        host.assertDrawer(DrawerValue.HandingOff)
        assertEquals(1, host.railPicks)

        host.focusSelectedRailItem()
        host.press(Key.DirectionCenter)
        assertEquals(1, host.railPicks)

        host.press(Key.DirectionDown)
        host.press(Key.DirectionCenter)
        host.awaitRoot(THIRD_TAB_ROOT)
        host.awaitFocused(ROOT_CARD_FIRST)
        assertEquals(2, host.railPicks)
    }

    @Test
    fun tabPickIntoNeverFocusableTab_failedHandoffReopensRail() {
        host.startMain(loadingRoot = OTHER_TAB_ROOT)
        host.awaitFocused(ROOT_CARD_FIRST)
        host.openRailFrom(ROOT_CARD_FIRST)
        host.pickRailTabBelow(R.string.main_tabs_history)
        host.awaitRoot(OTHER_TAB_ROOT)
        host.assertDrawer(DrawerValue.HandingOff)

        awaitHandoffEnd()
        host.assertDrawer(DrawerValue.Open)
    }

    @Test
    fun rePickOfSelectedTab_closesRailOntoContent() {
        host.startMain()
        host.awaitFocused(ROOT_CARD_FIRST)
        host.openRailFrom(ROOT_CARD_FIRST)
        host.focusSelectedRailItem()

        host.press(Key.DirectionCenter)
        host.startSampling()

        rule.waitUntil(RE_PICK_CLOSE_MILLIS) { host.drawerValue == DrawerValue.Closed }
        host.awaitFocused(ROOT_CARD_FIRST)
        assertNoOpenSampled()
    }

    @Test
    fun rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed() {
        openRailOverNeverFocusableRoot()
        host.focusSelectedRailItem()

        host.press(Key.DirectionCenter)
        host.assertDrawer(DrawerValue.HandingOff)

        awaitHandoffEnd()
        host.assertDrawer(DrawerValue.Closed)
    }

    /** The first tab root drops to Loading after its card took focus; Left then opens the rail. */
    private fun openRailOverNeverFocusableRoot() {
        host.startMain()
        host.awaitFocused(ROOT_CARD_FIRST)
        host.setLoadingRoot(FIRST_TAB_ROOT)
        host.assertNothingFocused()
        host.sendLeftThroughInputPipeline()
        host.awaitDrawer(DrawerValue.Open)
        host.awaitRailItemFocused()
    }

    /** Back from the rail arms the exit; the arm expires, then the handoff fails into Closed. */
    private fun failBackHandoffAtNeverFocusableRoot() {
        openRailOverNeverFocusableRoot()
        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        host.startSampling()
        host.advanceExitClock(PAST_EXIT_WINDOW_MILLIS)

        awaitHandoffEnd()
        host.assertDrawer(DrawerValue.Closed)
        assertNoOpenSampled()
    }

    /** Waits out the 5 s handoff budget; the caller asserts where the handoff ended. */
    private fun awaitHandoffEnd() {
        rule.waitUntil(HANDOFF_END_MILLIS) { host.drawerValue != DrawerValue.HandingOff }
        rule.waitForIdle()
    }

    private fun assertNoOpenSampled() {
        val sampled = host.sampledDrawerValues
        assertFalse("rail reopened: $sampled", DrawerValue.Open in sampled)
    }
}

/** One millisecond past the 3 s exit window. */
private const val PAST_EXIT_WINDOW_MILLIS = 3_001L

/** Past the 5 s handoff budget with room for a slow frame. */
private const val HANDOFF_END_MILLIS = 8_000L

private const val INTO_INITIAL_HANDOFF_MILLIS = 1_500L

/** Four of the handoff's 250 ms backoff retries: an outgoing tab that may settle does so in this window. */
private const val OUTGOING_SETTLE_WINDOW_MILLIS = 1_000L

/** From the pick, past what is left of the cold-start budget begun before it. */
private const val PAST_INITIAL_BUDGET_MILLIS = 4_000L

private const val RE_PICK_CLOSE_MILLIS = 1_000L
