package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue

/** Which of the Main-level Back handlers governs the next Back press. Exactly one is enabled. */
internal enum class RailBackOwner {
    /** Rail closed over a tab root: Back opens the rail, or leaves the app when armed. */
    RailClosed,

    /** A screen is pushed in the tab and the rail is closed: Back pops it. */
    Tab,

    /** The rail is open or handing off: Back closes it. */
    RailOpen,
}

/**
 * The one owner of Back for the current rail and tab stack state, so the outcome does not depend on
 * the order the handlers happened to register in.
 *
 * @param drawer the rail's state, or null where there is no rail host.
 * @param tabCanPop whether the tab's navigator has a screen to pop.
 * @return null when nothing owns Back (no rail host and nothing to pop).
 */
internal fun railBackOwner(drawer: DrawerValue?, tabCanPop: Boolean): RailBackOwner? = when {
    drawer == DrawerValue.Open || drawer == DrawerValue.HandingOff -> RailBackOwner.RailOpen
    tabCanPop -> RailBackOwner.Tab
    drawer == DrawerValue.Closed -> RailBackOwner.RailClosed
    else -> null
}
