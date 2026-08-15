package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerButtonRowTest {

    @Test
    fun shouldOpenEpisodesFromButtons_returnsTrue_whenDownPressedForSeries() {
        assertTrue(
            shouldOpenEpisodesFromButtons(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                isMovie = false,
            )
        )
    }

    @Test
    fun shouldOpenEpisodesFromButtons_returnsFalse_whenDownPressedForMovie() {
        assertFalse(
            shouldOpenEpisodesFromButtons(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                isMovie = true,
            )
        )
    }

    @Test
    fun shouldOpenEpisodesFromButtons_returnsFalse_whenOtherDirectionPressed() {
        assertFalse(
            shouldOpenEpisodesFromButtons(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                isMovie = false,
            )
        )
    }
}
