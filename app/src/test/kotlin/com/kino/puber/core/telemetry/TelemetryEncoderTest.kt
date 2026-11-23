package com.kino.puber.core.telemetry

import com.kino.puber.core.startup.RevealCause
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class TelemetryEncoderTest {
    private val encoder = TelemetryEncoder(
        sid = "abcd1234",
        sessionStartElapsedMs = 1_000,
        names = TelemetryNames(),
        sessionInfo = { SessionInfo("debug", "1.0", 36, "Pixel", 256, false, 60f) },
        wallMs = { 1_700_000_000_000 },
    )
    private val envelope = setOf("t", "sid", "type", "scr")

    private val samples: Map<TelemetryEventType, Pair<TelemetryEvent, Set<String>>> = mapOf(
        TelemetryEventType.SESSION to (
            TelemetryEvent.Session(1_500) to
                setOf("build", "ver", "api", "model", "ram_class", "low_ram", "hz", "wall_ms")
            ),
        TelemetryEventType.STARTUP to (
            TelemetryEvent.Startup(2_000, 100, 150, 300, 900) to
                setOf("proc_start", "app_create", "app_composed", "first_content", "dropped_keys")
            ),
        TelemetryEventType.NAV to (
            TelemetryEvent.Nav(2_000, NavCmd.PUSH, HomeScreen::class.java, ScreenScreen::class.java) to
                setOf("cmd", "from", "to")
            ),
        TelemetryEventType.READY to (
            TelemetryEvent.Ready(2_000, HomeVM::class.java, SampleState.Content::class.java, 42) to
                setOf("st", "ms")
            ),
        TelemetryEventType.ACT to (
            TelemetryEvent.Act(2_000, HomeVM::class.java, SampleState.Content::class.java, 3) to
                setOf("a", "n")
            ),
        TelemetryEventType.ERR to (
            TelemetryEvent.Err(2_000, ErrSource.LOG, IllegalStateException("boom")) to
                setOf("src", "et", "msg", "frames", "n")
            ),
        TelemetryEventType.FRAMES to (
            TelemetryEvent.Frames(2_000, HomeScreen::class.java, 100, 12, 8, 8, 20, 250) to
                setOf("frames", "janky", "p50", "p90", "p99", "max")
            ),
        TelemetryEventType.KEY to (
            TelemetryEvent.Key(2_000, HomeScreen::class.java, KeyName.DPAD_DOWN, 10, 450) to
                setOf("key", "n", "dur_ms")
            ),
        TelemetryEventType.KEY_LAT to (
            TelemetryEvent.KeyLat(2_000, HomeScreen::class.java, KeyName.BACK, 70, 33) to
                setOf("key", "lat_ms", "frame_ms")
            ),
        TelemetryEventType.MEM to (
            TelemetryEvent.Mem(2_000, HomeScreen::class.java, 1_000, 2_000, 3_000, 4_000, null) to
                setOf("java_kb", "java_max_kb", "native_kb", "pss_kb")
            ),
        TelemetryEventType.META to (
            TelemetryEvent.Meta(2_000, MetaEv.START) to setOf("ev")
            ),
    )

    @ParameterizedTest
    @EnumSource(TelemetryEventType::class)
    fun everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields(type: TelemetryEventType) {
        val (event, fields) = requireNotNull(samples[type]) { "no sample for $type" }
        assertEquals(type, event.type)
        val json = encoder.encode(event, "Home")
        assertEquals(envelope + fields, json.keys)
        assertEquals(listOf("t", "sid", "type", "scr"), json.keys.take(4))
        assertEquals(type.wire, json.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun memTrimLineCarriesTrimAndNoPss() {
        val json = encoder.encode(
            TelemetryEvent.Mem(2_000, HomeScreen::class.java, 1_000, 2_000, 3_000, null, 10),
            "Home",
        )
        assertEquals(envelope + setOf("java_kb", "java_max_kb", "native_kb", "trim"), json.keys)
        assertEquals(10L, json.getValue("trim").jsonPrimitive.long)
    }

    @Test
    fun newTypesWriteTheirValues() {
        val frames = encoder.encode(samples.getValue(TelemetryEventType.FRAMES).first, "Home")
        val names = listOf("frames", "janky", "p50", "p90", "p99", "max")
        assertEquals(listOf(100L, 12L, 8L, 8L, 20L, 250L), names.map { frames.getValue(it).jsonPrimitive.long })
        val key = encoder.encode(samples.getValue(TelemetryEventType.KEY).first, "Home")
        assertEquals("dpad_down", key.getValue("key").jsonPrimitive.content)
        assertEquals(10L, key.getValue("n").jsonPrimitive.long)
        assertEquals(450L, key.getValue("dur_ms").jsonPrimitive.long)
        val lat = encoder.encode(samples.getValue(TelemetryEventType.KEY_LAT).first, "Home")
        assertEquals("back", lat.getValue("key").jsonPrimitive.content)
        assertEquals(70L, lat.getValue("lat_ms").jsonPrimitive.long)
        assertEquals(33L, lat.getValue("frame_ms").jsonPrimitive.long)
    }

    @Test
    fun droppedMetaCarriesCountsPerTypeWire() {
        val json = encoder.encode(
            TelemetryEvent.Meta(2_000, MetaEv.DROPPED, mapOf(TelemetryEventType.NAV to 7)),
            "",
        )
        assertEquals(envelope + setOf("ev", "counts"), json.keys)
        assertEquals(7, json.getValue("counts").jsonObject.getValue("nav").jsonPrimitive.content.toInt())
    }

    @Test
    fun timeIsRelativeToSessionStartAndStartupToProcessStart() {
        val json = encoder.encode(TelemetryEvent.Startup(2_000, 100, 150, 300, 900), "")
        assertEquals(1_000L, json.getValue("t").jsonPrimitive.long)
        assertEquals(0L, json.getValue("proc_start").jsonPrimitive.long)
        assertEquals(50L, json.getValue("app_create").jsonPrimitive.long)
        assertEquals(200L, json.getValue("app_composed").jsonPrimitive.long)
        assertEquals(800L, json.getValue("first_content").jsonPrimitive.long)
    }

    @Test
    fun startupWritesRevealed() {
        val json = encoder.encode(TelemetryEvent.Startup(2_000, 100, 150, 300, 900, 1_400), "")
        assertEquals(1_300L, json.getValue("revealed").jsonPrimitive.long)
    }

    @Test
    fun startupWritesRevealCauseAndImageFields() {
        val json = encoder.encode(
            TelemetryEvent.Startup(
                t = 2_000,
                procStartMs = 100,
                appCreateMs = 150,
                appComposedMs = 300,
                firstContentMs = 900,
                revealedMs = 1_400,
                cause = RevealCause.CAP,
                readyMs = 1_000,
                imagesIdleMs = 1_200,
                inFlight = 3,
                readyInFlight = 5,
                readyDrainedMs = 1_250,
                droppedKeys = 2,
            ),
            "",
        )
        assertEquals("cap", json.getValue("cause").jsonPrimitive.content)
        assertEquals(900L, json.getValue("ready").jsonPrimitive.long)
        assertEquals(1_100L, json.getValue("img_idle").jsonPrimitive.long)
        assertEquals(1_150L, json.getValue("ready_drained").jsonPrimitive.long)
        assertEquals(3L, json.getValue("in_flight").jsonPrimitive.long)
        assertEquals(5L, json.getValue("ready_in_flight").jsonPrimitive.long)
        assertEquals(2L, json.getValue("dropped_keys").jsonPrimitive.long)
    }

    @Test
    fun startupOmitsImageFieldsNotReached() {
        val json = encoder.encode(
            TelemetryEvent.Startup(2_000, 100, 150, 300, 900, 1_400, cause = RevealCause.READY),
            "",
        )
        assertEquals(
            envelope + setOf(
                "proc_start", "app_create", "app_composed", "first_content", "revealed", "cause", "dropped_keys",
            ),
            json.keys,
        )
    }

    @Test
    fun startupWritesReadyRequested() {
        val with = encoder.encode(TelemetryEvent.Startup(2_000, 100, 150, 300, 900, 1_400, readyReqMs = 1_200), "")
        assertEquals(1_100L, with.getValue("ready_req").jsonPrimitive.long)

        val without = encoder.encode(TelemetryEvent.Startup(2_000, 100, 150, 300, 900, 1_400, readyReqMs = null), "")
        assertTrue("ready_req" !in without.keys)
    }

    @Test
    fun keyLatWritesWaitMs() {
        val lat = TelemetryEvent.KeyLat(2_000, HomeScreen::class.java, KeyName.DPAD_DOWN, 90, 40, waitMs = 12)
        val expired = TelemetryEvent.KeyLat(
            2_000, HomeScreen::class.java, KeyName.DPAD_DOWN, null, 40, expired = true, waitMs = 12,
        )
        assertEquals(12L, encoder.encode(lat, "").getValue("wait_ms").jsonPrimitive.long)
        assertEquals(12L, encoder.encode(expired, "").getValue("wait_ms").jsonPrimitive.long)

        val none = TelemetryEvent.KeyLat(2_000, HomeScreen::class.java, KeyName.DPAD_DOWN, 90, 40, waitMs = null)
        assertTrue("wait_ms" !in encoder.encode(none, "").keys)
    }

    @Test
    fun keyLatExpiredOmitsLatMs() {
        val json = encoder.encode(
            TelemetryEvent.KeyLat(2_000, HomeScreen::class.java, KeyName.DPAD_DOWN, null, 40, expired = true),
            "",
        )
        assertEquals(envelope + setOf("key", "frame_ms", "expired"), json.keys)
        assertEquals(true, json.getValue("expired").jsonPrimitive.boolean)
    }

    @Test
    fun startupOmitsMarksNotReached() {
        val json = encoder.encode(TelemetryEvent.Startup(2_000, 100, 150, null, null), "")
        assertEquals(envelope + setOf("proc_start", "app_create", "dropped_keys"), json.keys)
    }

    @Test
    fun startupWritesProfWhenKnown() {
        val json = encoder.encode(TelemetryEvent.Startup(2_000, 100, 150, 300, 900, profileStatus = 327_680), "")
        assertEquals(327_680L, json.getValue("prof").jsonPrimitive.long)
    }

    @Test
    fun errFramesAreAnArrayOfAtMostFive() {
        val json: JsonObject = encoder.encode(TelemetryEvent.Err(2_000, ErrSource.UNCAUGHT, RuntimeException("x")), "")
        val frames: JsonArray = json.getValue("frames").jsonArray
        assertTrue(frames.size <= 5)
    }

    @Test
    fun lineEndsWithNewline() {
        assertTrue(encoder.line(TelemetryEvent.Meta(2_000, MetaEv.STOP), "").endsWith("}\n"))
    }
}
