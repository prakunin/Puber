package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.VideoSizeCap
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.ResponseOutcome
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tracks, quality switches, faults and network isolation of the real controller over loopback HTTPS. */
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerNetworkTracksTest {

    private lateinit var harness: PlaybackControllerHarness

    @Before
    fun setUp() {
        harness = PlaybackControllerHarness()
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) harness.close()
    }

    @Test
    fun hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText() {
        harness.server.reset(harness.commonHlsRoutes())
        val subtitleUrl = harness.url("/media/subtitle.vtt?signature=test-signature")
        val probe = harness.prepare(
            "/media/hls/master.m3u8",
            subtitles = listOf(SubtitleLink(lang = "en", url = subtitleUrl)),
        )

        harness.awaitReady(probe)
        harness.onMain { harness.controller.pause() }
        harness.awaitCondition("both AAC renditions are exposed") {
            audioTracks().map { it.language }.toSet() == setOf("en", "es")
        }

        harness.onMain { harness.controller.selectAudioTrack(1) }
        harness.awaitCondition("Spanish AAC rendition selected") { selectedAudioLanguage() == "es" }

        harness.onMain {
            harness.controller.selectSubtitle(SubtitleTrackUIState(label = "English", language = "en", url = subtitleUrl))
        }
        harness.awaitCondition("side-loaded WebVTT track enabled on HLS") {
            !textTracksDisabled() && selectedTextTrack()
        }
        assertEquals("es", harness.playerRead { selectedAudioLanguage() })

        harness.onMain {
            harness.controller.seekTo(900L)
            harness.controller.play()
        }
        harness.awaitCondition("fixture cue is rendered") {
            probe.cueTexts.any { it.contains(CUE_TEXT) } || currentCueTexts().any { it.contains(CUE_TEXT) }
        }
        assertTrue(harness.server.requestJournal.entries.any { it.path == "/media/subtitle.vtt" })

        harness.onMain { harness.controller.selectSubtitle(null) }
        harness.awaitCondition("WebVTT track disabled") { textTracksDisabled() }
        assertEquals("es", harness.playerRead { selectedAudioLanguage() })
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
    }

    @Test
    fun hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes() {
        val gate = harness.newGate()
        harness.server.reset(
            harness.commonHlsRoutes(
                extraRoutes = listOf(
                    gatedSegment("chunkless-video-gate", "video_720_000.ts", gate),
                    gatedSegment("chunkless-audio-gate", "audio_english_000.ts", gate),
                ),
            ),
        )
        val probe = harness.prepare("/media/hls/master.m3u8")

        try {
            harness.awaitCondition("first HLS media chunks are blocked") { harness.server.activeRequestCount > 0 }
            harness.awaitCondition("chunkless HLS preparation exposes CODECS tracks") {
                val trackTypes = player?.currentTracks?.groups?.map { it.type }.orEmpty()
                harness.server.activeRequestCount > 0 &&
                    C.TRACK_TYPE_VIDEO in trackTypes &&
                    C.TRACK_TYPE_AUDIO in trackTypes
            }
        } finally {
            gate.countDown()
        }

        harness.awaitReady(probe)
        assertTrue(probe.error.get() == null)
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
    }

    @Test
    fun hlsVideoCap_switchStreamReplacesCap() {
        val master = "/media/hls/master.m3u8"
        harness.server.reset(harness.commonHlsRoutes())
        val probe = harness.prepare(master, videoCap = VideoSizeCap(640, 360))
        val defaultViewport = DefaultTrackSelector.Parameters.Builder(harness.targetContext).build()
            .let { it.viewportWidth to it.viewportHeight }

        harness.awaitReady(probe)
        // The 360 variant is also where ABR starts without a cap, so the parameters carry the proof.
        harness.awaitCondition("prepare applied the 640x360 cap without a viewport trim") {
            maxVideoSize() == (640 to 360) && forceHighestSupportedBitrate() &&
                viewportSize() == (Int.MAX_VALUE to Int.MAX_VALUE)
        }
        harness.awaitCondition("360 variant selected under the 640x360 cap") { selectedVideoHeight() == 360 }

        // A device whose default viewport is already unbounded cannot show the trim being cleared.
        harness.playerRead { setViewport(100, 100) }
        harness.onMain { harness.controller.switchStream(harness.stream(master, VideoSizeCap(1280, 720)), null) }
        harness.awaitCondition("720 variant selected after switching to the 1280x720 cap") {
            playbackState() == Player.STATE_READY && selectedVideoHeight() == 720 &&
                viewportSize() == (Int.MAX_VALUE to Int.MAX_VALUE)
        }

        harness.onMain { harness.controller.switchStream(harness.stream(master), null) }
        harness.awaitCondition("cap lifted by an uncapped switch") {
            maxVideoSize() == (Int.MAX_VALUE to Int.MAX_VALUE) && !forceHighestSupportedBitrate() &&
                viewportSize() == defaultViewport
        }
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
        assertTrue(probe.error.get() == null)
    }

    @Test
    fun hlsVideoCap_belowEveryVariant_stillSelectsVideo() {
        harness.server.reset(harness.commonHlsRoutes())
        val probe = harness.prepare("/media/hls/master.m3u8", videoCap = VideoSizeCap(320, 180))

        harness.awaitReady(probe)
        harness.awaitCondition("prepare applied the 320x180 cap") { maxVideoSize() == (320 to 180) }
        harness.awaitCondition("a video variant is selected although the cap excludes all") {
            selectedVideoHeight() == 360
        }
        assertTrue(probe.error.get() == null)
    }

    @Test
    fun unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback() {
        harness.server.reset(harness.commonHlsRoutes())
        val probe = harness.prepare("/media/hls/audio_english.m3u8")

        harness.awaitReady(probe)
        harness.awaitCondition("the controller reported its audio rows") {
            harness.events.all.any { it is PlaybackEvent.Tracks }
        }
        val row = harness.events.all.filterIsInstance<PlaybackEvent.Tracks>().first().audio.single()
        assertEquals(harness.targetContext.getString(R.string.player_audio_track_fallback, 1), row.label)
        assertTrue(row.isFallbackLabel)
        assertTrue(probe.error.get() == null)
    }

    @Test
    fun hlsSwitch_preservesPositionIntentTracks_andLastStreamWins() {
        val lowPath = "/media/hls/quality-low.m3u8"
        val highPath = "/media/hls/quality-high.m3u8"
        harness.server.reset(harness.commonHlsRoutes())
        val subtitleUrl = harness.url("/media/subtitle.vtt?scenario=quality-switch")
        val subtitle = SubtitleLink(lang = "en", url = subtitleUrl)
        val subtitleTrack = SubtitleTrackUIState(label = "English", language = "en", url = subtitleUrl)
        val probe = harness.prepare(lowPath, listOf(subtitle))

        harness.awaitReady(probe)
        harness.awaitCondition("audio tracks before switch") { audioTracks().size == 2 }
        harness.onMain {
            harness.controller.selectAudioTrack(1)
            harness.controller.selectSubtitle(subtitleTrack)
            harness.controller.seekTo(1_500L)
        }
        harness.awaitCondition("position before switch") { currentPosition in 1_000L..2_100L }
        harness.awaitCondition("Spanish selection before switch") { selectedAudioLanguage() == "es" }
        harness.awaitCondition("WebVTT selection before switch") { !textTracksDisabled() && selectedTextTrack() }
        harness.awaitCondition("WebVTT request before switch") {
            harness.server.requestJournal.entries.any { it.path == "/media/subtitle.vtt" }
        }

        harness.onMain { harness.controller.pause() }
        harness.awaitCondition("paused switch source") { !isPlaying }
        val pausedPosition = harness.playerRead { currentPosition }
        harness.onMain { harness.controller.switchStream(harness.stream(highPath), listOf(subtitle)) }
        harness.awaitCondition("high quality source prepared") {
            currentMediaPath() == highPath && playbackState() == Player.STATE_READY
        }
        assertFalse(harness.playerRead { textTracksDisabled() })
        assertTrue(harness.playerRead { selectedTextTrack() })
        assertFalse(harness.playerRead { isPlaying })
        assertFalse(harness.playerRead { playWhenReady() })
        assertTrue(harness.playerRead { currentPosition } in (pausedPosition - 800L)..(pausedPosition + 800L))
        harness.awaitCondition("audio selection restored after switch") { selectedAudioLanguage() == "es" }

        harness.onMain { harness.controller.play() }
        harness.awaitCondition("play intent restored") { isPlaying && playWhenReady() }
        harness.onMain {
            harness.controller.switchStream(harness.stream(lowPath), listOf(subtitle))
            harness.controller.switchStream(harness.stream(highPath), listOf(subtitle))
            harness.controller.switchStream(harness.stream(lowPath), listOf(subtitle))
        }
        harness.awaitCondition("last rapid switch wins") { currentMediaPath() == lowPath && playWhenReady() }

        assertTrue(harness.playerRead { currentPosition } >= pausedPosition - 1_000L)
        val masterPaths = harness.server.requestJournal.entries
            .filter { it.path.startsWith("/media/hls/quality-") }
            .map { it.path }
        assertTrue(masterPaths.contains(lowPath))
        assertTrue(masterPaths.contains(highPath))
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
        assertTrue(probe.error.get() == null)
    }

    @Test
    fun progressiveSwitch_restoresSelectedSubtitleAndPausedIntent() {
        harness.server.reset(harness.commonHlsRoutes())
        val subtitleUrl = harness.url("/media/subtitle.vtt")
        val subtitle = SubtitleTrackUIState(label = "English", language = "en", url = subtitleUrl)
        val first = harness.prepare(
            "/media/progressive-a.mp4",
            subtitles = listOf(SubtitleLink(lang = "en", url = subtitleUrl)),
        )

        harness.awaitReady(first)
        harness.onMain { harness.controller.pause() }
        harness.onMain { harness.controller.selectSubtitle(subtitle) }
        harness.awaitCondition("subtitle selected before progressive switch") {
            !textTracksDisabled() && selectedTextTrack()
        }
        val positionBeforeSwitch = harness.playerRead { currentPosition }

        harness.onMain {
            harness.controller.switchStream(
                harness.stream("/media/progressive-b.mp4"),
                listOf(SubtitleLink(lang = "en", url = subtitleUrl)),
            )
        }
        harness.awaitCondition("progressive switch completes") {
            currentMediaPath() == "/media/progressive-b.mp4" &&
                playbackState() == Player.STATE_READY
        }
        assertFalse(harness.playerRead { playWhenReady() })
        assertFalse(harness.playerRead { isPlaying })
        assertTrue(
            harness.playerRead { currentPosition } in
                (positionBeforeSwitch - 800L)..(positionBeforeSwitch + 800L),
        )

        harness.awaitCondition("subtitle selection restored after progressive switch") {
            !textTracksDisabled() && selectedTextTrack()
        }
        harness.onMain { harness.controller.selectSubtitle(null) }
        harness.awaitCondition("subtitle disabled after progressive switch") { textTracksDisabled() }
    }

    @Test
    fun hls404AndMalformedPlaylist_failWithoutUnboundedRetry() {
        val notFound = runPlaylistFailure(
            path = "/media/fault-404.m3u8",
            response = HermeticTestServer.text(
                body = "not found",
                status = 404,
                contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
            ),
        )
        // MINIMUM_RETRY_COUNT retries after the first attempt.
        assertTrue("Expected a bounded 404 retry count, got $notFound", notFound in 1..PlaybackNetworkTuning.MINIMUM_RETRY_COUNT + 1)

        val malformed = runPlaylistFailure(
            path = "/media/fault-malformed.m3u8",
            response = HermeticTestServer.text(
                body = "this is not an HLS playlist",
                contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
            ),
        )
        assertEquals("A malformed playlist is not retried", 1, malformed)
    }

    @Test
    fun hlsVariant400_fallsBackToTheHealthyVariant() {
        assertVariantHttpFailureFallsBack(status = 400)
    }

    @Test
    fun hlsVariant502_fallsBackToTheHealthyVariant() {
        assertVariantHttpFailureFallsBack(status = 502)
    }

    @Test
    fun hlsDelayedAndInterruptedSegment_enterBufferingThenRecover() {
        val gate = harness.newGate()
        val lowPlaylist = harness.fixtureText(FixtureId.HlsVideo360Playlist)
        harness.server.reset(
            harness.commonHlsRoutes(
                extraRoutes = listOf(gatedSegment("delayed-segment", "video_360_000.ts", gate)),
            ),
        )
        val delayedProbe = harness.prepare("/media/hls/quality-low.m3u8")
        harness.awaitCondition("delayed segment enters BUFFERING") {
            Player.STATE_BUFFERING in delayedProbe.stateEvents ||
                playbackState() == Player.STATE_BUFFERING
        }

        gate.countDown()
        harness.awaitReady(delayedProbe)
        assertTrue(delayedProbe.error.get() == null)

        harness.onMain { harness.controller.release() }
        harness.server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
        val segment = harness.fixtureBytes("hls/video_360_000.ts")
        harness.server.reset(
            harness.commonHlsRoutes(
                extraRoutes = listOf(
                    harness.server.route(
                        id = "recovery-master",
                        path = "/media/hls/recovery.m3u8",
                        response = HermeticTestServer.text(
                            body = singleVariantMaster("video_360_recovery.m3u8"),
                            contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
                        ),
                    ),
                    harness.server.route(
                        id = "recovering-playlist",
                        path = "/media/hls/video_360_recovery.m3u8",
                        response = HermeticTestServer.text(
                            body = lowPlaylist.replace("video_360_", "video_360_recovery_"),
                            contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
                        ),
                    ),
                    harness.server.route(
                        id = "recovering-segment",
                        path = "/media/hls/video_360_recovery_000.ts",
                        response = HermeticTestServer.sequence(
                            HermeticTestServer.truncated(
                                body = segment,
                                bytesToWrite = segment.size / 4,
                                contentType = "video/mp2t",
                            ),
                            HermeticTestServer.bytes(segment, contentType = "video/mp2t"),
                        ),
                    ),
                    harness.server.route(
                        id = "recovering-segment-1",
                        path = "/media/hls/video_360_recovery_001.ts",
                        response = HermeticTestServer.bytes(
                            harness.fixtureBytes("hls/video_360_001.ts"),
                            contentType = "video/mp2t",
                        ),
                    ),
                ),
            ),
        )
        val recoveryProbe = harness.prepare("/media/hls/recovery.m3u8")

        harness.awaitReady(recoveryProbe)
        val recoveryEntries = harness.server.requestJournal.entries.filter { it.routeId == "recovering-segment" }
        assertTrue("Expected interrupted segment recovery; entries=$recoveryEntries", recoveryEntries.size >= 2)
        assertEquals(ResponseOutcome.Truncated(200), recoveryEntries[0].outcome)
        assertEquals(ResponseOutcome.Completed(200), recoveryEntries[1].outcome)
        assertTrue("Expected a ranged recovery request; entries=$recoveryEntries", recoveryEntries[1].range != null)
        assertTrue(recoveryProbe.error.get() == null)
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
    }

    @Test
    fun playerMediaNetworkIsolation_rejectsExternalRedirect() {
        harness.server.reset(
            listOf(
                harness.server.route(
                    id = "external-redirect",
                    path = "/media/redirect.m3u8",
                    response = HermeticTestServer.redirect("https://media.invalid/x"),
                ),
            ),
        )

        harness.prepare("/media/redirect.m3u8")

        try {
            // Six attempts with the retry backoff of PlaybackNetworkTuning.
            harness.awaitCondition("the external redirect fails locally", timeoutMs = REDIRECT_TIMEOUT_MS) {
                harness.events.all.any { it is PlaybackEvent.StreamFailure }
            }
            // The blocker journals the origin ProxySelector.select receives, so the path is "/".
            val journaled = harness.networkJournal.snapshot().distinct()
            assertEquals(listOf("https://media.invalid:443/"), journaled)
            assertEquals(0, harness.server.requestJournal.unknownRequests.size)
        } finally {
            harness.networkJournal.clear()
        }
    }

    private fun gatedSegment(id: String, filename: String, gate: CountDownLatch) =
        harness.server.route(
            id = id,
            path = "/media/hls/$filename",
            response = HermeticTestServer.delayed(
                gate = gate,
                body = harness.fixtureBytes("hls/$filename"),
                contentType = "video/mp2t",
            ),
        )

    /** Returns how many times [path] was requested before the stream failed. */
    private fun runPlaylistFailure(path: String, response: ResponsePlan): Int {
        harness.onMain { harness.controller.release() }
        harness.server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
        harness.server.reset(listOf(harness.server.route(id = "fault", path = path, response = response)))
        val mark = harness.events.mark()
        val probe = harness.prepare(path)

        harness.awaitCondition("playlist failure $path", timeoutMs = REDIRECT_TIMEOUT_MS) {
            harness.events.since(mark).any { it is PlaybackEvent.StreamFailure }
        }
        val failure = harness.events.since(mark).filterIsInstance<PlaybackEvent.StreamFailure>().first()
        assertEquals(PlaybackControl.StreamRecovery.NEXT_URL, failure.recovery)
        assertTrue(probe.error.get() != null)
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
        return harness.server.requestJournal.entries.count { it.path == path }
    }

    private fun assertVariantHttpFailureFallsBack(status: Int) {
        val validLowPlaylist = harness.fixtureText(FixtureId.HlsVideo360Playlist)
        harness.server.reset(
            harness.commonHlsRoutes(
                lowPlaylist = HermeticTestServer.sequence(
                    HermeticTestServer.text(
                        body = "variant unavailable",
                        status = status,
                        contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
                    ),
                    HermeticTestServer.text(
                        body = validLowPlaylist,
                        contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
                    ),
                ),
            ),
        )
        val probe = harness.prepare("/media/hls/master.m3u8")

        harness.awaitReady(probe)
        harness.awaitCondition("healthy HLS variant after $status") {
            playbackState() == Player.STATE_READY &&
                harness.server.requestJournal.entries.any { it.path == "/media/hls/video_720.m3u8" }
        }
        val failedVariant = harness.server.requestJournal.entries.filter { it.path == "/media/hls/video_360.m3u8" }
        assertEquals(1, failedVariant.size)
        assertEquals(status, (failedVariant.single().outcome as ResponseOutcome.Completed).status)
        assertTrue(probe.error.get() == null)
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
    }

    private companion object {
        const val CUE_TEXT = "Synthetic player fixture cue"
        const val REDIRECT_TIMEOUT_MS = 40_000L
    }
}
