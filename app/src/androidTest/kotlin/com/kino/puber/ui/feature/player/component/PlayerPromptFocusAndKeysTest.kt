package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.domain.model.BufferPreset
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPresetUIState
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAboutUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val SKIP_LABEL = "Skip intro"
private const val PROMPT_SECONDS = 7
private const val HOLD_MS = 1_000L

/**
 * The skip and next-episode prompts over the bare video: what Left/Right/OK/Back do while one is
 * up, and who holds the focus as it comes and goes. The prompt must never take the seek keys.
 */
internal class PlayerPromptFocusAndKeysTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<UIAction>()
    private var state by mutableStateOf<PlayerViewState>(PlayerViewState.Content(contentState()))

    private val nextEpisodeLabel: String
        get() = rule.activity.getString(R.string.player_next_episode)

    private fun show(content: PlayerContentState) {
        rule.runOnUiThread { state = PlayerViewState.Content(content) }
        rule.waitForIdle()
    }

    private fun launch(content: PlayerContentState = contentState()) {
        state = PlayerViewState.Content(content)
        rule.setContent {
            PuberTheme {
                PlayerScreenContent(state = state, onAction = actions::add, exoPlayer = { null })
            }
        }
        rule.waitForIdle()
    }

    // The action chip prints "<label> " and draws the seconds beside it, so its merged text is
    // the label plus a no-break space: match the merged node by substring.
    private fun action(label: String): SemanticsNodeInteraction =
        rule.onNode(hasClickAction() and hasText(label, substring = true))

    private fun press(key: Key, holdMs: Long = 0) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            if (holdMs > 0) advanceEventTime(holdMs)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    @Test
    fun skipPrompt_leftAndRightSeekAndFocusStaysOnAction() {
        launch(contentState(skip = skipPrompt()))

        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        assertTrue(PlayerAction.SeekBackward in actions)
        assertTrue(PlayerAction.SeekForward in actions)
        action(SKIP_LABEL).assertIsFocused()
    }

    @Test
    fun skipPrompt_heldRightSeeksOnEveryRepeat() {
        launch(contentState(skip = skipPrompt()))

        press(Key.DirectionRight, holdMs = HOLD_MS)

        assertTrue(actions.count { it == PlayerAction.SeekForward } > 1)
    }

    @Test
    fun nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction() {
        launch(contentState(nextEpisode = PROMPT_SECONDS))

        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        assertTrue(PlayerAction.SeekBackward in actions)
        assertTrue(PlayerAction.SeekForward in actions)
        action(nextEpisodeLabel).assertIsFocused()
    }

    @Test
    fun skipPromptHidden_nextRightSeeks() {
        launch(contentState(skip = skipPrompt()))
        show(contentState(skip = null))

        press(Key.DirectionRight)

        assertTrue(PlayerAction.SeekForward in actions)
    }

    @Test
    fun nextEpisodePromptHidden_nextRightSeeks() {
        launch(contentState(nextEpisode = PROMPT_SECONDS))
        show(contentState(nextEpisode = null))

        press(Key.DirectionRight)

        assertTrue(PlayerAction.SeekForward in actions)
    }

    @Test
    fun skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction() {
        launch(contentState(skip = skipPrompt()))
        show(contentState(skip = skipPrompt(), nextEpisode = PROMPT_SECONDS))

        action(nextEpisodeLabel).assertIsFocused()
    }

    @Test
    fun skipPrompt_appearsWithActionFocused_okSkips() {
        launch(contentState(skip = skipPrompt()))

        action(SKIP_LABEL).assertIsFocused()
        press(Key.DirectionCenter)

        assertTrue(PlayerAction.SkipSegmentClicked in actions)
    }

    @Test
    fun skipPrompt_backCancels() {
        launch(contentState(skip = skipPrompt()))

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertTrue(PlayerAction.CancelSkipSegment in actions)
    }

    @Test
    fun promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks() {
        launch(contentState(controlsVisible = true, controlsFocusTarget = FocusTarget.SeekBar))
        show(
            contentState(
                controlsVisible = true,
                controlsFocusTarget = FocusTarget.SeekBar,
                skip = skipPrompt(),
            ),
        )

        action(SKIP_LABEL).assertIsNotFocused()
        press(Key.DirectionRight)

        assertTrue(PlayerAction.SeekForward in actions)
    }

    @Test
    fun controlsHidingWhilePromptIsUp_focusesThePromptAction() {
        launch(
            contentState(
                controlsVisible = true,
                controlsFocusTarget = FocusTarget.SeekBar,
                skip = skipPrompt(),
            ),
        )
        show(contentState(controlsVisible = false, skip = skipPrompt()))

        action(SKIP_LABEL).assertIsFocused()
    }

    @Test
    fun skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel() {
        launch(contentState(skip = skipPrompt()))

        press(Key.DirectionLeft, holdMs = HOLD_MS)
        press(Key.DirectionRight, holdMs = HOLD_MS)

        assertFalse(PlayerAction.SkipSegmentClicked in actions)
        assertFalse(PlayerAction.CancelSkipSegment in actions)
    }

    @Test
    fun skipPrompt_okUpWithoutDownDoesNotSkip() {
        launch(contentState(skip = skipPrompt()))
        action(SKIP_LABEL).assertIsFocused()

        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        }
        rule.waitForIdle()

        assertFalse(PlayerAction.SkipSegmentClicked in actions)
    }

    private fun skipPrompt() = SkipSegmentUIState(
        label = SKIP_LABEL,
        targetPositionMs = 60_000,
        type = SkipSegmentType.INTRO,
        countdown = PROMPT_SECONDS,
    )

    private fun contentState(
        controlsVisible: Boolean = false,
        controlsFocusTarget: FocusTarget? = null,
        skip: SkipSegmentUIState? = null,
        nextEpisode: Int? = null,
    ) = PlayerContentState(
        title = "Breaking Bad",
        subtitle = "S1E1",
        about = PlayerAboutUIState(
            title = "Breaking Bad",
            metaLine = "2008 · 47 min · USA · 18+",
            genresLine = "Drama, crime",
            ratingsLine = "IMDb 9.5",
            description = "A chemistry teacher starts cooking.",
            director = "Vince Gilligan",
            cast = "Bryan Cranston, Aaron Paul",
        ),
        isPlaying = true,
        currentPosition = 0L,
        duration = 2_400_000L,
        bufferedPosition = 0L,
        controlsVisible = controlsVisible,
        controlsFocusTarget = controlsFocusTarget,
        activePanel = ActivePanel.None,
        seekIndicator = null,
        playPauseIndicator = null,
        audioTracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Russian", "rus"),
        ),
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
        bufferPresets = listOf(BufferPresetUIState(0, "Auto", BufferPreset.AUTO)),
        selectedBufferPresetIndex = 0,
        isMovie = false,
        hasNextEpisode = true,
        hasPreviousEpisode = true,
        nextEpisodeCountdown = nextEpisode,
        activeSkipSegment = skip,
        canMarkCurrentWatched = true,
        isCurrentMediaWatched = false,
        isMarkCurrentWatchedInFlight = false,
        resumeDialog = null,
        episodes = null,
        currentEpisodeId = 101,
    )
}
