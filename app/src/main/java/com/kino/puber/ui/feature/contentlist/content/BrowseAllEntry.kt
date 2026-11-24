package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorFocusRestored
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive

private val EntryTopPadding = 4.dp
private val EntryHeight = 24.dp
private val EntryHorizontalPadding = 12.dp
private const val ENTRY_FOCUS_ATTEMPTS = 3

/**
 * The «browse everything of this type» button above a tab's first shelf.
 *
 * [isOwner] says this button, not a shelf card, was the last thing focused: it takes focus back on
 * a return from the grid and on a side rail handoff, by the contract of a row's own focus restore
 * (`RestoreItemFocusOnTabReentryEffect`), including the anchor-restore notification. [canFocus]
 * is false while the first section loads, so a cold tab opens on the first card and not here.
 */
@Composable
internal fun BrowseAllEntry(
    label: String,
    isOwner: Boolean,
    canFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val hasFocusRef = remember { BooleanArray(1) }
    val contentFocusActive = LocalContentFocusActive.current
    val anchorFocusRestored = LocalRootAnchorFocusRestored.current

    // Latched for the same reason as the grid's: read directly the id is null again once the
    // handoff settles, which would cancel the effect below before it lands.
    var latchedHandoffId by remember { mutableStateOf<Long?>(null) }
    val handoffId = LocalContentFocusHandoff.current?.pendingRequestId
    if (handoffId != null && handoffId != latchedHandoffId) latchedHandoffId = handoffId

    LaunchedEffect(isOwner, contentFocusActive, canFocus, latchedHandoffId) {
        val mayTakeFocus = isOwner && contentFocusActive && canFocus
        if (mayTakeFocus && !hasFocusRef[0]) {
            var landed = false
            repeat(ENTRY_FOCUS_ATTEMPTS) {
                if (!landed) {
                    // Asked at once, then retried per frame: waiting a frame first lets the side
                    // rail, which Android focuses when the grid leaves, take focus ahead of us.
                    landed = runCatching { focusRequester.requestFocus() }.getOrDefault(false)
                    if (!landed) withFrameNanos { }
                }
            }
            if (landed) anchorFocusRestored()
        }
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .padding(top = EntryTopPadding)
            .height(EntryHeight)
            .focusRequester(focusRequester)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                hasFocusRef[0] = it.hasFocus
                if (it.hasFocus) onFocused()
            },
        contentPadding = PaddingValues(horizontal = EntryHorizontalPadding),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
