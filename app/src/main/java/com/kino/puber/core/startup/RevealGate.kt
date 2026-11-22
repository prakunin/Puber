package com.kino.puber.core.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val REVEAL_CAP_MS = 2_500L

/** Whether the startup splash still covers the app, and how long the cap leaves it. */
internal class RevealGate(
    private val clock: () -> Long,
    private val capMs: Long = REVEAL_CAP_MS,
    /** Runs the block once the screen has been drawn; a ready mark waits for it. */
    private val afterFrame: (() -> Unit) -> Unit = { it() },
) {
    private val state = MutableStateFlow(false)
    val covered: StateFlow<Boolean> = state.asStateFlow()

    private val lock = Any()

    @Volatile
    private var beganMs = NOT_BEGUN
    private var begun = false
    private var ready = false
    private var forced = false
    private var imagesBusy = false
    private var revealedOnce = false
    private var generation = 0

    /** Covers the app and counts the cap from now; images in flight from before it still count. */
    fun begin() = update {
        beganMs = clock()
        generation++
        begun = true
        ready = false
        forced = false
        revealedOnce = false
    }

    /**
     * The landing screen has content; the splash still waits for the images in flight. The mark
     * counts from the next drawn frame, so the screen's composition has issued its image requests
     * before they are looked at. A mark made before a later begin() or reset() is dropped.
     */
    fun markReady() {
        val markedIn = synchronized(lock) { generation }
        afterFrame {
            update { if (generation == markedIn) ready = true }
        }
    }

    fun onCap() = update { forced = true }

    /** Reveals regardless of content and images. */
    fun revealNow() = update { forced = true }

    fun onImagesBusy(busy: Boolean) = update { imagesBusy = busy }

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
        forced = false
        imagesBusy = false
        revealedOnce = false
    }

    // Once uncovered, the splash never covers again until the next begin().
    private inline fun update(change: () -> Unit) = synchronized(lock) {
        change()
        val covers = begun && !forced && !revealedOnce && !(ready && !imagesBusy)
        if (!covers && begun) revealedOnce = true
        state.value = covers
    }

    private companion object {
        const val NOT_BEGUN = Long.MIN_VALUE
    }
}
