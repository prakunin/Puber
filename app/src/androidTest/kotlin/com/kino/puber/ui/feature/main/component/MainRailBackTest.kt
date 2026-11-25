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

/**
 * Who owns Back when the rail is open over a screen pushed in a tab, in every order the Main and
 * tab handlers can register in. An arm of the exit confirmation is observed as the exit it allows,
 * or read directly where the next Back would disarm it before any exit could show.
 */
internal class MainRailBackTest {

    @get:Rule
    val rule = createMainHostRule()

    private val host = MainHostHarness(rule)

    @Before
    fun setUp() = host.setUp()

    @After
    fun tearDown() = host.tearDown()

    @Test
    fun railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart() {
        host.startMain()
        host.pushChildFromRoot()

        assertRailBackKeepsChild()
    }

    @Test
    fun railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip() {
        host.startMain()
        host.detailsRoundTripFromRoot()
        host.pushChildFromRoot()
        assertRailBackKeepsChild()

        host.back()
        host.awaitRoot(FIRST_TAB_ROOT)
        host.back()
        host.awaitDrawer(DrawerValue.Open)

        assertFalse(host.isFinishing)
    }

    @Test
    fun railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch() {
        host.startMain()
        host.switchAwayAndBack()
        host.pushChildFromRoot()

        assertRailBackKeepsChild()
    }

    @Test
    fun backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory() {
        host.startMain()
        popChildToRoot()

        host.detailsRoundTripFromRoot()
        popChildToRoot()

        host.switchAwayAndBack()
        popChildToRoot()
    }

    @Test
    fun fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth() {
        host.startMain()
        host.pushChildFromRoot()
        // The child takes its own initial focus after the push; a request made before it lands races it.
        host.awaitFocused(CHILD_CARD_FIRST)
        host.openRailFrom(CHILD_CARD_FIRST)

        host.back()
        host.awaitDrawer(DrawerValue.Closed)
        host.assertShown(CHILD_SCREEN)
        // Checked before the pop: the next Back changes canPop, whose effect disarms the exit anyway.
        assertFalse(host.isExitArmed)
        assertFalse(host.isFinishing)

        host.back()
        host.awaitRoot(FIRST_TAB_ROOT)
        host.assertDrawer(DrawerValue.Closed)
        assertFalse(host.isFinishing)

        host.back()
        host.awaitDrawer(DrawerValue.Open)
        assertFalse(host.isFinishing)

        host.back()
        host.awaitDrawer(DrawerValue.Closed)
        assertFalse(host.isFinishing)

        host.back()
        rule.waitUntil(FINISH_TIMEOUT_MILLIS) { host.isFinishing }
    }

    @Test
    fun armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit() {
        host.startMain()
        host.back()
        host.awaitDrawer(DrawerValue.Open)
        host.back()
        host.awaitDrawer(DrawerValue.Closed)
        assertTrue(host.isExitArmed)

        host.pushChildFromRoot()
        host.focus(CHILD_CARD_FIRST)
        host.back()
        host.awaitRoot(FIRST_TAB_ROOT)
        host.back()
        host.awaitDrawer(DrawerValue.Open)

        assertFalse(host.isFinishing)
    }

    @Test
    fun armedAtRoot_thenTabPick_nextBackOpensRailNotExit() {
        host.startMain()
        host.back()
        host.awaitDrawer(DrawerValue.Open)
        host.back()
        host.awaitDrawer(DrawerValue.Closed)
        assertTrue(host.isExitArmed)

        host.openRailFrom(ROOT_CARD_FIRST)
        host.pickRailTabBelow(R.string.main_tabs_history)
        host.awaitRoot("other")
        host.awaitDrawer(DrawerValue.Closed)
        assertFalse(host.isExitArmed)

        // The tab root takes its own initial focus, which closes a rail opened before it lands.
        host.awaitFocused(ROOT_CARD_FIRST)
        host.back()
        host.awaitDrawer(DrawerValue.Open)
        assertFalse(host.isFinishing)
    }

    @Test
    fun backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff() {
        // The child stays in Loading for the whole test: nothing in it ever takes focus.
        host.startMain(childLoading = true)
        host.pushChildFromRoot()
        Thread.sleep(SETTLE_MILLIS)
        host.assertDrawer(DrawerValue.Closed)
        host.assertNothingFocused()
        host.sendLeftThroughInputPipeline()
        host.awaitDrawer(DrawerValue.Open)
        host.awaitRailItemFocused()

        // The handoff fails at its 5 s timeout and must leave the rail closed, not reopen it, so Back
        // is not trapped in the rail over a screen it can pop.
        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        // Short of the timeout the collapsed rail still holds the item focused before Back.
        Thread.sleep(BEFORE_HANDOFF_TIMEOUT_MILLIS)
        host.assertDrawer(DrawerValue.HandingOff)
        host.assertRailItemFocused()
        host.awaitDrawer(DrawerValue.Closed)
        host.assertShown(CHILD_SCREEN)
        // A collapsed rail keeps no focused item, or Center would pick a tab the user cannot see.
        host.assertNoRailItemFocused()
        assertFalse(host.isFinishing)

        host.back()
        host.awaitRoot(FIRST_TAB_ROOT)
        host.assertAbsent(CHILD_SCREEN)
        host.assertDrawer(DrawerValue.Closed)
        host.awaitFocused(ROOT_PUSH_CHILD)
        assertFalse(host.isFinishing)
    }

