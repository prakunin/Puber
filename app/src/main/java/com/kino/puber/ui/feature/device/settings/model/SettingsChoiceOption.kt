package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class SettingsChoiceOption(
    val key: String,
    val label: String,
    val description: String? = null,
    val selected: Boolean,
)
