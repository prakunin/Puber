package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.telemetry.NavCmd
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.telemetry.telemetryClassOf
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.PuberScreenActivity

internal fun navCmdOf(command: Command): NavCmd = when (command) {
    is Command.NavigateTo -> NavCmd.PUSH
    is Command.NavigateForResult -> NavCmd.PUSH_RESULT
    is Command.Replace -> NavCmd.REPLACE
    is Command.NewRoot -> NavCmd.NEW_ROOT
    is Command.BackTo -> NavCmd.BACK_TO
    Command.FinishFlow -> NavCmd.FINISH_FLOW
    Command.Back -> NavCmd.BACK
}

/** Class names only: never a screen key, which carries ids on parameterized screens. */
internal fun recordNav(cmd: NavCmd, from: Any?, to: Any?) {
    if (!Telemetry.isEnabled) return
    Telemetry.record(TelemetryEvent.Nav(Telemetry.nowMs(), cmd, telemetryClassOf(from), telemetryClassOf(to)))
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
