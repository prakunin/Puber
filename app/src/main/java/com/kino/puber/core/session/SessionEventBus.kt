package com.kino.puber.core.session

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

sealed interface SessionEvent {
    data class Unauthorized(val reason: SessionEndReason) : SessionEvent
}

/**
 * Carries the end of the session to the app. The latest end is kept until [clearPendingEnd], so a
 * collector that starts after the end (the app warms up before the UI collects) still gets it.
 */
class SessionEventBus {
    // Not a data class: two equal ends must be two distinct StateFlow values.
    private class Pending(val event: SessionEvent) {
        val claimed = AtomicBoolean(false)
        val linkReset = AtomicBoolean(false)
    }

    private val pending = MutableStateFlow<Pending?>(null)

    val events: Flow<SessionEvent> = pending.filterNotNull().map { it.event }

    val hasPendingEnd: Boolean get() = pending.value != null

    fun emit(event: SessionEvent) {
        pending.value = Pending(event)
    }

    /**
     * True once per end, for the first caller: the one that runs the clears of the end. A handler
     * that replays an end already handled only routes, so it cannot wipe state accepted since.
     */
    fun claimPendingEnd(): Boolean = pending.value?.claimed?.compareAndSet(false, true) ?: false

    /** Gives the claim back when its holder was cancelled before the clears finished. */
    fun releasePendingEnd() {
        pending.value?.claimed?.set(false)
    }

    /** True once per end: the deep-link reset must not run again on a replay or a retry. */
    fun claimLinkReset(): Boolean = pending.value?.linkReset?.compareAndSet(false, true) ?: false

    fun clearPendingEnd() {
        pending.value = null
    }
}
