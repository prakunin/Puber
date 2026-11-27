package com.kino.puber.ui.feature.player.vm

/**
 * Ties player callbacks to the stream session that produced them.
 *
 * After `switchStream` the player can still deliver events of the stream it left, most notably an
 * error queued before the switch. Read against the current URL it would be reported for the new
 * stream, and Media3 masks the `prepare()` of the new source while the error is pending, so the
 * new stream would never load. Each `prepare`/`switchStream` opens a session; events of an older
 * session are dropped, and an error of a switch session that has not started loading its own URL
 * is answered with one re-prepare instead of being reported.
 *
 * Main thread only; no locking.
 */
internal class PlaybackCallbackGate {

    class Session(val generation: Long, val streamUrl: String, val fromSwitch: Boolean) {
        internal var streamLoadStarted = false
        internal var staleErrorRetried = false
    }

    private var generation = 0L
    private var current: Session? = null

    fun beginSession(streamUrl: String, fromSwitch: Boolean): Session {
        val session = Session(++generation, streamUrl, fromSwitch)
        current = session
        return session
    }

    fun invalidate() {
        generation++
        current = null
    }

    fun dispatch(session: Session, action: (streamUrl: String) -> Unit) {
        if (isCurrent(session)) action(session.streamUrl)
    }

    /** [requestedUri] is the URI the load was requested for, before any redirect. */
    fun onLoadStarted(requestedUri: String) {
        current?.let { if (it.streamUrl == requestedUri) it.streamLoadStarted = true }
    }

    fun routeError(
        session: Session,
        reprepare: () -> Unit,
        report: (streamUrl: String) -> Unit,
    ) {
        if (!isCurrent(session)) return
        if (session.fromSwitch && !session.streamLoadStarted && !session.staleErrorRetried) {
            session.staleErrorRetried = true
            reprepare()
        } else {
            report(session.streamUrl)
        }
    }

    private fun isCurrent(session: Session) = current === session && session.generation == generation
}
