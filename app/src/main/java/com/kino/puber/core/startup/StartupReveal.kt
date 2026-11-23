package com.kino.puber.core.startup

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.annotation.VisibleForTesting
import androidx.core.os.HandlerCompat
import com.kino.puber.core.telemetry.Telemetry
import kotlinx.coroutines.flow.StateFlow

/** The reveal gate of the process: the splash covers the app from `begin()` until ready or the cap. */
internal object StartupReveal {
    // Immediate until production startup installs the frame wait: the Android looper does not exist on the JVM.
    private var frameHook: (() -> Unit) -> Unit = { it() }

    // No-op until production startup installs the grace timer: the Android looper does not exist on the JVM.
    private var graceHook: (Long, () -> Unit) -> Unit = { _, _ -> }

    // Async, so the message is not held behind the Choreographer sync barrier.
    private val graceHandler by lazy { HandlerCompat.createAsync(Looper.getMainLooper()) }

    private val gate = RevealGate(
        clock = { System.nanoTime() / NS_PER_MS },
        afterFrame = { frameHook(it) },
        scheduleGrace = { delayMs, task -> graceHook(delayMs, task) },
        observer = object : RevealObserver {
            override fun onReadyRequested() {
                Telemetry.startupMarks?.markReadyRequested()
            }

            override fun onReady(inFlight: Int) {
                Telemetry.startupMarks?.markReady(inFlight)
            }

            override fun onImagesIdle() {
                Telemetry.startupMarks?.markImagesIdle()
            }

            override fun onHeldDrained() {
                Telemetry.startupMarks?.markHeldDrained()
            }

            override fun onRevealed(cause: RevealCause, inFlight: Int) {
                Telemetry.startupMarks?.markRevealCause(cause, inFlight)
            }
        },
    )

    val imageActivity = StartupImageActivity(gate)

    val covered: StateFlow<Boolean> get() = gate.covered
    val isCovered: Boolean get() = gate.covered.value

    fun begin() = gate.begin()

    /**
     * Makes the reveal wait for two frames after the content is ready and installs the grace timer;
     * called once from the application.
     */
    fun installFrameWait() {
        frameHook = ::afterTwoFrames
        graceHook = ::postGrace
    }

    /**
     * A restored activity (recreation, process death) brings its screen stack back, so there is no
     * startup to wait for and nothing but Home would ever mark ready: reveal at once.
     */
    fun onActivityCreated(restored: Boolean) = if (restored) revealNow() else begin()

    fun markReady() = gate.markReady()

    fun revealNow() = gate.revealNow()

    /**
     * An open update prompt closes the content focus gate, so Home's launch claim cannot run and
     * would never report back: reveal at once, without waiting for content or images.
     */
    fun onUpdatePromptOpenChanged(open: Boolean) {
        if (open) revealNow()
    }

    fun onCap() = gate.onCap()

    fun remainingCapMs(): Long = gate.remainingCapMs()

    @VisibleForTesting
    fun resetForTests() {
        gate.reset()
        frameHook = { it() }
        graceHook = { _, _ -> }
    }

    private fun postGrace(delayMs: Long, task: () -> Unit) {
        graceHandler.postDelayed(task, delayMs)
    }

    // The first frame composes and lays out the screen, the second sees the image requests it started.
    private fun afterTwoFrames(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            val choreographer = Choreographer.getInstance()
            choreographer.postFrameCallback { choreographer.postFrameCallback { block() } }
        }
    }

    private const val NS_PER_MS = 1_000_000L
}
