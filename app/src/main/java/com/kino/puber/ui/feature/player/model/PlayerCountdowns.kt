package com.kino.puber.ui.feature.player.model

/**
 * Countdown timings shared by the view model that runs them and the overlays that draw them.
 *
 * They have to agree: the overlay divides by the total to size its fill, so a total that drifts from
 * the one the countdown actually ticks through mis-scales the bar without failing anywhere.
 */
internal object PlayerCountdowns {

    /** One step of a countdown. */
    const val TICK_MS = 1_000L

    /**
     * Zero is a number the viewer gets to see: both countdowns hold it for one more tick before
     * their action runs. Anything sizing itself against the wait — the bar in the prompt, the room
     * a segment needs for the skip to fit — has to count this tick in.
     */
    const val ZERO_TICK_SEC = 1

    /** Seconds the next-episode prompt waits before switching. */
    const val NEXT_EPISODE_SEC = 7

    /** Seconds the skip-segment prompt waits before skipping. */
    const val SKIP_SEGMENT_SEC = 7

    /**
     * Both prompts go up this long before their segment starts, so that by the time the intro or the
     * credits are actually on screen the countdown is already part-way through and only about five
     * seconds are left.
     */
    const val PROMPT_LEAD_IN_MS = 2_000L

    /**
     * Where the switch lands when the episode has no credits data: this long before the end,
     * whatever [NEXT_EPISODE_SEC] happens to be. The prompt therefore goes up a countdown earlier.
     */
    const val NEXT_EPISODE_TAIL_MS = 15_000L

    /**
     * How much of the segment the skip has to save for the prompt to be worth putting up. A
     * countdown that eats the whole remainder lands the viewer where playback would have taken
     * them anyway, so nothing is offered at all.
     */
    const val SKIP_MIN_SAVING_SEC = 3

    /**
     * The shortest countdown still worth showing. Below this the prompt is up for barely longer
     * than its own entrance animation.
     */
    const val SKIP_MIN_COUNTDOWN_SEC = 3

    /**
     * How long a cancelled skip prompt stays cancelled. Long enough that rewinding a few seconds
     * inside the segment does not undo the answer, short enough that coming back to the same
     * segment much later is treated as a fresh question.
     */
    const val SKIP_DISMISS_TTL_MS = 60_000L

    /** How far from the end the prompt goes up without credits data. */
    const val NEXT_EPISODE_FALLBACK_OFFSET_MS = NEXT_EPISODE_TAIL_MS + NEXT_EPISODE_SEC * 1_000L
}
