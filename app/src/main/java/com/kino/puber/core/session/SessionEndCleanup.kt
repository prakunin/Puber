package com.kino.puber.core.session

import com.kino.puber.core.logger.log
import kotlinx.coroutines.CancellationException

/**
 * The clears that follow the end of a session. [run] is called by every handler that replays the
 * end; only the first one does the work. The deep-link reset happens once per end. The other clears
 * are best-effort and run again for a replay when the handler was cancelled before they all
 * finished, so an ended account's caches cannot survive to the next account.
 */
internal class SessionEndCleanup(
    private val bus: SessionEventBus,
    private val resetDeepLink: () -> Unit,
    private val clears: List<suspend () -> Unit>,
) {
    suspend fun run() {
        if (!bus.claimPendingEnd()) return
        var finished = false
        try {
            if (bus.claimLinkReset()) resetDeepLink()
            // An exception escaping would kill the app's only session-end collector, so a failing
            // clear is logged and the next one still runs.
            clears.forEach { clear ->
                try {
                    clear()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    log(error, "Failed to clear session-scoped state on logout")
                }
            }
            finished = true
        } finally {
            // Cancelled mid-way: give the claim back so a replay clears again.
            if (!finished) bus.releasePendingEnd()
        }
    }
}
