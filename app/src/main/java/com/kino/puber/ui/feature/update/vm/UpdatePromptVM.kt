package com.kino.puber.ui.feature.update.vm

import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.repository.AppUpdateDownload
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.data.repository.InstallLaunchResult
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.ui.feature.update.model.UpdatePromptAction
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
import kotlinx.coroutines.Job
import java.io.File

internal class UpdatePromptVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val updateInteractor: IAppUpdateInteractor,
    private val updateCheckCoordinator: AppUpdateCheckCoordinator,
    private val resources: ResourceProvider,
) : PuberVM<UpdatePromptViewState>(router) {

    override val initialViewState = UpdatePromptViewState.Hidden

    private var dismissedTagName: String? = null
    private var manualCheckJob: Job? = null
    private var manualCheckRequestId: Long = 0L
    private var downloadJob: Job? = null
    private var downloadRequestId: Long = 0L

    override fun onStart() {
        launch {
            updateCheckCoordinator.manualCheckRequests.collect {
                checkForUpdateNow()
            }
        }

        if (!updateInteractor.isAutoCheckEnabled()) {
            return
        }

        launch {
            updateInteractor.checkForUpdate(BuildConfig.VERSION_NAME)
                .onSuccess { update ->
                    if (update != null && update.tagName != dismissedTagName) {
                        updateViewState(UpdatePromptViewState.Available(update))
                    }
                }
            // Automatic check failures are intentionally silent.
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            UpdatePromptAction.UpdateClicked -> startDownloadFromCurrentState()
            UpdatePromptAction.DismissClicked -> dismiss()
            UpdatePromptAction.OpenInstallPermissionSettingsClicked -> openInstallPermissionSettings()
            UpdatePromptAction.RetryInstallClicked -> retryFromCurrentState()
            UpdatePromptAction.RetryCheckClicked -> checkForUpdateNow()
            UpdatePromptAction.OnResume -> retryInstallerAfterPermission()
            else -> super.onAction(action)
        }
    }

    override fun onBackPressed() {
        if (stateValue != UpdatePromptViewState.Hidden) {
            dismiss()
        } else if (!router.dispatchBackPressed()) {
            router.back()
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        val message = error.message.ifBlank {
            resources.getString(R.string.update_prompt_install_failed)
        }
        when (val state = stateValue) {
            is UpdatePromptViewState.Available -> updateViewState(
                UpdatePromptViewState.Error(
                    update = state.update,
                    downloadedFile = null,
                    message = message,
                    canRetryInstall = false,
                )
            )
            is UpdatePromptViewState.Downloading -> updateViewState(
                UpdatePromptViewState.Error(
                    update = state.update,
                    downloadedFile = null,
                    message = message,
                    canRetryInstall = false,
                )
            )
            is UpdatePromptViewState.PermissionRequired -> updateViewState(
                UpdatePromptViewState.Error(
                    update = state.update,
                    downloadedFile = state.downloadedFile,
                    message = message,
                    canRetryInstall = true,
                )
            )
            is UpdatePromptViewState.Error -> updateViewState(state.copy(message = message))
            is UpdatePromptViewState.CheckError -> updateViewState(
                UpdatePromptViewState.CheckError(
                    message = error.message.ifBlank {
                        resources.getString(R.string.update_prompt_check_failed)
                    }
                )
            )
            UpdatePromptViewState.Checking -> updateViewState(
                UpdatePromptViewState.CheckError(
                    message = error.message.ifBlank {
                        resources.getString(R.string.update_prompt_check_failed)
                    }
                )
            )
            UpdatePromptViewState.Hidden,
            UpdatePromptViewState.UpToDate,
            -> Unit
        }
    }

    private fun checkForUpdateNow() {
        manualCheckJob?.cancel()
        val currentRequestId = ++manualCheckRequestId
        updateViewState(UpdatePromptViewState.Checking)
        manualCheckJob = launch {
            val result = updateInteractor.checkForUpdateNow(BuildConfig.VERSION_NAME)
            if (!isActiveManualCheck(currentRequestId)) {
                return@launch
            }
            manualCheckJob = null
            result.fold(
                onSuccess = { update ->
                    updateViewState(
                        update?.let(UpdatePromptViewState::Available)
                            ?: UpdatePromptViewState.UpToDate
                    )
                },
                onFailure = {
                    updateViewState(
                        UpdatePromptViewState.CheckError(
                            resources.getString(R.string.update_prompt_check_failed)
                        )
                    )
                },
            )
        }
    }

    private fun isActiveManualCheck(requestId: Long): Boolean {
        return manualCheckRequestId == requestId && stateValue == UpdatePromptViewState.Checking
    }

    private fun startDownloadFromCurrentState() {
        val update = when (val state = stateValue) {
            is UpdatePromptViewState.Available -> state.update
            is UpdatePromptViewState.Error -> state.update
            else -> null
        } ?: return

        startDownload(update)
    }

    private fun startDownload(update: AvailableUpdate) {
        downloadJob?.cancel()
        val currentDownloadRequestId = ++downloadRequestId
        updateViewState(UpdatePromptViewState.Downloading(update = update, progressPercent = 0))
        downloadJob = launch {
            val result = updateInteractor.downloadUpdate(update) { progress ->
                if (isActiveDownload(currentDownloadRequestId, update)) {
                    updateViewState(
                        UpdatePromptViewState.Downloading(
                            update = update,
                            progressPercent = progress.percent,
                        )
                    )
                }
            }

            if (!isActiveDownload(currentDownloadRequestId, update)) {
                return@launch
            }

            when (result) {
                is AppUpdateDownload.Completed -> handleInstallLaunch(update, result.file)
                is AppUpdateDownload.Progress ->
                    updateViewState(UpdatePromptViewState.Downloading(update, result.percent))
                AppUpdateDownload.Error.StorageUnavailable -> showDownloadError(
                    update = update,
                    messageRes = R.string.update_prompt_storage_unavailable,
                )
                is AppUpdateDownload.Error.DownloadFailed,
                is AppUpdateDownload.Error.ChecksumDownloadFailed,
                -> showDownloadError(
                    update = update,
                    messageRes = R.string.update_prompt_download_failed,
                )
                AppUpdateDownload.Error.InvalidChecksum,
                is AppUpdateDownload.Error.ChecksumMismatch,
                -> showDownloadError(
                    update = update,
                    messageRes = R.string.update_prompt_checksum_failed,
                )
            }
        }
    }

    private fun isActiveDownload(requestId: Long, update: AvailableUpdate): Boolean {
        if (downloadRequestId != requestId) {
            return false
        }

        val state = stateValue as? UpdatePromptViewState.Downloading ?: return false
        return state.update.tagName == update.tagName
    }

    private fun showDownloadError(update: AvailableUpdate, messageRes: Int) {
        updateViewState(
            UpdatePromptViewState.Error(
                update = update,
                downloadedFile = null,
                message = resources.getString(messageRes),
                canRetryInstall = false,
            )
        )
    }

    private fun handleInstallLaunch(update: AvailableUpdate, file: File) {
        when (updateInteractor.launchInstaller(file)) {
            InstallLaunchResult.Launched -> updateViewState(UpdatePromptViewState.Hidden)
            InstallLaunchResult.PermissionRequired ->
                updateViewState(UpdatePromptViewState.PermissionRequired(update = update, downloadedFile = file))
            is InstallLaunchResult.Failed -> showInstallError(update, file)
        }
    }

    private fun openInstallPermissionSettings() {
        val state = stateValue as? UpdatePromptViewState.PermissionRequired ?: return
        runCatching {
            updateInteractor.openInstallPermissionSettings()
        }.onFailure {
            showInstallError(update = state.update, file = state.downloadedFile)
        }
    }

    private fun retryFromCurrentState() {
        when (val state = stateValue) {
            is UpdatePromptViewState.PermissionRequired -> handleInstallLaunch(state.update, state.downloadedFile)
            is UpdatePromptViewState.Error -> {
                val file = state.downloadedFile
                val update = state.update
                when {
                    file != null && update != null -> handleInstallLaunch(update, file)
                    update != null -> startDownload(update)
                }
            }
            else -> Unit
        }
    }

    private fun retryInstallerAfterPermission() {
        val state = stateValue as? UpdatePromptViewState.PermissionRequired ?: return
        if (updateInteractor.canRequestPackageInstalls()) {
            handleInstallLaunch(update = state.update, file = state.downloadedFile)
        }
    }

    private fun dismiss() {
        manualCheckRequestId++
        manualCheckJob?.cancel()
        manualCheckJob = null
        downloadRequestId++
        downloadJob?.cancel()
        downloadJob = null

        when (val state = stateValue) {
            is UpdatePromptViewState.Available -> dismissedTagName = state.update.tagName
            is UpdatePromptViewState.Downloading -> dismissedTagName = state.update.tagName
            is UpdatePromptViewState.PermissionRequired -> dismissedTagName = state.update.tagName
            is UpdatePromptViewState.Error -> dismissedTagName = state.update?.tagName
            is UpdatePromptViewState.CheckError,
            UpdatePromptViewState.Checking,
            UpdatePromptViewState.Hidden,
            UpdatePromptViewState.UpToDate,
            -> Unit
        }
        updateViewState(UpdatePromptViewState.Hidden)
    }

    private fun showInstallError(update: AvailableUpdate, file: File) {
        updateViewState(
            UpdatePromptViewState.Error(
                update = update,
                downloadedFile = file,
                message = resources.getString(R.string.update_prompt_install_failed),
                canRetryInstall = true,
            )
        )
    }
}
