package com.kino.puber.ui.feature.player.vm

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.extractor.DefaultExtractorsFactory
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

internal interface PlaybackControl {
    enum class StreamRecovery {
        NEXT_URL,
        REFRESH_LINKS,
    }

    interface Callback {
        fun onPlaybackStateChanged(
            isPlaying: Boolean,
            isBuffering: Boolean,
            position: Long,
            duration: Long,
            buffered: Long,
        )

        fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int)
        fun onPlaybackEnded()
        fun onStreamReady(streamUrl: String)
        fun onStreamFailure(message: String, recovery: StreamRecovery, streamUrl: String)
        fun onError(message: String)
    }

    /** Live playback characteristics, surfaced by the info panel and the debug overlay. */
    data class DebugInfo(
        val videoResolution: String,
        val videoCodec: String,
        val videoBitrate: String,
        val videoFrameRate: String,
        val audioCodec: String,
        val audioChannels: String,
        val droppedFrames: String,
        val bufferedDuration: String,
        val bufferedBytes: String,
        val streamSource: String,
    )

    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
    val bufferedPosition: Long
    val playbackSpeed: Float

    fun setCallback(callback: Callback)
    fun prepare(
        stream: StreamCandidate,
        subtitles: List<SubtitleLink>?,
        startPosition: Long?,
        bufferPreset: BufferPreset = BufferPreset.AUTO,
        fastDns: Boolean = true,
    )

    fun switchStream(stream: StreamCandidate, subtitles: List<SubtitleLink>?)

    /**
     * Pulls the head of [stream] into the shared media cache ahead of time, so that switching to it
     * starts from cached bytes instead of a cold connection. Repeat calls for the same URL are
     * ignored; playback of the current stream is untouched.
     */
    fun warmUpNext(stream: StreamCandidate, subtitles: List<SubtitleLink>?)

    /** Drops the warm-up player and forgets what was warmed. */
    fun cancelWarmUp()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun selectAudioTrack(groupIndex: Int)
    fun selectSubtitle(track: SubtitleTrackUIState?)
    fun getDebugInfo(): DebugInfo?
    fun release()
}

