package com.kino.puber.ui.feature.player.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.ui.feature.player.vm.SUBTITLE_FILE_EXTENSION
import com.kino.puber.ui.feature.player.vm.stableSubtitleKey
import java.util.Locale

/**
 * Builds the strings the subtitle picker shows.
 *
 * Every row is named after its language. A qualifier is appended only when two rows would
 * otherwise read identically, and raw manifest strings only reach the UI when they are
 * genuinely descriptive: CDN naming such as `RUS #03` and the subtitle file names Media3
 * uses as side-loaded track labels are rejected.
 */
internal class SubtitleLabeler(
    private val displayLocale: Locale,
    private val aiGeneratedLabel: String,
    private val forcedQualifier: String,
    private val variantLabel: (label: String, ordinal: Int) -> String,
    private val unknownLabel: (position: Int) -> String,
) {

    fun apply(tracks: List<SubtitleTrackUIState>): List<SubtitleTrackUIState> {
        val labeled = tracks.mapIndexed { position, track ->
            track.copy(label = withForcedQualifier(baseLabel(track, position), track))
        }
        return disambiguate(labeled)
    }

    private fun baseLabel(track: SubtitleTrackUIState, position: Int): String {
        val isAiGenerated = canonicalSubtitleLanguage(track.language) ==
            AI_GENERATED_SUBTITLE_LANGUAGE
        val languageLabel = if (isAiGenerated) {
            aiGeneratedLabel
        } else {
            subtitleLanguageDisplayName(track.language, displayLocale)
        }
        val descriptiveLabel = track.readableDescriptiveLabel()
        val sourceLabel = track.sourceDescriptiveLabel()
        val rawLanguageLabel = track.language
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
        return languageLabel
            ?: descriptiveLabel
            ?: sourceLabel
            ?: rawLanguageLabel
            ?: unknownLabel(position + 1)
    }

    private fun withForcedQualifier(label: String, track: SubtitleTrackUIState): String =
        if (track.isForced == true) "$label$QUALIFIER_SEPARATOR$forcedQualifier" else label

    /**
     * Rows that still read the same are separated by their manifest labels when every one
     * of them has a distinct readable label, and by an ordinal otherwise.
     */
    private fun disambiguate(tracks: List<SubtitleTrackUIState>): List<SubtitleTrackUIState> {
        val collisions = tracks.groupBy { it.label }.filterValues { it.size > 1 }
        if (collisions.isEmpty()) return tracks

        val describable = collisions.filterValues(::hasDistinctReadableLabels).keys
        val ordinals = mutableMapOf<String, Int>()
        return tracks.map { track ->
            val description = track.readableDescriptiveLabel()?.takeIf { track.label in describable }
            when {
                track.label !in collisions -> track
                description != null -> track.copy(label = withForcedQualifier(description, track))
                else -> {
                    val ordinal = ordinals.merge(track.label, 1, Int::plus) ?: 1
                    track.copy(label = variantLabel(track.label, ordinal))
                }
            }
        }
    }

    private fun hasDistinctReadableLabels(group: List<SubtitleTrackUIState>): Boolean {
        val labels = group.mapNotNull { it.readableDescriptiveLabel() }
        return labels.size == group.size && labels.toSet().size == group.size
    }

    private companion object {
        const val QUALIFIER_SEPARATOR = " · "
    }
}

/**
 * A manifest label is worth showing only when it reads as a name. Anything that is really
 * a file name or a language code with a channel number is not.
 */
internal fun SubtitleTrackUIState.readableDescriptiveLabel(): String? {
    val candidate = sourceDescriptiveLabel() ?: return null
    val isLanguageCode = candidate.count(Char::isLetter) <= SHORTEST_DESCRIPTIVE_LABEL
    return candidate.takeUnless { isLanguageCode }
}

/** A source label is safe to show as a last resort when it is not a file identity. */
private fun SubtitleTrackUIState.sourceDescriptiveLabel(): String? {
    val candidate = descriptiveLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val isFileName = SUBTITLE_FILE_EXTENSION.containsMatchIn(candidate) ||
        candidate == sourceFile ||
        candidate == url.stableSubtitleKey()
    return candidate.takeUnless { isFileName }
}

private const val SHORTEST_DESCRIPTIVE_LABEL = 3

/** Built per call, so the strings and the locale are read when rows are labelled. */
internal fun subtitleLabeler(resources: ResourceProvider): SubtitleLabeler = SubtitleLabeler(
    displayLocale = resources.locale,
    aiGeneratedLabel = resources.getString(R.string.player_subtitle_ai_generated),
    forcedQualifier = resources.getString(R.string.player_subtitle_forced),
    variantLabel = { label, ordinal ->
        resources.getString(R.string.player_subtitle_variant_label, label, ordinal)
    },
    unknownLabel = { position -> resources.getString(R.string.player_subtitle_unknown, position) },
)
