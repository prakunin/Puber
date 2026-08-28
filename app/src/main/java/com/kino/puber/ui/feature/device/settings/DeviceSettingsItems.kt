package com.kino.puber.ui.feature.device.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.SettingsChoiceOption
import com.kino.puber.ui.feature.device.settings.model.titleRes
import com.kino.puber.ui.feature.device.settings.model.localizedLabelRes
import kotlin.math.roundToInt

internal val LocalSettingsLeftFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

private val ItemMinHeight = 48.dp
private val DenseItemMinHeight = 40.dp
private val TrailingSlotSize = 24.dp
private val RadioIndicatorSize = 18.dp

/** Brings the 52x32 dp Material switch down to 39x24, the size of the radio slot beside it. */
private const val SwitchScale = 0.75f

@Composable
internal fun SettingsListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingText: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusableWhenDisabled: Boolean = false,
    dimWhenDisabled: Boolean = true,
    dense: Boolean = false,
    role: Role? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val leftFocusRequester = LocalSettingsLeftFocusRequester.current
    val itemModifier = modifier
        .fillMaxWidth()
        .returnFocusLeft(leftFocusRequester)
        .semantics {
            if (role != null || selected) this.selected = selected
            role?.let { this.role = it }
            if (!enabled) disabled()
        }
        .alpha(if (enabled || !dimWhenDisabled) 1f else 0.46f)

    if (onClick == null) {
        SettingsListItemBody(
            headline = headline,
            supportingText = supportingText,
            trailingText = trailingText,
            trailingContent = trailingContent,
            dense = dense,
            modifier = itemModifier,
        )
        return
    }

    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled || focusableWhenDisabled,
        modifier = itemModifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            pressedContentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        SettingsListItemBody(
            headline = headline,
            supportingText = supportingText,
            trailingText = trailingText,
            trailingContent = trailingContent,
            dense = dense,
        )
    }
}

@Suppress("DEPRECATION")
internal fun Modifier.returnFocusLeft(focusRequester: FocusRequester?): Modifier =
    if (focusRequester == null) {
        this
    } else {
        focusProperties { left = focusRequester }
    }

@Composable
private fun SettingsListItemBody(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String?,
    trailingText: String?,
    trailingContent: (@Composable RowScope.() -> Unit)?,
    dense: Boolean = false,
) {
    Row(
        modifier = modifier
            .heightIn(min = if (dense) DenseItemMinHeight else ItemMinHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.let {
            Spacer(modifier = Modifier.width(16.dp))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                it()
            }
        }
    }
}

@Composable
internal fun SettingsToggleItem(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    focusableWhenDisabled: Boolean = false,
    readOnly: Boolean = false,
) {
    val interactionEnabled = enabled && !readOnly
    SettingsListItem(
        headline = label,
        supportingText = description,
        // A read-only value remains focusable and visually current, but its disabled semantics
        // make it clear that activating the row cannot change it.
        enabled = interactionEnabled,
        focusableWhenDisabled = focusableWhenDisabled || readOnly,
        dimWhenDisabled = !readOnly,
        role = Role.Switch,
        onClick = onToggle,
        modifier = modifier,
        trailingContent = { SmallSwitch(checked = checked, enabled = enabled) },
    )
}

/**
 * The Material switch at a smaller scale. The layer scales from the top-left corner and the
 * layout reports the scaled bounds, so the row does not reserve the width the switch no longer
 * draws in.
 */
@Composable
private fun SmallSwitch(
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(
                    (placeable.width * SwitchScale).roundToInt(),
                    (placeable.height * SwitchScale).roundToInt(),
                ) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                scaleX = SwitchScale
                scaleY = SwitchScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .focusProperties { canFocus = false },
    )
}

