package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kino.puber.core.ui.uikit.component.modifier.FOCUS_ON_LAUNCH_DELAY_MILLIS
import com.kino.puber.ui.feature.contentlist.model.SectionState
import kotlinx.coroutines.delay

/**
 * Who may hold focus around the browse entry of a tab's first section.
 *
 * [isOwner] says the entry, not a card, was the last thing focused: it takes focus back on a
 * return from the grid and on a rail handoff. While it is true no row is the target, or the return
 * would land on the row's remembered card.
 *
 * The entry is not focusable before the screen's launch focus has settled ([launchSettled]), unless
 * it is the owner. Otherwise a first section that is Empty or Error would give it launch focus,
 * being the first focusable thing on the screen, instead of the next section's first card. The
 * launch request (`rememberFocusRequesterOnLaunch`) fires a fixed delay after composition and
 * moves focus to the first focusable thing in the list, so a card that took focus earlier would be
 * pulled onto the entry the moment it became focusable: settling waits out that delay after the
 * first row focus.
 *
 * Shared with the instrumented scenes so they run this rule and not a copy of it.
 */
@Stable
internal class BrowseEntryFocus(owner: Boolean = false) {
    var isOwner by mutableStateOf(owner)
        private set
    private var launchSettled by mutableStateOf(false)
    internal var firstRowLanded by mutableStateOf(false)
        private set

    // Not observed: read once when the all-Empty settle fires.
    private var listHasFocus = false

    /** The Loading shimmer is focusable, so it never counts as a landing nor releases ownership. */
    fun canFocus(sectionState: SectionState): Boolean =
        sectionState !is SectionState.Loading && (launchSettled || isOwner)

    fun onEntryFocused() {
        isOwner = true
        launchSettled = true
    }

    /** Focus entered a card, the Retry button or any other row content of a section. */
    fun onRowFocused(sectionState: SectionState) {
        if (sectionState is SectionState.Loading) return
        isOwner = false
        firstRowLanded = true
    }

    internal fun settleLaunch() {
        launchSettled = true
    }

    internal fun onListFocusChanged(hasFocus: Boolean) {
        listHasFocus = hasFocus
    }

    /**
     * Every section stayed Empty for the launch delay: the entry becomes focusable, and when
     * nothing in the list holds focus (the Loading shimmer that had it is gone) it takes focus, or
     * focus would stay nowhere until the next D-pad press.
     */
    internal fun settleLaunchWhenEveryEmpty() {
        launchSettled = true
        if (!listHasFocus) isOwner = true
    }

    companion object {
        val Saver: Saver<BrowseEntryFocus, Boolean> = Saver(
            save = { it.isOwner },
            restore = { BrowseEntryFocus(owner = it) },
        )
    }
}

/**
 * When every section is Empty nothing takes row focus, so launch would never settle and the entry
 * would stay out of reach: once that state has lasted the launch delay the entry becomes focusable
 * and takes focus if the list holds none. A shorter all-Empty state is the paginator's replayed
 * initial value ahead of the real one, and settles nothing.
 */
@Composable
internal fun rememberBrowseEntryFocus(sectionStates: List<SectionState>): BrowseEntryFocus {
    val focus = rememberSaveable(saver = BrowseEntryFocus.Saver) { BrowseEntryFocus() }
    val everySectionEmpty = sectionStates.isNotEmpty() && sectionStates.all { it is SectionState.Empty }
    LaunchedEffect(everySectionEmpty) {
        if (everySectionEmpty) {
            delay(FOCUS_ON_LAUNCH_DELAY_MILLIS)
            focus.settleLaunchWhenEveryEmpty()
        }
    }
    LaunchedEffect(focus.firstRowLanded) {
        if (focus.firstRowLanded) {
            delay(FOCUS_ON_LAUNCH_DELAY_MILLIS)
            focus.settleLaunch()
        }
    }
    return focus
}

/**
 * The entry as section 0's header, gated by [focus]. The one place the gating is applied, for the
 * screen and for the instrumented scenes.
 */
@Composable
internal fun BrowseEntryHeader(
    label: String,
    focus: BrowseEntryFocus,
    sectionState: SectionState,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowseAllEntry(
        label = label,
        isOwner = focus.isOwner,
        canFocus = focus.canFocus(sectionState),
        onFocused = onFocused,
        onClick = onClick,
        modifier = modifier,
    )
}
