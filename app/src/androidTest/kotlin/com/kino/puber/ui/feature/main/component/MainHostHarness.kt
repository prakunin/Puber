package com.kino.puber.ui.feature.main.component

import android.app.Activity
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.TelevisionSimple
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.component.drawer.DrawerState
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalDrawerState
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal typealias MainHostRule =
    AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

internal fun createMainHostRule(): MainHostRule = createAndroidComposeRule<ComponentActivity>()

internal const val ROOT_CARD_FIRST = "root_card_first"
internal const val ROOT_PUSH_CHILD = "root_push_child"
internal const val ROOT_PUSH_DETAILS = "root_push_details"
internal const val CHILD_CARD_FIRST = "child_card_first"
internal const val CHILD_PUSH_DETAILS = "child_push_details"
internal const val CHILD_SCREEN = "child_screen"
internal const val DETAILS_SCREEN = "details_screen"

private const val ROOT_SCREEN_PREFIX = "root_screen_"
private const val AWAIT_MILLIS = 10_000L
private const val SAMPLE_PERIOD_MILLIS = 2L

/** What the probe screens share with the test that drives them; reset by [MainHostHarness.setUp]. */
private object ProbeState {
    @Volatile
    var drawer: DrawerState? = null
    var childLoading by mutableStateOf(false)

    /** The tab root, by name, that stays in Loading with nothing focusable; `null` for none. */
    var loadingRoot: String? by mutableStateOf(null)
    var childBackHandler by mutableStateOf(false)
    val childBackConsumed = AtomicInteger()
    val seenDrawerValues = CopyOnWriteArrayList<DrawerValue>()
    var exitConfirmation = probeExitConfirmation()
    var tabsByType: Map<TabType, PuberTab> = emptyMap()

    /** The probe root name of each tab, so a pick can put the arriving tab in Loading. */
    var rootNamesByType: Map<TabType, String> = emptyMap()

    /** The tab the rail shows as selected; a rail pick moves it, as MainVM does. */
    var selectedTab: TabType by mutableStateOf(TabType.Movies)

    /** Rail picks Main received; a pick the rail refused leaves it unchanged. */
    val railPicks = AtomicInteger()

    /** The exit clock; see [probeExitConfirmation]. */
    @Volatile
    var exitNowMillis = 0L

    /** Whether a rail pick puts the picked tab in Loading and so makes the outgoing tab focusable. */
    var loadingFollowsPick = false

    /** Whether a rail pick holds its tab swap in [heldTab] until [MainHostHarness.releaseTabSwap]. */
    var holdTabSwap = false
    var heldTab: PuberTab? = null
    var tabRouter: TabRouter? = null
    var tabAppRouterHolder: TabAppRouterHolder? = null

    fun reset() {
        drawer = null
        childLoading = false
        loadingRoot = null
        childBackHandler = false
        childBackConsumed.set(0)
        seenDrawerValues.clear()
        selectedTab = TabType.Movies
        railPicks.set(0)
        exitNowMillis = 0L
        loadingFollowsPick = false
        holdTabSwap = false
        heldTab = null
        exitConfirmation = probeExitConfirmation()
    }
}

/**
 * The clock stands still unless a test advances it ([MainHostHarness.advanceExitClock]), so an arm
 * never expires on its own: a test that proves a disarm cannot pass because a slow run outlasted
 * the 3 s window.
 */
private fun probeExitConfirmation() = ExitConfirmation(nowMillis = { ProbeState.exitNowMillis })

/**
 * The real [DrawerMainContent] over a [FlowComponent] with probe tab screens, driven the way the
 * device is: D-pad keys through Compose, Back through the activity dispatcher, and Left with
 * nothing focused through the real input pipeline.
 */
internal class MainHostHarness(private val rule: MainHostRule) {

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var tabRouter: TabRouter
    private lateinit var tabAppRouterHolder: TabAppRouterHolder

    @Volatile
    private var sampling = false
    private var sampler: Thread? = null

    val tabFirst = PuberTab(screen = ProbeTabRootScreen("first"), tag = TabType.Movies)
    val tabOther = PuberTab(screen = ProbeTabRootScreen("other"), tag = TabType.History)
    val tabThird = PuberTab(screen = ProbeTabRootScreen("third"), tag = TabType.Series)

    // --- lifecycle ---------------------------------------------------------------------------

