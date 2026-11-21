package com.kino.puber.core.startup

import android.view.KeyEvent

/** Keys that must not reach the content while the startup splash covers it. */
internal fun isDroppedWhileCovered(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    -> true
    else -> false
}

/**
 * Drops the covered keys, and the repeats and the `ACTION_UP` of a key whose `ACTION_DOWN` it dropped
 * even after the reveal, so an OK pressed under the splash cannot act on release. Main thread only.
 */
internal class CoveredKeyFilter {
    private val pressedWhileCovered = mutableSetOf<Int>()

    fun shouldDrop(covered: Boolean, action: Int, keyCode: Int): Boolean = when (action) {
        KeyEvent.ACTION_DOWN -> when {
            keyCode in pressedWhileCovered -> true
            covered && isDroppedWhileCovered(keyCode) -> pressedWhileCovered.add(keyCode)
            else -> false
        }
        KeyEvent.ACTION_UP -> pressedWhileCovered.remove(keyCode) || (covered && isDroppedWhileCovered(keyCode))
        else -> false
    }
}
