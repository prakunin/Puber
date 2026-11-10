package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.vm.PlayerVMTestFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    fun seekActionForKeyCode_returnsSeekActions_forLeftRightAndMediaSeekKeys() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        ).forEach { assertEquals(PlayerAction.SeekBackward, seekActionForKeyCode(it)) }
        listOf(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        ).forEach { assertEquals(PlayerAction.SeekForward, seekActionForKeyCode(it)) }
    }

    @Test
    fun seekActionForKeyCode_returnsNull_forUpDownCenterAndPlayPause() {
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { assertNull(seekActionForKeyCode(it)) }
    }

    @Test
    fun shouldKeepScreenOn_isFalse_whenPausedOrEnded_andTrueWhenPlaying() {
        val base = minimalContent()

        assertFalse(base.copy(isPlaying = false, isBuffering = true).shouldKeepScreenOn())
        assertFalse(base.copy(isPlaying = false, isBuffering = false).shouldKeepScreenOn())
        assertTrue(base.copy(isPlaying = true).shouldKeepScreenOn())
    }

    private fun minimalContent(): PlayerContentState = object : PlayerVMTestFixture() {
        fun content() = testContentState
    }.content()
}