@Composable
internal fun SettingSwitchItem(
    setting: DeviceSettingUIModel.TypeValue,
    isSaving: Boolean = false,
    onToggle: () -> Unit,
) {
    if (!setting.supported) {
        SettingsListItem(
            headline = stringResource(setting.type.titleRes),
            supportingText = stringResource(R.string.device_settings_not_supported),
            enabled = false,
        )
        return
    }

    Box {
        SettingsToggleItem(
            label = stringResource(setting.type.titleRes),
            checked = setting.value,
            enabled = !isSaving,
            onToggle = onToggle,
        )
        if (isSaving) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
internal fun SettingListItem(
    setting: DeviceSettingUIModel.TypeList,
    isExpanded: Boolean,
    savingOptionId: Int?,
    leftFocusRequester: FocusRequester,
    onToggleExpand: () -> Unit,
    onOptionSelect: (Int) -> Unit,
) {
    val options = setting.values.map { option ->
        val localizedLabel = option.localizedLabelRes(setting.type)
            ?.let { stringResource(it) }
            ?: option.label
        SettingsChoiceOption(
            key = option.id.toString(),
            label = localizedLabel,
            description = option.description,
            selected = option.selected,
        )
    }
    SettingsChoiceItem(
        label = stringResource(setting.type.titleRes),
        options = options,
        isExpanded = isExpanded,
        savingOptionKey = savingOptionId?.toString(),
        leftFocusRequester = leftFocusRequester,
        onToggleExpand = onToggleExpand,
        onOptionSelect = { onOptionSelect(it.toInt()) },
    )
}

@Composable
internal fun SettingsChoiceItem(
    label: String,
    options: List<SettingsChoiceOption>,
    isExpanded: Boolean,
    leftFocusRequester: FocusRequester,
    onToggleExpand: () -> Unit,
    onOptionSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    savingOptionKey: String? = null,
) {
    val headerFocusRequester = remember { FocusRequester() }
    val optionFocusRequesters = remember(options.map(SettingsChoiceOption::key)) {
        options.associate { it.key to FocusRequester() }
    }
    val selectedOption = options.firstOrNull(SettingsChoiceOption::selected)

    Column(
        modifier = modifier
            .focusRestorer(headerFocusRequester)
            .focusGroup(),
    ) {
        SettingsListItem(
            headline = label,
            supportingText = description,
            trailingText = selectedOption?.label.orEmpty(),
            onClick = onToggleExpand,
            modifier = Modifier
                .focusRequester(headerFocusRequester)
                .focusProperties {
                    @Suppress("DEPRECATION")
                    left = leftFocusRequester
                },
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .onKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            // Focus goes back to the header before the options leave the
                            // composition. Collapsing out from under the focused row drops focus
                            // out of the screen entirely, and the navigation rail reveals itself
                            // on focus — so Back would both close the list and open the rail.
                            headerFocusRequester.requestFocus()
                            onToggleExpand()
                            true
                        } else {
                            false
                        }
                    }
                    .focusGroup()
                    .padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEach { option ->
                    OptionItem(
                        option = option,
                        isSaving = savingOptionKey == option.key,
                        enabled = savingOptionKey == null,
                        onClick = {
                            headerFocusRequester.requestFocus()
                            onOptionSelect(option.key)
                        },
                        modifier = Modifier
                            .focusRequester(optionFocusRequesters.getValue(option.key))
                            .focusProperties {
                                @Suppress("DEPRECATION")
                                left = leftFocusRequester
                            },
                    )
                }
            }
        }

        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                val requester = selectedOption
                    ?.let { optionFocusRequesters[it.key] }
                    ?: optionFocusRequesters.values.firstOrNull()
                requester?.requestFocus()
            }
        }
    }
}

@Composable
private fun OptionItem(
    option: SettingsChoiceOption,
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsListItem(
        headline = option.label,
        supportingText = option.description,
        selected = option.selected,
        enabled = enabled,
        dense = true,
        role = Role.RadioButton,
        onClick = onClick,
        modifier = modifier,
        trailingContent = {
            // A fixed slot keeps the label still while the indicator swaps for the saving spinner.
            Box(
                modifier = Modifier.size(TrailingSlotSize),
                contentAlignment = Alignment.Center,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(TrailingSlotSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    RadioIndicator(selected = option.selected)
                }
            }
        },
    )
}

/**
 * A radio dot drawn in the current content colour so it inverts together with the focused row,
 * and sized exactly, unlike the Material button that pads itself out to a touch target.
 */
@Composable
private fun RadioIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(RadioIndicatorSize)) {
        val strokeWidth = 2.dp.toPx()
        drawCircle(
            color = color.copy(alpha = if (selected) 1f else 0.6f),
            radius = (size.minDimension - strokeWidth) / 2,
            style = Stroke(width = strokeWidth),
        )
        if (selected) {
            drawCircle(color = color, radius = size.minDimension / 4)
        }
    }
}
