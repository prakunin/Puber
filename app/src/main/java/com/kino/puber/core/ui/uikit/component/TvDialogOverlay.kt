package com.kino.puber.core.ui.uikit.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalInspectionMode

private const val DialogScrimAlpha = 0.62f

// D-pad and Tab moves must not reach content beneath the scrim; Enter, Exit and programmatic
// changes (clearFocus, requestFocus) still pass.
private val TrapDirections = setOf(
    FocusDirection.Up,
    FocusDirection.Down,
    FocusDirection.Left,
    FocusDirection.Right,
    FocusDirection.Next,
    FocusDirection.Previous,
)

internal data class TvDialogFocusRestorer(
    val onDialogOpening: () -> Unit,
    val onDialogClosed: () -> Unit,
)

internal val LocalTvDialogFocusRestorer = compositionLocalOf<TvDialogFocusRestorer?> { null }

@Composable
internal fun TvDialogOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val focusRestorer = LocalTvDialogFocusRestorer.current
    val latestFocusRestorer = rememberUpdatedState(focusRestorer)
    val dismiss: () -> Unit = {
        onDismiss()
    }
    DisposableEffect(Unit) {
        onDispose {
            latestFocusRestorer.value?.onDialogClosed()
        }
    }
    if (!LocalInspectionMode.current) {
        BackHandler(onBack = dismiss)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = DialogScrimAlpha))
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection in TrapDirections) cancelFocusChange()
                }
            }
            .focusGroup(),
        contentAlignment = contentAlignment,
    ) {
        content(dismiss)
    }
}
