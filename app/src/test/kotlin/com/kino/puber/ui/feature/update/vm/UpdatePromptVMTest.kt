package com.kino.puber.ui.feature.update.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.startup.StartupSettled
import com.kino.puber.core.startup.StartupSlot
import com.kino.puber.core.startup.settledStartup
import com.kino.puber.core.startup.unsettledStartup
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.repository.AppUpdateDownload
import com.kino.puber.data.repository.AppVersion
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.data.repository.InstallLaunchResult
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.ui.feature.update.model.UpdatePromptAction
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.io.IOException

class UpdatePromptVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val updateInteractor = mockk<IAppUpdateInteractor>(relaxUnitFun = true)
    private val updateCheckCoordinator = AppUpdateCheckCoordinator()

    private val startupSettled = settledStartup(TestScope(mainDispatcher.dispatcher))

    private fun passUpdateSlot(extraMs: Long = 1) {
        val scheduler = mainDispatcher.dispatcher.scheduler
        scheduler.advanceTimeBy(StartupSlot.UpdateCheck.offset.inWholeMilliseconds + extraMs)
        scheduler.runCurrent()
    }

    private fun passInputGuard() {
        val scheduler = mainDispatcher.dispatcher.scheduler
        scheduler.advanceTimeBy(UPDATE_PROMPT_INPUT_GUARD.inWholeMilliseconds + 1)
        scheduler.runCurrent()
    }

    private fun createVM(startupSettled: StartupSettled = this.startupSettled): UpdatePromptVM {
        every { updateInteractor.isAutoCheckEnabled() } returns true
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(null)
        coEvery { updateInteractor.checkForUpdateNow(any()) } returns Result.success(null)
        every { updateInteractor.canRequestPackageInstalls() } returns true
        return UpdatePromptVM(
            router = router,
            errorHandler = errorHandler,
            updateInteractor = updateInteractor,
            updateCheckCoordinator = updateCheckCoordinator,
            startupSettled = startupSettled,
            resources = FakeResourceProvider(),
        )
    }

    @Test
    fun onStart_keepsHiddenAndSkipsCheck_whenAutoCheckDisabled() {
        every { updateInteractor.isAutoCheckEnabled() } returns false

        val vm = UpdatePromptVM(
            router = router,
            errorHandler = errorHandler,
            updateInteractor = updateInteractor,
            updateCheckCoordinator = updateCheckCoordinator,
            startupSettled = startupSettled,
            resources = FakeResourceProvider(),
        )
        vm.testOnStart()

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
        coVerify(exactly = 0) { updateInteractor.checkForUpdate(any()) }
    }

    @Test
    fun manualCheck_showsUpToDateAndBypassesAutoPreference_whenNoUpdateExists() {
        val vm = createVM()
        every { updateInteractor.isAutoCheckEnabled() } returns false
        vm.testOnStart()

        updateCheckCoordinator.requestManualCheck()

        assertEquals(UpdatePromptViewState.UpToDate, vm.testStateValue)
        coVerify(exactly = 1) { updateInteractor.checkForUpdateNow(any()) }
        coVerify(exactly = 0) { updateInteractor.checkForUpdate(any()) }
    }

    @Test
    fun manualCheck_showsAvailable_whenUpdateExists() {
        val update = availableUpdate()
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdateNow(any()) } returns Result.success(update)
        vm.testOnStart()

        updateCheckCoordinator.requestManualCheck()

        assertEquals(UpdatePromptViewState.Available(update), vm.testStateValue)
    }

    @Test
    fun manualCheck_showsCheckError_whenRequestFails() {
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdateNow(any()) } returns Result.failure(IOException("Network error"))
        vm.testOnStart()

        updateCheckCoordinator.requestManualCheck()

        assertTrue(vm.testStateValue is UpdatePromptViewState.CheckError)
    }

    @Test
    fun retryCheck_showsUpToDate_afterPreviousFailure() {
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdateNow(any()) } returnsMany listOf(
            Result.failure(IOException("Network error")),
            Result.success(null),
        )
        vm.testOnStart()
        updateCheckCoordinator.requestManualCheck()

        vm.onAction(UpdatePromptAction.RetryCheckClicked)

        assertEquals(UpdatePromptViewState.UpToDate, vm.testStateValue)
        coVerify(exactly = 2) { updateInteractor.checkForUpdateNow(any()) }
    }

    @Test
    fun dismiss_keepsPromptHidden_whenCanceledManualCheckReturnsFailure() = runTest {
        val checkStarted = CompletableDeferred<Unit>()
        val releaseCheck = CompletableDeferred<Unit>()
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdateNow(any()) } coAnswers {
            checkStarted.complete(Unit)
            try {
                releaseCheck.await()
                Result.success(availableUpdate())
            } catch (_: CancellationException) {
                Result.failure(IOException("Canceled check"))
            }
        }
        vm.testOnStart()
        updateCheckCoordinator.requestManualCheck()
        checkStarted.await()

        vm.onAction(UpdatePromptAction.DismissClicked)

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
    }

    @Test
    fun onStart_doesNotCheckForUpdateBeforeItsStartupSlot() {
        val unsettled = unsettledStartup(TestScope(mainDispatcher.dispatcher))
        val vm = createVM(unsettled)
        val scheduler = mainDispatcher.dispatcher.scheduler

        vm.testOnStart()
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        coVerify(exactly = 0) { updateInteractor.checkForUpdate(any()) }

        unsettled.onSplashGone()
        scheduler.advanceTimeBy(StartupSlot.UpdateCheck.offset.inWholeMilliseconds - 1)
        scheduler.runCurrent()
        coVerify(exactly = 0) { updateInteractor.checkForUpdate(any()) }

        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        coVerify(exactly = 1) { updateInteractor.checkForUpdate(any()) }
    }

    @Test
    fun manualCheck_runsBeforeStartupSettles() {
        val vm = createVM(unsettledStartup(TestScope(mainDispatcher.dispatcher)))
        val update = availableUpdate()
        coEvery { updateInteractor.checkForUpdateNow(any()) } returns Result.success(update)
        vm.testOnStart()

        updateCheckCoordinator.requestManualCheck()

        assertEquals(UpdatePromptViewState.Available(update), vm.testStateValue)
    }

    @Test
    fun onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot() {
        val unsettled = unsettledStartup(TestScope(mainDispatcher.dispatcher))
        val vm = createVM(unsettled)
        vm.testOnStart()

        vm.testCancelScope()
        unsettled.onSplashGone()
        val scheduler = mainDispatcher.dispatcher.scheduler
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()

        coVerify(exactly = 0) { updateInteractor.checkForUpdate(any()) }
    }

    @Test
    fun onStart_keepsHidden_whenNoUpdateExists() {
        val vm = createVM()

        vm.testOnStart()
        passUpdateSlot()

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
    }

    @Test
    fun onStart_keepsHidden_whenAutomaticCheckFails() {
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.failure(IOException("Network error"))

        vm.testOnStart()
        passUpdateSlot()

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
    }

    @Test
    fun onStart_showsAvailable_whenUpdateExists() {
        val update = availableUpdate()
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)

        vm.testOnStart()
        passUpdateSlot()

        assertEquals(UpdatePromptViewState.Available(update), vm.testStateValue)
    }

    @Test
    fun dismiss_hidesCurrentUpdateForSession() {
        val update = availableUpdate()
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()

        vm.onAction(UpdatePromptAction.DismissClicked)

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
    }

    @Test
    fun updateClicked_downloadsAndLaunchesInstaller_whenDownloadCompletes() {
        val update = availableUpdate()
        val file = File("update.apk")
        coEvery { updateInteractor.downloadUpdate(update, any()) } returns AppUpdateDownload.Completed(file)
        every { updateInteractor.launchInstaller(file) } returns InstallLaunchResult.Launched
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()
        passInputGuard()

        vm.onAction(UpdatePromptAction.UpdateClicked)

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
        coVerify(exactly = 1) { updateInteractor.downloadUpdate(update, any()) }
        verify(exactly = 1) { updateInteractor.launchInstaller(file) }
    }

    @Test
    fun updateClicked_showsPermissionRequired_whenInstallerNeedsPermission() {
        val update = availableUpdate()
        val file = File("update.apk")
        coEvery { updateInteractor.downloadUpdate(update, any()) } returns AppUpdateDownload.Completed(file)
        every { updateInteractor.launchInstaller(file) } returns InstallLaunchResult.PermissionRequired
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()
        passInputGuard()

        vm.onAction(UpdatePromptAction.UpdateClicked)

        assertEquals(UpdatePromptViewState.PermissionRequired(update, file), vm.testStateValue)
    }

    @Test
    fun onResume_retriesInstaller_whenPermissionWasGranted() {
        val update = availableUpdate()
        val file = File("update.apk")
        coEvery { updateInteractor.downloadUpdate(update, any()) } returns AppUpdateDownload.Completed(file)
        every { updateInteractor.launchInstaller(file) } returns InstallLaunchResult.PermissionRequired andThen
            InstallLaunchResult.Launched
        every { updateInteractor.canRequestPackageInstalls() } returns true
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()
        passInputGuard()

        vm.onAction(UpdatePromptAction.UpdateClicked)
        vm.onAction(UpdatePromptAction.OnResume)

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
        verify(exactly = 2) { updateInteractor.launchInstaller(file) }
    }

    @Test
    fun updateClicked_showsError_whenDownloadFails() {
        val update = availableUpdate()
        coEvery { updateInteractor.downloadUpdate(update, any()) } returns AppUpdateDownload.Error.DownloadFailed(
            IOException("Network error")
        )
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()
        passInputGuard()

        vm.onAction(UpdatePromptAction.UpdateClicked)

        assertTrue(vm.testStateValue is UpdatePromptViewState.Error)
        assertEquals(update, (vm.testStateValue as UpdatePromptViewState.Error).update)
    }

    @Test
    fun dismiss_keepsPromptHidden_whenCanceledDownloadReturnsError() = runTest {
        val update = availableUpdate()
        val downloadStarted = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<Unit>()
        coEvery { updateInteractor.downloadUpdate(update, any()) } coAnswers {
            downloadStarted.complete(Unit)
            try {
                releaseDownload.await()
                AppUpdateDownload.Completed(File("unexpected.apk"))
            } catch (_: CancellationException) {
                AppUpdateDownload.Error.DownloadFailed(IOException("Canceled download"))
            }
        }
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()
        passInputGuard()

        vm.onAction(UpdatePromptAction.UpdateClicked)
        downloadStarted.await()
        vm.onAction(UpdatePromptAction.DismissClicked)

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
    }

    @Test
    fun automaticResult_doesNotReplaceAManualCheckInProgress() {
        val update = availableUpdate()
        val autoResult = CompletableDeferred<Result<AvailableUpdate?>>()
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } coAnswers { autoResult.await() }
        coEvery { updateInteractor.checkForUpdateNow(any()) } coAnswers {
            CompletableDeferred<Result<AvailableUpdate?>>().await()
        }
        vm.testOnStart()
        passUpdateSlot()
        updateCheckCoordinator.requestManualCheck()
        assertEquals(UpdatePromptViewState.Checking, vm.testStateValue)

        autoResult.complete(Result.success(update))
        mainDispatcher.dispatcher.scheduler.runCurrent()

        assertEquals(UpdatePromptViewState.Checking, vm.testStateValue)
    }

    @Test
    fun updateClicked_isIgnored_whileTheAutomaticPromptIsInItsInputGuard() {
        val update = availableUpdate()
        coEvery { updateInteractor.downloadUpdate(update, any()) } returns
            AppUpdateDownload.Completed(File("update.apk"))
        every { updateInteractor.launchInstaller(any()) } returns InstallLaunchResult.Launched
        val vm = createVM()
        coEvery { updateInteractor.checkForUpdate(any()) } returns Result.success(update)
        vm.testOnStart()
        passUpdateSlot()

        vm.onAction(UpdatePromptAction.UpdateClicked)

        assertEquals(UpdatePromptViewState.Available(update), vm.testStateValue)
        coVerify(exactly = 0) { updateInteractor.downloadUpdate(any(), any()) }

        passInputGuard()
        vm.onAction(UpdatePromptAction.UpdateClicked)

        assertEquals(UpdatePromptViewState.Hidden, vm.testStateValue)
        coVerify(exactly = 1) { updateInteractor.downloadUpdate(update, any()) }
    }

    private fun availableUpdate() = AvailableUpdate(
        version = AppVersion(major = 1, minor = 5, patch = 0),
        tagName = "v1.5.0",
        title = "Puber 1.5.0",
        releaseNotes = "Release notes",
        apkAssetName = "puber-v1.5.0.apk",
        apkDownloadUrl = "https://example.com/puber-v1.5.0.apk",
        apkSizeBytes = 42_000_000,
        checksumDownloadUrl = "https://example.com/puber-v1.5.0.apk.sha256",
        releasePageUrl = "https://github.com/prakunin/Puber/releases/tag/v1.5.0",
    )
}
