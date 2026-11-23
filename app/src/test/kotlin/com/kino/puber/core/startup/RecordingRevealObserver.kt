package com.kino.puber.core.startup

/** Records the observer calls of a [RevealGate] as short strings, in order. */
internal class RecordingRevealObserver : RevealObserver {
    val log = mutableListOf<String>()
    var readyRequested = 0
        private set

    override fun onReadyRequested() {
        readyRequested++
    }

    override fun onReady(inFlight: Int) {
        log += "ready:$inFlight"
    }

    override fun onImagesIdle() {
        log += "idle"
    }

    override fun onHeldDrained() {
        log += "drained"
    }

    override fun onRevealed(cause: RevealCause, inFlight: Int) {
        log += "revealed:$cause:$inFlight"
    }
}
