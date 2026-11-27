package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.core.system.AndroidResourceProvider
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import com.kino.puber.domain.interactor.player.VideoSizeCap
import com.kino.puber.domain.model.BufferPreset
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.profile.BaselineInstrumentationEnvironment
import com.kino.puber.profile.BaselineNetworkJournal
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** One entry per [PlaybackControl.Callback] call the controller made, in order. */
internal sealed interface PlaybackEvent {
    data class State(val isPlaying: Boolean, val isBuffering: Boolean, val position: Long) : PlaybackEvent
    data class Tracks(
        val audio: List<AudioTrackUIState>,
        val selectedIndex: Int,
        val subtitles: List<SubtitleTrackUIState>?,
    ) : PlaybackEvent
    data object Ended : PlaybackEvent
    data object Seek : PlaybackEvent
    data class StreamReady(val url: String) : PlaybackEvent
    data class StreamFailure(
        val message: String,
        val recovery: PlaybackControl.StreamRecovery,
        val url: String,
    ) : PlaybackEvent
    data class Error(val message: String) : PlaybackEvent
}

internal class RecordingCallback : PlaybackControl.Callback {
    private val recorded = CopyOnWriteArrayList<PlaybackEvent>()

    val all: List<PlaybackEvent> get() = recorded.toList()

    /** The number of events so far; pass it to [since] to read only what came after. */
    fun mark(): Int = recorded.size

    fun since(mark: Int): List<PlaybackEvent> = recorded.drop(mark)

    override fun onPlaybackStateChanged(
        isPlaying: Boolean,
        isBuffering: Boolean,
        position: Long,
        duration: Long,
        buffered: Long,
    ) {
        recorded += PlaybackEvent.State(isPlaying, isBuffering, position)
    }

    override fun onTracksUpdated(
        audioTracks: List<AudioTrackUIState>,
        selectedIndex: Int,
        subtitleTracks: List<SubtitleTrackUIState>?,
    ) {
        recorded += PlaybackEvent.Tracks(audioTracks, selectedIndex, subtitleTracks)
    }

    override fun onPlaybackEnded() {
        recorded += PlaybackEvent.Ended
    }

    override fun onSeekPerformed() {
        recorded += PlaybackEvent.Seek
    }

    override fun onStreamReady(streamUrl: String) {
        recorded += PlaybackEvent.StreamReady(streamUrl)
    }

    override fun onStreamFailure(message: String, recovery: PlaybackControl.StreamRecovery, streamUrl: String) {
        recorded += PlaybackEvent.StreamFailure(message, recovery, streamUrl)
    }

    override fun onError(message: String) {
        recorded += PlaybackEvent.Error(message)
    }
}

/** What the real player reported to a listener attached in the same main-thread turn as `prepare`. */
internal class PlayerProbe : Player.Listener {
    @Volatile var sawReady = false
        private set
    val error = AtomicReference<PlaybackException?>()
    val cueTexts = CopyOnWriteArrayList<String>()
    val stateEvents = CopyOnWriteArrayList<Int>()

    override fun onPlaybackStateChanged(playbackState: Int) {
        stateEvents += playbackState
        if (playbackState == Player.STATE_READY) sawReady = true
    }

    override fun onPlayerError(error: PlaybackException) {
        this.error.set(error)
    }

    override fun onCues(cueGroup: CueGroup) {
        cueGroup.cues.forEach { cue -> cue.text?.let { cueTexts += it.toString() } }
    }
}

/**
 * The real [PlaybackController] against the loopback fixture server, one instance per test.
 *
 * Every controller and player call goes through [onMain]: ExoPlayer is built on the calling thread
 * and answers on its looper. [close] runs every teardown step even when an earlier one fails and
 * throws the first failure with the rest suppressed.
 */
