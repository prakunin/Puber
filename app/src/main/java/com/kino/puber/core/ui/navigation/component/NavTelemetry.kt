package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.telemetry.NavCmd
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.telemetry.telemetryClassOf
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.PuberScreenActivity
import com.kino.puber.core.ui.navigation.TabHostScreen

internal fun navCmdOf(command: Command): NavCmd = when (command) {
    is Command.NavigateTo -> NavCmd.PUSH
    is Command.NavigateForResult -> NavCmd.PUSH_RESULT
    is Command.Replace -> NavCmd.REPLACE
    is Command.NewRoot -> NavCmd.NEW_ROOT
    is Command.BackTo -> NavCmd.BACK_TO
    Command.FinishFlow -> NavCmd.FINISH_FLOW
    Command.Back -> NavCmd.BACK
}

/**
 * What the tab host shows is the top of the live tab navigator, bound while that navigator is
 * composed and cleared when it leaves. A binding is tied to the host instance it was made for, so
 * a fresh host never reads a previous host's tab.
 */
internal object TabHostTelemetry {
    internal class Binding(val host: Any, val top: () -> Any?)

    @Volatile
    private var binding: Binding? = null

    fun bind(host: Any, top: () -> Any?): Binding = Binding(host, top).also { binding = it }

    fun unbind(handle: Binding) {
        if (binding === handle) binding = null
    }

    fun topOf(host: Any): Any? = binding?.takeIf { it.host === host }?.top?.invoke()
}

private fun navClassOf(screen: Any?): Class<*>? {
    if (screen is TabHostScreen) TabHostTelemetry.topOf(screen)?.let { return telemetryClassOf(it) }
    return telemetryClassOf(screen)
}

/** Class names only: never a screen key, which carries ids on parameterized screens. */
internal fun recordNav(cmd: NavCmd, from: Any?, to: Any?) {
    if (!Telemetry.isEnabled) return
    val target = navClassOf(to)
    if (cmd != NavCmd.ACTIVITY) Telemetry.screen = target
    Telemetry.record(TelemetryEvent.Nav(Telemetry.nowMs(), cmd, navClassOf(from), target))
}

/** A router command seen by a flow runner; [top] is the navigator's top screen after it ran. */
internal fun recordNavCommand(event: Command, from: Any?, top: Any?) {
    val screen = event.screen
    if (screen is PuberScreenActivity) {
        recordNav(NavCmd.ACTIVITY, from, screen)
    } else {
        recordNav(navCmdOf(event), from, top)
    }
}
