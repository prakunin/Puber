package com.kino.puber.ui.feature.player.vm

import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kino.puber.R
import com.kino.puber.core.content.ContentChange
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.tvhome.TvHomeSyncCoordinator
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.WatchedDetailsRefreshException
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayPauseIndicatorState
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import com.kino.puber.domain.model.BufferPreset
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.QualityUIState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SeekIndicatorState
import com.kino.puber.domain.model.SubtitleSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Deliberately left whole for now: the panel, skip-segment and watch-progress responsibilities
// want their own collaborators, but the player is the riskiest screen to restructure and that
// change needs a pass on a real device.
@Suppress("LargeClass")
internal class PlayerVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val params: PlayerScreenParams,
    private val mapper: PlayerUIMapper,
    private val interactor: PlayerInteractor,
    private val skipSegmentInteractor: SkipSegmentInteractor,
    private val contentStateFactory: ContentStateFactory,
    private val playbackController: PlaybackControl,
    private val resources: ResourceProvider,
    private val tvHomeSyncCoordinator: TvHomeSyncCoordinator? = null,
) : PuberVM<PlayerViewState>(router) {

    override val initialViewState: PlayerViewState = PlayerViewState.Loading

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is PlayerViewState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(PlayerViewState.Error(error.message))
        }
    }

    private inline fun updateContent(crossinline update: PlayerContentState.() -> PlayerContentState) {
        updateViewState<PlayerViewState.Content> { PlayerViewState.Content(content.update()) }
    }

    private data class CurrentMedia(
        val token: MediaToken,
        val item: Item,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val videoNumber: Int?,
        val files: List<VideoFile>?,
        val subtitles: List<SubtitleLink>?,
    )

    private data class MediaKey(
        val itemId: Int,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val videoNumber: Int?,
    )

    private data class MediaToken(
        val generation: Long,
        val key: MediaKey,
    )

    private data class ProgressSave(
        val key: MediaKey,
        val videoNumber: Int,
        val timeSeconds: Int,
        val seasonNumber: Int?,
    )

    private enum class WatchedOrigin {
        Auto,
        Manual,
    }

    private data class WatchedRequest(
        val token: MediaToken,
        val isMovie: Boolean,
    )

    private data class CurrentEpisode(
        val media: CurrentMedia,
        val seasonNumber: Int,
        val episodeNumber: Int,
    )

    private var currentMedia: CurrentMedia? = null
    private var mediaGeneration = 0L
    private var streamCandidates: List<StreamCandidate> = emptyList()
    private var streamCandidateIndex = -1
    private var streamLinksRefreshAttempted = false

    private var controlsHideJob: Job? = null
    private var seekIndicatorHideJob: Job? = null
    private var playPauseHideJob: Job? = null
    private var countdownJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var skipSegmentsJob: Job? = null
    private var skipCountdownJob: Job? = null
    private var bufferingDebounceJob: Job? = null
    private var dismissedSegmentJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchedEpisode: Pair<Int, Int>? = null

    private var segments: List<SkipSegment> = emptyList()
    private var creditsSegment: SkipSegment? = null
    private var dismissedSegmentType: SkipSegmentType? = null
    private var countdownDismissed = false
    private var tracksRestoredForCurrentMedia = false
    private var episodeSwitchInProgress = false
    private var lastPositionMs: Long = 0L
    private var contentChanges = ContentChangeSet.empty()
    private val pendingMutations = mutableSetOf<Job>()
    private val queuedProgressSaves = LinkedHashMap<MediaKey, ProgressSave>()
    private val watchedMutationsInFlight = mutableMapOf<MediaKey, Int>()
    private val watchedMutationMutex = Mutex()
    private var progressDrainJob: Job? = null
    private var autoMarkHandledToken: MediaToken? = null
    private var closeJob: Job? = null
    private var closing = false

    private val seekHandler = SeekHandler()
    private val controlsStateMachine = ControlsStateMachine()
    private val progressTracker = ProgressTracker()
    private val audioTrackPreferenceResolver = AudioTrackPreferenceResolver()
    private val behaviourPreferences = interactor.getBehaviourPreferences()

    private val playbackCallback = object : PlaybackControl.Callback {
        override fun onPlaybackStateChanged(
            isPlaying: Boolean,
            isBuffering: Boolean,
            position: Long,
            duration: Long,
            buffered: Long,
        ) {
            val wasBuffering = (stateValue as? PlayerViewState.Content)?.content?.isBuffering == true
            updateContent {
                copy(isPlaying = isPlaying)
            }
            if (isPlaying) controlsStateMachine.onPlaybackResumed()
            if (isBuffering && !wasBuffering) {
                // Debounce: only show spinner if buffering lasts > 800ms
                bufferingDebounceJob?.cancel()
                bufferingDebounceJob = launch {
                    delay(BUFFERING_DEBOUNCE_MS)
                    updateContent { copy(isBuffering = true) }
                    controlsHideJob?.cancel()
                }
            } else if (!isBuffering) {
                bufferingDebounceJob?.cancel()
                if (wasBuffering) {
                    updateContent { copy(isBuffering = false) }
                    val controlsVisible = (stateValue as? PlayerViewState.Content)?.content?.controlsVisible == true
                    if (controlsVisible) {
                        scheduleControlsHide()
                    }
                }
            }
        }

        override fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int) {
            updateContent {
                copy(
                    audioTracks = audioTracks,
                    selectedAudioTrackIndex = selectedIndex,
                )
            }
            if (!tracksRestoredForCurrentMedia) {
                tracksRestoredForCurrentMedia = true
                restoreTrackPreferences()
            }
        }

        override fun onPlaybackEnded() {
            this@PlayerVM.onPlaybackEnded()
        }

        override fun onStreamReady(streamUrl: String) {
            if (streamCandidates.getOrNull(streamCandidateIndex)?.url == streamUrl) {
                streamLinksRefreshAttempted = false
            }
        }

        override fun onStreamFailure(
            message: String,
            recovery: PlaybackControl.StreamRecovery,
            streamUrl: String,
        ) {
            if (streamCandidates.getOrNull(streamCandidateIndex)?.url != streamUrl) return
            when (recovery) {
                PlaybackControl.StreamRecovery.NEXT_URL -> if (!switchToNextStreamCandidate()) {
                    refreshStreamLinksOnce(message)
                }
                PlaybackControl.StreamRecovery.REFRESH_LINKS -> refreshStreamLinksOnce(message)
            }
        }

        override fun onError(message: String) {
            updateViewState(PlayerViewState.Error(message))
        }
    }

    override fun onStart() {
        playbackController.setCallback(playbackCallback)
        loadContent()
    }

    private fun loadContent() {
        startPreparingPlayback(
            seasonNumber = params.seasonNumber,
            episodeNumber = params.episodeNumber,
            startMode = params.startMode,
            videoNumber = params.videoNumber,
        )
    }

    private fun startPreparingPlayback(
        seasonNumber: Int?,
        episodeNumber: Int?,
        startMode: PlayerStartMode = PlayerStartMode.ResumeIfAvailable,
        videoNumber: Int? = null,
    ) {
        val generation = ++mediaGeneration
        // prepare() drops the warm-up player itself; this only clears what was aimed at.
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedEpisode = null
        streamCandidates = emptyList()
        streamCandidateIndex = -1
        streamLinksRefreshAttempted = false
        launch {
            preparePlayback(
                generation = generation,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                startMode = startMode,
                videoNumber = videoNumber,
            )
        }
    }

    private suspend fun preparePlayback(
        generation: Long,
        seasonNumber: Int?,
        episodeNumber: Int?,
        startMode: PlayerStartMode = PlayerStartMode.ResumeIfAvailable,
        videoNumber: Int? = null,
    ) {
        val item = interactor.getItemDetails(params.itemId)
        if (!isCurrentPrepare(generation)) return
        val resolved = interactor.resolveMedia(item, seasonNumber, episodeNumber, videoNumber)
        if (!isCurrentPrepare(generation)) return
        if (videoNumber != null && !resolved.isSeries && resolved.videoNumber != videoNumber) {
            dispatchError(
                ErrorEntity(
                    message = resources.getString(R.string.player_error_playback),
                    code = "PlaybackContent",
                )
            )
            return
        }
        val token = MediaToken(
            generation = generation,
            key = MediaKey(
                itemId = params.itemId,
                seasonNumber = resolved.seasonNumber,
                episodeNumber = resolved.episodeNumber,
                videoNumber = resolved.videoNumber,
            ),
        )

        currentMedia = CurrentMedia(
            token = token,
            item = item,
            seasonNumber = resolved.seasonNumber,
            episodeNumber = resolved.episodeNumber,
            videoNumber = resolved.videoNumber,
            files = resolved.files,
            subtitles = resolved.subtitles,
        )
        val resumeDialog = when (startMode) {
            PlayerStartMode.ResumeIfAvailable -> buildResumeDialog(resolved.watchingTime)
            PlayerStartMode.StartFromBeginning -> null
        }
        val baseContentState = contentStateFactory.build(
            item = item,
            resolved = resolved,
            resumeDialog = resumeDialog,
            subtitleSize = interactor.getSubtitleSize(),
            savedBufferPreset = interactor.getBufferPreset(),
            fastDnsEnabled = interactor.isFastDnsEnabled(),
        )
        val contentState = baseContentState.copy(
            showMarkWatchedButton = behaviourPreferences.showMarkWatchedButton,
            isMarkCurrentWatchedInFlight = watchedMutationsInFlight[token.key].orZero() > 0,
        )

        updateViewState(PlayerViewState.Content(contentState))
        autoMarkHandledToken = token.takeIf { resolved.isCurrentMediaWatched }
        episodeSwitchInProgress = false
        initializePlayer(savedPosition = if (resumeDialog != null) null else 0L)
        startProgressSync()
        if (resumeDialog == null) scheduleControlsHide()
        loadSkipSegments(item, resolved.seasonNumber, resolved.episodeNumber, token)
    }

    private fun isCurrentPrepare(generation: Long): Boolean {
        return !closing && generation == mediaGeneration
    }

    private fun buildResumeDialog(watchingTime: Int?): ResumeDialogState? {
        val savedPosition = watchingTime?.toLong()?.times(1000) ?: 0L
        if (savedPosition <= 0) return null
        val media = currentMedia
        val episodeInfo = if (media?.seasonNumber != null && media.episodeNumber != null) {
            mapper.buildSubtitle(media.seasonNumber, media.episodeNumber, null)
        } else {
            null
        }
        return ResumeDialogState(
            savedPosition = savedPosition,
            formattedTime = mapper.formatTime(savedPosition),
            episodeInfo = episodeInfo,
        )
    }

    private fun initializePlayer(savedPosition: Long?) {
        val media = currentMedia ?: return
        val content = (stateValue as? PlayerViewState.Content)?.content
        val qualityIndex = content?.selectedQualityIndex ?: 0
        val bufferPreset = content
            ?.bufferPresets
            ?.getOrNull(content.selectedBufferPresetIndex)
            ?.preset
            ?: BufferPreset.AUTO
        val fastDns = content?.fastDnsEnabled ?: true
        val streamUrl = replaceStreamCandidates(
            candidates = interactor.selectStreamCandidates(media.files, qualityIndex),
            refreshAttempted = false,
        ) ?: return
        playbackController.prepare(streamUrl, media.subtitles, savedPosition, bufferPreset, fastDns)
    }

    private fun switchStreamUrl(qualityIndex: Int): Boolean {
        val media = currentMedia ?: return false
        val streamUrl = replaceStreamCandidates(
            candidates = interactor.selectStreamCandidates(media.files, qualityIndex),
            refreshAttempted = false,
        ) ?: return false
        playbackController.switchStream(streamUrl, media.subtitles)
        return true
    }

    private fun replaceStreamCandidates(
        candidates: List<StreamCandidate>,
        refreshAttempted: Boolean,
    ): StreamCandidate? {
        val firstCandidate = candidates.firstOrNull() ?: return null
        streamCandidates = candidates
        streamCandidateIndex = 0
        streamLinksRefreshAttempted = refreshAttempted
        return firstCandidate
    }

    private fun switchToNextStreamCandidate(): Boolean {
        val media = currentMedia ?: return false
        val nextIndex = streamCandidateIndex + 1
        val streamUrl = streamCandidates.getOrNull(nextIndex) ?: return false
        streamCandidateIndex = nextIndex
        playbackController.switchStream(streamUrl, media.subtitles)
        return true
    }

    private fun refreshStreamLinksOnce(message: String) {
        val media = currentMedia ?: return
        val contentAtFailure = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (streamLinksRefreshAttempted) {
            updateViewState(PlayerViewState.Error(message))
            return
        }
        streamLinksRefreshAttempted = true
        val token = media.token
        launch {
            try {
                val item = interactor.refreshItemDetails(token.key.itemId)
                if (!isCurrentMedia(token)) return@launch
                val resolved = interactor.resolveMedia(
                    item = item,
                    seasonNumber = token.key.seasonNumber,
                    episodeNumber = token.key.episodeNumber,
                    videoNumber = token.key.videoNumber,
                )
                if (!isCurrentMedia(token)) return@launch
                val refreshedMedia = media.copy(
                    item = item,
                    files = resolved.files,
                    subtitles = resolved.subtitles,
                )
                val latestContent = (stateValue as? PlayerViewState.Content)?.content ?: contentAtFailure
                val (refreshedQualities, qualityIndex) = refreshedQualitySelection(
                    files = resolved.files,
                    previousContent = contentAtFailure,
                )
                val streamUrl = replaceStreamCandidates(
                    candidates = interactor.selectStreamCandidates(resolved.files, qualityIndex),
                    refreshAttempted = true,
                )
                if (streamUrl == null) {
                    updateViewState(PlayerViewState.Error(message))
                    return@launch
                }
                currentMedia = refreshedMedia
                updateViewState(
                    PlayerViewState.Content(
                        latestContent.copy(
                            qualities = refreshedQualities,
                            selectedQualityIndex = qualityIndex,
                        ),
                    ),
                )
                playbackController.switchStream(streamUrl, refreshedMedia.subtitles)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isCurrentMedia(token)) {
                    updateViewState(PlayerViewState.Error(message))
                }
            }
        }
    }

    private fun refreshedQualitySelection(
        files: List<VideoFile>?,
        previousContent: PlayerContentState,
    ): Pair<List<QualityUIState>, Int> {
        val refreshedQualities = mapper.mapQualities(files)
        if (previousContent.selectedQualityIndex == 0) return refreshedQualities to 0
        val selectedQuality = previousContent.qualities.getOrNull(previousContent.selectedQualityIndex)
        val refreshedIndex = refreshedQualities.indexOfFirst { quality ->
            quality.qualityId == selectedQuality?.qualityId &&
                quality.width == selectedQuality?.width &&
                quality.height == selectedQuality?.height
        }.takeIf { it >= 0 } ?: 0
        return refreshedQualities to refreshedIndex
    }

    private fun isCurrentMedia(token: MediaToken): Boolean {
        return !closing && currentMedia?.token == token
    }

    private fun restoreTrackPreferences() {
        val preferredLabel = interactor.getPreferredAudioLabel(params.itemId)
        val preferredLang = interactor.getPreferredAudioLang(params.itemId)
        val subtitleLang = interactor.getPreferredSubtitleLang(params.itemId)
        val subtitleUrl = interactor.getPreferredSubtitleUrl(params.itemId)
        val content = (stateValue as? PlayerViewState.Content)?.content ?: return
        val audioIndex = audioTrackPreferenceResolver.findAudioTrackIndex(
            tracks = content.audioTracks,
            preferredLabel = preferredLabel,
            preferredLang = preferredLang,
        )

        if (audioIndex >= 0) {
            applyAudioTrackSelection(audioIndex, persist = false)
        }

        val subtitleIndex = audioTrackPreferenceResolver.findSubtitleTrackIndex(
            tracks = content.subtitleTracks,
            preferredLang = subtitleLang,
            preferredUrl = subtitleUrl,
        )
        if (subtitleIndex >= 0) {
            applySubtitleSelection(subtitleIndex, persist = false)
        }
    }

    override fun onAction(action: UIAction) {
        if (closing) {
            if (action is PlayerAction.OnBackPressed) {
                onBackPressed()
            }
            return
        }
        when (action) {
            is PlayerAction.TogglePlayPause -> togglePlayPause()
            is PlayerAction.OkPressed -> onOkPressed()
            is PlayerAction.OkReleased -> onOkReleased()
            is PlayerAction.SeekForward -> seekForward()
            is PlayerAction.SeekBackward -> seekBackward()
            is PlayerAction.ShowControls -> showControls(action.focusTarget)
            is PlayerAction.HideControls -> hideControls()
            is PlayerAction.ResetControlsTimer -> scheduleControlsHide()
            is PlayerAction.OpenSettingsPanel -> openPanel(ActivePanel.Settings)
            is PlayerAction.OpenEpisodesPanel -> openPanel(ActivePanel.Episodes)
            is PlayerAction.OpenAboutPanel -> openPanel(ActivePanel.About)
            is PlayerAction.ClosePanel -> closePanel()
            is PlayerAction.SelectAudioTrack -> applyAudioTrackSelection(action.index)
            is PlayerAction.SelectSubtitle -> applySubtitleSelection(action.index)
            is PlayerAction.SelectSoundMode -> selectSoundMode(action.index)
            is PlayerAction.CycleSubtitleSize -> cycleSubtitleSize()
            is PlayerAction.SelectQuality -> selectQuality(action.index)
            is PlayerAction.SelectSpeed -> selectSpeed(action.index)
            is PlayerAction.SelectAspectRatio -> selectAspectRatio(action.index)
            is PlayerAction.SelectBufferPreset -> selectBufferPreset(action.index)
            is PlayerAction.ToggleFastDns -> toggleFastDns()
            is PlayerAction.SelectEpisode -> switchEpisode(action.seasonNumber, action.episodeNumber)
            is PlayerAction.SelectEpisodeById -> selectEpisodeById(action.episodeId)
            is PlayerAction.EpisodeWatchedChanged -> onEpisodeWatchedChanged(action.item, action.watched)
            is PlayerAction.SeasonWatchedChanged -> onSeasonWatchedChanged(action.item, action.watched)
            is PlayerAction.MarkCurrentWatched -> markCurrentMediaWatched()
            is PlayerAction.NextEpisode -> playNextEpisode()
            is PlayerAction.PreviousEpisode -> playPreviousEpisode()
            is PlayerAction.CancelNextEpisodeCountdown -> cancelCountdown()
            is PlayerAction.SkipSegmentClicked -> performSkipSegment()
            is PlayerAction.CancelSkipSegment -> cancelSkipSegment()
            is PlayerAction.SkipSegmentCountdownFinished -> performSkipSegment()
            is PlayerAction.ResumeFromPosition -> resumeFromSavedPosition()
            is PlayerAction.StartFromBeginning -> startFromBeginning()
            is PlayerAction.RetryPlayback -> retryPlayback()
            is PlayerAction.OnBackground -> pauseForBackground()
            is PlayerAction.OnBackPressed -> onBackPressed()
            else -> super.onAction(action)
        }
    }

    private fun togglePlayPause() {
        if (playbackController.isPlaying) {
            playbackController.pause()
            saveCurrentPosition()
            updateContent { copy(isPlaying = false) }
            showPlayPauseIndicator(isPlaying = false)
        } else {
            playbackController.play()
            updateContent { copy(isPlaying = true) }
            showPlayPauseIndicator(isPlaying = true)
        }
    }

    private fun showPlayPauseIndicator(isPlaying: Boolean) {
        updateContent {
            copy(playPauseIndicator = PlayPauseIndicatorState(isPlaying = isPlaying))
        }
        playPauseHideJob?.cancel()
        playPauseHideJob = launch {
            delay(PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS)
            updateContent { copy(playPauseIndicator = null) }
        }
    }

    private fun seekForward() {
        val step = seekHandler.nextStep()
        val newPosition = (playbackController.currentPosition + step * 1000L).coerceAtMost(playbackController.duration)
        playbackController.seekTo(newPosition)
        updateContent { copy(currentPosition = newPosition) }
        showSeekIndicator(isForward = true, stepSeconds = step, targetPosition = newPosition)
    }

    private fun seekBackward() {
        val step = seekHandler.nextStep()
        val newPosition = (playbackController.currentPosition - step * 1000L).coerceAtLeast(0)
        playbackController.seekTo(newPosition)
        updateContent { copy(currentPosition = newPosition) }
        showSeekIndicator(isForward = false, stepSeconds = step, targetPosition = newPosition)
    }

    private fun showSeekIndicator(isForward: Boolean, stepSeconds: Int, targetPosition: Long) {
        val offsetText = mapper.formatSeekOffset(isForward, stepSeconds)
        updateContent {
            copy(
                seekIndicator = SeekIndicatorState(
                    isForward = isForward,
                    offsetText = offsetText,
                    targetTimeText = mapper.formatTime(targetPosition),
                )
            )
        }
        seekIndicatorHideJob?.cancel()
        seekIndicatorHideJob = launch {
            delay(SEEK_INDICATOR_HIDE_DELAY_MS)
            updateContent { copy(seekIndicator = null) }
        }
    }

    private fun readDebugInfo(settingsPanelOpen: Boolean): PlaybackControl.DebugInfo? {
        val wanted = behaviourPreferences.debugOverlayEnabled || settingsPanelOpen
        return if (wanted) playbackController.getDebugInfo() else null
    }

    /** The stream diagnostics are a door inside the settings panel, and read live while it is up. */
    private fun isSettingsPanel(panel: ActivePanel?): Boolean = panel == ActivePanel.Settings

    private fun onOkPressed() {
        if (behaviourPreferences.okTogglesPlayPause) {
            togglePlayPause()
            return
        }
        // Reveal controls on key-down, but keep focus on the video until key-up. Otherwise the
        // same physical OK press can be delivered to the newly focused Play/Pause button.
        showControls(focusTarget = null)
    }

    private fun onOkReleased() {
        if (!behaviourPreferences.okTogglesPlayPause) {
            showControls(FocusTarget.Buttons)
        }
    }

    private fun showControls(focusTarget: FocusTarget?) {
        val effects = controlsStateMachine.showControls(focusTarget)
        applyControlsState()
        processEffects(effects)
    }

    private fun hideControls() {
        val effects = controlsStateMachine.hideControls()
        applyControlsState()
        processEffects(effects)
    }

    /**
     * Puts the controls up for good at the end of the media. Writing `controlsVisible` into the
     * content on its own, which is what this used to do, left the state machine holding the
     * opposite, and back only worked afterwards because of that disagreement.
     */
    private fun showControlsForEndedPlayback() {
        val effects = controlsStateMachine.showControlsForEndedPlayback()
        applyControlsState()
        processEffects(effects)
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = launch {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsStateMachine.applyControlsVisibility(false)
            applyControlsState()
        }
    }

    private fun openPanel(panel: ActivePanel) {
        val effects = controlsStateMachine.openPanel(panel)
        applyControlsState()
        if (isSettingsPanel(panel)) {
            // Fill the readings right away instead of waiting for the next position tick: the
            // stream info page sits one press from the settings root, and the root itself shows
            // the resolution next to its door.
            updateContent { copy(debugInfo = playbackController.getDebugInfo()) }
        }
        processEffects(effects)
    }

    private fun closePanel() {
        val effects = controlsStateMachine.closePanel()
        applyControlsState()
        processEffects(effects)
    }

    private fun onPanelClosed() {
        if (!behaviourPreferences.debugOverlayEnabled) {
            // Readings taken for the info panel must not leak into the debug overlay, which
            // becomes visible again together with the controls.
            updateContent { copy(debugInfo = null) }
        }
        // A countdown may have become eligible while the panel owned focus. Re-evaluate once the
        // player is interactive again, including the case where playback already reached its end.
        checkEarlyNextEpisode()
    }

    private fun applyControlsState() {
        val cs = controlsStateMachine.state
        updateContent {
            copy(
                controlsVisible = cs.controlsVisible,
                controlsFocusTarget = cs.focusTarget,
                activePanel = cs.activePanel,
            )
        }
    }

    private fun processEffects(effects: List<ControlsStateMachine.Effect>) {
        for (effect in effects) {
            when (effect) {
                is ControlsStateMachine.Effect.ScheduleHide -> scheduleControlsHide()
                is ControlsStateMachine.Effect.CancelHide -> controlsHideJob?.cancel()
                is ControlsStateMachine.Effect.PanelClosed -> onPanelClosed()
                is ControlsStateMachine.Effect.SaveAndExit -> exitPlayer()
            }
        }
    }

    private fun applyAudioTrackSelection(index: Int, persist: Boolean = true) {
        updateContent {
            copy(selectedAudioTrackIndex = index)
        }
        playbackController.selectAudioTrack(index)
        if (persist) {
            saveTrackPreferences()
        }
    }

    private fun applySubtitleSelection(index: Int, persist: Boolean = true) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        val subtitle = currentState.subtitleTracks.getOrNull(index) ?: return
        updateContent {
            copy(selectedSubtitleIndex = index)
        }
        playbackController.selectSubtitle(subtitle)
        if (persist) {
            saveTrackPreferences()
        }
    }

    private fun selectSoundMode(index: Int) {
        updateContent {
            copy(selectedSoundModeIndex = index)
        }
    }

    private fun cycleSubtitleSize() {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        val newSize = when (currentState.subtitleSize) {
            SubtitleSize.SMALL -> SubtitleSize.MEDIUM
            SubtitleSize.MEDIUM -> SubtitleSize.LARGE
            SubtitleSize.LARGE -> SubtitleSize.SMALL
        }
        interactor.saveSubtitleSize(newSize)
        updateContent {
            copy(subtitleSize = newSize)
        }
    }

    private fun selectQuality(index: Int) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (currentState.selectedQualityIndex == index) return

        if (switchStreamUrl(index)) {
            updateContent {
                copy(selectedQualityIndex = index)
            }
        }
    }

    private fun selectSpeed(index: Int) {
        updateContent { copy(selectedSpeedIndex = index) }
        val speed = (stateValue as? PlayerViewState.Content)?.content?.speeds?.getOrNull(index)?.speed ?: 1.0f
        playbackController.setSpeed(speed)
    }

    private fun selectAspectRatio(index: Int) {
        updateContent {
            copy(selectedAspectRatioIndex = index)
        }
        // Aspect ratio is applied in PlayerScreenContent via PlayerView.resizeMode
    }

    private fun selectBufferPreset(index: Int) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (currentState.selectedBufferPresetIndex == index) return

        val preset = currentState.bufferPresets.getOrNull(index)?.preset ?: return
        interactor.saveBufferPreset(preset)

        val position = playbackController.currentPosition
        updateContent { copy(selectedBufferPresetIndex = index) }
        tracksRestoredForCurrentMedia = false
        initializePlayer(savedPosition = position)
    }

    private fun toggleFastDns() {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        val newValue = !currentState.fastDnsEnabled
        interactor.setFastDnsEnabled(newValue)

        val position = playbackController.currentPosition
        updateContent { copy(fastDnsEnabled = newValue) }
        tracksRestoredForCurrentMedia = false
        initializePlayer(savedPosition = position)
    }

    private fun selectEpisodeById(episodeId: Int) {
        val item = currentMedia?.item ?: return
        val seasons = item.seasons ?: return
        for (season in seasons) {
            val episode = season.episodes?.find { it.id == episodeId }
            if (episode != null) {
                closePanel()
                switchEpisode(season.number, episode.number)
                return
            }
        }
    }

    /**
     * Drops everything that belonged to the media that was playing, so whatever starts next begins
     * from the same place however it was reached.
     *
     * Written once because it was not: switching episode cleared all of this and retrying after an
     * error cleared none of it, so a retried stream came back with the previous episode's countdown
     * still ticking, its skip segments still armed, and the saved audio and subtitle tracks already
     * marked as restored and therefore never applied.
     */
    private fun resetMediaSession() {
        countdownJob?.cancel()
        countdownJob = null
        skipCountdownJob?.cancel()
        skipCountdownJob = null
        dismissedSegmentJob?.cancel()
        dismissedSegmentJob = null
        seekIndicatorHideJob?.cancel()
        positionUpdateJob?.cancel()
        skipSegmentsJob?.cancel()
        bufferingDebounceJob?.cancel()
        segments = emptyList()
        creditsSegment = null
        dismissedSegmentType = null
        countdownDismissed = false
        tracksRestoredForCurrentMedia = false
        controlsStateMachine.onPlaybackResumed()
        // Left where the last media stopped, the first tick of the next one reads as a seek jump
        // and takes down the prompt that has only just gone up.
        lastPositionMs = 0L
    }

    private fun switchEpisode(seasonNumber: Int, episodeNumber: Int) {
        if (episodeSwitchInProgress) return
        episodeSwitchInProgress = true
        saveCurrentPosition()
        playbackController.release()
        resetMediaSession()

        updateViewState(PlayerViewState.Loading)
        startPreparingPlayback(
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            startMode = PlayerStartMode.StartFromBeginning,
        )
    }

    private fun playNextEpisode() {
        currentEpisode()?.let { episode ->
            interactor.findNextEpisode(
                item = episode.media.item,
                currentSeason = episode.seasonNumber,
                currentEpisode = episode.episodeNumber,
            )
        }?.let(::switchEpisode)
    }

    private fun playPreviousEpisode() {
        currentEpisode()?.let { episode ->
            interactor.findPreviousEpisode(
                item = episode.media.item,
                currentSeason = episode.seasonNumber,
                currentEpisode = episode.episodeNumber,
            )
        }?.let(::switchEpisode)
    }

    private fun switchEpisode(episode: Pair<Int, Int>) {
        switchEpisode(episode.first, episode.second)
    }

    private fun onEpisodeWatchedChanged(item: VideoItemUIState, watched: Boolean) {
        val season = item.seasonNumber ?: return
        val episode = item.episodeNumber ?: return
        val currentToken = currentMedia?.token?.takeIf { token ->
            token.key.seasonNumber == season && token.key.episodeNumber == episode
        }
        val previousAutoMarkToken = autoMarkHandledToken
        if (currentToken != null) {
            autoMarkHandledToken = currentToken
            beginWatchedMutation(currentToken)
        }
        launchMutation {
            try {
                val updated = watchedMutationMutex.withLock {
                    interactor.setEpisodeWatched(params.itemId, season, episode, watched)
                }
                markContentChanged(ContentChangeType.Watched)
                if (updated != null) {
                    syncContentWithItem(updated)
                } else {
                    syncEpisodeWatchedLocally(season, episode, watched)
                }
                showMessage(
                    resources.getString(
                        if (watched) {
                            R.string.context_menu_episode_watched
                        } else {
                            R.string.context_menu_episode_unwatched
                        }
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (throwable: Throwable) {
                if (currentToken != null && autoMarkHandledToken == currentToken) {
                    autoMarkHandledToken = previousAutoMarkToken
                }
                showMessage(errorHandler.map(throwable).message)
            } finally {
                currentToken?.let { token ->
                    endWatchedMutation(token)
                }
            }
        }
    }

    private fun onSeasonWatchedChanged(item: VideoItemUIState, watched: Boolean) {
        val season = item.seasonNumber ?: return
        val currentToken = currentMedia?.token?.takeIf { token ->
            token.key.seasonNumber == season
        }
        val previousAutoMarkToken = autoMarkHandledToken
        if (currentToken != null) {
            autoMarkHandledToken = currentToken
            beginWatchedMutation(currentToken)
        }
        launchMutation {
            try {
                val updated = watchedMutationMutex.withLock {
                    interactor.setSeasonWatched(params.itemId, season, watched)
                }
                markContentChanged(ContentChangeType.Watched)
                if (updated != null) {
                    syncContentWithItem(updated)
                } else {
                    syncSeasonWatchedLocally(season, watched)
                }
                showMessage(
                    resources.getString(
                        if (watched) {
                            R.string.context_menu_season_watched
                        } else {
                            R.string.context_menu_season_unwatched
                        }
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (throwable: Throwable) {
                if (currentToken != null && autoMarkHandledToken == currentToken) {
                    autoMarkHandledToken = previousAutoMarkToken
                }
                showMessage(errorHandler.map(throwable).message)
            } finally {
                currentToken?.let { token ->
                    endWatchedMutation(token)
                }
            }
        }
    }

    private fun markCurrentMediaWatched() {
        requestCurrentMediaWatched(WatchedOrigin.Manual)
    }

    private fun requestCurrentMediaWatched(origin: WatchedOrigin) {
        val request = currentWatchedRequest(origin) ?: return
        val token = request.token

        if (origin == WatchedOrigin.Auto) {
            autoMarkHandledToken = token
        }
        beginWatchedMutation(token)
        launchMutation {
            try {
                val updated = watchedMutationMutex.withLock {
                    interactor.markCurrentAsWatched(
                        id = token.key.itemId,
                        season = token.key.seasonNumber,
                        videoNumber = token.key.videoNumber,
                    )
                }
                markContentChanged(ContentChangeType.Watched)
                if (currentMedia?.token == token) {
                    syncContentWithItem(updated, fallbackCurrentWatched = true)
                    autoMarkHandledToken = token
                    if (origin == WatchedOrigin.Manual) {
                        showMessage(
                            resources.getString(
                                if (request.isMovie) {
                                    R.string.video_details_watched_added
                                } else {
                                    R.string.context_menu_episode_watched
                                }
                            )
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: WatchedDetailsRefreshException) {
                markContentChanged(ContentChangeType.Watched)
                if (currentMedia?.token == token) {
                    syncCurrentMediaWatchedLocally(watched = true)
                    autoMarkHandledToken = token
                    if (origin == WatchedOrigin.Manual) {
                        showMessage(errorHandler.map(error.cause ?: error).message)
                    }
                }
            } catch (throwable: Throwable) {
                if (currentMedia?.token == token) {
                    showMessage(errorHandler.map(throwable).message)
                }
            } finally {
                endWatchedMutation(token)
            }
        }
    }

    private fun currentWatchedRequest(origin: WatchedOrigin): WatchedRequest? {
        val media = currentMedia
        val content = (stateValue as? PlayerViewState.Content)?.content
        if (closing || media == null || content == null) return null
        val token = media.token
        val blocked = content.isCurrentMediaWatched ||
            !content.canMarkCurrentWatched ||
            watchedMutationsInFlight[token.key].orZero() > 0 ||
            (origin == WatchedOrigin.Auto && autoMarkHandledToken == token)
        return if (blocked) null else WatchedRequest(token = token, isMovie = content.isMovie)
    }

    private fun beginWatchedMutation(token: MediaToken) {
        watchedMutationsInFlight[token.key] = watchedMutationsInFlight[token.key].orZero() + 1
        updateWatchedMutationState(token.key)
    }

    private fun endWatchedMutation(token: MediaToken) {
        val remaining = watchedMutationsInFlight[token.key].orZero() - 1
        if (remaining > 0) {
            watchedMutationsInFlight[token.key] = remaining
        } else {
            watchedMutationsInFlight.remove(token.key)
        }
        updateWatchedMutationState(token.key)
    }

    private fun updateWatchedMutationState(key: MediaKey) {
        if (currentMedia?.token?.key == key) {
            updateContent {
                copy(isMarkCurrentWatchedInFlight = watchedMutationsInFlight[key].orZero() > 0)
            }
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun syncContentWithItem(item: Item, fallbackCurrentWatched: Boolean? = null) {
        val media = currentMedia ?: return
        val content = (stateValue as? PlayerViewState.Content)?.content ?: return
        val watched = item.currentMediaWatched(
            isMovie = content.isMovie,
            seasonNumber = media.seasonNumber,
            episodeNumber = media.episodeNumber,
            videoNumber = media.videoNumber,
        ) ?: fallbackCurrentWatched ?: content.isCurrentMediaWatched
        val episodes = if (content.isMovie) {
            content.episodes
        } else {
            mapper.mapEpisodes(item) ?: content.episodes
        }

        currentMedia = media.copy(item = item)
        updateContent {
            copy(
                isCurrentMediaWatched = watched,
                episodes = episodes,
            )
        }
    }

    private fun syncCurrentMediaWatchedLocally(watched: Boolean) {
        val media = currentMedia ?: return
        val content = (stateValue as? PlayerViewState.Content)?.content ?: return
        val updated = media.item.withCurrentMediaWatched(
            watched = watched,
            isMovie = content.isMovie,
            seasonNumber = media.seasonNumber,
            episodeNumber = media.episodeNumber,
            videoNumber = media.videoNumber,
        )
        syncContentWithItem(updated, fallbackCurrentWatched = watched)
    }

    private fun syncEpisodeWatchedLocally(seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        val media = currentMedia ?: return
        val updated = media.item.copy(
            seasons = media.item.seasons?.map { season ->
                if (season.number != seasonNumber) {
                    season
                } else {
                    season.copy(
                        episodes = season.episodes?.map { episode ->
                            if (episode.number == episodeNumber) {
                                episode.copy(watched = watched.toStatus())
                            } else {
                                episode
                            }
                        }
                    )
                }
            }
        )
        syncContentWithItem(
            item = updated,
            fallbackCurrentWatched = watched.takeIf {
                media.seasonNumber == seasonNumber && media.episodeNumber == episodeNumber
            },
        )
    }

    private fun syncSeasonWatchedLocally(seasonNumber: Int, watched: Boolean) {
        val media = currentMedia ?: return
        val updated = media.item.copy(
            seasons = media.item.seasons?.map { season ->
                if (season.number != seasonNumber) {
                    season
                } else {
                    season.copy(
                        episodes = season.episodes?.map { episode ->
                            episode.copy(watched = watched.toStatus())
                        },
                    )
                }
            }
        )
        syncContentWithItem(
            item = updated,
            fallbackCurrentWatched = watched.takeIf { media.seasonNumber == seasonNumber },
        )
    }

    private fun currentEpisode(): CurrentEpisode? {
        val media = currentMedia ?: return null
        return if (canUseCurrentEpisode(media)) {
            CurrentEpisode(
                media = media,
                seasonNumber = requireNotNull(media.seasonNumber),
                episodeNumber = requireNotNull(media.episodeNumber),
            )
        } else {
            null
        }
    }

    private fun canUseCurrentEpisode(media: CurrentMedia): Boolean {
        return !episodeSwitchInProgress &&
            media.seasonNumber != null &&
            media.episodeNumber != null
    }

    private fun onPlaybackEnded() {
        autoMarkCurrentAsWatched()
        val state = stateValue as? PlayerViewState.Content ?: return
        val content = state.content
        when {
            !content.isMovie &&
                content.hasNextEpisode &&
                content.activePanel == ActivePanel.None &&
                content.nextEpisodeCountdown == null &&
                // Cancelling on the credits means cancelled, not postponed: only a seek since then
                // puts the question back on the table.
                !countdownDismissed -> startNextEpisodeCountdown()
            // An open panel owns the screen; revealing the controls under it would also expose
            // the debug overlay the user may have switched off.
            !content.isMovie && content.activePanel == ActivePanel.None ->
                showControlsForEndedPlayback()
        }
    }

    private fun startNextEpisodeCountdown() {
        prefetchNextEpisode()
        updateContent {
            copy(nextEpisodeCountdown = PlayerCountdowns.NEXT_EPISODE_SEC)
        }
        countdownJob?.cancel()
        countdownJob = launch {
            // Zero is a number the viewer gets to see: the switch happens on the tick after it, not
            // on the same frame it appears, which is what made the last second feel clipped.
            for (i in PlayerCountdowns.NEXT_EPISODE_SEC downTo 0) {
                updateContent { copy(nextEpisodeCountdown = i) }
                delay(PlayerCountdowns.TICK_MS)
            }
            playNextEpisode()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownDismissed = true
        updateContent {
            copy(nextEpisodeCountdown = null)
        }
    }

    private fun resumeFromSavedPosition() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val position = state.resumeDialog?.savedPosition ?: 0L
        playbackController.seekTo(position)
        playbackController.play()
        updateContent {
            copy(resumeDialog = null, isPlaying = true)
        }
        scheduleControlsHide()
    }

    private fun startFromBeginning() {
        playbackController.seekTo(0)
        playbackController.play()
        updateContent {
            copy(resumeDialog = null, isPlaying = true)
        }
        scheduleControlsHide()
    }

    private fun pauseForBackground() {
        val wasPlaying = playbackController.isPlaying
        if (wasPlaying) {
            playbackController.pause()
            updateContent { copy(isPlaying = false) }
        }
        saveCurrentPosition()
    }

    private fun retryPlayback() {
        updateViewState(PlayerViewState.Loading)
        playbackController.release()
        resetMediaSession()
        loadContent()
    }

    override fun onBackPressed() {
        if (closing) {
            router.addBackDispatcher(this)
            return
        }
        val navigatedAway = handleBackAndCheckExit()
        if (!navigatedAway || closing) {
            // Re-register: dispatchBackPressed() removes us from the stack,
            // and the screen remains visible while panels close or writes drain.
            router.addBackDispatcher(this)
        }
    }

    private fun handleBackAndCheckExit(): Boolean {
        val state = stateValue as? PlayerViewState.Content
        return when {
            state == null -> {
                exitPlayer()
                true
            }
            state.content.activeSkipSegment != null -> {
                cancelSkipSegment()
                false
            }
            state.content.nextEpisodeCountdown != null -> {
                cancelCountdown()
                false
            }
            else -> handleControlsBack()
        }
    }

    private fun handleControlsBack(): Boolean {
        val effects = controlsStateMachine.handleBack()
        val shouldExit = effects.any { it is ControlsStateMachine.Effect.SaveAndExit }
        if (!shouldExit) {
            applyControlsState()
        }
        processEffects(effects)
        return shouldExit
    }

    private fun startProgressSync() {
        progressTracker.startSync(
            scope = viewModelScope,
            intervalMs = PROGRESS_SYNC_INTERVAL_MS,
            isPlaying = { playbackController.isPlaying },
            onSave = { saveCurrentPosition() },
        )
        startPositionUpdates()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                val isPlaying = playbackController.isPlaying
                val content = (stateValue as? PlayerViewState.Content)?.content
                val isBuffering = content?.isBuffering == true
                // The settings panel keeps its readings live even while playback is paused: the
                // stream info page lives inside it.
                val settingsPanelOpen = isSettingsPanel(content?.activePanel)
                if (isPlaying || isBuffering || settingsPanelOpen) {
                    if (isAnythingShowingPosition(content, settingsPanelOpen, isBuffering)) {
                        updateContent {
                            copy(
                                currentPosition = playbackController.currentPosition,
                                duration = playbackController.duration,
                                bufferedPosition = playbackController.bufferedPosition,
                                debugInfo = readDebugInfo(settingsPanelOpen),
                            )
                        }
                    }
                    if (isPlaying) {
                        // Ahead of the rest: a countdown that is already up used to hide the jump
                        // check behind itself and keep running through a seek.
                        val jumped = handlePositionJump(playbackController.currentPosition)
                        checkAutoMarkWatched()
                        checkNextEpisodePrefetch()
                        if (!jumped) {
                            checkEarlyNextEpisode()
                            checkSkipSegment()
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether anything on screen is currently drawing a playback position.
     *
     * Publishing copies the whole [PlayerContentState], and `PlayerContent` hands that one object to
     * all six of its layers, so a copy is a recomposition pass across the entire player — settings
     * panels and episode grid included, closed or not. Every reader of the four fields the tick
     * writes is behind one of these three conditions: the seek bar and the debug overlay on
     * `controlsVisible`, the info panel on its own flag, and the buffering bar on `isBuffering`.
     *
     * The checks the tick also drives are deliberately *not* covered by this — auto-mark, the early
     * next-episode prompt and skip segments all read [playbackController] directly and keep running
     * with the controls away.
     *
     * Revealing the controls does not publish by itself, so the seek bar can be up to one tick
     * behind when it appears. That is the resolution it draws at anyway.
     */
    private fun isAnythingShowingPosition(
        content: PlayerContentState?,
        settingsPanelOpen: Boolean,
        isBuffering: Boolean,
    ): Boolean = content?.controlsVisible == true || settingsPanelOpen || isBuffering

    private fun checkEarlyNextEpisode() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val duration = playbackController.duration
        if (shouldCheckEarlyNextEpisode(state, duration) && isEarlyNextEpisodePosition(duration)) {
            startNextEpisodeCountdown()
        }
    }

    private fun shouldCheckEarlyNextEpisode(state: PlayerContentState, duration: Long): Boolean {
        return !state.isMovie &&
            state.hasNextEpisode &&
            state.activePanel == ActivePanel.None &&
            state.nextEpisodeCountdown == null &&
            !countdownDismissed &&
            duration > 0
    }

    private fun isEarlyNextEpisodePosition(duration: Long): Boolean {
        val currentPosition = playbackController.currentPosition
        val creditsStart = creditsSegment?.startMs
        return if (creditsStart != null) {
            currentPosition >= creditsStart - PlayerCountdowns.PROMPT_LEAD_IN_MS
        } else {
            duration > PlayerCountdowns.NEXT_EPISODE_FALLBACK_OFFSET_MS &&
                duration - currentPosition <= PlayerCountdowns.NEXT_EPISODE_FALLBACK_OFFSET_MS
        }
    }

    /**
     * Whichever comes first — credits detected, or the tail of the episode — starts pulling the next
     * one in. Both entry points funnel into [prefetchNextEpisode], which does nothing the second
     * time it is asked for the same episode.
     */
    private fun checkNextEpisodePrefetch() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (isNextEpisodePrefetchPosition(state)) {
            prefetchNextEpisode()
        }
    }

    private fun isNextEpisodePrefetchPosition(state: PlayerContentState): Boolean {
        if (state.isMovie || !state.hasNextEpisode) return false
        val duration = playbackController.duration
        return duration > NEXT_EPISODE_PREFETCH_LEAD_MS &&
            duration - playbackController.currentPosition <= NEXT_EPISODE_PREFETCH_LEAD_MS
    }

    private fun prefetchNextEpisode() {
        val episode = currentEpisode() ?: return
        val next = interactor.findNextEpisode(
            item = episode.media.item,
            currentSeason = episode.seasonNumber,
            currentEpisode = episode.episodeNumber,
        ) ?: return
        if (prefetchedEpisode == next) return
        prefetchedEpisode = next
        prefetchJob?.cancel()
        prefetchJob = launch {
            // A prefetch that fails is a prefetch that did not happen: the real switch will hit the
            // network and report for itself, so nothing here reaches the error handler.
            runCatchingCancellable {
                val item = interactor.getItemDetails(params.itemId)
                if (closing || prefetchedEpisode != next) return@runCatchingCancellable
                val resolved = interactor.resolveMedia(item, next.first, next.second)
                // Not the current episode's quality: a switch rebuilds the state through
                // ContentStateFactory, which always starts the next episode at Auto. Warming any
                // other index would cache a URL the switch never asks for.
                val candidate = interactor
                    .selectStreamCandidates(resolved.files, SWITCHED_EPISODE_QUALITY_INDEX)
                    .firstOrNull() ?: return@runCatchingCancellable
                playbackController.warmUpNext(candidate, resolved.subtitles)
            }
        }
    }

    private fun loadSkipSegments(item: Item, season: Int?, episode: Int?, token: MediaToken) {
        skipSegmentsJob?.cancel()
        skipSegmentsJob = launch {
            val loadedSegments = skipSegmentInteractor.loadSegments(item, season, episode)
            if (closing || currentMedia?.token != token) return@launch
            segments = loadedSegments
            creditsSegment = skipSegmentInteractor.findCreditsSegment(segments)
            dismissedSegmentType = null
            // Late arrival check: if credits segment exists and position already past it
            if (creditsSegment != null && !countdownDismissed) {
                val state = (stateValue as? PlayerViewState.Content)?.content ?: return@launch
                if (shouldStartNextEpisodeForLoadedCredits(state, requireNotNull(creditsSegment))) {
                    startNextEpisodeCountdown()
                }
            }
        }
    }

    private fun shouldStartNextEpisodeForLoadedCredits(
        state: PlayerContentState,
        segment: SkipSegment,
    ): Boolean {
        return !state.isMovie &&
            state.hasNextEpisode &&
            state.activePanel == ActivePanel.None &&
            state.nextEpisodeCountdown == null &&
            playbackController.currentPosition >= segment.startMs - PlayerCountdowns.PROMPT_LEAD_IN_MS
    }

    private fun checkSkipSegment() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (
            state.activePanel == ActivePanel.None &&
            state.nextEpisodeCountdown == null &&
            state.resumeDialog == null
        ) {
            handleActiveSkipSegment(state, playbackController.currentPosition)
        }
    }

    /**
     * Seeking is the viewer taking the wheel. Whatever prompt is up comes down, and an earlier "not
     * this time" on the next episode is forgotten — by the time they reach the credits again the
     * answer may well be different. A cancelled skip segment is left alone: that one runs on its own
     * timer, so a rewind inside the segment does not undo it.
     */
    private fun handlePositionJump(currentPos: Long): Boolean {
        val positionDelta = kotlin.math.abs(currentPos - lastPositionMs)
        lastPositionMs = currentPos
        return if (positionDelta > SEEK_JUMP_THRESHOLD_MS) {
            updateContent { copy(activeSkipSegment = null, nextEpisodeCountdown = null) }
            skipCountdownJob?.cancel()
            skipCountdownJob = null
            countdownJob?.cancel()
            countdownJob = null
            countdownDismissed = false
            true
        } else {
            false
        }
    }

    private fun handleActiveSkipSegment(state: PlayerContentState, currentPos: Long) {
        val activeSegment = skipSegmentInteractor.findActiveSegment(
            segments = segments,
            positionMs = currentPos,
            leadInMs = PlayerCountdowns.PROMPT_LEAD_IN_MS,
        )
        when {
            activeSegment == null -> clearInactiveSkipSegment(state)
            shouldStartSkipSegmentCountdown(state, activeSegment) -> startSkipSegmentCountdown(activeSegment)
        }
    }

    private fun clearInactiveSkipSegment(state: PlayerContentState) {
        // Left the segment. The dismissal is not cleared here: it runs on its own timer so that
        // stepping out and back in does not reopen a question the viewer already answered.
        if (state.activeSkipSegment != null) {
            updateContent { copy(activeSkipSegment = null) }
            skipCountdownJob?.cancel()
            skipCountdownJob = null
        }
    }

    private fun shouldStartSkipSegmentCountdown(
        state: PlayerContentState,
        activeSegment: SkipSegment,
    ): Boolean {
        return leadsSomewhere(state, activeSegment) &&
            activeSegment.type != dismissedSegmentType &&
            state.activeSkipSegment?.type != activeSegment.type
    }

    /**
     * Whether skipping this segment would get the viewer anywhere.
     *
     * Credits are the one kind that may have nothing behind them. With a next episode to go to they
     * are the next-episode countdown's business and the prompt stands aside, which this already
     * did. Without one — a film, or the last episode of a series — the prompt used to go up anyway,
     * on the same plate in the same corner as the next-episode countdown, and skipping wound the
     * media on to its closing frame. Only a tail with something left in it still earns the offer.
     */
    private fun leadsSomewhere(state: PlayerContentState, segment: SkipSegment): Boolean {
        if (segment.type != SkipSegmentType.CREDITS) return true
        val nextEpisodeOwnsThem = !state.isMovie && state.hasNextEpisode
        val skipTarget = segment.endMs
        val duration = playbackController.duration
        return !nextEpisodeOwnsThem &&
            skipTarget != null &&
            duration > 0 &&
            duration - skipTarget >= PlayerCountdowns.CREDITS_MIN_TAIL_MS
    }

    private fun startSkipSegmentCountdown(segment: SkipSegment) {
        // Worked out before anything is cancelled: a segment with too little left gets no prompt,
        // and the one already up — if any — is none of this call's business.
        val seconds = skipCountdownSeconds(segment) ?: return
        skipCountdownJob?.cancel()
        val uiState = SkipSegmentUIState(
            label = mapper.mapSkipSegmentLabel(segment.type),
            targetPositionMs = segment.endMs ?: playbackController.duration,
            type = segment.type,
            countdown = seconds,
            totalSeconds = seconds,
        )
        updateContent { copy(activeSkipSegment = uiState) }
        skipCountdownJob = launch {
            // Same as the next-episode countdown: zero gets its own second before the skip runs.
            for (i in seconds - 1 downTo 0) {
                delay(PlayerCountdowns.TICK_MS)
                updateContent { copy(activeSkipSegment = activeSkipSegment?.copy(countdown = i)) }
            }
            delay(PlayerCountdowns.TICK_MS)
            performSkipSegment()
        }
    }

    /**
     * How long this prompt may honestly count for, or null when it should not go up at all.
     *
     * The playhead leaving the segment takes the prompt down and the skip never runs, so a
     * countdown longer than the segment has left is a promise the player cannot keep: the bar
     * stops part-way and the plate vanishes as the segment ends by itself. What is left has to
     * cover the countdown, the second zero gets, and enough of a jump to be worth offering.
     */
    private fun skipCountdownSeconds(segment: SkipSegment): Int? {
        val endMs = segment.endMs ?: return PlayerCountdowns.SKIP_SEGMENT_SEC
        // Media time, not wall clock: at 1.5x the segment runs out sooner than the countdown ticks.
        val leftSeconds = (endMs - playbackController.currentPosition) /
            currentPlaybackSpeed() / PlayerCountdowns.TICK_MS
        val fits = leftSeconds.toInt() -
            PlayerCountdowns.ZERO_TICK_SEC -
            PlayerCountdowns.SKIP_MIN_SAVING_SEC
        return fits.coerceAtMost(PlayerCountdowns.SKIP_SEGMENT_SEC)
            .takeIf { it >= PlayerCountdowns.SKIP_MIN_COUNTDOWN_SEC }
    }

    private fun currentPlaybackSpeed(): Float {
        return playbackController.playbackSpeed.takeIf { it > 0f } ?: DEFAULT_SPEED
    }

    private fun performSkipSegment() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val skipState = state.activeSkipSegment ?: return

        if (skipState.type == SkipSegmentType.CREDITS && !state.isMovie && state.hasNextEpisode) {
            autoMarkCurrentAsWatched()
            playNextEpisode()
        } else if (skipState.targetPositionMs > playbackController.currentPosition) {
            playbackController.seekTo(skipState.targetPositionMs)
        }
        // Nothing to skip when playback has already carried past the target — an open panel holds
        // the segment checks off, and faster playback can outrun a countdown that fitted when it
        // started. A skip that rewinds is worse than one that does not happen.
        updateContent { copy(activeSkipSegment = null) }
        // Cancelled, not merely forgotten: pressing the button ends a countdown that is still
        // running, and a job only dropped keeps writing to a plate that has already gone.
        skipCountdownJob?.cancel()
        skipCountdownJob = null
    }

    /**
     * A "no" is remembered rather than forgotten the moment the playhead moves: rewinding inside the
     * segment used to clear it and put the prompt straight back up, overriding the answer. It does
     * expire, so returning to the same segment much later asks again.
     */
    private fun cancelSkipSegment() {
        val currentType = (stateValue as? PlayerViewState.Content)?.content?.activeSkipSegment?.type
        dismissedSegmentType = currentType
        skipCountdownJob?.cancel()
        skipCountdownJob = null
        updateContent { copy(activeSkipSegment = null) }
        dismissedSegmentJob?.cancel()
        dismissedSegmentJob = launch {
            delay(PlayerCountdowns.SKIP_DISMISS_TTL_MS)
            dismissedSegmentType = null
        }
    }

    private fun saveCurrentPosition() {
        val media = currentMedia ?: return
        val videoNumber = media.videoNumber ?: return
        val timeSeconds = (playbackController.currentPosition / 1000).toInt()
        queuedProgressSaves[media.token.key] = ProgressSave(
            key = media.token.key,
            videoNumber = videoNumber,
            timeSeconds = timeSeconds,
            seasonNumber = media.seasonNumber,
        )
        if (progressDrainJob?.isActive != true) {
            launchMutation(
                onCreated = { job ->
                    progressDrainJob = job
                },
            ) {
                try {
                    while (true) {
                        val request = queuedProgressSaves.entries.firstOrNull()?.let { entry ->
                            queuedProgressSaves.remove(entry.key)
                            entry.value
                        } ?: break
                        try {
                            interactor.saveWatchingTime(
                                id = request.key.itemId,
                                videoNumber = request.videoNumber,
                                time = request.timeSeconds,
                                season = request.seasonNumber,
                            )
                            markContentChanged(ContentChangeType.PlaybackProgress)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            PlayerProgressDiagnostics.reportSaveFailure()
                        }
                    }
                } finally {
                    progressDrainJob = null
                }
            }
        }
    }

    private fun checkAutoMarkWatched() {
        val duration = playbackController.duration
        if (duration <= 0) return
        val remaining = duration - playbackController.currentPosition
        if (remaining < duration * AUTO_MARK_WATCHED_THRESHOLD) {
            autoMarkCurrentAsWatched()
        }
    }

    private fun autoMarkCurrentAsWatched() {
        requestCurrentMediaWatched(WatchedOrigin.Auto)
    }

    private fun saveTrackPreferences() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val audioTrack = state.audioTracks.getOrNull(state.selectedAudioTrackIndex)
        val subtitle = state.subtitleTracks.getOrNull(state.selectedSubtitleIndex)
        interactor.saveTrackPreferences(
            itemId = params.itemId,
            audioLang = audioTrack?.language?.takeIf { it.isNotEmpty() },
            audioLabel = audioTrack?.label?.takeIf { it.isNotEmpty() },
            subtitleLang = subtitle?.language?.takeIf { it.isNotEmpty() },
            subtitleUrl = subtitle?.url?.takeIf { it.isNotEmpty() },
        )
    }

    private fun markContentChanged(type: ContentChangeType) {
        contentChanges = contentChanges.merge(ContentChange(params.itemId, type))
        if (type == ContentChangeType.Watched || type == ContentChangeType.PlaybackProgress) {
            tvHomeSyncCoordinator?.requestRefresh()
        }
    }

    private fun launchMutation(
        onCreated: (Job) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                pendingMutations.remove(job)
            }
        }
        pendingMutations += job
        onCreated(job)
        job.start()
        return job
    }

    private suspend fun awaitPendingMutations() {
        while (true) {
            val activeJobs = pendingMutations.filter(Job::isActive)
            if (activeJobs.isEmpty()) return
            activeJobs.joinAll()
        }
    }

    private fun exitPlayer() {
        if (closeJob != null) return
        closing = true
        mediaGeneration += 1
        progressTracker.stopSync()
        positionUpdateJob?.cancel()
        skipSegmentsJob?.cancel()
        // Both countdowns end here too: either one firing while the writes drain would start the
        // next episode on a player that is already on its way out.
        countdownJob?.cancel()
        skipCountdownJob?.cancel()
        saveCurrentPosition()
        closeJob = launch {
            awaitPendingMutations()
            router.back(RESULT_CONTENT_CHANGED, contentChanges)
        }
    }

    fun getExoPlayer(): ExoPlayer? = (playbackController as? PlaybackController)?.player

    override fun onCleared() {
        playbackController.release()
        progressTracker.stopSync()
        controlsHideJob?.cancel()
        seekIndicatorHideJob?.cancel()
        playPauseHideJob?.cancel()
        countdownJob?.cancel()
        skipSegmentsJob?.cancel()
        skipCountdownJob?.cancel()
        positionUpdateJob?.cancel()
    }

    private companion object {
        const val CONTROLS_HIDE_DELAY_MS = 4_500L
        const val SEEK_INDICATOR_HIDE_DELAY_MS = 1500L
        const val PROGRESS_SYNC_INTERVAL_MS = 30_000L
        const val POSITION_UPDATE_INTERVAL_MS = 500L
        const val AUTO_MARK_WATCHED_THRESHOLD = 0.10
        const val PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS = 1500L
        const val BUFFERING_DEBOUNCE_MS = 800L
        const val SEEK_JUMP_THRESHOLD_MS = 2_000L
        const val NEXT_EPISODE_PREFETCH_LEAD_MS = 60_000L
        const val SWITCHED_EPISODE_QUALITY_INDEX = 0
        const val DEFAULT_SPEED = 1.0f
    }
}
