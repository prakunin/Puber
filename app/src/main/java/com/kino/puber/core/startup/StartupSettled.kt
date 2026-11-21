package com.kino.puber.core.startup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val STARTUP_SLOT_GAP: Duration = 500.milliseconds

/** Order of the background work that waits for startup to settle; prefetch has no slot, it uses [StartupSettled.await]. */
internal enum class StartupSlot {
    TvHome, UpdateCheck, DeviceInfo, WatchState;

    val offset: Duration get() = STARTUP_SLOT_GAP * (ordinal + 1)
}

/**
 * A process-wide latch that completes once, when the first start is done competing with background
 * work: the splash has left composition, the activity was restored (no splash), or [fallback] has
 * passed since a fresh activity was created. It is independent of [StartupReveal.covered].
 */
internal class StartupSettled(
    private val scope: CoroutineScope,
    private val fallback: Duration = 5.seconds,
) {
    private val settled = CompletableDeferred<Unit>()
    private val fallbackArmed = AtomicBoolean(false)

    val isSettled: Boolean get() = settled.isCompleted

    fun onActivityCreated(restored: Boolean) {
        if (restored) {
            settled.complete(Unit)
        } else if (!settled.isCompleted && fallbackArmed.compareAndSet(false, true)) {
            scope.launch {
                delay(fallback)
                settled.complete(Unit)
            }
        }
    }

    fun onSplashGone() {
        settled.complete(Unit)
    }

    suspend fun await() {
        settled.await()
    }

    /** Waits for the latch, then for the slot's offset counted from the moment this call resumes. */
    suspend fun awaitSlot(slot: StartupSlot) {
        await()
        delay(slot.offset)
    }
}
