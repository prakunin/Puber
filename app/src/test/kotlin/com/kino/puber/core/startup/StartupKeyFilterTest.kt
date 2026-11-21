package com.kino.puber.core.startup

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StartupKeyFilterTest {
    private val filter = CoveredKeyFilter()
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP
    private val dropped = listOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
    )

    @Test
    fun dpadAndOkAreDroppedWhileCovered() {
        for (key in dropped) {
            assertTrue(isDroppedWhileCovered(key), "predicate $key")
            assertTrue(CoveredKeyFilter().shouldDrop(covered = true, action = down, keyCode = key), "down $key")
            assertTrue(CoveredKeyFilter().shouldDrop(covered = true, action = up, keyCode = key), "up $key")
        }
    }

    @Test
    fun backAndOtherKeysPass() {
        val passing = listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
        )
        for (key in passing) {
            assertFalse(isDroppedWhileCovered(key), "predicate $key")
            assertFalse(filter.shouldDrop(covered = true, action = down, keyCode = key), "down $key")
            assertFalse(filter.shouldDrop(covered = true, action = up, keyCode = key), "up $key")
        }
    }

    @Test
    fun everythingPassesWhenNotCovered() {
        for (key in dropped) {
            assertFalse(CoveredKeyFilter().shouldDrop(covered = false, action = down, keyCode = key), "down $key")
        }
    }

    @Test
    fun upOfAKeyPressedWhileCoveredIsDroppedAfterReveal() {
        val ok = KeyEvent.KEYCODE_DPAD_CENTER
        assertTrue(filter.shouldDrop(covered = true, action = down, keyCode = ok))
        assertTrue(filter.shouldDrop(covered = false, action = down, keyCode = ok), "repeat after reveal")
        assertTrue(filter.shouldDrop(covered = false, action = up, keyCode = ok))
        assertFalse(filter.shouldDrop(covered = false, action = down, keyCode = ok), "a fresh press passes")
        assertFalse(filter.shouldDrop(covered = false, action = up, keyCode = ok))
    }
}
