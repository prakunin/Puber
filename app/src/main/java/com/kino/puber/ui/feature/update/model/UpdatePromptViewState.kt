package com.kino.puber.ui.feature.update.model

import androidx.compose.runtime.Immutable
import com.kino.puber.data.repository.AvailableUpdate
import java.io.File

@Immutable
internal sealed interface UpdatePromptViewState {
    data object Hidden : UpdatePromptViewState

    data object Checking : UpdatePromptViewState

    data object UpToDate : UpdatePromptViewState

    @Immutable
    data class CheckError(val message: String) : UpdatePromptViewState

    @Immutable
    data class Available(val update: AvailableUpdate) : UpdatePromptViewState

    @Immutable
    data class Downloading(
        val update: AvailableUpdate,
        val progressPercent: Int,
    ) : UpdatePromptViewState

    @Immutable
    data class PermissionRequired(
        val update: AvailableUpdate,
        val downloadedFile: File,
    ) : UpdatePromptViewState

    @Immutable
    data class Error(
        val update: AvailableUpdate?,
        val downloadedFile: File?,
        val message: String,
        val canRetryInstall: Boolean,
    ) : UpdatePromptViewState
}
