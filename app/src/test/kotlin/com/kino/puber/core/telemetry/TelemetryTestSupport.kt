package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import com.kino.puber.util.ManualTelemetryExecutor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val testSessionInfo = SessionInfo("debug", "1.0", 36, "Pixel", 256, false, 60f)

internal fun parseLine(line: String): JsonObject = Json.parseToJsonElement(line).jsonObject

internal fun JsonObject.str(key: String): String? = get(key)?.jsonPrimitive?.content

/** A sink on a manual executor and a fake store: the "writer thread" runs when the test says so. */
internal class SinkHarness(
    val store: FakeTelemetryStore = FakeTelemetryStore(),
    val clock: FakeTelemetryClock = FakeTelemetryClock(elapsed = 0),
) {
    val executor = ManualTelemetryExecutor()
    var infoCalls = 0
    val sink = TelemetrySink(clock, store, executor, { infoCalls++; testSessionInfo }, sid = "deadbeef")
    var expired = 0
    var stopped = 0

    fun start(enabledAtWallMs: Long = clock.wall) {
        sink.start(enabledAtWallMs, onExpired = { expired++ }, onStopped = { stopped++ })
        executor.runAll()
    }

    fun drain() = executor.runRepeating()

    fun allLines(): List<JsonObject> =
        store.files.keys.sorted().flatMap { name -> store.lines(name).map(::parseLine) }

    fun linesOf(type: String) = allLines().filter { it.str("type") == type }
}