    @Test
    fun centerOnCollapsedRail_duringHandoff_doesNotSwitchTab() {
        // The child stays in Loading for the whole test, so the handoff after Back never lands.
        host.startMain(childLoading = true)
        host.pushChildFromRoot()
        Thread.sleep(SETTLE_MILLIS)
        host.sendLeftThroughInputPipeline()
        host.awaitDrawer(DrawerValue.Open)
        host.awaitRailItemFocused()

        host.back()
        host.assertDrawer(DrawerValue.HandingOff)
        // Down then Center on the collapsed rail: the History item, which the user cannot see.
        host.press(Key.DirectionDown)
        host.assertUnselectedRailItemFocused()
        host.press(Key.DirectionCenter)

        host.awaitDrawer(DrawerValue.Closed)
        host.assertShown(CHILD_SCREEN)
        host.assertRootAbsent("other")
        assertFalse(host.isFinishing)
    }

    @Test
    fun correctivePickDuringTabPickHandoff_switchesToTheSecondTab() {
        // History, the first pick, stays in Loading: its handoff cannot land on it.
        host.startMain(loadingRoot = "other")
        host.awaitFocused(ROOT_CARD_FIRST)
        host.openRailFrom(ROOT_CARD_FIRST)
        host.pickRailTabBelow(R.string.main_tabs_history)
        host.awaitRoot("other")
        host.assertDrawer(DrawerValue.HandingOff)

        // Down then Center on the collapsed rail while History's handoff is pending: Series.
        host.press(Key.DirectionDown)
        host.assertRailItemFocused()
        host.press(Key.DirectionCenter)

        host.awaitRoot("third")
        host.assertRootAbsent("other")
        host.awaitDrawer(DrawerValue.Closed)
        host.awaitFocused(ROOT_CARD_FIRST)
        assertFalse(host.isFinishing)
    }

    @Test
    fun backAtTabRoot_opensRailThenArmsThenExits() {
        host.startMain()

        host.back()
        host.awaitDrawer(DrawerValue.Open)
        host.awaitRailItemFocused()
        assertFalse(host.isFinishing)

        host.back()
        host.awaitDrawer(DrawerValue.Closed)
        assertFalse(host.isFinishing)

        host.back()
        rule.waitUntil(FINISH_TIMEOUT_MILLIS) { host.isFinishing }
    }

    @Test
    fun screenBackHandlerOnPushedScreen_winsOverMainHandlers() {
        host.startMain(childBackHandler = true)
        host.pushChildFromRoot()
        host.focus(CHILD_CARD_FIRST)

        host.back()

        assertEquals(1, host.childBackConsumed)
        host.assertShown(CHILD_SCREEN)
        host.assertDrawer(DrawerValue.Closed)
    }

    @Test
    fun backOnRootPushedDetails_popsToMain() {
        host.startMain()
        host.pushChildFromRoot()
        host.pushDetailsFromChild()

        host.back()

        host.awaitTag(CHILD_SCREEN)
        host.assertDrawer(DrawerValue.Closed)
        assertFalse(host.isFinishing)
    }

    @Test
    fun backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail() {
        host.startMain()
        host.pushChildFromRoot()
        host.openTab(host.tabOther)
        host.awaitRoot("other")
        // The tab root takes its own initial focus, which closes a rail opened before it lands.
        host.awaitFocused(ROOT_CARD_FIRST)

        host.back()

        host.awaitDrawer(DrawerValue.Open)
        assertFalse(host.isFinishing)
    }

    /** Rail open from the pushed child's first card; Back closes the rail, the child stays, focus returns. */
    private fun assertRailBackKeepsChild() {
        // The child takes its own initial focus after the push; a request made before it lands races it.
        host.awaitFocused(CHILD_CARD_FIRST)
        host.openRailFrom(CHILD_CARD_FIRST)

        host.back()

        host.awaitDrawer(DrawerValue.Closed)
        host.assertShown(CHILD_SCREEN)
        // Checked before any pop: a pop changes canPop, whose effect disarms the exit anyway.
        assertFalse(host.isExitArmed)
        host.awaitFocused(CHILD_CARD_FIRST)
    }

    /** Back with the rail closed on the pushed child pops to the tab root and focuses its opener. */
    private fun popChildToRoot() {
        host.pushChildFromRoot()
        host.focus(CHILD_CARD_FIRST)
        host.assertDrawer(DrawerValue.Closed)

        host.back()

        host.awaitRoot(FIRST_TAB_ROOT)
        host.assertDrawer(DrawerValue.Closed)
        host.awaitFocused(ROOT_PUSH_CHILD)
    }
}

private const val FINISH_TIMEOUT_MILLIS = 5_000L
private const val SETTLE_MILLIS = 1_500L

/** Well inside the 5 s handoff timeout, which starts before Back returns. */
private const val BEFORE_HANDOFF_TIMEOUT_MILLIS = 2_500L
