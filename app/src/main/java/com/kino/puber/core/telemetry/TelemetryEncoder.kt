package com.kino.puber.core.telemetry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class SessionInfo(
    val build: String,
    val ver: String,
    val api: Int,
    val model: String,
    val ramClass: Int,
    val lowRam: Boolean,
    val hz: Float,
)

/**
 * The allow-list: one exhaustive `when` over [TelemetryEvent]. A new event type does not compile
 * until it has a branch here, and a branch writes exactly its catalogue fields.
 */
internal class TelemetryEncoder(
    private val sid: String,
    private val sessionStartElapsedMs: Long,
    private val names: TelemetryNames,
    private val sessionInfo: () -> SessionInfo,
    private val wallMs: () -> Long,
) {
    fun line(event: TelemetryEvent, scr: String): String = encode(event, scr).toString() + "\n"

    fun encode(event: TelemetryEvent, scr: String): JsonObject = buildJsonObject {
        put("t", event.t - sessionStartElapsedMs)
        put("sid", sid)
        put("type", event.type.wire)
        put("scr", scr)
        putFields(event)
    }

    private fun JsonObjectBuilder.putFields(event: TelemetryEvent) = when (event) {
        is TelemetryEvent.Session -> putSession()
        is TelemetryEvent.Startup -> putStartup(event)
        is TelemetryEvent.Nav -> putNav(event)
        is TelemetryEvent.Ready -> putReady(event)
        is TelemetryEvent.Act -> putAct(event)
        is TelemetryEvent.Err -> putErr(event)
        is TelemetryEvent.Frames -> putFrames(event)
        is TelemetryEvent.Key -> putKey(event)
        is TelemetryEvent.KeyLat -> putKeyLat(event)
        is TelemetryEvent.Mem -> putMem(event)
        is TelemetryEvent.Meta -> putMeta(event)
    }

    private fun JsonObjectBuilder.putSession() {
        val info = sessionInfo()
        put("build", info.build)
        put("ver", info.ver)
        put("api", info.api)
        put("model", info.model)
        put("ram_class", info.ramClass)
        put("low_ram", info.lowRam)
        put("hz", info.hz)
        put("wall_ms", wallMs())
    }

    private fun JsonObjectBuilder.putStartup(event: TelemetryEvent.Startup) {
        put("proc_start", 0)
        put("app_create", event.appCreateMs - event.procStartMs)
        event.appComposedMs?.let { put("app_composed", it - event.procStartMs) }
        event.firstContentMs?.let { put("first_content", it - event.procStartMs) }
    }

    private fun JsonObjectBuilder.putNav(event: TelemetryEvent.Nav) {
        put("cmd", event.cmd.wire)
        put("from", names.owner(event.from))
        put("to", names.owner(event.to))
    }

    private fun JsonObjectBuilder.putReady(event: TelemetryEvent.Ready) {
        put("st", names.plain(event.state))
        put("ms", event.sinceOpenMs)
    }

    private fun JsonObjectBuilder.putAct(event: TelemetryEvent.Act) {
        put("a", names.plain(event.action))
        put("n", event.n)
    }

    private fun JsonObjectBuilder.putErr(event: TelemetryEvent.Err) {
        put("src", event.src.wire)
        put("et", ErrorScrubber.type(event.error))
        put("msg", ErrorScrubber.message(event.error))
        put("frames", JsonArray(ErrorScrubber.frames(event.error).map(::JsonPrimitive)))
        put("n", event.n)
    }

    private fun JsonObjectBuilder.putFrames(event: TelemetryEvent.Frames) {
        put("frames", event.frames)
        put("janky", event.janky)
        put("p50", event.p50)
        put("p90", event.p90)
        put("p99", event.p99)
        put("max", event.maxMs)
    }

    private fun JsonObjectBuilder.putKey(event: TelemetryEvent.Key) {
        put("key", event.key.wire)
        put("n", event.n)
        put("dur_ms", event.durMs)
    }

    private fun JsonObjectBuilder.putKeyLat(event: TelemetryEvent.KeyLat) {
        put("key", event.key.wire)
        put("lat_ms", event.latMs)
        put("frame_ms", event.frameMs)
    }

    private fun JsonObjectBuilder.putMem(event: TelemetryEvent.Mem) {
        put("java_kb", event.javaKb)
        put("java_max_kb", event.javaMaxKb)
        put("native_kb", event.nativeKb)
        event.pssKb?.let { put("pss_kb", it) }
        event.trim?.let { put("trim", it) }
    }

    private fun JsonObjectBuilder.putMeta(event: TelemetryEvent.Meta) {
        put("ev", event.ev.wire)
        if (event.ev == MetaEv.DROPPED) {
            put(
                "counts",
                buildJsonObject {
                    event.counts.forEach { (type, count) -> put(type.wire, count) }
                },
            )
        }
    }
}
