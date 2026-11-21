package com.kino.puber.data.cache

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.core.lifecycle.AppForegroundState
import com.kino.puber.data.repository.PersistentPayloadStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the `cached_payload` table bounded: once shortly after startup, so the sweep does not
 * compete with the first screens, and again every time the app leaves the screen.
 *
 * Rows older than [CachedFeed.HardCeiling] are never served (the read path drops them), so deleting
 * them changes no behaviour; the row cap then trims what a long browsing session piled up.
 *
 * The first sweep also runs [trimMediaCache] once per process, off the main thread (the scope is
 * on IO), to shrink a media cache that predates a lower size cap.
 */
class CachedPayloadSweeper(
    private val store: PersistentPayloadStore,
    private val foreground: AppForegroundState,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val startupDelay: Duration = STARTUP_DELAY,
    private val maxRows: Int = MAX_ROWS,
    private val trimMediaCache: () -> Unit = {},
) {

    private val started = AtomicBoolean(false)
    private val mediaTrimmed = AtomicBoolean(false)

    /** Idempotent: a second call does not start a second collector. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // drop(1) is defensive: the flow starts at true, which `filter { !it }` already rejects,
            // so removing it changes nothing a test can observe.
            merge(
                flow {
                    delay(startupDelay)
                    emit(Unit)
                },
                foreground.isForeground.drop(1).filter { isForeground -> !isForeground }.map { },
            ).collect { sweepOnce() }
        }
    }

    private suspend fun sweepOnce() {
        if (mediaTrimmed.compareAndSet(false, true)) {
            runCatchingCancellable { trimMediaCache() }
                .onFailure { error -> Timber.w(error, "Media cache trim failed") }
        }
        runCatchingCancellable {
            store.sweep(
                olderThan = clock() - CachedFeed.HardCeiling.inWholeMilliseconds,
                keepNewest = maxRows,
            )
        }.onFailure { error -> Timber.w(error, "Cached payload sweep failed") }
    }

    companion object {
        private val STARTUP_DELAY = 60.seconds
        private const val MAX_ROWS = 2_000

        /** Off the main thread, and a failure in one sweep does not cancel the scope. */
        fun defaultScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