    fun setUp() {
        ProbeState.reset()
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        tabRouter = TabRouter(coroutineScope)
        tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        ProbeState.tabRouter = tabRouter
        ProbeState.tabAppRouterHolder = tabAppRouterHolder
        ProbeState.tabsByType = mapOf(
            TabType.Movies to tabFirst,
            TabType.History to tabOther,
            TabType.Series to tabThird,
        )
        ProbeState.rootNamesByType = mapOf(
            TabType.Movies to "first",
            TabType.History to "other",
            TabType.Series to "third",
        )
    }

    fun tearDown() {
        stopSampling()
        rule.runOnIdle { tabAppRouterHolder.dispose() }
        ProbeState.tabRouter = null
        ProbeState.tabAppRouterHolder = null
        coroutineScope.cancel()
    }

    /**
     * Composes Main, then opens the first tab only after that first frame, so the tab composes a
     * pass after Main (the cold-start order).
     */
    fun startMain(
        childLoading: Boolean = false,
        childBackHandler: Boolean = false,
        loadingRoot: String? = null,
        loadingFollowsPick: Boolean = false,
        holdTabSwap: Boolean = false,
    ) {
        ProbeState.childLoading = childLoading
        ProbeState.loadingRoot = loadingRoot
        ProbeState.loadingFollowsPick = loadingFollowsPick
        ProbeState.holdTabSwap = holdTabSwap
        ProbeState.childBackHandler = childBackHandler
        rule.setContent {
            PuberTheme {
                FlowComponent(
                    scopeName = "MainHostHarness",
                    screen = ProbeMainHostScreen,
                    moduleFactory = { scopeId, _ ->
                        module {
                            scope(named(scopeId)) {
                                scoped<AppLauncher> { ProbeAppLauncher }
                                scoped<Screens> { ScreensImpl }
                            }
                        }
                    },
                )
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { tabRouter.openTab(tabFirst) }
        awaitRoot("first")
    }

    // --- navigation --------------------------------------------------------------------------

    fun openTab(tab: PuberTab) {
        rule.runOnIdle { tabRouter.openTab(tab) }
    }

    /** Tab-switch order: the other tab, then back to the first. */
    fun switchAwayAndBack() {
        openTab(tabOther)
        awaitRoot("other")
        openTab(tabFirst)
        awaitRoot("first")
    }

    /** Pushes the probe child from the tab root; the child's first card is focused by the caller. */
    fun pushChildFromRoot() {
        focusAndPress(ROOT_PUSH_CHILD, Key.DirectionCenter)
        awaitTag(CHILD_SCREEN)
    }

    /** Root push of the probe Details from the tab root, then Back to Main. */
    fun detailsRoundTripFromRoot() {
        focusAndPress(ROOT_PUSH_DETAILS, Key.DirectionCenter)
        awaitTag(DETAILS_SCREEN)
        back()
        awaitRoot("first")
    }

    fun pushDetailsFromChild() {
        focusAndPress(CHILD_PUSH_DETAILS, Key.DirectionCenter)
        awaitTag(DETAILS_SCREEN)
    }

    fun endChildLoading() {
        rule.runOnUiThread { ProbeState.childLoading = false }
        rule.waitForIdle()
    }

    /** Changes which tab root stays in Loading with nothing focusable; `null` for none. */
    fun setLoadingRoot(name: String?) {
        rule.runOnUiThread { ProbeState.loadingRoot = name }
        rule.waitForIdle()
    }

    /** Opens the tab of the rail pick held by `startMain(holdTabSwap = true)`. */
    fun releaseTabSwap() {
        rule.runOnUiThread {
            requireNotNull(ProbeState.tabRouter).openTab(requireNotNull(ProbeState.heldTab) { "no tab swap is held" })
            ProbeState.heldTab = null
        }
        rule.waitForIdle()
    }

    /** Moves the exit clock forward, so an arm older than the 3 s window expires. */
    fun advanceExitClock(ms: Long) {
        ProbeState.exitNowMillis += ms
    }

    fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    val childBackConsumed: Int get() = ProbeState.childBackConsumed.get()

    /** Rail picks Main received since [setUp]. */
    val railPicks: Int get() = ProbeState.railPicks.get()

    val isFinishing: Boolean get() = rule.activity.isFinishing

    /** Whether the next Back at a tab root would leave the app; read where no exit follows to show it. */
    val isExitArmed: Boolean get() = ProbeState.exitConfirmation.isArmed

    // --- input -------------------------------------------------------------------------------

    fun focus(tag: String) {
        awaitTag(tag)
        rule.onNodeWithTag(tag).requestFocus()
        rule.waitForIdle()
    }

    fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    fun focusAndPress(tag: String, key: Key) {
        focus(tag)
        press(key)
    }

    /** From the focused item of the open rail, Down to the tab titled [titleRes], then Enter, as on a remote. */
    fun pickRailTabBelow(titleRes: Int) {
        awaitRailItemFocused()
        press(Key.DirectionDown)
        val title = rule.activity.getString(titleRes)
        rule.waitUntil(AWAIT_MILLIS) {
            runCatching {
                rule.onNode(isFocused() and hasAnyDescendant(hasText(title)), useUnmergedTree = true).assertExists()
            }.isSuccess
        }
        press(Key.Enter)
    }

    /** Focuses the rail item of the selected tab, matched as in [railItemFocused]. */
    fun focusSelectedRailItem() {
        rule.onNode(isSelectable() and isSelected()).requestFocus()
        rule.waitForIdle()
    }

    /** Left from a focused content card through Compose, as on a remote. */
    fun openRailFrom(tag: String) {
        focusAndPress(tag, Key.DirectionLeft)
        awaitDrawer(DrawerValue.Open)
    }

    /**
     * Left through the real input pipeline, from the instrumentation thread. `performKeyInput` goes
     * through Compose and never reaches the view's unhandled-key path.
     */
    fun sendLeftThroughInputPipeline() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.waitForIdle()
    }

    // --- observation -------------------------------------------------------------------------

    val drawerValue: DrawerValue
        get() = requireNotNull(ProbeState.drawer) { "no probe screen has captured the drawer yet" }.currentValue

    fun awaitDrawer(expected: DrawerValue) {
        rule.waitUntil(AWAIT_MILLIS) { ProbeState.drawer?.currentValue == expected }
        rule.waitForIdle()
    }

    fun assertDrawer(expected: DrawerValue) = assertEquals(expected, drawerValue)

    fun awaitTag(tag: String) {
        rule.waitUntil(AWAIT_MILLIS) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    fun awaitRoot(name: String) = awaitTag(ROOT_SCREEN_PREFIX + name)

    fun assertRootAbsent(name: String) = assertAbsent(ROOT_SCREEN_PREFIX + name)

    fun assertShown(tag: String) {
        rule.onNodeWithTag(tag).assertExists()
    }

    fun assertAbsent(tag: String) {
        rule.onAllNodesWithTag(tag).assertCountEquals(0)
    }

    fun awaitFocused(tag: String) {
        awaitTag(tag)
        rule.waitUntil(AWAIT_MILLIS) {
            runCatching { rule.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }

    fun assertNothingFocused() {
        rule.onAllNodes(isFocused()).assertCountEquals(0)
    }

    /**
     * Matched by the selected semantics of the rail's list item, which no probe card has, not by its
     * label: a collapsed rail (HandingOff, Closed) drops the label from composition.
     */
    fun railItemFocused(): SemanticsNodeInteraction = rule.onNode(isFocused() and isSelectable())

    fun assertRailItemFocused() {
        railItemFocused().assertExists()
    }

    /** A rail item other than the selected tab's holds focus: a D-pad move in the rail landed. */
    fun assertUnselectedRailItemFocused() {
        rule.onNode(isFocused() and isSelectable() and isNotSelected()).assertExists()
    }

    fun assertNoRailItemFocused() {
        railItemFocused().assertDoesNotExist()
    }

    fun awaitRailItemFocused() {
        rule.waitUntil(AWAIT_MILLIS) {
            runCatching { railItemFocused().assertExists() }.isSuccess
        }
    }

    /** Drawer values sampled every few milliseconds since [startSampling]; catches a transient `Open`. */
    val sampledDrawerValues: List<DrawerValue> get() = ProbeState.seenDrawerValues.toList()

    fun startSampling() {
        ProbeState.seenDrawerValues.clear()
        sampling = true
        sampler = Thread {
            while (sampling) {
                ProbeState.drawer?.currentValue?.let(ProbeState.seenDrawerValues::add)
                Thread.sleep(SAMPLE_PERIOD_MILLIS)
            }
        }.also(Thread::start)
    }

    fun stopSampling() {
        sampling = false
        sampler?.join()
        sampler = null
    }
}

private data object ProbeAppLauncher : AppLauncher {
    override fun restart() = Unit
    override fun finish() = Unit
    override fun bind(activity: Activity) = Unit
    override fun unbind() = Unit
}

@Parcelize
private data object ProbeMainHostScreen : PuberScreen {
    @Composable
    override fun Content() {
        DrawerMainContent(
            state = MainViewState(
                tabs = listOf(
                    probeTab(TabType.Movies, PhosphorIcons.Duotone.FilmSlate),
                    probeTab(TabType.History, PhosphorIcons.Duotone.ClockCounterClockwise),
                    probeTab(TabType.Series, PhosphorIcons.Duotone.TelevisionSimple),
                ),
                selectedTab = ProbeState.selectedTab,
            ),
            onAction = { action ->
                // A rail pick selects the tab, then switches to it, as MainVM does.
                if (action is CommonAction.ItemSelected<*>) {
                    val type = (action.item as MainTab).type
                    ProbeState.railPicks.incrementAndGet()
                    ProbeState.selectedTab = type
                    if (ProbeState.loadingFollowsPick) {
                        ProbeState.loadingRoot = ProbeState.rootNamesByType.getValue(type)
                    }
                    val tab = ProbeState.tabsByType.getValue(type)
                    if (ProbeState.holdTabSwap) {
                        ProbeState.heldTab = tab
                    } else {
                        requireNotNull(ProbeState.tabRouter).openTab(tab)
                    }
                }
            },
            tabRouter = requireNotNull(ProbeState.tabRouter),
            tabAppRouterHolder = requireNotNull(ProbeState.tabAppRouterHolder),
            exitConfirmation = ProbeState.exitConfirmation,
        )
    }
}

private fun probeTab(type: TabType, icon: ImageVector) =
    MainTab(type, icon, isSelected = type == ProbeState.selectedTab)

@Parcelize
private data class ProbeTabRootScreen(val name: String) : PuberScreen {

    @IgnoredOnParcel
    override val key = "ProbeTabRoot_$name"

    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        CaptureDrawer()
        if (ProbeState.loadingRoot == name) {
            Box(Modifier.testTag(ROOT_SCREEN_PREFIX + name)) { Text("Loading") }
            return
        }
        val firstCard = rememberFocusRequesterOnLaunch()
        Column(Modifier.testTag(ROOT_SCREEN_PREFIX + name)) {
            Row {
                ProbeCard(ROOT_CARD_FIRST, Modifier.focusRequester(firstCard))
                ProbeCard(ROOT_PUSH_CHILD) { router.navigateTo(ProbeChildScreen) }
                ProbeCard(ROOT_PUSH_DETAILS) { router.navigateTo(ProbeDetailsScreen) }
            }
        }
    }
}

/** A grid of cards as on Show all; `Loading` shows nothing focusable until the test ends it. */
@Parcelize
private data object ProbeChildScreen : PuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        CaptureDrawer()
        if (ProbeState.childBackHandler) {
            BackHandler { ProbeState.childBackConsumed.incrementAndGet() }
        }
        Box(Modifier.testTag(CHILD_SCREEN)) {
            if (ProbeState.childLoading) {
                Text("Loading")
            } else {
                val firstCard = rememberFocusRequesterOnLaunch()
                Row {
                    ProbeCard(CHILD_CARD_FIRST, Modifier.focusRequester(firstCard))
                    ProbeCard(CHILD_PUSH_DETAILS) { router.navigateTo(ProbeDetailsScreen) }
                }
            }
        }
    }
}

@Parcelize
private data object ProbeDetailsScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        val firstCard = rememberFocusRequesterOnLaunch()
        Box(Modifier.testTag(DETAILS_SCREEN)) {
            ProbeCard("details_card", Modifier.focusRequester(firstCard))
        }
    }
}

@Composable
private fun CaptureDrawer() {
    val drawer = LocalDrawerState.current
    SideEffect { if (drawer != null) ProbeState.drawer = drawer }
}

@Composable
private fun ProbeCard(tag: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Box(
        modifier
            .testTag(tag)
            .size(120.dp, 60.dp)
            .clickable(onClick = onClick),
    ) {
        Text(tag)
    }
}
