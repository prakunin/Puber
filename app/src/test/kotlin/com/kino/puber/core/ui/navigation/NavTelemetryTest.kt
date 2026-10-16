package com.kino.puber.core.ui.navigation

import android.os.Parcel
import com.kino.puber.core.telemetry.HomeScreen
import com.kino.puber.core.telemetry.NavCmd
import com.kino.puber.core.telemetry.ScreenScreen
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.telemetry.tabOf
import com.kino.puber.core.ui.navigation.component.TabHostTelemetry
import com.kino.puber.core.ui.navigation.component.TabRootScreen
import com.kino.puber.core.ui.navigation.component.navCmdOf
import com.kino.puber.core.ui.navigation.component.recordNav
import com.kino.puber.core.ui.navigation.component.recordNavCommand
import com.kino.puber.ui.feature.main.component.MainScreen
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NavTelemetryTest {
    private val recorder = RecordingTelemetryRecorder()
    private val home = HomeScreen()
    private val other = ScreenScreen()
    private var binding: TabHostTelemetry.Binding? = null

    private class FakeActivityScreen : PuberScreenActivity() {
        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, FakeTelemetryClock())
    }

    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
        binding?.let(TabHostTelemetry::unbind)
    }

    private fun navs() = recorder.events.filterIsInstance<TelemetryEvent.Nav>()

    @Test
    fun commandsMapToCmdWithFromAndTo() {
        assertEquals(NavCmd.PUSH, navCmdOf(Command.NavigateTo(home)))
        assertEquals(NavCmd.PUSH_RESULT, navCmdOf(Command.NavigateForResult(home, 1) {}))
        assertEquals(NavCmd.REPLACE, navCmdOf(Command.Replace(home)))
        assertEquals(NavCmd.NEW_ROOT, navCmdOf(Command.NewRoot(listOf(home))))
        assertEquals(NavCmd.BACK_TO, navCmdOf(Command.BackTo(home)))
        assertEquals(NavCmd.FINISH_FLOW, navCmdOf(Command.FinishFlow))
        assertEquals(NavCmd.BACK, navCmdOf(Command.Back))

        recordNavCommand(Command.NavigateTo(other), home, other)
        recordNavCommand(Command.Back, other, home)
        recordNavCommand(Command.NavigateTo(FakeActivityScreen()), home, home)

        val events = navs()
        assertEquals(listOf(NavCmd.PUSH, NavCmd.BACK, NavCmd.ACTIVITY), events.map { it.cmd })
        assertEquals(HomeScreen::class.java, events[0].from)
        assertEquals(ScreenScreen::class.java, events[0].to)
        assertEquals(FakeActivityScreen::class.java, events[2].to)
    }

    @Test
    fun tabsAreReportedAsTheScreenTheyWrap() {
        recordNav(NavCmd.TAB, tabOf(home), tabOf(other))
        assertEquals(HomeScreen::class.java, navs().single().from)
        assertEquals(ScreenScreen::class.java, navs().single().to)
    }

    @Test
    fun nothingIsRecordedWhenOff() {
        recorder.isEnabled = false
        recordNav(NavCmd.PUSH, home, other)
        recordNavCommand(Command.Back, home, other)
        assertEquals(0, recorder.events.size)
    }

    @Test
    fun recordNavSetsScreenHolderWhenOnAndNeverWhenOffOrActivity() {
        recordNav(NavCmd.PUSH, home, other)
        assertEquals(ScreenScreen::class.java, Telemetry.screen)
        recordNav(NavCmd.TAB, other, tabOf(home))
        assertEquals(HomeScreen::class.java, Telemetry.screen)
        recordNav(NavCmd.ACTIVITY, home, FakeActivityScreen())
        assertEquals(HomeScreen::class.java, Telemetry.screen)

        Telemetry.resetForTests()
        Telemetry.install(recorder, FakeTelemetryClock())
        recorder.isEnabled = false
        recordNav(NavCmd.PUSH, home, other)
        assertNull(Telemetry.screen)
    }

    private fun bindTop(host: MainScreen, top: () -> Any?) {
        binding = TabHostTelemetry.bind(host, top)
    }

    @Test
    fun tabHostIsReportedAsTheTopOfItsLiveTabNavigator() {
        val host = MainScreen()
        bindTop(host) { TabRootScreen(home, "slot") }
        recordNavCommand(Command.Back, other, host)
        assertEquals(HomeScreen::class.java, Telemetry.screen)
        assertEquals(HomeScreen::class.java, navs().single().to)
    }

    @Test
    fun tabLocalPushThenRootBackNamesThePushedScreen() {
        val host = MainScreen()
        var top: Any? = home
        bindTop(host) { top }
        top = other
        recordNavCommand(Command.Back, FakeActivityScreen(), host)
        assertEquals(ScreenScreen::class.java, Telemetry.screen)
        assertEquals(ScreenScreen::class.java, navs().single().to)
    }

    @Test
    fun pushFromTheHostIsFromTheTabContent() {
        val host = MainScreen()
        bindTop(host) { home }
        recordNavCommand(Command.NavigateTo(other), host, other)
        assertEquals(HomeScreen::class.java, navs().single().from)
    }

    @Test
    fun staleBindingOfAnEarlierHostGivesTheFallbackForAFreshHost() {
        bindTop(MainScreen()) { home }
        recordNavCommand(Command.NewRoot(listOf(MainScreen())), other, MainScreen())
        assertEquals(MainScreen::class.java, Telemetry.screen)
    }

    @Test
    fun clearedBindingGivesTheFallback() {
        val host = MainScreen()
        TabHostTelemetry.unbind(TabHostTelemetry.bind(host) { home })
        recordNavCommand(Command.Back, other, host)
        assertEquals(MainScreen::class.java, Telemetry.screen)
    }

    @Test
    fun tabHostWithoutKnownTabFallsBackToItself() {
        recordNavCommand(Command.Back, other, MainScreen())
        assertEquals(MainScreen::class.java, Telemetry.screen)
    }
}
