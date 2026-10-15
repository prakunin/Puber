package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
import com.kino.puber.ui.feature.player.model.readableDescriptiveLabel
import com.kino.puber.ui.feature.player.model.sameSubtitleLanguage

internal class AudioTrackPreferenceResolver {

    fun findAudioTrackIndex(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
        preferredLang: String?,
    ): Int {
        val matchers = listOf(
            { exactLabelMatch(tracks, preferredLabel) },
            { normalizedLabelMatch(tracks, preferredLabel) },
            { voiceTypeAndLanguageMatch(tracks, preferredLabel, preferredLang) },
            { languageMatch(tracks, preferredLang) },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: NO_MATCH
    }

    /**
     * The row a saved subtitle preference names, or [NO_MATCH].
     *
     * Rules, in order; the first that names a row wins. An empty language with no identity means
     * Off. An API url, exact and then by stable key, is looked up over every row. After that only
     * rows compatible with the preference (language, forced flag, readable label) are candidates,
     * and each rule has to name exactly one of them: identity, then label, then language, where a
     * single manifest row beats a single side-load.
     */
    fun findSubtitleTrackIndex(
        tracks: List<SubtitleTrackUIState>,
        preferredLang: String?,
        preferredUrl: String?,
        preferredIsForced: Boolean? = null,
        preferredDescriptiveLabel: String? = null,
    ): Int {
        if (preferredLang == "" && preferredUrl.isNullOrEmpty()) return tracks.indexOfFirst { it.isOff }
        val preferredLabel = preferredDescriptiveLabel.normalizedLabel()
        val candidates = tracks.withIndex().filter { (_, row) ->
            !row.isOff && row.isCompatibleWith(preferredLang, preferredIsForced, preferredLabel)
        }
        val matchers = listOf(
            { exactSubtitleUrlMatch(tracks, preferredUrl) },
            { stableSubtitleUrlMatch(tracks, preferredUrl) },
            { subtitleIdentityMatch(candidates, preferredUrl) },
            { subtitleLabelMatch(candidates, preferredLabel) },
            { subtitleLanguageMatch(candidates, preferredLang) },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: NO_MATCH
    }

    /**
     * Where the row the viewer picked sits after the rows were rebuilt, or Off when it is gone.
     *
     * Rules, in order: the row itself, its player track id, its source identity (API url or
     * file), its full track group id with the track index, and an unambiguous language. Each
     * rule has to name exactly one row; none looks at a position.
     */
    fun findSameSubtitleTrackIndex(
        tracks: List<SubtitleTrackUIState>,
        previous: SubtitleTrackUIState,
    ): Int {
        val offIndex = tracks.indexOfFirst { it.isOff }.coerceAtLeast(0)
        if (previous.isOff) return offIndex
        val candidates = tracks.withIndex().filterNot { it.value.isOff }
        val matchers = listOf(
            { tracks.indexOf(previous) },
            { candidates.uniqueIndex { it.hasSamePlayerTrackId(previous) } },
            { candidates.uniqueIndex { it.hasSameSourceIdentity(previous) } },
            { candidates.uniqueIndex { it.hasSameTrackGroupAndIndex(previous) } },
            { candidates.uniqueIndex { it.hasSameLanguage(previous) } },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: offIndex
    }

    private fun List<IndexedValue<SubtitleTrackUIState>>.uniqueIndex(
        predicate: (SubtitleTrackUIState) -> Boolean,
    ): Int = filter { predicate(it.value) }.singleOrNull()?.index ?: NO_MATCH

    private fun SubtitleTrackUIState.hasSamePlayerTrackId(other: SubtitleTrackUIState): Boolean =
        !playerTrackId.isNullOrEmpty() && playerTrackId == other.playerTrackId

    private fun SubtitleTrackUIState.hasSameSourceIdentity(other: SubtitleTrackUIState): Boolean {
        val ids = listOfNotNull(url, sourceFile).filter { it.isNotEmpty() }
        val otherIds = listOfNotNull(other.url, other.sourceFile).filter { it.isNotEmpty() }
        return ids.any { id -> otherIds.any { otherId -> sameSubtitleIdentity(id, otherId) } }
    }

    private fun SubtitleTrackUIState.hasSameTrackGroupAndIndex(other: SubtitleTrackUIState): Boolean =
        !playerTrackGroupId.isNullOrEmpty() &&
            playerTrackGroupId == other.playerTrackGroupId &&
            playerTrackIndex == other.playerTrackIndex &&
            hasCompatibleLanguageAndForced(other)

    /** Group ids are positional, so a refreshed list can put another subtitle at the same address. */
    private fun SubtitleTrackUIState.hasCompatibleLanguageAndForced(other: SubtitleTrackUIState): Boolean {
        val languageMatches = language.isBlank() || other.language.isBlank() ||
            sameSubtitleLanguage(language, other.language)
        val forcedMatches = isForced == null || other.isForced == null || isForced == other.isForced
        return languageMatches && forcedMatches
    }

    private fun SubtitleTrackUIState.hasSameLanguage(other: SubtitleTrackUIState): Boolean =
        sameSubtitleLanguage(language, other.language) &&
            (isForced == null || other.isForced == null || isForced == other.isForced)

    private fun exactSubtitleUrlMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredUrl: String?,
    ): Int {
        if (preferredUrl.isNullOrEmpty()) return NO_MATCH
        return tracks.indexOfFirst { it.url == preferredUrl }
    }

    private fun stableSubtitleUrlMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredUrl: String?,
    ): Int {
        val preferredKey = preferredUrl?.stableSubtitleKey()?.takeIf { it.isNotEmpty() } ?: return NO_MATCH
        return tracks.indexOfFirst { it.url.stableSubtitleKey() == preferredKey }
    }

    private fun subtitleIdentityMatch(
        candidates: List<IndexedValue<SubtitleTrackUIState>>,
        preferredUrl: String?,
    ): Int {
        if (preferredUrl.isNullOrEmpty()) return NO_MATCH
        return candidates.uniqueIndex { row ->
            row.preferenceIdentities.any { sameSubtitleIdentity(it, preferredUrl) }
        }
    }

    private fun subtitleLabelMatch(
        candidates: List<IndexedValue<SubtitleTrackUIState>>,
        preferredLabel: String?,
    ): Int {
        if (preferredLabel == null) return NO_MATCH
        return candidates.uniqueIndex { it.readableDescriptiveLabel().normalizedLabel() == preferredLabel }
    }

    private fun subtitleLanguageMatch(
        candidates: List<IndexedValue<SubtitleTrackUIState>>,
        preferredLang: String?,
    ): Int {
        if (preferredLang.isNullOrEmpty()) return NO_MATCH
        val matches = candidates.filter { sameSubtitleLanguage(it.value.language, preferredLang) }
        val manifestMatch = matches.uniqueIndex { it.fromManifest }
        return if (manifestMatch >= 0) manifestMatch else matches.singleOrNull()?.index ?: NO_MATCH
    }

    private fun SubtitleTrackUIState.isCompatibleWith(
        preferredLang: String?,
        preferredIsForced: Boolean?,
        preferredLabel: String?,
    ): Boolean = matchesPreferredLanguage(preferredLang) &&
        matchesPreferredForced(preferredIsForced) &&
        matchesPreferredLabel(preferredLabel)

    private fun SubtitleTrackUIState.matchesPreferredLanguage(preferredLang: String?): Boolean =
        preferredLang.isNullOrEmpty() || language.isBlank() || sameSubtitleLanguage(language, preferredLang)

    private fun SubtitleTrackUIState.matchesPreferredForced(preferredIsForced: Boolean?): Boolean =
        preferredIsForced == null || isForced == null || isForced == preferredIsForced

    private fun SubtitleTrackUIState.matchesPreferredLabel(preferredLabel: String?): Boolean {
        val rowLabel = readableDescriptiveLabel().normalizedLabel()
        return preferredLabel == null || rowLabel == null || rowLabel == preferredLabel
    }

    private fun String?.normalizedLabel(): String? = this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun exactLabelMatch(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
    ): Int {
        if (preferredLabel == null) return NO_MATCH
        return tracks.indexOfFirst { it.label == preferredLabel }
    }

    private fun normalizedLabelMatch(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
    ): Int {
        if (preferredLabel == null) return NO_MATCH
        val coreLabel = preferredLabel.withoutNumberPrefix()
        return tracks.indexOfFirst { it.label.withoutNumberPrefix() == coreLabel }
    }

    private fun voiceTypeAndLanguageMatch(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
        preferredLang: String?,
    ): Int {
        if (preferredLabel == null || preferredLang == null) return NO_MATCH
        val savedType = extractVoiceType(preferredLabel) ?: return NO_MATCH
        return tracks.indexOfFirst { track ->
            extractVoiceType(track.label) == savedType && track.language == preferredLang
        }
    }

    private fun languageMatch(
        tracks: List<AudioTrackUIState>,
        preferredLang: String?,
    ): Int {
        if (preferredLang == null) return NO_MATCH
        return tracks.indexOfFirst { it.language == preferredLang }
    }

    /** Extracts voice type from HLS labels like "03. Многоголосый. Red Head Sound (RUS)". */
    fun extractVoiceType(label: String): String? {
        val core = label.withoutNumberPrefix()
        val withoutLang = core.substringBeforeLast(" (")
        return withoutLang.substringBefore(". ").trim().takeIf { it.isNotEmpty() }
    }

    private fun String.withoutNumberPrefix(): String {
        return replace(NUMBER_PREFIX_REGEX, "")
    }

    private companion object {
        const val NO_MATCH = -1
        val NUMBER_PREFIX_REGEX = Regex("""^\d+\.\s*""")
    }
}
