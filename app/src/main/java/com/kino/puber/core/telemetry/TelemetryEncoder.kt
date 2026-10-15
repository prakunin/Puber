package com.kino.puber.core.telemetry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
        when (event) {
            is TelemetryEvent.Session -> {
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

            is TelemetryEvent.Startup -> {
                put("proc_start", 0)
                put("app_create", event.appCreateMs - event.procStartMs)
                event.appComposedMs?.let { put("app_composed", it - event.procStartMs) }
                event.firstContentMs?.let { put("first_content", it - event.procStartMs) }
            }

            is TelemetryEvent.Nav -> {
                put("cmd", event.cmd.wire)
                put("from", names.owner(event.from))
                put("to", names.owner(event.to))
            }

            is TelemetryEvent.Ready -> {
                put("st", names.plain(event.state))
                put("ms", event.sinceOpenMs)
            }

            is TelemetryEvent.Act -> {
                put("a", names.plain(event.action))
                put("n", event.n)
            }

            is TelemetryEvent.Err -> {
                put("src", event.src.wire)
                put("et", ErrorScrubber.type(event.error))
                put("msg", ErrorScrubber.message(event.error))
                put("frames", JsonArray(ErrorScrubber.frames(event.error).map(::JsonPrimitive)))
                put("n", event.n)
            }

            is TelemetryEvent.Meta -> {
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
    }
}
