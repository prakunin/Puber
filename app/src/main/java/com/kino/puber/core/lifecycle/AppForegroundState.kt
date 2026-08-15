package com.kino.puber.core.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether the app is on screen, for background work that should not run while it is not.
 *
 * Reported by the main screen, which is the only screen whose lifecycle spans the whole session and
 * already observes the events this needs. That makes this a claim about the app being visible, not
 * about which screen is showing: navigating to the player or a details page happens inside the same
 * activity and does not touch it.
 *
 * Starts out foreground. The alternative — starting backgrounded and waiting to be told otherwise —
 * would park every waiter until the first lifecycle event arrives, including on a cold start where
 * the app plainly is in front of the user.
 */
class AppForegroundState {

    private val mutableIsForeground = MutableStateFlow(true)

    val isForeground: StateFlow<Boolean> = mutableIsForeground.asStateFlow()

    fun onEnteredForeground() {
        mutableIsForeground.value = true
    }

    fun onLeftForeground() {
        mutableIsForeground.value = false
    }

    /** Returns as soon as the app is on screen, immediately if it already is. */
    suspend fun awaitForeground() {
        isForeground.first { it }
    }
}
