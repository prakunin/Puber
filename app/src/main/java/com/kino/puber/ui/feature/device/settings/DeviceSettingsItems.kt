package com.kino.puber.ui.feature.device.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.SettingsChoiceOption

internal val LocalSettingsLeftFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

@Composable
internal fun SettingsListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingText: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
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
        .alpha(if (enabled) 1f else 0.46f)

    if (onClick == null) {
        SettingsListItemBody(
            headline = headline,
            supportingText = supportingText,
            trailingText = trailingText,
            trailingContent = trailingContent,
            modifier = itemModifier,
        )
        return
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = itemModifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
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
        )
    }
}

@Suppress("DEPRECATION")
private fun Modifier.returnFocusLeft(focusRequester: FocusRequester?): Modifier =
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
) {
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f),
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
                color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.let {
            Spacer(modifier = Modifier.width(16.dp))
            it()
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
) {
    SettingsListItem(
        headline = label,
        supportingText = description,
        enabled = enabled,
        role = Role.Switch,
        onClick = onToggle,
        modifier = modifier,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        },
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
            headline = setting.label,
            supportingText = stringResource(R.string.device_settings_not_supported),
            enabled = false,
        )
        return
    }

    Box {
        SettingsToggleItem(
            label = setting.label,
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
    listState: LazyListState? = null,
    lazyItemIndex: Int = 0,
) {
    val options = remember(setting.values) {
        setting.values.map { option ->
            SettingsChoiceOption(
                key = option.id.toString(),
                label = option.label,
                description = option.description,
                selected = option.selected,
            )
        }
    }
    SettingsChoiceItem(
        label = setting.label,
        options = options,
        isExpanded = isExpanded,
        savingOptionKey = savingOptionId?.toString(),
        leftFocusRequester = leftFocusRequester,
        onToggleExpand = onToggleExpand,
        onOptionSelect = { onOptionSelect(it.toInt()) },
        listState = listState,
        lazyItemIndex = lazyItemIndex,
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
    listState: LazyListState? = null,
    lazyItemIndex: Int = 0,
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
                            onToggleExpand()
                            true
                        } else {
                            false
                        }
                    }
                    .focusGroup()
                    .padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                listState?.animateScrollToItem(lazyItemIndex)
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
        role = Role.RadioButton,
        onClick = onClick,
        modifier = modifier,
        trailingContent = {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                RadioButton(
                    selected = option.selected,
                    onClick = null,
                    enabled = enabled,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
        },
    )
}
