package com.kino.puber.core.ui.navigation.component

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.telemetry.JvmTelemetryClock
import com.kino.puber.core.telemetry.NavCmd
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.telemetry.TelemetryRecorder
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabHostScreen
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.component.LifecycleAction
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.ScreensImpl
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val HOME_TAG = "wiring_home"
private const val HOME_LOCAL_TAG = "wiring_home_local"
private const val HOME_DETAILS_TAG = "wiring_home_details"
private const val OTHER_TAG = "wiring_other"
private const val OTHER_DETAILS_TAG = "wiring_other_details"
private const val CHILD_TAG = "wiring_child"
private const val CHILD_DETAILS_TAG = "wiring_child_details"
private const val DETAILS_TAG = "wiring_details"
private const val BACK_TAG = "wiring_back"
private const val OTHER_LOCAL_TAG = "wiring_other_local"
private const val OTHER_CHILD_TAG = "wiring_other_child"
private const val OTHER_CHILD_DETAILS_TAG = "wiring_other_child_details"
private const val CHILD_BACK_TAG = "wiring_child_back"
private const val OTHER_CHILD_BACK_TAG = "wiring_other_child_back"
private const val LOGOUT_TAG = "wiring_logout"
private const val AUTH_TAG = "wiring_auth"
private const val AUTH_RELOGIN_TAG = "wiring_auth_relogin"

