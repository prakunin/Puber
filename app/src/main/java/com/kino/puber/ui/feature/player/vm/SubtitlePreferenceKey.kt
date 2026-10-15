package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.model.SubtitlePreference
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
import com.kino.puber.ui.feature.player.model.readableDescriptiveLabel

/**
 * The value stored in the URL slot of a [SubtitlePreference]: the first non-empty identity of the
 * row. A manifest group id is stored without the merge prefix, because that prefix depends on
 * whether the item has side-loads. A side-load's group id is positional and never stored.
 */
internal fun SubtitleTrackUIState.subtitlePreferenceKey(): String? = listOf(
    url,
    sourceFile,
    playerTrackGroupId?.withoutMergedSourcePrefix()?.takeIf { fromManifest },
    playerTrackUri,
    playerTrackId,
).firstOrNull { !it.isNullOrEmpty() }

/** Every value of the row that a stored preference key may name. */
internal val SubtitleTrackUIState.preferenceIdentities: List<String>
    get() {
        val groupIds = if (fromManifest) {
            listOfNotNull(playerTrackGroupId, playerTrackGroupId?.withoutMergedSourcePrefix())
        } else {
            emptyList()
        }
        return (listOf(url, sourceFile, playerTrackUri, playerTrackId) + groupIds)
            .filter { !it.isNullOrEmpty() }
            .filterNotNull()
            .distinct()
    }

internal fun SubtitleTrackUIState?.toSubtitlePreference(): SubtitlePreference =
    if (this == null || isOff) {
        SubtitlePreference.NONE
    } else {
        SubtitlePreference(
            language = language.takeIf { it.isNotEmpty() },
            url = subtitlePreferenceKey(),
            isForced = isForced,
            descriptiveLabel = readableDescriptiveLabel(),
        )
    }
