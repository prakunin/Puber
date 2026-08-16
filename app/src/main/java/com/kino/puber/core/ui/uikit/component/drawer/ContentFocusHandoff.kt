package com.kino.puber.core.ui.uikit.component.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

val LocalContentFocusHandoff = staticCompositionLocalOf<ContentFocusHandoff?> { null }

/**
 * How long the content is given to produce something focusable before the rail comes back.
 *
 * Generous on purpose. The first budget for this was 30 frames — half a second — which is fine for
 * a warm tab and wrong for the case the feature exists to serve: a tab whose first page is still
 * in flight. Reopening the rail under a user who was one network round-trip away from content is
 * worse than making a genuinely empty tab wait.
 */
private const val HandoffTimeoutMillis = 5_000L

/**
 * How long focus is retried every frame before the retry backs off.
 *
 * Frame-tight retries matter only while the content is composing; past that the wait is for data,
 * and a focus search per frame for five seconds is pure cost on TV hardware.
 */
private const val HandoffEagerFrames = 30

/** Retry interval once [HandoffEagerFrames] is spent. */
private const val HandoffBackoffMillis = 250L

/**
 * The contract by which the rail hands focus to the content it just revealed.
 *
 * A thin facade over [DrawerState] rather than a second store of state: the rail's value stays the
 * single source of truth, and this exposes only the part the content needs to see.
 */
@Stable
class ContentFocusHandoff(
    private val drawerState: DrawerState,
    private val contentFocusRequester: FocusRequester,
) {
    val pendingRequestId: Long?
        get() = drawerState.pendingHandoffId

    val expectsNewContent: Boolean
        get() = drawerState.handoffExpectsNewContent

    fun settle(requestId: Long) = drawerState.settleHandoff(requestId)

    fun fail(requestId: Long) = drawerState.failHandoff(requestId)

    /**
     * Sends focus back to the content. Used while a handoff is in flight, when the focus system
     * bounces focus into the rail because the arriving content had nothing focusable yet.
     */
    fun redirectFocusToContent() {
        runCatching { contentFocusRequester.requestFocus() }
    }
}

/**
 * Drives the content side of a handoff: retries focus until the content has something to focus,
 * and gives up rather than leaving the rail stuck.
 *
 * Success is not read from [FocusRequester.requestFocus]'s return value. That reports only that the
 * request was accepted at that instant, whereas what matters is that focus *stayed* out of the rail
 * — the failure mode is an accepted request followed by the focus system bouncing focus back. The
 * loop therefore runs until [contentHasFocus] reports the landing.
 *
 * @param restartKey identifies this content instance; changing it means different content arrived.
 * @param contentHasFocus reads whether this content currently holds focus. Polled rather than
 *   pushed because a handoff can begin while the content is *already* focused — Back closing the
 *   rail before focus has physically moved into it — and then no focus change will ever arrive to
 *   confirm it.
 */
@Composable
fun ContentFocusHandoffEffect(
    handoff: ContentFocusHandoff?,
    restartKey: Any?,
    contentFocusRequester: FocusRequester,
    contentHasFocus: () -> Boolean,
) {
    val requestId = handoff?.pendingRequestId
    // Null when this content was already on screen before the handoff began, which is exactly what
    // marks it as the outgoing tab.
    val bornDuringRequest = remember(restartKey) { handoff?.pendingRequestId }

    LaunchedEffect(requestId, restartKey) {
        if (handoff == null || requestId == null) return@LaunchedEffect
        if (handoff.expectsNewContent && bornDuringRequest != requestId) {
            // A tab swap is in flight and this is the tab being swapped out. Taking focus here
            // would confirm a landing on content about to be torn down.
            return@LaunchedEffect
        }
        if (contentHasFocus()) {
            handoff.settle(requestId)
            return@LaunchedEffect
        }

        val landed = withTimeoutOrNull(HandoffTimeoutMillis) {
            var attempt = 0
            while (handoff.pendingRequestId == requestId) {
                if (attempt < HandoffEagerFrames) withFrameNanos { } else delay(HandoffBackoffMillis)
                attempt++
                // Someone else finished this handoff while we waited.
                if (handoff.pendingRequestId != requestId) return@withTimeoutOrNull true
                if (!contentFocusRequester.restoreFocusedChild()) {
                    runCatching { contentFocusRequester.requestFocus() }
                }
                if (contentHasFocus()) {
                    handoff.settle(requestId)
                    return@withTimeoutOrNull true
                }
            }
            true
        }
        if (landed == null) handoff.fail(requestId)
    }
}
