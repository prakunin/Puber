package com.kino.puber.data.repository

/**
 * Decides when a played video counts as finished.
 *
 * The history endpoint reports a position and a length, never a verdict, so the line has to be
 * drawn on the client — and drawn in exactly one place. The watched mark on a history row and the
 * mark the catalogue index stores come from the same entries; two thresholds would let the same
 * title read as finished on one screen and unfinished on the other.
 *
 * The line sits short of the end because playback stops in the credits, and the credits-skip
 * feature stops it earlier still. Demanding the position reach the full duration would call almost
 * nothing finished.
 */
object WatchCompletionPolicy {

    /** How much of a video has to have played before it counts as finished. */
    private const val COMPLETION_RATIO = 0.9f

    fun isFinished(time: Int?, duration: Int?): Boolean {
        val playedTo = time ?: return false
        val length = duration?.takeIf { it > 0 } ?: return false
        return playedTo >= length * COMPLETION_RATIO
    }
}
