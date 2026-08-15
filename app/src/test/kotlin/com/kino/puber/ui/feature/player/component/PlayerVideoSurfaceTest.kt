package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import org.junit.jupiter.api.Assertions.assertEquals
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
}
