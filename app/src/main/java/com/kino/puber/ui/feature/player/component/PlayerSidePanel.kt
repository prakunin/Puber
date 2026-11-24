package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.panel.TvSidePanelItem
import com.kino.puber.core.ui.uikit.component.panel.TvSidePanelSectionHeader
import com.kino.puber.core.ui.uikit.component.panel.TvSidePanelSurface
import kotlinx.coroutines.launch

private const val PANEL_GRADIENT_START = 0.52f
private const val PANEL_GRADIENT_ALPHA = 0.42f
private const val PANEL_ENTER_DURATION_MS = 210
private const val PANEL_EXIT_DURATION_MS = 160
private const val PANEL_SLIDE_DIVISOR = 7
private const val SCROLL_BOX_FOCUS_ALPHA = 0.28f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerSidePanel(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(PANEL_ENTER_DURATION_MS)),
            exit = fadeOut(tween(PANEL_EXIT_DURATION_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(playerSidePanelScrim()),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(PANEL_ENTER_DURATION_MS)) + slideInHorizontally(
                animationSpec = tween(PANEL_ENTER_DURATION_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { it / PANEL_SLIDE_DIVISOR },
            ),
            exit = fadeOut(tween(PANEL_EXIT_DURATION_MS)) + slideOutHorizontally(
                animationSpec = tween(PANEL_EXIT_DURATION_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { it / PANEL_SLIDE_DIVISOR },
            ),
        ) {
            TvSidePanelSurface(title = title, content = content)
        }
    }
}

/**
 * A page with nothing selectable on it: the whole column takes the focus and the arrows scroll
 * it, because focusing a row that does not act would promise something that is not there.
 *
 * [scrollStep] is a whole number of whatever the page is made of — rows on the stream page, lines
 * of prose in the About panel — so a press never leaves half of one showing.
 *
 * [scrollState] is the caller's, because a panel outlives its own dismissal: the side panel keeps
 * this content composed through its exit animation, and a page that must reopen at the top has to
 * say so itself.
 */
@Composable
internal fun PlayerPanelScrollBox(
    focusRequester: FocusRequester,
    scrollStep: Dp,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { scrollStep.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SCROLL_BOX_FOCUS_ALPHA),
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val delta = when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> scrollStepPx
                    KeyEvent.KEYCODE_DPAD_UP -> -scrollStepPx
                    else -> return@onPreviewKeyEvent false
                }
                scope.launch { scrollState.animateScrollBy(delta) }
                true
            }
            .focusable(interactionSource = interactionSource)
            .verticalScroll(scrollState)
            .padding(2.dp),
        content = content,
    )
}

@Composable
internal fun PlayerPanelSectionHeader(text: String, modifier: Modifier = Modifier) =
    TvSidePanelSectionHeader(text = text, modifier = modifier)

@Composable
internal fun PlayerPanelItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailingText: String? = null,
    supportingText: String? = null,
    focusRequester: FocusRequester? = null,
) = TvSidePanelItem(
    text = text,
    onClick = onClick,
    modifier = modifier,
    selected = selected,
    trailingText = trailingText,
    supportingText = supportingText,
    focusRequester = focusRequester,
)

@Composable
internal fun PlayerPanelReadOnlyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The value takes the slack, not the label: labels are short and fixed, values are the
        // long and variable half, and squeezing the label wrapped it mid-word.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun playerSidePanelScrim(): Brush {
    val scrim = MaterialTheme.colorScheme.scrim
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            PANEL_GRADIENT_START to Color.Transparent,
            1f to scrim.copy(alpha = PANEL_GRADIENT_ALPHA),
        ),
    )
}