internal class TabHostTelemetryWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var tabRouter: TabRouter
    private lateinit var tabAppRouterHolder: TabAppRouterHolder
    private lateinit var recorder: WiringRecorder

    private val homeTab = PuberTab(WiringHomeScreen, TabType.Home)
    private val otherTab = PuberTab(WiringOtherScreen, TabType.History)
    private val hostClass: Class<*> = WiringTabHostScreen::class.java

    @Before
    fun setUp() {
        WiringResumes.clear()
        recorder = WiringRecorder()
        Telemetry.install(recorder, JvmTelemetryClock)
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        tabRouter = TabRouter(coroutineScope)
        tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        WiringHost.bind(tabRouter, tabAppRouterHolder)
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { tabAppRouterHolder.dispose() }
        Telemetry.resetForTests()
        WiringHost.clear()
        WiringResumes.clear()
        coroutineScope.cancel()
    }

    @Test
    fun backFromRootPushedScreenNeverRecordsMain() {
        showHost()
        openTab(homeTab, HOME_TAG)
        click(HOME_DETAILS_TAG, DETAILS_TAG)
        val mark = mark()
        click(BACK_TAG, HOME_TAG)

        val navs = navsAfter(mark)
        assertEquals(NavCmd.BACK, navs.first().cmd)
        assertEquals(WiringHomeScreen::class.java, navs.first().to)
        assertTrue(navs.none { it.to == hostClass || it.from == hostClass })
        assertTrue(screensAfter(mark).none { it == hostClass })
    }

    @Test
    fun backOntoTheHostRecordsNoTabReannounce() {
        showHost()
        openTab(homeTab, HOME_TAG)
        click(HOME_DETAILS_TAG, DETAILS_TAG)
        val mark = mark()
        click(BACK_TAG, HOME_TAG)

        assertTrue(navsAfter(mark).none { it.cmd == NavCmd.TAB })
        assertTrue(screensAfter(mark).all { it == WiringHomeScreen::class.java })
        assertEquals(WiringHomeScreen::class.java, Telemetry.screen)
    }

    @Test
    fun openingAnotherTabIsRecordedAndSetsTheScreen() {
        showHost()
        openTab(homeTab, HOME_TAG)
        val mark = mark()
        openTab(otherTab, OTHER_TAG)

        val tabNavs = navsAfter(mark).filter { it.cmd == NavCmd.TAB }
        assertEquals(1, tabNavs.size)
        assertEquals(WiringHomeScreen::class.java, tabNavs.single().from)
        assertEquals(WiringOtherScreen::class.java, tabNavs.single().to)
        assertEquals(WiringOtherScreen::class.java, Telemetry.screen)
    }

    @Test
    fun reselectingTheCurrentTabWithANewInstanceIsRecorded() {
        showHost()
        openTab(homeTab, HOME_TAG)
        val mark = mark()
        val reselected = PuberTab(WiringHomeScreen, TabType.Home)
        assertEquals(homeTab, reselected)
        assertNotSame(homeTab, reselected)
        openTab(reselected, HOME_TAG)

        val tabNavs = navsAfter(mark).filter { it.cmd == NavCmd.TAB }
        assertEquals(1, tabNavs.size)
        assertEquals(WiringHomeScreen::class.java, tabNavs.single().to)
        assertEquals(WiringHomeScreen::class.java, Telemetry.screen)
    }

    @Test
    fun tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack() {
        showHost()
        openTab(homeTab, HOME_TAG)
        val pushMark = mark()
        click(HOME_LOCAL_TAG, CHILD_TAG)
        click(CHILD_DETAILS_TAG, DETAILS_TAG)
        val push = navsAfter(pushMark).first { it.cmd == NavCmd.PUSH && it.to == WiringDetailsScreen::class.java }
        assertEquals(WiringChildScreen::class.java, push.from)
        val mark = mark()
        click(BACK_TAG, CHILD_TAG)
        assertTrue(composeRule.onAllNodesWithTag(HOME_TAG).fetchSemanticsNodes().isEmpty())

        val navs = navsAfter(mark)
        assertEquals(NavCmd.BACK, navs.first().cmd)
        assertEquals(WiringChildScreen::class.java, navs.first().to)
        assertTrue(screensAfter(mark).all { it == WiringChildScreen::class.java })
        assertEquals(WiringChildScreen::class.java, Telemetry.screen)
        assertTrue(
            navs.none { it.to == WiringHomeScreen::class.java || it.from == WiringHomeScreen::class.java },
        )
    }

    @Test
    fun rootPushAndBackRestoresBothTabStacks() {
        showHost()
        openTab(homeTab, HOME_TAG)
        click(HOME_LOCAL_TAG, CHILD_TAG)
        openTab(otherTab, OTHER_TAG)
        click(OTHER_LOCAL_TAG, OTHER_CHILD_TAG)
        click(OTHER_CHILD_DETAILS_TAG, DETAILS_TAG)
        click(BACK_TAG, OTHER_CHILD_TAG)
        assertTrue(composeRule.onAllNodesWithTag(OTHER_TAG).fetchSemanticsNodes().isEmpty())

        openTab(homeTab, CHILD_TAG)
        assertTrue(composeRule.onAllNodesWithTag(HOME_TAG).fetchSemanticsNodes().isEmpty())
        click(CHILD_BACK_TAG, HOME_TAG)
        assertTrue(composeRule.onAllNodesWithTag(CHILD_TAG).fetchSemanticsNodes().isEmpty())

        openTab(otherTab, OTHER_CHILD_TAG)
        click(OTHER_CHILD_BACK_TAG, OTHER_TAG)
    }

    @Test
    fun tabSwitchThenRootPushAndBackNamesTheNewTab() {
        showHost()
        openTab(homeTab, HOME_TAG)
        openTab(otherTab, OTHER_TAG)
        click(OTHER_DETAILS_TAG, DETAILS_TAG)
        val mark = mark()
        click(BACK_TAG, OTHER_TAG)

        val navs = navsAfter(mark)
        assertEquals(NavCmd.BACK, navs.first().cmd)
        assertEquals(WiringOtherScreen::class.java, navs.first().to)
        val screens = screensAfter(mark)
        assertFalse(screens.any { it == hostClass || it == WiringHomeScreen::class.java })
    }

    @Test
    fun replacedHostStartsAtTabRootsWhenShownAgain() {
        showHost()
        openTab(homeTab, HOME_TAG)
        click(HOME_LOCAL_TAG, CHILD_TAG)
        click(CHILD_DETAILS_TAG, DETAILS_TAG)
        click(LOGOUT_TAG, AUTH_TAG)
        assertTrue(composeRule.onAllNodesWithTag(HOME_TAG).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag(CHILD_TAG).fetchSemanticsNodes().isEmpty())

        // Mirrors the new MainVM (and its TabAppRouterHolder) of a fresh MainScreen.
        composeRule.runOnIdle {
            tabAppRouterHolder.dispose()
            tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
            WiringHost.bind(tabRouter, tabAppRouterHolder)
        }
        click(AUTH_RELOGIN_TAG, HOME_TAG)
        assertTrue(composeRule.onAllNodesWithTag(CHILD_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun resumeIsDeliveredToTabRootAfterRootPushAndBack() {
        showHost()
        openTab(homeTab, HOME_TAG)
        val before = WiringResumes.of(HOME_TAG)
        click(HOME_DETAILS_TAG, DETAILS_TAG)
        click(BACK_TAG, HOME_TAG)

        composeRule.runOnIdle { assertTrue(WiringResumes.of(HOME_TAG) > before) }
    }

    @Test
    fun resumeIsDeliveredToRestoredTabLocalScreenAfterRootPushAndBack() {
        showHost()
        openTab(homeTab, HOME_TAG)
        click(HOME_LOCAL_TAG, CHILD_TAG)
        val before = WiringResumes.of(CHILD_TAG)
        click(CHILD_DETAILS_TAG, DETAILS_TAG)
        click(BACK_TAG, CHILD_TAG)

        composeRule.runOnIdle { assertTrue(WiringResumes.of(CHILD_TAG) > before) }
    }

    private fun showHost() {
        composeRule.setContent {
            FlowComponent(
                scopeName = "TabHostTelemetryWiringTest",
                screen = WiringTabHostScreen(),
                moduleFactory = { scopeId, _ ->
                    module {
                        scope(named(scopeId)) {
                            scoped<AppLauncher> { WiringAppLauncher }
                            scoped<Screens> { ScreensImpl }
                        }
                    }
                },
            )
        }
    }

    private fun openTab(tab: PuberTab, tag: String) {
        composeRule.runOnIdle { tabRouter.openTab(tab) }
        waitForTag(tag)
        composeRule.waitForIdle()
    }

    private fun click(actionTag: String, expectedTag: String) {
        composeRule.onNodeWithTag(actionTag).performClick()
        waitForTag(expectedTag)
        composeRule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun mark(): Int {
        var size = 0
        composeRule.runOnIdle { size = recorder.records.size }
        return size
    }

    private fun navsAfter(mark: Int): List<TelemetryEvent.Nav> =
        synchronized(recorder.records) { recorder.records.drop(mark) }
            .map { it.first }
            .filterIsInstance<TelemetryEvent.Nav>()

    private fun screensAfter(mark: Int): List<Class<*>?> =
        synchronized(recorder.records) { recorder.records.drop(mark) }
            .filter { it.first is TelemetryEvent.Nav }
            .map { it.second }
}

/** Each pair holds the event and `Telemetry.screen` right after `recordNav` set it. */
private class WiringRecorder : TelemetryRecorder {
    override val isEnabled = true
    val records = Collections.synchronizedList(mutableListOf<Pair<TelemetryEvent, Class<*>?>>())

    override fun record(event: TelemetryEvent) {
        records += event to Telemetry.screen
    }
}

private data object WiringAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

/** Counts ON_RESUME deliveries per content tag, through the production `LifecycleAction`. */
private object WiringResumes {
    private val counts = mutableMapOf<String, Int>()

    fun record(tag: String) {
        synchronized(counts) { counts[tag] = (counts[tag] ?: 0) + 1 }
    }

    fun of(tag: String): Int = synchronized(counts) { counts[tag] ?: 0 }

    fun clear() {
        synchronized(counts) { counts.clear() }
    }
}

private object WiringHost {
    var tabRouter: TabRouter? = null
    var tabAppRouterHolder: TabAppRouterHolder? = null

    fun bind(
        tabRouter: TabRouter,
        tabAppRouterHolder: TabAppRouterHolder,
    ) {
        this.tabRouter = tabRouter
        this.tabAppRouterHolder = tabAppRouterHolder
    }

    fun clear() {
        tabRouter = null
        tabAppRouterHolder = null
    }
}

/** A class, like `MainScreen`, so host identity is exercised. */
@Parcelize
private class WiringTabHostScreen : PuberScreen, TabHostScreen {
    @Composable
    override fun Content() {
        CompositionLocalProvider(LocalTabHostScreen provides this) {
            TabComponent(
                tabRouter = requireNotNull(WiringHost.tabRouter),
                tabAppRouterHolder = requireNotNull(WiringHost.tabAppRouterHolder),
            ) {
                PuberCurrentTab()
            }
        }
    }
}

@Parcelize
private data object WiringHomeScreen : PuberScreen {
    @Composable
    override fun Content() {
        WiringTabContent(
            HOME_TAG,
            "Home",
            localTag = HOME_LOCAL_TAG,
            localScreen = WiringChildScreen,
            detailsTag = HOME_DETAILS_TAG,
        )
    }
}

@Parcelize
private data object WiringOtherScreen : PuberScreen {
    @Composable
    override fun Content() {
        WiringTabContent(
            OTHER_TAG,
            "Other",
            localTag = OTHER_LOCAL_TAG,
            localScreen = WiringOtherChildScreen,
            detailsTag = OTHER_DETAILS_TAG,
        )
    }
}

@Parcelize
private data object WiringChildScreen : PuberScreen {
    @Composable
    override fun Content() {
        WiringTabContent(
            CHILD_TAG,
            "Child",
            localTag = null,
            localScreen = null,
            detailsTag = CHILD_DETAILS_TAG,
            backTag = CHILD_BACK_TAG,
        )
    }
}

@Parcelize
private data object WiringOtherChildScreen : PuberScreen {
    @Composable
    override fun Content() {
        WiringTabContent(
            OTHER_CHILD_TAG,
            "Other child",
            localTag = null,
            localScreen = null,
            detailsTag = OTHER_CHILD_DETAILS_TAG,
            backTag = OTHER_CHILD_BACK_TAG,
        )
    }
}

@Parcelize
private data object WiringDetailsScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        Column {
            Text(text = "Details", modifier = Modifier.testTag(DETAILS_TAG))
            Button(
                modifier = Modifier.testTag(LOGOUT_TAG),
                onClick = { router.newRootScreen(WiringAuthScreen) },
            ) {
                Text("Logout")
            }
            Button(modifier = Modifier.testTag(BACK_TAG), onClick = router::back) {
                Text("Back")
            }
        }
    }
}

@Parcelize
private data object WiringAuthScreen : PuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        Column {
            Text(text = "Auth", modifier = Modifier.testTag(AUTH_TAG))
            Button(
                modifier = Modifier.testTag(AUTH_RELOGIN_TAG),
                onClick = { router.newRootScreen(WiringTabHostScreen()) },
            ) {
                Text("Relogin")
            }
        }
    }
}

@Composable
private fun WiringTabContent(
    tag: String,
    title: String,
    localTag: String?,
    localScreen: PuberScreen?,
    detailsTag: String,
    backTag: String? = null,
) {
    val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
    LifecycleAction(
        event = Lifecycle.Event.ON_RESUME,
        onAction = { WiringResumes.record(tag) },
        action = CommonAction.OnResume,
    )
    Column {
        Text(text = title, modifier = Modifier.testTag(tag))
        if (localTag != null && localScreen != null) {
            Button(
                modifier = Modifier.testTag(localTag),
                onClick = { router.navigateTo(localScreen) },
            ) {
                Text("Local")
            }
        }
        Button(
            modifier = Modifier.testTag(detailsTag),
            onClick = { router.navigateTo(WiringDetailsScreen) },
        ) {
            Text("Details")
        }
        if (backTag != null) {
            Button(modifier = Modifier.testTag(backTag), onClick = router::back) {
                Text("Back")
            }
        }
    }
}
