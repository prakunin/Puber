package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerAboutUIState
import com.kino.puber.ui.feature.player.model.PlayerContentState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerVideoSurfaceTest {

    @Test
    fun playerActionForKeyCode_opensButtons_whenDownPressed() {
        val action = playerActionForKeyCode(
            keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
        )

        assertEquals(PlayerAction.ShowControls(FocusTarget.Buttons), action)
    }

    @Test
    fun playerActionForKeyCode_opensSeekControls_whenUpPressed() {
        val action = playerActionForKeyCode(
            keyCode = KeyEvent.KEYCODE_DPAD_UP,
        )

        assertEquals(PlayerAction.ShowControls(FocusTarget.SeekBar), action)
    }

    @Test
    fun shouldKeepScreenOn_isFalse_whenPausedOrEnded_andTrueWhenPlaying() {
        val base = minimalContent()

        assertFalse(base.copy(isPlaying = false, isBuffering = true).shouldKeepScreenOn())
        assertFalse(base.copy(isPlaying = false, isBuffering = false).shouldKeepScreenOn())
        assertTrue(base.copy(isPlaying = true).shouldKeepScreenOn())
    }

    private fun minimalContent() = PlayerContentState(
        title = "Title",
        subtitle = null,
        about = PlayerAboutUIState(
            title = "Title",
            metaLine = "",
            genresLine = "",
            ratingsLine = "",
            description = "",
            director = "",
            cast = "",
        ),
        isPlaying = false,
        currentPosition = 0L,
        duration = 0L,
        bufferedPosition = 0L,
        controlsVisible = false,
        controlsFocusTarget = null,
        activePanel = ActivePanel.None,
        seekIndicator = null,
        playPauseIndicator = null,
        audioTracks = emptyList(),
        selectedAudioTrackIndex = 0,
        subtitleTracks = emptyList(),
        selectedSubtitleIndex = 0,
        soundModes = emptyList(),
        selectedSoundModeIndex = 0,
        subtitleSize = SubtitleSize.MEDIUM,
        qualities = emptyList(),
        selectedQualityIndex = 0,
        speeds = emptyList(),
        selectedSpeedIndex = 0,
        aspectRatios = emptyList(),
        selectedAspectRatioIndex = 0,
        bufferPresets = emptyList(),
        selectedBufferPresetIndex = 0,
        isMovie = false,
        hasNextEpisode = false,
        hasPreviousEpisode = false,
        nextEpisodeCountdown = null,
        canMarkCurrentWatched = false,
        isCurrentMediaWatched = false,
        isMarkCurrentWatchedInFlight = false,
        resumeDialog = null,
        episodes = null,
        currentEpisodeId = null,
    )
}
