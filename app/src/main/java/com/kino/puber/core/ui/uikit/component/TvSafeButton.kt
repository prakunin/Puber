package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ButtonHeight = 48.dp
private val ButtonCornerRadius = 24.dp

@Composable
internal fun TvSafeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    quiet: Boolean = false,
    focusableWhenDisabled: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isSelectPressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ButtonCornerRadius)
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = colorScheme.containerColor(enabled, isFocused, destructive, quiet, primary)
    val contentColor = colorScheme.contentColor(enabled, isFocused, destructive, quiet, primary)
    val borderColor = colorScheme.borderColor(isFocused, destructive, quiet, primary)

    Box(
        modifier = modifier
            .height(ButtonHeight)
            .graphicsLayer {
                val pressedScale = if (isSelectPressed) 0.98f else 1f
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .background(containerColor, shape)
            .then(
                if (borderColor == null) {
                    Modifier
                } else {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) {
                    isSelectPressed = false
                }
            }
            .focusable(enabled = enabled || focusableWhenDisabled)
            .semantics {
                role = SemanticsRole.Button
                if (!enabled) {
                    disabled()
                }
                onClick {
                    if (enabled) {
                        onClick()
                    }
                    enabled
                }
            }
            .onTvSelectClick(
                enabled = enabled,
                isPressed = { isSelectPressed },
                setPressed = { isSelectPressed = it },
                onClick = onClick,
            )
            .pointerInput(enabled, onClick) {
                if (enabled) {
                    detectTapGestures(onTap = { onClick() })
                }
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.onTvSelectClick(
    enabled: Boolean,
    isPressed: () -> Boolean,
    setPressed: (Boolean) -> Unit,
    onClick: () -> Unit,
): Modifier {
    fun handleEvent(event: KeyEvent): Boolean {
        if (!enabled) {
            setPressed(false)
            return false
        }
        if (!event.key.isSelectKey()) {
            return false
        }

        return when (event.type) {
            KeyEventType.KeyDown -> {
                setPressed(true)
                true
            }
            KeyEventType.KeyUp -> if (isPressed()) {
                setPressed(false)
                onClick()
                true
            } else {
                false
            }
            else -> false
        }
    }

    return onPreviewKeyEvent(::handleEvent)
}

private fun Key.isSelectKey(): Boolean = this == Key.DirectionCenter || this == Key.Enter

private fun ColorScheme.containerColor(
    enabled: Boolean,
    isFocused: Boolean,
    isDestructive: Boolean,
    isQuiet: Boolean,
    isPrimary: Boolean,
): Color = when {
    !enabled -> surfaceVariant.copy(alpha = 0.36f)
    isFocused && isDestructive -> error
    isFocused && isQuiet -> surfaceVariant
    isFocused -> onSurface
    isDestructive -> errorContainer.copy(alpha = 0.22f)
    isQuiet -> Color.Transparent
    isPrimary -> primaryContainer
    else -> surface
}

private fun ColorScheme.contentColor(
    enabled: Boolean,
    isFocused: Boolean,
    isDestructive: Boolean,
    isQuiet: Boolean,
    isPrimary: Boolean,
): Color = when {
    !enabled -> onSurface.copy(alpha = 0.38f)
    isFocused && isDestructive -> onError
    isFocused && isQuiet -> onSurface
    isFocused -> surface
    isDestructive -> error
    isQuiet -> onSurfaceVariant
    isPrimary -> onPrimaryContainer
    else -> onSurface
}

private fun ColorScheme.borderColor(
    isFocused: Boolean,
    isDestructive: Boolean,
    isQuiet: Boolean,
    isPrimary: Boolean,
): Color? = when {
    isFocused -> primary
    isDestructive -> error.copy(alpha = 0.72f)
    isQuiet -> outlineVariant.copy(alpha = 0.72f)
    isPrimary -> null
    else -> outline
}
