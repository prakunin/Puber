package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class TelemetryPrivacyTest {
    private val encoder = TelemetryEncoder(
        sid = "abcd1234",
        sessionStartElapsedMs = 0,
        names = TelemetryNames(),
        sessionInfo = { SessionInfo("debug", "1.0", 36, "Pixel", 256, false, 60f) },
        wallMs = { 0 },
    )

    // A title typed into a custom exception message as bare prose cannot be recognised by any
    // scrubber; the spec lists it as a residual risk caught only by the device audit. Titles and
    // search texts reach messages through URLs and JSON excerpts, which is what is covered here.
    @Test
    fun noSensitiveValueAppearsInAnyEncodedLine() {
        val title = "The Secret Movie Title"
        val search = "ZQXPROBE"
        val tokenUrl = "https://api.example.com/v1/items?access_token=tok-9f3a&q=ZQXPROBE"
        val json = """{"title":"The Secret Movie Title","id":99123}"""
        val screen = ParameterizedScreen("id-777123")
        val encodedTitle = title.replace(" ", "%20")
        val forbidden = listOf(title, encodedTitle, search, "tok-9f3a", "api.example.com", "id-777123", "99123")

        val errors = listOf(
            IllegalStateException("failed https://api.example.com/v1/items/search?title=$encodedTitle&q=$search"),
            IllegalStateException("GET $tokenUrl failed"),
            IllegalStateException("access_token=tok-9f3a rejected"),
            IllegalStateException("Unexpected JSON input: $json"),
        )
        val events = errors.map { TelemetryEvent.Err(1, ErrSource.LOG, it) } + listOf(
            TelemetryEvent.Nav(1, NavCmd.PUSH, null, telemetryClassOf(screen)),
            TelemetryEvent.Act(1, HomeVM::class.java, SampleState.Content::class.java),
        )
        val lines = events.map { encoder.line(it, "Parameterized") }

        for (line in lines) {
            for (literal in forbidden) {
                assertFalse(line.contains(literal), "$literal leaked in $line")
            }
        }
    }
}
