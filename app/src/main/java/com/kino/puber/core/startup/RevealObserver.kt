package com.kino.puber.core.startup

/** Why the splash was lifted; [wire] is the value of the `cause` field on the `startup` line. */
internal enum class RevealCause(val wire: String) {
    READY("ready"),
    CAP("cap"),
    FORCED("forced"),
    GRACE("grace"),
}

/**
 * Receives the edges of [RevealGate] that the startup telemetry records.
 *
 * Called inside the gate lock: implementations must not block or call back into the gate.
 */
internal interface RevealObserver {
    /** `markReady` was called with the gate begun, before the frame wait; inside the gate lock. */
    fun onReadyRequested() {}

    /** Content is ready and the frame wait is over; [inFlight] image requests are held. */
    fun onReady(inFlight: Int)

    /** The set of image requests in flight became empty; reported only before the reveal, so a reveal need not follow one. */
    fun onImagesIdle()

    /** Every request held at ready has ended. */
    fun onHeldDrained()

    /** The splash was lifted by [cause] with [inFlight] image requests still running; can be above 0 for `READY` (requests started after ready) and is always above 0 for `GRACE` (held requests still running). */
    fun onRevealed(cause: RevealCause, inFlight: Int)

    object None : RevealObserver {
        override fun onReady(inFlight: Int) = Unit
        override fun onImagesIdle() = Unit
        override fun onHeldDrained() = Unit
        override fun onRevealed(cause: RevealCause, inFlight: Int) = Unit
    }
}
