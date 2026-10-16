package com.kino.puber.core.telemetry

import android.view.KeyEvent
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class KeyNameTest {
    private val encoder = TelemetryEncoder("sid", 0, TelemetryNames(), { testSessionInfo }, { 0 })

    companion object {
        @JvmStatic
        fun typedCodes(): List<Int> =
            (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() +
                (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toList() +
                (KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9).toList() +
                listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_UNKNOWN, 0, 9999, -1)
    }

    @ParameterizedTest
    @MethodSource("typedCodes")
    fun everyLetterDigitAndNumpadCodeMapsToOther(code: Int) {
        assertEquals(KeyName.OTHER, keyNameOf(code))
    }

    @Test
    fun allowListIsExactlyTheDocumentedCodes() {
        val expected = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to "dpad_up",
            KeyEvent.KEYCODE_DPAD_DOWN to "dpad_down",
            KeyEvent.KEYCODE_DPAD_LEFT to "dpad_left",
            KeyEvent.KEYCODE_DPAD_RIGHT to "dpad_right",
            KeyEvent.KEYCODE_DPAD_CENTER to "dpad_center",
            KeyEvent.KEYCODE_ENTER to "enter",
            KeyEvent.KEYCODE_NUMPAD_ENTER to "enter",
            KeyEvent.KEYCODE_BACK to "back",
            KeyEvent.KEYCODE_MENU to "menu",
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "play_pause",
            KeyEvent.KEYCODE_MEDIA_STOP to "stop",
            KeyEvent.KEYCODE_MEDIA_NEXT to "next",
            KeyEvent.KEYCODE_MEDIA_PREVIOUS to "previous",
            KeyEvent.KEYCODE_MEDIA_REWIND to "rewind",
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "fast_forward",
            KeyEvent.KEYCODE_MEDIA_PLAY to "play",
            KeyEvent.KEYCODE_MEDIA_PAUSE to "pause",
            KeyEvent.KEYCODE_CHANNEL_UP to "channel_up",
            KeyEvent.KEYCODE_CHANNEL_DOWN to "channel_down",
            KeyEvent.KEYCODE_PAGE_UP to "page_up",
            KeyEvent.KEYCODE_PAGE_DOWN to "page_down",
            KeyEvent.KEYCODE_MOVE_HOME to "move_home",
            KeyEvent.KEYCODE_MOVE_END to "move_end",
        )
        for (code in 0..400) {
            val wire = expected[code] ?: "other"
            assertEquals(wire, keyNameOf(code).wire, "code $code")
        }
    }

    @ParameterizedTest
    @MethodSource("typedCodes")
    fun encodedKeyAndKeyLatLinesNeverContainTheRawCode(code: Int) {
        val name = keyNameOf(code)
        val key = encoder.encode(TelemetryEvent.Key(1, null, name, 1, 100_000), "")
        val lat = encoder.encode(TelemetryEvent.KeyLat(1, null, name, 77_777, 88_888), "")
        for (json in listOf(key, lat)) {
            assertEquals("other", json.getValue("key").jsonPrimitive.content)
            val values = json.filterKeys { it != "t" }.values.map { it.jsonPrimitive.content }
            assertFalse(values.contains(code.toString()), "code $code leaked in $json")
        }
    }

    @Test
    fun wireNamesAreUniqueAndNeverASingleLetterOrDigit() {
        val wires = KeyName.entries.map { it.wire }.filter { it != "enter" }
        assertEquals(wires.size, wires.toSet().size)
        for (wire in KeyName.entries.map { it.wire }) {
            assertNotEquals(1, wire.length)
            assertFalse(wire.all { it.isDigit() })
        }
        assertTrue(KeyName.entries.count { it.wire == "enter" } == 1)
    }

    @Test
    fun eventsCarryTheEnumNotAnInt() {
        for (type in listOf(TelemetryEvent.Key::class.java, TelemetryEvent.KeyLat::class.java)) {
            type.declaredConstructors.forEach { ctor ->
                ctor.parameters.forEach { p ->
                    assertFalse(
                        p.type == Int::class.javaPrimitiveType && p.name.lowercase().contains("code"),
                        "${type.simpleName} takes a raw code",
                    )
                }
            }
            assertTrue(type.declaredFields.none { it.name.lowercase().contains("code") })
            assertTrue(type.declaredFields.any { it.type == KeyName::class.java })
        }
    }
}
