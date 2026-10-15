package com.kino.puber.core.ui.navigation

import android.os.Parcel
import com.kino.puber.core.telemetry.HomeScreen
import com.kino.puber.core.telemetry.NavCmd
import com.kino.puber.core.telemetry.ScreenScreen
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.telemetry.tabOf
import com.kino.puber.core.ui.navigation.component.navCmdOf
import com.kino.puber.core.ui.navigation.component.recordNav
import com.kino.puber.core.ui.navigation.component.recordNavCommand
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NavTelemetryTest {
    private val recorder = RecordingTelemetryRecorder()
    private val home = HomeScreen()
    private val other = ScreenScreen()

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
}
