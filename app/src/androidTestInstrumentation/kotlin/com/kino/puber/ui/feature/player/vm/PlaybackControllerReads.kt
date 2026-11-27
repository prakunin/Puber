package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.Player
import com.kino.puber.ui.feature.player.model.AudioTrackUIState

// Reads of the real player's state. They run inside `PlaybackControllerHarness.awaitCondition` or
// `playerRead`, that is on the main thread, so none of them goes through `onMain` itself.

internal fun PlaybackController.playbackState(): Int = player?.playbackState ?: Player.STATE_IDLE

internal fun PlaybackController.audioTracks(): List<AudioTrackUIState> =
    player?.currentTracks?.groups
        ?.filter { it.type == C.TRACK_TYPE_AUDIO }
        ?.mapIndexed { index, group ->
            val format = group.getTrackFormat(0)
            AudioTrackUIState(
                index = index,
                label = format.label.orEmpty(),
                language = format.language.orEmpty(),
            )
        }
        .orEmpty()

internal fun PlaybackController.selectedAudioLanguage(): String? =
    player?.currentTracks?.groups
        ?.filter { it.type == C.TRACK_TYPE_AUDIO }
        ?.firstOrNull { it.isSelected }
        ?.getTrackFormat(0)
        ?.language

internal fun PlaybackController.selectedVideoHeight(): Int? =
    player?.currentTracks?.groups
        ?.filter { it.type == C.TRACK_TYPE_VIDEO }
        ?.firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it).height }
        }

internal fun PlaybackController.maxVideoSize(): Pair<Int, Int>? =
    player?.trackSelectionParameters?.let { it.maxVideoWidth to it.maxVideoHeight }

internal fun PlaybackController.viewportSize(): Pair<Int, Int>? =
    player?.trackSelectionParameters?.let { it.viewportWidth to it.viewportHeight }

internal fun PlaybackController.setViewport(width: Int, height: Int) {
    val player = player ?: return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setViewportSize(width, height, true).build()
}

internal fun PlaybackController.forceHighestSupportedBitrate(): Boolean =
    player?.trackSelectionParameters?.forceHighestSupportedBitrate == true

internal fun PlaybackController.selectedTextTrack(): Boolean =
    player?.currentTracks?.groups
        ?.filter { it.type == C.TRACK_TYPE_TEXT }
        ?.any { it.isSelected } == true

internal fun PlaybackController.textTrackLanguages(): Set<String?> =
    player?.currentTracks?.groups
        ?.filter { it.type == C.TRACK_TYPE_TEXT }
        ?.map { it.getTrackFormat(0).language }
        ?.toSet()
        .orEmpty()

internal fun PlaybackController.textTracksDisabled(): Boolean =
    player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true

internal fun PlaybackController.currentMediaPath(): String? =
    player?.currentMediaItem?.localConfiguration?.uri?.encodedPath

internal fun PlaybackController.currentCueTexts(): List<String> =
    player?.currentCues?.cues?.mapNotNull { it.text?.toString() }.orEmpty()

internal fun PlaybackController.playWhenReady(): Boolean = player?.playWhenReady == true
