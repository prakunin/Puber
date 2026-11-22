package com.kino.puber.core.startup

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.StateFlow

/** The reveal gate of the process: the splash covers the app from `begin()` until ready or the cap. */
internal object StartupReveal {
    // Immediate until production startup installs the frame wait: the Android looper does not exist on the JVM.
    private var frameHook: (() -> Unit) -> Unit = { it() }

    private val gate = RevealGate(
        clock = { System.nanoTime() / NS_PER_MS },
        afterFrame = { frameHook(it) },
    )

    val imageActivity = StartupImageActivity(gate::onImagesBusy)

    val covered: StateFlow<Boolean> get() = gate.covered
    val isCovered: Boolean get() = gate.covered.value

    fun begin() = gate.begin()

    /** Makes the reveal wait for two frames after the content is ready; called once from the application. */
    fun installFrameWait() {
        frameHook = ::afterTwoFrames
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
        imageActivity.reset()
        gate.reset()
        frameHook = { it() }
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
