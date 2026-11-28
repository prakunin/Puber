package com.kino.puber.ui.feature.player.vm

private const val SHORT_SEEK_STEP_SECONDS = 10
private const val MEDIUM_SEEK_STEP_SECONDS = 20
private const val LONG_SEEK_STEP_SECONDS = 30
private const val MAX_SEEK_STEP_SECONDS = 60
internal const val DEFAULT_SEEK_RESET_TIMEOUT_MS = 1_500L
private val DEFAULT_SEEK_STEPS_SECONDS = intArrayOf(
    SHORT_SEEK_STEP_SECONDS,
    SHORT_SEEK_STEP_SECONDS,
    MEDIUM_SEEK_STEP_SECONDS,
    LONG_SEEK_STEP_SECONDS,
    MAX_SEEK_STEP_SECONDS,
    MAX_SEEK_STEP_SECONDS,
)

internal class SeekHandler(
    private val steps: IntArray = DEFAULT_SEEK_STEPS_SECONDS,
) {
    private var stepIndex = 0

    /** Returns the current step, then moves the ladder up; [reset] starts it over. */
    fun nextStep(): Int {
        val step = steps[stepIndex]
        if (stepIndex < steps.size - 1) stepIndex++
        return step
    }

    fun reset() {
        stepIndex = 0
    }
}
