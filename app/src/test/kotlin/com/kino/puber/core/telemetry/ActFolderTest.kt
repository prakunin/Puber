package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ActFolderTest {
    private val out = mutableListOf<TelemetryEvent>()
    private val folder = ActFolder()

    private fun act(t: Long, action: Class<*> = SampleState.Content::class.java) =
        TelemetryEvent.Act(t, HomeVM::class.java, action)

    private fun counts() = out.map { (it as TelemetryEvent.Act).n }

    @Test
    fun identicalActionsWithinOneSecondFoldIntoOneLineWithCount() {
        folder.offer(act(0), out::add)
        folder.offer(act(400), out::add)
        folder.offer(act(999), out::add)
        folder.flush(5_000, force = false, emit = out::add)
        assertEquals(listOf(3), counts())
    }

    @Test
    fun gapOfExactlyOneSecondStartsANewLine() {
        folder.offer(act(0), out::add)
        folder.offer(act(1_000), out::add)
        folder.flush(5_000, force = false, emit = out::add)
        assertEquals(listOf(1, 1), counts())
    }

    @Test
    fun aDifferentActionStartsANewLine() {
        folder.offer(act(0), out::add)
        folder.offer(act(10, HomeVM::class.java), out::add)
        folder.flush(0, force = true, emit = out::add)
        assertEquals(listOf(1, 1), counts())
    }

    @Test
    fun flushHoldsAPendingLineUntilItsWindowClosesUnlessForced() {
        folder.offer(act(0), out::add)
        folder.flush(500, force = false, emit = out::add)
        assertEquals(0, out.size)
        folder.flush(500, force = true, emit = out::add)
        assertEquals(1, out.size)
    }
}
