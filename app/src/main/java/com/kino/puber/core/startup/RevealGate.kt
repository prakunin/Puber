package com.kino.puber.core.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.IdentityHashMap

internal const val REVEAL_CAP_MS = 2_500L

/**
 * Whether the startup splash still covers the app, and how long the cap leaves it. Image requests
 * are tracked by identity, so a request that ends twice cannot release another one. [observer] is
 * told the edges the startup telemetry records, from inside the gate lock.
 */
internal class RevealGate(
    private val clock: () -> Long,
    private val capMs: Long = REVEAL_CAP_MS,
    /** Runs the block once the screen has been drawn; a ready mark waits for it. */
    private val afterFrame: (() -> Unit) -> Unit = { it() },
    private val observer: RevealObserver = RevealObserver.None,
) {
    private val state = MutableStateFlow(false)
    val covered: StateFlow<Boolean> = state.asStateFlow()

    private val lock = Any()

    @Volatile
    private var beganMs = NOT_BEGUN
    private var begun = false
    private var ready = false
    private var forcedCause: RevealCause? = null
    private val inFlight: MutableSet<Any> = identitySet()
    private val held: MutableSet<Any> = identitySet()
    private var revealedOnce = false
    private var generation = 0

    /** Covers the app and counts the cap from now; images in flight from before it still count. */
    fun begin() = update {
        beganMs = clock()
        generation++
        begun = true
        ready = false
        forcedCause = null
        revealedOnce = false
        held.clear()
    }

    /**
     * The landing screen has content; the splash still waits for the images in flight. The mark
     * counts from the next drawn frame, so the screen's composition has issued its image requests
     * before they are looked at. A mark made before a later begin() or reset() is dropped.
     */
    fun markReady() {
        val markedIn = synchronized(lock) {
            if (begun) observer.onReadyRequested()
            generation
        }
        afterFrame {
            update {
                if (generation == markedIn && !ready) {
                    ready = true
                    if (begun) {
                        held.addAll(inFlight)
                        observer.onReady(held.size)
                    }
                }
            }
        }
    }

    fun onCap() = update { if (forcedCause == null) forcedCause = RevealCause.CAP }

    /** Reveals regardless of content and images. */
    fun revealNow() = update { if (forcedCause == null) forcedCause = RevealCause.FORCED }

    /** An image request of the loader has its size and is now loading. */
    fun onImageStarted(key: Any) = update { inFlight.add(key) }

    /** The request ended, by success, error or cancel; a second end of one key changes nothing. */
    fun onImageFinished(key: Any) = update {
        if (!inFlight.remove(key)) return@update
        val wasHeld = held.remove(key)
        if (begun && !revealedOnce && inFlight.isEmpty()) observer.onImagesIdle()
        if (begun && wasHeld && held.isEmpty()) observer.onHeldDrained()
    }

    fun remainingCapMs(): Long {
        val began = beganMs
        if (began == NOT_BEGUN) return 0
        return maxOf(0L, began + capMs - clock())
    }

    fun reset() = update {
        beganMs = NOT_BEGUN
        generation++
        begun = false
        ready = false
        forcedCause = null
        inFlight.clear()
        held.clear()
        revealedOnce = false
    }

    // Once uncovered, the splash never covers again until the next begin(). A forced reveal before
    // begin() (a restored start) is still reported once, though it never covered anything.
    private inline fun update(change: () -> Unit) = synchronized(lock) {
        change()
        val covers = begun && forcedCause == null && !revealedOnce && !(ready && inFlight.isEmpty())
        val reportable = begun || forcedCause != null
        if (!covers && reportable && !revealedOnce) {
            revealedOnce = true
            observer.onRevealed(forcedCause ?: RevealCause.READY, inFlight.size)
        }
        state.value = covers
    }

    private fun identitySet(): MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    private companion object {
        const val NOT_BEGUN = Long.MIN_VALUE
    }
}
