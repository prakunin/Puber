package com.kino.puber.ui.feature.player.vm

import com.kino.puber.R
import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.domain.interactor.player.PlayerBehaviourPreferences
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import timber.log.Timber

/**
 * PlayerVM: starting up, and the controls that only touch the current screen.
 *
 * No `runTest` — UnconfinedTestDispatcher makes all coroutines synchronous.
 * `runTest` adds `advanceUntilIdle()` at the end which spins PlayerVM's infinite
 * `startPositionUpdates()` loop forever → OOM.
 * Without `runTest`, the infinite loop stays suspended at its first `delay()` — harmless.
 */
internal class PlayerVMTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region Lifecycle

    @Test
    fun initialState_isLoading() {
        assertEquals(PlayerViewState.Loading, createVM().testStateValue)
    }

    @Test
    fun onStart_transitionsToContent() {
        val vm = startedVM()
        assertTrue(vm.testStateValue is PlayerViewState.Content)
    }

    @Test
    fun onStart_preparesPlayer() {
        startedVM()
        verify { playbackController.setCallback(any()) }
        verify {
            playbackController.prepare(
                StreamCandidate("https://test/v.m3u8", StreamType.HLS),
                any(),
                any(),
            )
        }
    }

    @Test
    fun onStart_hidesManualWatchedControlByDefault() {
        assertFalse(contentState(startedVM()).showMarkWatchedButton)
    }

    @Test
    fun onStart_showsManualWatchedControlWhenEnabled() {
        every { interactor.getBehaviourPreferences() } returns PlayerBehaviourPreferences(
            debugOverlayEnabled = false,
            okTogglesPlayPause = false,
            showMarkWatchedButton = true,
        )

        assertTrue(contentState(startedVM()).showMarkWatchedButton)
    }

    @Test
    fun onStart_urlBearingItemDetailsFailurePreservesErrorAndSanitizesTimberPipeline() {
        val privateItemId = 424_242
        val privateUrl = "https://api.example.test/v1/items/$privateItemId"
        val timeout = HttpRequestTimeoutException(privateUrl, 5_000L, null)
        val failure = IllegalStateException("Player startup failed", timeout)
        val logTree = CollectingLogTree()
        coEvery { interactor.getItemDetails(privateItemId) } throws failure

        Timber.plant(logTree)
        val vm = try {
            createVM(
                playerParams = PlayerScreenParams(itemId = privateItemId, videoNumber = 1),
                playerErrorHandler = DefaultErrorHandler(FakeResourceProvider()),
            ).also(PlayerVM::testOnStart)
        } finally {
            Timber.uproot(logTree)
        }

        assertEquals(
            "string_${R.string.error_generic}",
            (vm.testStateValue as PlayerViewState.Error).message,
        )
        val output = logTree.output()
        assertFalse(output.contains(privateItemId.toString()), output)
        assertFalse(output.contains(privateUrl), output)
        assertTrue(output.contains("/items/<redacted>"), output)
        assertEquals(1, logTree.entryCount)
        verify(exactly = 0) { contentStateFactory.build(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { playbackController.prepare(any(), any(), any()) }
    }

    @Test
    fun onStart_passesExplicitMovieVideoNumberToResolver() {
        val movieParams = PlayerScreenParams(itemId = 42, videoNumber = 7)
        val movieItem = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        val movieMedia = testResolvedMedia.copy(
            videoNumber = 7,
            episodeId = null,
            episodeTitle = null,
            isSeries = false,
            hasNext = false,
            hasPrevious = false,
            seasonNumber = null,
            episodeNumber = null,
        )
        coEvery { interactor.getItemDetails(42) } returns movieItem
        every { interactor.resolveMedia(movieItem, null, null, 7) } returns movieMedia

        createVM(movieParams).testOnStart()

        verify {
            interactor.resolveMedia(
                item = movieItem,
                seasonNumber = null,
                episodeNumber = null,
                videoNumber = 7,
            )
        }
    }

    @Test
    fun onStart_missingExplicitMovieVideo_showsPlaybackErrorWithoutInitialization() {
        val movieParams = PlayerScreenParams(itemId = 42, videoNumber = 7)
        val movieItem = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        val missingMedia = testResolvedMedia.copy(
            files = null,
            audios = null,
            subtitles = null,
            videoNumber = null,
            episodeId = null,
            episodeTitle = null,
            isSeries = false,
            hasNext = false,
            hasPrevious = false,
            seasonNumber = null,
            episodeNumber = null,
        )
        coEvery { interactor.getItemDetails(42) } returns movieItem
        every { interactor.resolveMedia(movieItem, null, null, 7) } returns missingMedia

        val vm = createVM(movieParams).also { it.testOnStart() }

        assertEquals(
            "string_${R.string.player_error_playback}",
            (vm.testStateValue as PlayerViewState.Error).message,
        )
        verify(exactly = 0) { contentStateFactory.build(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { playbackController.prepare(any(), any(), any()) }
    }

    @Test
    fun playerScreenKey_includesExplicitMovieVideoNumber() {
        assertEquals(
            "PlayerScreen_42_v7",
            ScreensImpl.player(itemId = 42, videoNumber = 7).key,
        )
    }

    @Test
    fun playerScreenKey_withoutVideoNumber_preservesExistingKey() {
        assertEquals(
            "PlayerScreen_42_s1_e2",
            ScreensImpl.player(itemId = 42, seasonNumber = 1, episodeNumber = 2).key,
        )
    }

    // endregion

    // region Play/Pause

    @Test
    fun togglePause_whenPlaying() {
        every { playbackController.isPlaying } returns true
        startedVM().onAction(PlayerAction.TogglePlayPause)
        verify { playbackController.pause() }
    }

    @Test
    fun togglePlay_whenPaused() {
        every { playbackController.isPlaying } returns false
        startedVM().onAction(PlayerAction.TogglePlayPause)
        verify { playbackController.play() }
    }

    // endregion

    // region Panels

    @Test
    fun openAudioPanel_setsActivePanel() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)
        assertEquals(ActivePanel.AudioSubtitles, contentState(vm).activePanel)
    }

    @Test
    fun closePanel_resetsToNone() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)
        vm.onAction(PlayerAction.ClosePanel)
        assertEquals(ActivePanel.None, contentState(vm).activePanel)
    }

    // endregion

    // region Error

    @Test
    fun onError_setsErrorState() {
        val vm = startedVM()
        callbackSlot.captured.onError("Network error")
        assertTrue(vm.testStateValue is PlayerViewState.Error)
        assertEquals("Network error", (vm.testStateValue as PlayerViewState.Error).message)
    }

    // endregion

    // region Episodes panel

    @Test
    fun openEpisodesPanel_setsActivePanel() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenEpisodesPanel)
        assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)
    }

    @Test
    fun openVideoSettingsPanel_setsActivePanel() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenVideoSettingsPanel)
        assertEquals(ActivePanel.VideoSettings, contentState(vm).activePanel)
    }

    // endregion
}
