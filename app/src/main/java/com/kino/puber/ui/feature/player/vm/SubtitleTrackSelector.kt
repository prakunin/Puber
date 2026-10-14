package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.readableDescriptiveLabel
import com.kino.puber.ui.feature.player.model.sameSubtitleLanguage

/**
 * A text track exposed by the player, flattened out of the Media3 track groups.
 *
 * [groupId] is the full, merged `TrackGroup.id`. MergingMediaSource rewrites the ids of its
 * children to `"<childIndex>:<originalId>"`; every side-loaded child is a single-track source
 * whose own id is `0`, so only the full id is unique, and only while the child order holds.
 * [groupIndex] is nothing but the controller's address of the group in its list of text
 * groups; no rule below matches on it.
 */
internal data class PlayerTextTrack(
    val groupId: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val formatId: String? = null,
    val formatLabel: String? = null,
    val language: String? = null,
    val isForced: Boolean? = null,
)

/**
 * Resolves the player text track a picker row points at.
 *
 * Order: the semantic filter (language, forced, readable label), then the source identity of
 * the row (its player track id, then its API url or file), then the full group id with the
 * track index, then an unambiguous language. Every candidate set is reduced with
 * `singleOrNull`, so an ambiguous match falls through to the next rule instead of picking an
 * arbitrary track, and nothing is ever matched by position.
 */
internal class SubtitleTrackSelector {

    fun select(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        if (candidates.isEmpty()) return null
        val compatibleCandidates = candidates.filter { candidate ->
            candidate.isSemanticallyCompatibleWith(track)
        }
        return matchByFormatId(track, compatibleCandidates)
            ?: matchBySourceIdentity(track, compatibleCandidates)
            ?: matchByTrackGroupId(track, compatibleCandidates)
            ?: matchByLanguage(track, compatibleCandidates)
    }

    private fun PlayerTextTrack.isSemanticallyCompatibleWith(
        track: SubtitleTrackUIState,
    ): Boolean {
        val languageMatches = track.language.isBlank() ||
            language?.let { sameSubtitleLanguage(it, track.language) } == true
        val forcedMatches = track.isForced == null || isForced == track.isForced
        val preferredLabel = track.readableDescriptiveLabel()?.normalizedSubtitleLabel()
        val candidateLabel = formatLabel?.normalizedSubtitleLabel()
        val labelMatches = preferredLabel == null || candidateLabel == null ||
            preferredLabel == candidateLabel
        return languageMatches && forcedMatches && labelMatches
    }

    /** The row's own track id, with or without the merge prefix on the candidate's id. */
    private fun matchByFormatId(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        val id = track.playerTrackId?.takeIf { it.isNotEmpty() } ?: return null
        return candidates
            .filter { it.formatId == id || it.formatId?.withoutMergedSourcePrefix() == id }
            .singleOrNull()
    }

    /** The API url or file of the row against the candidate's prefix-stripped id or label. */
    private fun matchBySourceIdentity(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        val rowIds = listOfNotNull(track.url, track.sourceFile).filter { it.isNotEmpty() }
        if (rowIds.isEmpty()) return null
        return candidates
            .filter { candidate ->
                val candidateIds = listOfNotNull(candidate.formatId?.withoutMergedSourcePrefix(), candidate.formatLabel)
                    .filter { it.isNotEmpty() }
                candidateIds.any { candidateId ->
                    rowIds.any { rowId -> sameSubtitleIdentity(rowId, candidateId) }
                }
            }
            .singleOrNull()
    }

    /** Exact, but only as stable as the order of the merged sources. */
    private fun matchByTrackGroupId(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        val groupId = track.playerTrackGroupId?.takeIf { it.isNotEmpty() } ?: return null
        val trackIndex = track.playerTrackIndex ?: return null
        return candidates
            .filter { it.groupId == groupId && it.trackIndex == trackIndex }
            .singleOrNull()
    }

    private fun matchByLanguage(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        if (track.language.isEmpty()) return null
        return candidates
            .filter { candidate ->
                candidate.language?.let { sameSubtitleLanguage(it, track.language) } == true
            }
            .singleOrNull()
    }
}

private fun String.normalizedSubtitleLabel(): String = trim().lowercase()
