package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

internal class FrameAggregatorTest {
    private val out = mutableListOf<TelemetryEvent.Frames>()
    private var nowMs = 10_000L
    private var nano = 0L
    private val agg = FrameAggregator({ out += it }, { nowMs }, { nano })
    private val home = HomeScreen::class.java
    private val history = HistoryScreen::class.java
    private val other = ScreenScreen::class.java

    private fun ms(v: Long) = v * 1_000_000L

    /** Feeds [count] frames of [uiMs] each, 16 ms apart, starting at [from]; returns the next start. */
    private fun feed(count: Int, uiMs: Long, from: Long, screen: Class<*>? = home, janky: Boolean = false): Long {
        var start = from
        repeat(count) {
            agg.onFrame(start, ms(uiMs), janky, screen)
            start += ms(16)
        }
        return start
    }

    @Test
    fun percentilesAndExactMaxOnCraftedFeed() {
        var start = 0L
        start = feed(90, 8, start)
        start = feed(9, 20, start)
        agg.onFrame(start, ms(250), false, home)
        // 12 janky frames: re-feed is not possible, so count them through a second aggregator run below.
        agg.close()
        val e = out.single()
        assertEquals(100, e.frames)
        assertEquals(listOf(8, 8, 20, 250), listOf(e.p50, e.p90, e.p99, e.maxMs))
    }

    @Test
    fun jankyFramesAreCounted() {
        var start = feed(88, 8, 0)
        start = feed(12, 8, start, janky = true)
        agg.close()
        assertEquals(12, out.single().janky)
        assertEquals(100, out.single().frames)
    }

    @Test
    fun percentileInOverflowBucketReportsExactMaxAndSubMsFloors() {
        var start = feed(98, 8, 0)
        agg.onFrame(start, ms(150), false, home)
        start += ms(16)
        agg.onFrame(start, ms(300), false, home)
        agg.close()
        val e = out.single()
        assertEquals(300, e.p99)
        assertEquals(300, e.maxMs)

        out.clear()
        var s = 0L
        repeat(5) {
            agg.onFrame(s, 8_900_000, false, home)
            s += ms(16)
        }
        agg.close()
        assertEquals(8, out.single().p50)
        assertEquals(8, out.single().maxMs)
    }

    @Test
    fun windowRollsOverAtTenSecondsInclusive() {
        agg.onFrame(0, ms(8), false, home)
        agg.onFrame(9_999_999_999L, ms(8), false, home)
        assertEquals(0, out.size)
        agg.onFrame(10_000_000_000L, ms(8), false, home)
        assertEquals(1, out.size)
        assertEquals(2, out.single().frames)
        agg.close()
        assertEquals(1, out.last().frames)
        assertEquals(2, out.size)
    }

    @Test
    fun secondWindowStartsFromZeroedHistogram() {
        feed(5, 90, 0)
        feed(5, 5, 10_000_000_000L)
        agg.close()
        assertEquals(2, out.size)
        assertEquals(90, out[0].p99)
        assertEquals(5, out[1].p99)
        assertEquals(5, out[1].maxMs)
        assertEquals(5, out[1].frames)
    }

    @Test
    fun jankyCountOfSecondWindowIsItsOwn() {
        feed(5, 8, 0, janky = true)
        feed(5, 8, 10_000_000_000L)
        agg.close()
        assertEquals(listOf(5, 0), out.map { it.janky })
    }

    @Test
    fun closeEmitsOpenWindowOnceAndEmptyWindowEmitsNothing() {
        agg.close()
        assertEquals(0, out.size)
        feed(3, 8, 0)
        agg.close()
        assertEquals(1, out.size)
        assertEquals(3, out.single().frames)
        agg.close()
        assertEquals(1, out.size)
    }

    @Test
    fun screenChangeClosesWindowWithTheOldScreen() {
        var start = feed(5, 8, 0, home)
        start = feed(1, 8, start, history)
        feed(1, 8, start, other)
        assertEquals(2, out.size)
        assertSame(home, out[0].screen)
        assertSame(history, out[1].screen)
        assertEquals(listOf(5, 1), out.map { it.frames })
        agg.close()
        assertSame(other, out[2].screen)
    }

    @Test
    fun nullScreenIsAWindowOfItsOwn() {
        val start = feed(2, 8, 0, null)
        feed(1, 8, start, home)
        assertEquals(1, out.size)
        assertEquals(null, out.single().screen)
        assertEquals(2, out.single().frames)
    }

    @Test
    fun idleGapBackDatesTToTheLastFrameNotTheClose() {
        nowMs = 10_000
        agg.onFrame(ms(1_000), ms(8), false, home)
        nowMs = 11_000
        agg.onFrame(ms(2_000), ms(8), false, home)
        nowMs = 41_000
        nano = ms(32_000)
        agg.onFrame(ms(32_000), ms(8), false, home)
        val e = out.single()
        assertTrue(e.t in 11_000..11_010, "t=${e.t}")
        assertTrue(e.t < nowMs)
    }

    @Test
    fun backDatedTIsClampedToWhenTheWindowOpened() {
        nowMs = 10_000
        agg.onFrame(ms(1_000), ms(8), false, home)
        nowMs = 30_000
        nano = ms(1_008) + ms(60_000)
        agg.close()
        assertEquals(10_000, out.single().t)
    }

    @Test
    fun thousandFramesInsideAWindowCallEmitNeverAndAllocateNothing() {
        repeat(1_000) { agg.onFrame(ms(1L * it), ms(8), false, home) }
        assertEquals(0, out.size)
        val bytes = allocatedBytesBy { repeat(200_000) { agg.onFrame(ms(1_000) + it, ms(8), false, home) } }
        assumeTrue(bytes != null)
        assertNotNull(bytes)
        assertTrue(bytes!! < ALLOCATION_BOUND, "allocated $bytes")
        val control = allocatedBytesBy { allocatingLoop(200_000) }
        assertTrue(control!! > ALLOCATION_BOUND)
    }
}
