package com.kino.puber.data.api.network

import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * The last word on whether an API domain answers, shared between the code that finds out and the
 * code that chooses.
 *
 * Probing a domain costs a full GET whose body has to be read and parsed, and the home screen
 * resolves before every load — so a domain that answered a moment ago is taken on trust rather than
 * asked again. That trust is what this holds, and it needs both halves of the story to be safe: a
 * probe saying the domain answered, and the client saying a request to it never arrived. With only
 * the first, a mirror that dies just after a probe keeps being chosen until the window lapses.
 *
 * Lives here rather than with the interactor that reads it because the client that reports failures
 * cannot see the domain layer, and neither should have to.
 */
class EndpointReachability(private val clock: () -> Long = System::currentTimeMillis) {

    /**
     * Written from whichever IO thread probes or fails, read from whichever one resolves next.
     *
     * An atomic reference rather than a volatile field, because retiring a verdict is a read, a
     * comparison and a write: probes and failures run concurrently on the OkHttp pool, so between
     * the read and the write a probe can install a verdict for a different domain, and a plain
     * write would then discard news it never looked at.
     */
    private val lastReachable = AtomicReference<ReachableDomain?>(null)

    /** Records that [domain] answered, just now. */
    fun markReachable(domain: String) {
        lastReachable.set(ReachableDomain(domain, clock()))
    }

    /**
     * Records that [domain] could not be reached, retiring any standing verdict for it.
     *
     * Keyed by domain because a failure can take a connect timeout to surface, by which point the
     * app may have switched elsewhere — and a verdict earned by the new domain must not be taken
     * down by news about the old one. That is also why the compare and the clear happen in one
     * atomic step: a switch landing between them is exactly the case this guards, so checking a copy
     * read moments earlier would let the stale news win after all.
     */
    fun markUnreachable(domain: String) {
        lastReachable.updateAndGet { known -> if (known?.domain == domain) null else known }
    }

    /**
     * Whether [domain] answered inside [window] and has not been reported unreachable since.
     *
     * The window is a tolerance for staleness rather than a claim the domain is still up. Once it
     * lapses the failover walk runs again, so a domain blocked since the last probe recovers even if
     * nothing ever reports it.
     */
    fun answeredWithin(domain: String, window: Duration): Boolean {
        val known = lastReachable.get() ?: return false
        return known.domain == domain && clock() - known.at < window.inWholeMilliseconds
    }

    /** The last domain a probe confirmed, and when it answered. */
    private data class ReachableDomain(val domain: String, val at: Long)
}
