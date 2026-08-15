package com.kino.puber.domain.interactor.prefetch

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.core.lifecycle.AppForegroundState
import com.kino.puber.core.logger.log
import com.kino.puber.data.repository.ItemDetailsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Fetches the details of cards focus is sitting on, and of the cards it may reach next.
 *
 * To press OK the user must first move focus onto a card, and focus usually rests there for a
 * moment first. That pause is enough to fetch what the details screen is about to ask for, so the
 * screen opens on a cache hit instead of a spinner. Focus also telegraphs where the next press may
 * land: from any card the D-pad can only reach four others.
 *
 * Nothing here knows about layout. It is given an ordered list of candidates and decides only when
 * and how many — which keeps the whole policy in one place that can be tested on virtual time.
 *
 * Not screen-scoped: focus survives moves between tabs, and a warm already on the network should
 * not be tied to the lifetime of the screen that asked for it.
 */
class DetailsPrefetcher(
    private val details: ItemDetailsRepository,
    private val foreground: AppForegroundState,
    private val focusedDwell: Duration = 250.milliseconds,
    private val neighbourDwell: Duration = 750.milliseconds,
    private val recentlyWarmedWindow: Duration = 60.seconds,
    maxConcurrent: Int = 2,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Opaque identity of one composed list surface. */
    class SurfaceId internal constructor()

    /**
     * OkHttp allows five connections per host. Two of them is as much as work nobody asked for may
     * take; the rest belongs to the requests the screen the user is looking at actually needs.
     */
    private val permits = Semaphore(maxConcurrent)

    private val warming = ConcurrentHashMap.newKeySet<Int>()

    /**
     * When each id was last warmed to a usable value.
     *
     * Without this, rocking focus left and right re-warms the same two cards for as long as the user
     * keeps doing it. The window is well under `CacheTtl.ItemDetails`, so it only suppresses repeats
     * and never stands in for the cache's own freshness rule.
     */
    private val warmedAt = ConcurrentHashMap<Int, Long>()

    /**
     * The surface focus is currently in, and the work scheduled for the card it is on. Both are
     * touched from the composition's thread, where focus callbacks and disposals arrive.
     */
    @Volatile
    private var activeSurface: SurfaceId? = null

    @Volatile
    private var scheduled: Job? = null

    /** Focus has landed on [itemId]; [neighbours] is where it may go next, likeliest first. */
    fun onFocused(surfaceId: SurfaceId, itemId: Int, neighbours: List<Int>) {
        // Everything the previous position had lined up has stopped being a prediction, including
        // its own card: focus moved off it before it was worth fetching.
        scheduled?.cancel()
        activeSurface = surfaceId
        // Two timers side by side rather than one behind the other. Both thresholds are measured
        // from the moment focus landed, and the focused card's own warm can be held up — by a busy
        // slot, by an app that is off screen — for far longer than the gap between them. Run in
        // sequence, that wait would push the neighbours out by however long it lasted. Cancelling
        // the job below still cancels both, and the focused card still reaches a slot first.
        scheduled = scope.launch {
            launch {
                delay(focusedDwell)
                warm(itemId)
            }
            launch {
                delay(neighbourDwell)
                neighbours.forEach { neighbour -> warm(neighbour) }
            }
        }
    }

    /**
     * The list surface went away; its scheduled work is dropped if it is still active.
     *
     * Guarded on the id because disposal is not ordered against focus: a tab or navigation
     * transition composes the incoming surface, which takes focus, and only then disposes the
     * outgoing one. An unguarded drop there would erase the schedule of the screen now on show.
     */
    fun onSurfaceGone(surfaceId: SurfaceId) {
        if (activeSurface !== surfaceId) return
        scheduled?.cancel()
        scheduled = null
        activeSurface = null
    }

    /**
     * Takes a slot and hands the fetch to [scope], so that a focus change from here on cancels only
     * what has not started.
     *
     * Both suspension points before the hand-off — the foreground wait and the slot — are still part
     * of "not started", and cancelling there is exactly right. Once the fetch is detached it is left
     * to finish: it is one request, `CachedFeed` deduplicates it against the one a press would
     * issue, and cancelling it would sometimes throw away precisely the work this class exists for.
     */
    private suspend fun warm(itemId: Int) {
        // Asked before the slot so that an app already off screen does not sit in the queue holding
        // one, and again after it because the wait itself can outlast the user's stay.
        foreground.awaitForeground()
        if (isStillWarm(itemId)) return
        permits.acquire()
        var handedOff = false
        try {
            foreground.awaitForeground()
            // Claimed here rather than before the waits, and released by the fetch below. An id
            // claimed on the way in and then abandoned by a focus change — which is what
            // cancellation at any suspension point above means — would stay claimed with nothing
            // left to clear it, and that card would be refused for the rest of the session. There
            // is no suspension point between the claim and the hand-off, so nothing slips between.
            if (isStillWarm(itemId) || !warming.add(itemId)) return
            handedOff = true
            scope.launch {
                try {
                    runCatchingCancellable { details.warmItemDetails(itemId) }
                        .onSuccess { warmedAt[itemId] = clock() }
                        // Nobody asked for this, so nobody may be told it failed: no snackbar, no
                        // error state, no touching a ViewState. A failed first warm leaves the id
                        // out of the recently-warmed set, so a later attempt is still allowed.
                        .onFailure { error -> log(error, "Failed to prefetch details for item $itemId") }
                } finally {
                    warming.remove(itemId)
                    permits.release()
                }
            }
        } finally {
            // Every way out of here that did not hand the slot on has to give it back: a return
            // above, or a cancellation while parked waiting for the app to come back.
            if (!handedOff) permits.release()
        }
    }

    /**
     * Forgets what was warmed, for callers that have just emptied the details cache — a domain
     * switch, a logout.
     *
     * Everything this remembers is a claim about that cache, and the claim dies with it. Left
     * standing, it suppresses fetches for up to a minute on ids whose entries are gone, which puts
     * a spinner in front of exactly the cards the user is about to open.
     *
     * Warms already in flight are left alone: `CachedFeed` withdraws a result that lands after a
     * wipe, and each of them clears its own entry on the way out.
     */
    fun invalidate() {
        warmedAt.clear()
    }

    /** Whether this id was warmed recently enough that fetching it again would buy nothing. */
    private fun isStillWarm(itemId: Int): Boolean {
        val lastWarm = warmedAt[itemId] ?: return false
        if (clock() - lastWarm < recentlyWarmedWindow.inWholeMilliseconds) return true
        warmedAt.remove(itemId, lastWarm)
        return false
    }
}
