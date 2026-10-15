package com.kino.puber.core.telemetry

import com.kino.puber.core.logger.SensitiveRequestLogPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ErrorScrubberTest {
    @Test
    fun replacesEveryUrl() {
        val out = ErrorScrubber.scrub("a https://x.y/z?q=1 b HTTP://h.io/p c ftp://h/f")
        assertEquals("a <url> b <url> c <url>", out)
    }

    @Test
    fun cutsAtJsonInput() {
        val message = "Unexpected token at offset 5 JSON input: {\"title\":\"X\"}"
        assertEquals("Unexpected token at offset 5", ErrorScrubber.scrub(message))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("a b c", ErrorScrubber.scrub("  a \n\t b   c  "))
    }

    @Test
    fun capsAt160() {
        assertEquals(160, ErrorScrubber.scrub("x".repeat(500)).length)
        assertEquals("", ErrorScrubber.scrub(null))
    }

    @Test
    fun keepsSanitizedQueryRedaction() {
        val sanitized = SensitiveRequestLogPolicy().sanitizeText("failed access_token=abc123secret here")
        val out = ErrorScrubber.scrub(sanitized)
        assertFalse(out.contains("abc123secret"))
        assertTrue(out.contains("access_token=<redacted>"))
    }

    @Test
    fun messageSanitizesPlainThrowablesBeforeScrubbing() {
        val out = ErrorScrubber.message(IllegalStateException("bad refresh_token=hunter2 end"))
        assertFalse(out.contains("hunter2"))
    }

    @Test
    fun framesKeepAtMostFiveAppFrames() {
        val error = Throwable()
        error.stackTrace = Array(8) { StackTraceElement("com.kino.puber.Thing$it", "run", "F.kt", it + 1) } +
            StackTraceElement("java.lang.Thread", "run", "Thread.java", 1)
        val frames = ErrorScrubber.frames(error)
        assertEquals(5, frames.size)
        assertEquals("com.kino.puber.Thing0.run:1", frames.first())
    }

    @Test
    fun framesDropLibraryPrefixes() {
        val error = Throwable()
        error.stackTrace = arrayOf(
            StackTraceElement("java.util.X", "a", "X.java", 1),
            StackTraceElement("kotlinx.coroutines.Y", "b", "Y.kt", 2),
            StackTraceElement("okhttp3.Z", "c", "Z.kt", 3),
            StackTraceElement("io.ktor.Q", "d", "Q.kt", 4),
            StackTraceElement("timber.log.Timber", "e", "T.kt", 5),
            StackTraceElement("com.kino.puber.App", "f", "App.kt", 6),
        )
        assertEquals(listOf("com.kino.puber.App.f:6"), ErrorScrubber.frames(error))
    }

    private class Nested : IllegalStateException("x")

    @Test
    fun typeIsTheFullBinaryNameOnEveryPath() {
        val raw = Nested()
        val sanitized = SensitiveRequestLogPolicy().sanitizeThrowable(raw)
        val expected = "com.kino.puber.core.telemetry.ErrorScrubberTest\$Nested"

        assertEquals(expected, ErrorScrubber.type(raw))
        assertEquals(expected, ErrorScrubber.type(sanitized))
    }
}
