package com.kino.puber.core.ui.model

import com.kino.puber.R

private const val SECONDS_PER_DAY = 86_400
private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60

/**
 * Which `duration_*` string a run time needs, and the numbers to fill it with.
 *
 * The eight-case chain is the same wherever a duration is shown, so it belongs to no single
 * mapper: [VideoItemUIMapper] resolves the result through its [com.kino.puber.core.system.ResourceProvider],
 * the player through its own [android.content.Context], and a test through neither.
 */
internal object DurationLabel {

    data class Template(val resId: Int, val args: List<Int>)

    fun template(totalSeconds: Int): Template {
        val days = totalSeconds / SECONDS_PER_DAY
        val hours = (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE

        return when {
            days > 0 && hours > 0 && minutes > 0 ->
                Template(R.string.duration_days_hours_minutes, listOf(days, hours, minutes))

            days > 0 && hours > 0 -> Template(R.string.duration_days_hours, listOf(days, hours))
            days > 0 && minutes > 0 -> Template(R.string.duration_days_minutes, listOf(days, minutes))
            days > 0 -> Template(R.string.duration_days_only, listOf(days))
            hours > 0 && minutes > 0 -> Template(R.string.duration_hours_minutes, listOf(hours, minutes))
            hours > 0 -> Template(R.string.duration_hours_only, listOf(hours))
            minutes > 0 -> Template(R.string.duration_minutes_only, listOf(minutes))
            else -> Template(R.string.duration_zero, emptyList())
        }
    }
}
