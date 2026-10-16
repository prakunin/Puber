package com.kino.puber.core.telemetry

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

internal class KeyBurstMachineTest {
    private val out = mutableListOf<TelemetryEvent.Key>()
    private val machine = KeyBurstMachine({ out += it }, { 777 })
    private val home = HomeScreen::class.java
    private val down = KeyEvent.KEYCODE_DPAD_DOWN
    private val down2 = KeyEvent.ACTION_DOWN
    private val up2 = KeyEvent.ACTION_UP

    private fun press(code: Int, repeat: Int, at: Long) = machine.onKey(down2, code, repeat, at, home)

    private fun release(code: Int, at: Long) = machine.onKey(up2, code, 0, at, home)

    @Test
    fun longPressEmitsOneKeyWithCountAndDurationOnUp() {
        press(down, 0, 1_000)
        for (r in 1..9) press(down, r, 1_000L + r * 50)
        assertEquals(0, out.size)
        release(down, 1_600)
        val e = out.single()
        assertEquals(KeyName.DPAD_DOWN, e.key)
        assertEquals(10, e.n)
        assertEquals(600, e.durMs)
        assertSame(home, e.screen)
        assertEquals(777, e.t)
    }

    @Test
    fun threeSinglePressesEmitThreeKeysWithNOne() {
        press(down, 0, 1_000)
        release(down, 1_040)
        press(down, 0, 1_500)
        release(down, 1_590)
        press(down, 0, 2_000)
        release(down, 2_015)
        assertEquals(listOf(1, 1, 1), out.map { it.n })
        assertEquals(listOf(40L, 90L, 15L), out.map { it.durMs })
    }

    @Test
    fun differentKeyClosesOpenBurstWithDurationFromLastRepeat() {
        press(down, 0, 1_000)
        for (r in 1..3) press(down, r, 1_000L + r * 100)
        press(KeyEvent.KEYCODE_BACK, 0, 1_700)
        val e = out.single()
        assertEquals(KeyName.DPAD_DOWN, e.key)
        assertEquals(4, e.n)
        assertEquals(300, e.durMs)
        release(KeyEvent.KEYCODE_BACK, 1_750)
        assertEquals(KeyName.BACK, out.last().key)
        assertEquals(2, out.size)
    }

    @Test
    fun idleGapAboveTwoSecondsEmitsOnNextEventAndBoundaryDoesNot() {
        press(down, 0, 1_000)
        press(down, 1, 3_000)
        assertEquals(0, out.size)
        press(down, 2, 5_001)
        val e = out.single()
        assertEquals(2, e.n)
        assertEquals(2_000, e.durMs)
        // The event that closed it opened a burst of its own.
        machine.flush()
        assertEquals(2, out.size)
        assertEquals(1, out.last().n)
    }

    @Test
    fun flushEmitsOpenBurstOnceAndEmptyFlushEmitsNothing() {
        machine.flush()
        assertEquals(0, out.size)
        press(down, 0, 1_000)
        press(down, 1, 1_100)
        machine.flush()
        assertEquals(1, out.size)
        assertEquals(100, out.single().durMs)
        machine.flush()
        assertEquals(1, out.size)
    }

    @Test
    fun repeatWithoutOpenBurstOpensOneWithNOneAndNeverThrows() {
        press(down, 5, 1_000)
        press(down, 6, 1_050)
        release(down, 1_100)
        val e = out.single()
        assertEquals(2, e.n)
        assertEquals(100, e.durMs)
    }

    @Test
    fun upOfAKeyThatIsNotOpenIsIgnored() {
        release(down, 900)
        assertEquals(0, out.size)
        press(down, 0, 1_000)
        release(KeyEvent.KEYCODE_DPAD_UP, 1_010)
        assertEquals(0, out.size)
        release(down, 1_020)
        assertEquals(1, out.size)
    }

    @Test
    fun repeatsAfterTheFirstDoNotAllocate() {
        press(down, 0, 1_000)
        val bytes = allocatedBytesBy { for (r in 1..100_000) machine.onKey(down2, down, r, 1_000L + r, home) }
        assumeTrue(bytes != null)
        assertTrue(bytes!! < ALLOCATION_BOUND, "allocated $bytes")
        assertTrue(allocatedBytesBy { allocatingLoop(100_000) }!! > ALLOCATION_BOUND)
        release(down, 101_100)
        assertEquals(100_001, out.single().n)
    }
}
