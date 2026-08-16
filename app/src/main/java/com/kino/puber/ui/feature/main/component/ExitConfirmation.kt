package com.kino.puber.ui.feature.main.component

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/** Never-armed sentinel. Compared against explicitly — see [ExitConfirmation.isArmed]. */
private const val NeverArmed = Long.MIN_VALUE

private const val ExitConfirmationWindowMillis = 3_000L

/**
 * Whether the next Back leaves the app.
 *
 * Back is the only way out of a TV app, and the side rail took over the gesture that used to
 * provide it, so the exit has to be re-offered deliberately: one press closes the rail and arms
 * this, a second press within the window leaves. Two presses rather than one so a stray press
 * cannot drop the user onto the launcher.
 *
 * Armed only by a Back that closed the rail — never by picking a tab, which also passes through
 * `DrawerValue.HandingOff` and would otherwise offer to leave at a moment the user did not ask to.
 *
 * @param nowMillis monotonic clock, injectable so the window can be tested without waiting.
 */
@Stable
internal class ExitConfirmation(
    private val nowMillis: () -> Long = SystemClock::uptimeMillis,
) {
    private var armedAt by mutableLongStateOf(NeverArmed)

    val isArmed: Boolean
        // The NeverArmed comparison is not redundant: subtracting Long.MIN_VALUE overflows to a
        // negative result, which reads as "armed a moment ago" and would send the very first Back
        // straight to the launcher.
        get() = armedAt != NeverArmed && nowMillis() - armedAt <= ExitConfirmationWindowMillis

    fun arm() {
        armedAt = nowMillis()
    }

    fun disarm() {
        armedAt = NeverArmed
    }
}
