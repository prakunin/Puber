package com.kino.puber.core.ui.navigation.component

import androidx.compose.runtime.saveable.SaveableStateHolder
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import com.kino.puber.core.telemetry.HistoryScreen
import com.kino.puber.core.telemetry.HomeScreen
import com.kino.puber.core.telemetry.ScreenScreen
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(InternalVoyagerApi::class)
internal class NavigatorChangedByTest {
    private val recorder = RecordingTelemetryRecorder()
    private val parent = navigator("parent", null, HomeScreen(), ScreenScreen())
    private val child = navigator("child", parent, HistoryScreen())

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, FakeTelemetryClock())
    }

    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    private fun navigator(key: String, parent: Navigator?, vararg screens: PuberScreen) = Navigator(
        screens = screens.toList(),
        key = key,
        stateHolder = mockk<SaveableStateHolder>(relaxed = true),
        disposeBehavior = NavigatorDisposeBehavior(),
        parent = parent,
    )

    @Test
    fun backOnASingleScreenChildChangesTheParent() {
        assertSame(parent, navigatorChangedBy(Command.Back, child))
        assertSame(parent, navigatorChangedBy(Command.FinishFlow, child))
    }

    @Test
    fun backWithRoomInTheRunnerChangesTheRunner() {
        child.push(HomeScreen())
        assertSame(child, navigatorChangedBy(Command.Back, child))
    }

    @Test
    fun finishFlowChangesTheParentEvenWhenTheChildCouldPop() {
        child.push(HomeScreen())
        assertSame(child, navigatorChangedBy(Command.Back, child))
        assertSame(parent, navigatorChangedBy(Command.FinishFlow, child))
    }

    @Test
    fun reportedNavigatorIsTheOneTheRealPopRoutineShrinks() {
        assertShrinks(Command.Back, expected = parent)
        assertShrinks(Command.FinishFlow, expected = parent)
        child.push(HomeScreen())
        assertShrinks(Command.Back, expected = child)
        assertShrinks(Command.FinishFlow, expected = parent)
    }

    private fun assertShrinks(event: Command, expected: Navigator) {
        val reported = navigatorChangedBy(event, child)
        assertSame(expected, reported)
        val before = listOf(parent, child).associateWith { it.size }
        val runner = if (event == Command.FinishFlow) checkNotNull(child.parent) else child
        onBackWithNavigator(runner, null)
        val shrunk = before.filter { (nav, size) -> nav.size < size }.keys
        assertEquals(setOf(reported), shrunk)
        // Put back what the pop took, for the next scenario.
        if (parent.size < before.getValue(parent)) parent.push(ScreenScreen())
        if (child.size < before.getValue(child)) child.push(HomeScreen())
    }

    @Test
    fun backThatPopsTheParentRecordsTheParentTop() {
        val changed = navigatorChangedBy(Command.Back, child)
        val from = changed.lastItemOrNull
        changed.pop()
        recordNavCommand(Command.Back, from, changed.lastItemOrNull)

        assertEquals(HomeScreen::class.java, Telemetry.screen)
        val nav = recorder.events.filterIsInstance<TelemetryEvent.Nav>().single()
        assertEquals(ScreenScreen::class.java, nav.from)
        assertEquals(HomeScreen::class.java, nav.to)
    }
}