// Opted in at the class rather than per member, and not only for brevity: lint reports the
// unstable types of `trackSelector`, `bufferAllocator`, `bandwidthMeter` and the `mediaCache`
// constructor property against the declarations themselves, and no annotation on a property
// satisfies it - neither the bare form, which already lands on the backing field, nor `@get:`.
// Eleven member annotations left twenty-two errors standing. The cost is that a method added here
// later reaches experimental Media3 without the compiler asking it to say so, which is a fair
// price only because this class is an ExoPlayer wrapper and nothing else.
@OptIn(UnstableApi::class)
internal class PlaybackController(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val mediaCache: androidx.media3.datasource.cache.Cache,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
) : PlaybackControl {

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var callback: PlaybackControl.Callback? = null
    private var ac3FallbackApplied = false
    private var useFastDns = true
    private var pendingSubtitleTrack: SubtitleTrackUIState? = null
    private var currentStreamUrl: String? = null
    private var effectiveStreamSource: String? = null
    private var warmUpPlayer: ExoPlayer? = null
    private var warmUpStreamUrl: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val warmUpTimeout = Runnable { releaseWarmUpPlayer() }
    private val seekStallRecovery = SeekStallRecovery(
        handler = mainHandler,
        player = { exoPlayer },
        streamUrl = { currentStreamUrl },
        onStalled = { streamUrl ->
            callback?.onStreamFailure(
                message = context.getString(R.string.player_error_playback),
                recovery = PlaybackControl.StreamRecovery.NEXT_URL,
                streamUrl = streamUrl,
            )
        },
    )

    // Owned rather than left to DefaultLoadControl, which keeps its allocator to itself: this is
    // the only way to read how many bytes the buffer actually holds.
    private var bufferAllocator: DefaultAllocator? = null
    private var targetBufferBytes = 0

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    private var dataSourceFactory: DataSource.Factory? = null

    val player: ExoPlayer? get() = exoPlayer
    override val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    override val duration: Long get() = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L
    override val isPlaying: Boolean
        get() = exoPlayer?.let {
            isPlaybackIntended(it.playWhenReady, it.playbackState, it.playbackSuppressionReason)
        } == true
    override val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    override val playbackSpeed: Float get() = exoPlayer?.playbackParameters?.speed ?: 1f
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            notifyPlaybackState()
        }

        // onIsPlayingChanged stays silent while the player is stalled or suppressed, so these two
        // report the transitions it misses.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) seekStallRecovery.cancel()
            notifyPlaybackState()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            notifyPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    seekStallRecovery.cancel()
                    notifyPlaybackState()
                    callback?.onPlaybackEnded()
                }
                Player.STATE_READY -> {
                    seekStallRecovery.cancel()
                    notifyPlaybackState()
                    notifyTracksUpdated()
                    callback?.onStreamReady(currentStreamUrl.orEmpty())
                }
                Player.STATE_BUFFERING -> notifyPlaybackState()
                else -> {}
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            notifyTracksUpdated()
            applyPendingSubtitleSelection()
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val message = error.localizedMessage ?: context.getString(R.string.player_error_playback)
            val recovery = cause.streamRecovery()
            when {
                cause is BehindLiveWindowException -> recoverBehindLiveWindow()
                cause.isAc3DecoderInitializationException() -> disableAc3AndRetry()
                recovery != null -> callback?.onStreamFailure(
                    message = message,
                    recovery = recovery,
                    streamUrl = currentStreamUrl.orEmpty(),
                )
                else -> {
                    seekStallRecovery.cancel()
                    callback?.onError(message)
                }
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            retryAttempt: Int,
        ) {
            updateEffectiveStreamSource(loadEventInfo.uri.host, mediaLoadData)
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            // Unlike DataSpec.uri, this URI reflects the endpoint after HTTP redirects.
            updateEffectiveStreamSource(loadEventInfo.uri.host, mediaLoadData)
        }
    }

    private fun recoverBehindLiveWindow() {
        exoPlayer?.let { player ->
            player.seekToDefaultPosition()
            player.prepare()
        }
    }

    private fun Throwable?.isAc3DecoderInitializationException(): Boolean {
        return this is MediaCodecRenderer.DecoderInitializationException && mimeType == MimeTypes.AUDIO_AC3
    }

    private fun Throwable?.streamRecovery(): PlaybackControl.StreamRecovery? {
        var error = this
        var classified = false
        var recovery: PlaybackControl.StreamRecovery? = null
        while (error != null && !classified) {
            when (error) {
                // Expired signed links can return a 200 HTML/JSON error page or a portal redirect.
                // Media3 then reports a parser failure, so another backend-provided candidate is valid.
                is ParserException -> {
                    classified = true
                    recovery = PlaybackControl.StreamRecovery.NEXT_URL
                }
                is HttpDataSource.InvalidResponseCodeException -> {
                    classified = true
                    recovery = when (error.responseCode) {
                        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> PlaybackControl.StreamRecovery.REFRESH_LINKS
                        HTTP_NOT_FOUND,
                        HTTP_REQUEST_TIMEOUT,
                        HTTP_TOO_MANY_REQUESTS,
                        in HTTP_SERVER_ERROR_RANGE,
                        -> PlaybackControl.StreamRecovery.NEXT_URL
                        else -> null
                    }
                }
                is FileNotFoundException -> {
                    classified = true
                    recovery = PlaybackControl.StreamRecovery.NEXT_URL
                }
                is HttpDataSource.HttpDataSourceException,
                is IOException,
                -> {
                    classified = true
                    recovery = PlaybackControl.StreamRecovery.NEXT_URL
                }
            }
            error = error.cause
        }
        return recovery
    }

    override fun setCallback(callback: PlaybackControl.Callback) {
        this.callback = callback
    }

    override fun prepare(
        stream: StreamCandidate,
        subtitles: List<SubtitleLink>?,
        startPosition: Long?,
        bufferPreset: BufferPreset,
        fastDns: Boolean,
    ) {
        release()
        ac3FallbackApplied = false
        useFastDns = fastDns

        val loadControl = buildLoadControl(bufferPreset)

        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            /* maxDurationForQualityDecreaseMs = */ MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            /* minDurationToRetainAfterDiscardMs = */ MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            /* bandwidthFraction = */ BANDWIDTH_FRACTION,
        )
        val trackSelector = DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            parameters = buildUponParameters()
                .setExceedVideoConstraintsIfNecessary(false)
                .setExceedRendererCapabilitiesIfNecessary(false)
                .build()
        }
        this.trackSelector = trackSelector

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val sourceFactory = createDataSourceFactory()
        dataSourceFactory = sourceFactory

        val mediaSourceFactory = createMediaSourceFactory(sourceFactory)

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()
            .apply {
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
            }
        exoPlayer = player

        val mediaItem = buildMediaItem(stream.url, subtitles)
        setMediaSource(player, mediaItem, stream.type)
        currentStreamUrl = stream.url
        effectiveStreamSource = PlaybackDebugFormat.streamSource(stream.url)

        player.prepare()
        if (startPosition != null) {
            if (startPosition > 0) {
                player.seekTo(startPosition)
            }
            player.playWhenReady = true
        }
    }

    /** Also records the buffer budget the info panel reports the current fill against. */
    private fun buildLoadControl(bufferPreset: BufferPreset): DefaultLoadControl {
        val bufferParams = DeviceBufferConfig.resolve(context, bufferPreset)
        val allocator = DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        bufferAllocator = allocator
        targetBufferBytes = bufferParams.targetBufferBytes
        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                bufferParams.minBufferMs,
                bufferParams.maxBufferMs,
                bufferParams.bufferForPlaybackMs,
                bufferParams.bufferForPlaybackAfterRebufferMs,
            )
            .setBackBuffer(
                bufferParams.backBufferDurationMs,
                /* retainBackBufferFromKeyframe = */ false,
            )
            .setTargetBufferBytes(bufferParams.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(bufferParams.prioritizeTimeOverSize)
            .build()
    }

    /**
     * Points the player at another URL for the media it is already playing, at the position it
     * already reached.
     *
     * Deliberately no `stop()`: that drops the player to idle and takes `DefaultLoadControl` with
     * it, which resets the allocator and — because the allocator trims on reset — hands the whole
     * pooled buffer back to the heap to be re-allocated a moment later. The buffered media itself
     * cannot survive a source swap either way, but the pool can, and giving it up costs a GC pause
     * at the one moment playback has nothing in reserve. Handing the position to `setMediaSource`
     * rather than seeking afterwards saves a second buffering round-trip on top.
     */
    private fun createMediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
    ): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(
            dataSourceFactory,
            DefaultExtractorsFactory().setDisableArtworkMetadata(
                playerPreferencesRepository.discardEmbeddedArtworkMetadata,
            ),
        )
            // Covers the progressive fallback; the HLS source sets the same policy itself.
            .setLoadErrorHandlingPolicy(PlaybackErrorPolicy())
            .setExperimentalEnableHagcPlayback(playerPreferencesRepository.hagcPlaybackEnabled)
    }

    override fun switchStream(stream: StreamCandidate, subtitles: List<SubtitleLink>?) {
        val player = exoPlayer ?: return
        val savedPosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        val savedTrackParams = player.trackSelectionParameters

        val mediaItem = buildMediaItem(stream.url, subtitles)
        setMediaSource(player, mediaItem, stream.type, startPositionMs = savedPosition)
        currentStreamUrl = stream.url
        effectiveStreamSource = PlaybackDebugFormat.streamSource(stream.url)

        player.trackSelectionParameters = savedTrackParams
        player.prepare()
        player.playWhenReady = wasPlaying
        seekStallRecovery.onStreamSwitched(player)
    }

    override fun play() {
        exoPlayer?.let { player ->
            // playWhenReady stays true once media ends, so play() alone would be a no-op there.
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekToDefaultPosition()
            }
            player.play()
        }
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.let { player ->
            player.seekTo(positionMs)
            if (player.playWhenReady) {
                seekStallRecovery.start(player)
            } else {
                seekStallRecovery.cancel()
            }
        }
    }

    override fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    override fun selectAudioTrack(groupIndex: Int) {
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val targetGroup = audioGroups.getOrNull(groupIndex) ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(targetGroup.mediaTrackGroup, 0)
            )
            .build()
    }

    override fun selectSubtitle(track: SubtitleTrackUIState?) {
        val player = exoPlayer ?: return
        if (track == null || track.url.isEmpty()) {
            pendingSubtitleTrack = null
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        pendingSubtitleTrack = track
        applySubtitleTrackSelection(track)
    }

    override fun release() {
        seekStallRecovery.cancel()
        cancelWarmUp()
        exoPlayer?.let { player ->
            player.removeAnalyticsListener(analyticsListener)
            player.removeListener(playerListener)
            player.release()
        }
        exoPlayer = null
        trackSelector = null
        dataSourceFactory = null
        currentStreamUrl = null
        effectiveStreamSource = null
        bufferAllocator = null
        targetBufferBytes = 0
    }

    private fun disableAc3AndRetry() {
        if (ac3FallbackApplied) {
            callback?.onError(context.getString(R.string.player_error_playback))
            return
        }
        ac3FallbackApplied = true

        val player = exoPlayer ?: return
        val selector = trackSelector ?: return
        val position = player.currentPosition

        player.stop()

        selector.parameters = selector.parameters.buildUpon()
            .setExceedRendererCapabilitiesIfNecessary(false)
            .setExceedAudioConstraintsIfNecessary(false)
            .build()

        player.seekTo(position)
        player.prepare()
        player.playWhenReady = true
    }

    private fun buildMediaItem(streamUrl: String, subtitles: List<SubtitleLink>?): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl)
        if (!subtitles.isNullOrEmpty()) {
            val subtitleConfigs = subtitles.map { sub ->
                val stableKey = sub.url.stableSubtitleKey()
                MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                    .setMimeType(subtitleMimeType(sub.url))
                    .setLanguage(sub.lang)
                    .setLabel(stableKey)
                    .setId(stableKey)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }
        return builder.build()
    }

    private fun subtitleMimeType(url: String): String {
        val normalizedUrl = url
            .substringBefore('?')
            .substringBefore('#')
            .lowercase(Locale.ROOT)
        return when {
            normalizedUrl.endsWith(".vtt") || normalizedUrl.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            normalizedUrl.endsWith(".ass") || normalizedUrl.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            normalizedUrl.endsWith(".ttml") || normalizedUrl.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        val builder = okHttpClient.newBuilder()
            .connectTimeout(
                PlaybackNetworkTuning.CONNECT_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            .readTimeout(
                PlaybackNetworkTuning.READ_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS,
            )
        if (useFastDns) {
            builder.dns(okhttp3.Dns.SYSTEM)
        }
        val playerClient = builder.build()
        val httpFactory = OkHttpDataSource.Factory(playerClient)
            .setUserAgent("Puber/${BuildConfig.VERSION_NAME} (Android)")
        return CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun setMediaSource(
        player: ExoPlayer,
        mediaItem: MediaItem,
        streamType: StreamType,
        startPositionMs: Long? = null,
    ) {
        val dsFactory = dataSourceFactory ?: return
        val hlsSource = if (streamType == StreamType.HLS) {
            HlsMediaSource.Factory(dsFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(PlaybackErrorPolicy())
                .createMediaSource(mediaItem)
        } else {
            null
        }
        when {
            hlsSource != null && startPositionMs != null ->
                player.setMediaSource(hlsSource, startPositionMs)
            hlsSource != null -> player.setMediaSource(hlsSource)
            startPositionMs != null -> player.setMediaItem(mediaItem, startPositionMs)
            else -> player.setMediaItem(mediaItem)
        }
    }

    /**
     * A second, surface-less player whose only job is to make ExoPlayer fetch the next episode's
     * playlist and first segments through the same [CacheDataSource], so they land in the shared
     * cache. Nothing of it survives: once it reports ready the bytes are on disk and the player is
     * released, and the real [prepare] reads them back instead of opening a cold connection.
     */
    override fun warmUpNext(stream: StreamCandidate, subtitles: List<SubtitleLink>?) {
        if (warmUpStreamUrl == stream.url) return
        cancelWarmUp()
        warmUpStreamUrl = stream.url

        val sourceFactory = dataSourceFactory ?: createDataSourceFactory()
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                WARM_UP_BUFFER_MS,
                WARM_UP_BUFFER_MS,
                WARM_UP_BUFFER_MS,
                WARM_UP_BUFFER_MS,
            )
            .setTargetBufferBytes(WARM_UP_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(sourceFactory)
                    .setLoadErrorHandlingPolicy(PlaybackErrorPolicy()),
            )
            .build()
        warmUpPlayer = player

        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        // Releasing from inside a player callback is not allowed; hand it to the loop.
                        mainHandler.post { releaseWarmUpPlayer(expected = player) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // A failed warm-up is not a playback failure: drop it and let the real prepare
                    // hit the network and report for itself.
                    mainHandler.post {
                        if (warmUpPlayer === player) cancelWarmUp()
                    }
                }
            },
        )

        setWarmUpMediaSource(player, sourceFactory, buildMediaItem(stream.url, subtitles), stream.type)
        player.playWhenReady = false
        player.prepare()
        mainHandler.postDelayed(warmUpTimeout, WARM_UP_TIMEOUT_MS)
    }

    override fun cancelWarmUp() {
        releaseWarmUpPlayer()
        warmUpStreamUrl = null
    }

    /**
     * @param expected the player the caller meant to release, when the call was posted and another
     * warm-up may have replaced it in the meantime. Null releases whatever is current.
     */
    private fun releaseWarmUpPlayer(expected: ExoPlayer? = null) {
        if (expected != null && warmUpPlayer !== expected) return
        mainHandler.removeCallbacks(warmUpTimeout)
        warmUpPlayer?.release()
        warmUpPlayer = null
    }

    private fun setWarmUpMediaSource(
        player: ExoPlayer,
        sourceFactory: DataSource.Factory,
        mediaItem: MediaItem,
        streamType: StreamType,
    ) {
        if (streamType == StreamType.HLS) {
            player.setMediaSource(
                HlsMediaSource.Factory(sourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(PlaybackErrorPolicy())
                    .createMediaSource(mediaItem),
            )
        } else {
            player.setMediaItem(mediaItem)
        }
    }

    override fun getDebugInfo(): PlaybackControl.DebugInfo? {
        val player = exoPlayer ?: return null
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat

        val decoderCounters = player.videoDecoderCounters
        val dropped = decoderCounters?.droppedBufferCount ?: 0

        val bufferedMs = player.bufferedPosition - player.currentPosition
        val bufferedSec = (bufferedMs / 1000.0).coerceAtLeast(0.0)

        return PlaybackControl.DebugInfo(
            videoResolution = videoFormat?.let { "${it.width}x${it.height}" } ?: UNKNOWN_VALUE,
            videoCodec = codecName(videoFormat),
            videoBitrate = videoFormat?.bitrate
                ?.takeIf { it > 0 }
                ?.let { String.format(Locale.US, "%.1f Mbps", it / BITS_PER_MEGABIT) }
                ?: UNKNOWN_VALUE,
            videoFrameRate = videoFormat?.frameRate
                ?.takeIf { it > 0f }
                ?.let { String.format(Locale.US, "%.0f fps", it) }
                ?: UNKNOWN_VALUE,
            audioCodec = codecName(audioFormat),
            audioChannels = channelLayout(audioFormat?.channelCount),
            droppedFrames = dropped.toString(),
            bufferedDuration = String.format(Locale.US, "%.1fs", bufferedSec),
            bufferedBytes = PlaybackDebugFormat.bufferFill(
                allocatedBytes = bufferAllocator?.totalBytesAllocated ?: 0,
                targetBytes = targetBufferBytes,
            ),
            streamSource = effectiveStreamSource
                ?: PlaybackDebugFormat.streamSource(currentStreamUrl),
        )
    }

    private fun updateEffectiveStreamSource(host: String?, mediaLoadData: MediaLoadData) {
        if (mediaLoadData.trackType == C.TRACK_TYPE_TEXT) return
        PlaybackDebugFormat.streamSourceHost(host)
            .takeUnless { it == UNKNOWN_VALUE }
            ?.let { effectiveStreamSource = it }
    }

    private fun codecName(format: Format?): String {
        return format?.codecs ?: format?.sampleMimeType?.substringAfter("/") ?: UNKNOWN_VALUE
    }

    private fun channelLayout(channelCount: Int?): String {
        return when (channelCount) {
            CHANNELS_MONO -> "mono"
            CHANNELS_STEREO -> "stereo"
            CHANNELS_SURROUND_5_1 -> "5.1"
            CHANNELS_SURROUND_7_1 -> "7.1"
            else -> channelCount?.toString() ?: UNKNOWN_VALUE
        }
    }

    private fun notifyPlaybackState() {
        val player = exoPlayer ?: return
        callback?.onPlaybackStateChanged(
            isPlaying = isPlaybackIntended(
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState,
                playbackSuppressionReason = player.playbackSuppressionReason,
            ),
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            position = player.currentPosition,
            duration = player.duration.coerceAtLeast(0),
            buffered = player.bufferedPosition,
        )
    }

    private fun notifyTracksUpdated() {
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) return

        val audioTracks = audioGroups.mapIndexed { index, group ->
            val format = group.getTrackFormat(0)
            val label = format.label ?: format.language ?: "Track ${index + 1}"
            AudioTrackUIState(
                index = index,
                label = label,
                language = format.language ?: "",
            )
        }
        val selectedIndex = audioGroups.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        callback?.onTracksUpdated(audioTracks, selectedIndex)
    }

    private fun applyPendingSubtitleSelection() {
        pendingSubtitleTrack?.let(::applySubtitleTrackSelection)
    }

    private fun applySubtitleTrackSelection(track: SubtitleTrackUIState) {
        val player = exoPlayer ?: return
        val stableKey = track.url.stableSubtitleKey()
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val target = findTextTrack(track, stableKey, textGroups)
        val builder = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(track.language)
        if (target != null) {
            builder.setOverrideForType(
                TrackSelectionOverride(target.group.mediaTrackGroup, target.trackIndex),
            )
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun findTextTrack(
        track: SubtitleTrackUIState,
        stableKey: String,
        textGroups: List<Tracks.Group>,
    ): TextTrackSelection? {
        return findTextTrackBy(textGroups) { format ->
            format.id == track.url
        } ?: findTextTrackBy(textGroups) { format ->
            format.id == stableKey || format.label == stableKey
        } ?: findTextTrackBySubtitleIndex(textGroups, track.index)
        ?: findUnambiguousTextTrackByLanguage(textGroups, track.language)
    }

    // Media3 may not expose SubtitleConfiguration id/label for every source type.
    // The current track list still preserves the subtitle configuration order.
    private fun findTextTrackBySubtitleIndex(
        textGroups: List<Tracks.Group>,
        subtitleIndex: Int,
    ): TextTrackSelection? {
        val targetIndex = subtitleIndex - 1
        if (targetIndex < 0) return null
        return textGroups
            .flatMap { group ->
                (0 until group.length).map { trackIndex ->
                    TextTrackSelection(group = group, trackIndex = trackIndex)
                }
            }
            .getOrNull(targetIndex)
    }

    private fun findUnambiguousTextTrackByLanguage(
        textGroups: List<Tracks.Group>,
        language: String,
    ): TextTrackSelection? {
        if (language.isEmpty()) return null
        val matches = textGroups.flatMap { group ->
            (0 until group.length).mapNotNull { trackIndex ->
                group.getTrackFormat(trackIndex).takeIf { format ->
                    format.language == language
                }?.let {
                    TextTrackSelection(group = group, trackIndex = trackIndex)
                }
            }
        }
        return matches.singleOrNull()
    }

    private fun findTextTrackBy(
        textGroups: List<Tracks.Group>,
        predicate: (Format) -> Boolean,
    ): TextTrackSelection? {
        return textGroups.firstNotNullOfOrNull { group ->
            (0 until group.length).firstNotNullOfOrNull { trackIndex ->
                group.getTrackFormat(trackIndex).takeIf(predicate)?.let {
                    TextTrackSelection(group = group, trackIndex = trackIndex)
                }
            }
        }
    }

    private data class TextTrackSelection(
        val group: Tracks.Group,
        val trackIndex: Int,
    )

    private companion object {
        const val MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10_000
        const val MAX_DURATION_FOR_QUALITY_DECREASE_MS = 15_000
        const val MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25_000
        const val BANDWIDTH_FRACTION = 0.75f
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERROR_RANGE = 500..599
        const val BITS_PER_MEGABIT = 1_000_000.0
        const val UNKNOWN_VALUE = "—"
        const val CHANNELS_MONO = 1
        const val CHANNELS_STEREO = 2
        const val CHANNELS_SURROUND_5_1 = 6
        const val CHANNELS_SURROUND_7_1 = 8
        const val WARM_UP_BUFFER_MS = 15_000
        const val WARM_UP_TARGET_BUFFER_BYTES = 8 * 1024 * 1024
        const val WARM_UP_TIMEOUT_MS = 45_000L
    }
}

/**
 * Whether playback is running from the user's point of view.
 *
 * Deliberately not [androidx.media3.common.Player.isPlaying], which drops to `false` on every
 * re-buffering: the UI would then report a pause the user never asked for, and keep-screen-on
 * would be released mid-playback, letting the TV screen saver in.
 *
 * Suppression is honoured, though — on transient audio focus loss, say when Alexa answers,
 * playback really does stop while `playWhenReady` stays true.
 */
internal fun isPlaybackIntended(
    playWhenReady: Boolean,
    playbackState: Int,
    playbackSuppressionReason: Int,
): Boolean =
    playWhenReady &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