@UnstableApi
internal class PlaybackControllerHarness(
    val resources: ResourceProvider = AndroidResourceProvider(InstrumentationRegistry.getInstrumentation().targetContext),
) : Closeable {

    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val testContext = InstrumentationRegistry.getInstrumentation().context

    val networkJournal = BaselineNetworkJournal(BaselineInstrumentationEnvironment.journalDirectory(targetContext))
    val server = PlayerTestControl()
    val events = RecordingCallback()
    val closedResponses = ClosedResponseSignals()

    private val cacheDirectory = targetContext.cacheDir.resolve("player-device-${UUID.randomUUID()}")
    private val gates = CopyOnWriteArrayList<CountDownLatch>()
    val cache = SimpleCache(
        cacheDirectory,
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
        StandaloneDatabaseProvider(targetContext),
    )

    // Built after the app started, so the client takes the network blocker as its ProxySelector.
    val controller = PlaybackController(
        context = targetContext,
        okHttpClient = server.newClient(closedResponses),
        mediaCache = cache,
        playerPreferencesRepository = PlayerPreferencesRepository(targetContext),
        resources = resources,
    )

    init {
        try {
            networkJournal.clear()
            server.start(mediaRoutes())
            onMain { controller.setCallback(events) }
        } catch (failure: Throwable) {
            // The caller never receives the harness, so nothing else will release what was built.
            try {
                close()
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            value.set(runCatching(block))
        }
        return value.get().getOrThrow()
    }

    fun <T> playerRead(read: PlaybackController.() -> T): T = onMain { controller.read() }

    /** A gate for [HermeticTestServer.delayed]; [close] opens it so no request is left hanging. */
    fun newGate(): CountDownLatch = CountDownLatch(1).also { gates += it }

    fun url(pathAndQuery: String): String {
        val query = pathAndQuery.substringAfter('?', "")
        val base = server.url(pathAndQuery.substringBefore('?'))
        return if (query.isEmpty()) base else "$base?$query"
    }

    fun stream(pathAndQuery: String, videoCap: VideoSizeCap? = null): StreamCandidate = StreamCandidate(
        url = url(pathAndQuery),
        type = if (pathAndQuery.substringBefore('?').endsWith(".m3u8")) StreamType.HLS else StreamType.PROGRESSIVE,
        videoCap = videoCap,
    )

    fun fixtureBytes(path: String): ByteArray = PlayerTestFixtures.openPath(path, testContext).use { it.readBytes() }

    /**
     * Prepares [pathAndQuery] and attaches a [PlayerProbe] in the same main-thread turn, so no player
     * event is missed. A null [startPosition] leaves playWhenReady false.
     */
    fun prepare(
        pathAndQuery: String,
        subtitles: List<SubtitleLink>? = null,
        startPosition: Long? = 0L,
        videoCap: VideoSizeCap? = null,
    ): PlayerProbe {
        val probe = PlayerProbe()
        onMain {
            controller.prepare(
                stream = stream(pathAndQuery, videoCap),
                subtitles = subtitles,
                startPosition = startPosition,
                bufferPreset = BufferPreset.SMALL,
                fastDns = false,
            )
            checkNotNull(controller.player).addListener(probe)
        }
        return probe
    }

    /** Polls [condition] on the main thread every 50 ms; fails with [description] and the last events. */
    fun awaitCondition(
        description: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        condition: PlaybackController.() -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (onMain { controller.condition() }) return
            if (System.nanoTime() >= deadline) break
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("$description: condition stayed false for $timeoutMs ms; ${diagnostics()}")
    }

    fun awaitReady(probe: PlayerProbe) {
        awaitCondition("player reached READY") { probe.sawReady || probe.error.get() != null }
        check(probe.error.get() == null) { "player failed before READY: ${probe.error.get()}; ${diagnostics()}" }
    }

    fun diagnostics(): String =
        "events=${events.all.takeLast(DIAGNOSTIC_ITEMS)}, requests=${server.requestJournal.entries.takeLast(DIAGNOSTIC_ITEMS)}"

    /** Replaces the routes with the media routes; [required] ids must be requested by teardown. */
    fun useMediaRoutes(required: Set<String> = emptySet(), extra: List<HermeticRoute> = emptyList()) {
        server.reset(mediaRoutes(required) + extra)
    }

    fun mediaRoutes(required: Set<String> = emptySet()): List<HermeticRoute> {
        val routes = mutableListOf<HermeticRoute>()
        routes += server.route(
            id = "progressive",
            path = "/media/progressive.mp4",
            response = ResponsePlan.Range(
                body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, testContext),
                contentType = "video/mp4",
            ),
            required = "progressive" in required,
        )
        routes += server.route(
            id = "master",
            path = "/media/hls/master.m3u8",
            response = HermeticTestServer.text(
                body = PlayerTestFixtures.readBytes(FixtureId.HlsMaster, testContext).toString(Charsets.UTF_8),
                contentType = HLS_CONTENT_TYPE,
            ),
            required = "master" in required,
        )
        FixtureId.entries
            .filter { it.path.startsWith("hls/") && it != FixtureId.HlsMaster }
            .forEach { fixture ->
                routes += server.route(
                    id = fixture.path,
                    path = "/media/${fixture.path}",
                    response = HermeticTestServer.text(
                        body = PlayerTestFixtures.readBytes(fixture, testContext).toString(Charsets.UTF_8),
                        contentType = HLS_CONTENT_TYPE,
                    ),
                    required = fixture.path in required,
                )
            }
        HLS_SEGMENTS.forEach { filename ->
            routes += server.route(
                id = filename,
                path = "/media/hls/$filename",
                response = HermeticTestServer.bytes(body = fixtureBytes("hls/$filename"), contentType = "video/mp2t"),
                required = filename in required,
            )
        }
        return routes
    }

    override fun close() {
        val failures = mutableListOf<Throwable>()
        fun step(block: () -> Unit) {
            try {
                block()
            } catch (failure: Throwable) {
                failures += failure
            }
        }
        step { gates.forEach(CountDownLatch::countDown) }
        step { onMain { controller.release() } }
        step { server.awaitQuiescence(QUIESCENCE_SECONDS, TimeUnit.SECONDS) }
        step { server.assertRequiredRoutesRequested() }
        step { server.assertNoUnknownRequests() }
        step { server.close() }
        step { cache.release() }
        step {
            check(!cacheDirectory.exists() || cacheDirectory.deleteRecursively()) {
                "Failed to clean player cache: $cacheDirectory"
            }
        }
        step { check(networkJournal.snapshot().isEmpty()) { "Unexpected network egress: ${networkJournal.snapshot()}" } }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    companion object {
        const val HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"
        private const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val POLL_MS = 50L
        private const val QUIESCENCE_SECONDS = 10L
        private const val DIAGNOSTIC_ITEMS = 8
        private val HLS_SEGMENTS = listOf(
            "video_360_000.ts",
            "video_360_001.ts",
            "video_720_000.ts",
            "video_720_001.ts",
            "audio_english_000.ts",
            "audio_english_001.ts",
            "audio_english_002.ts",
            "audio_spanish_000.ts",
            "audio_spanish_001.ts",
            "audio_spanish_002.ts",
        )
    }
}
