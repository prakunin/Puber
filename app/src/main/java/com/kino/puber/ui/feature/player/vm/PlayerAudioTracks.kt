package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Format
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.knownLanguageOrNull

/** Rows for the audio picker: the manifest name, else its language, else a localized "Track N". */
internal fun buildAudioRows(formats: List<Format>, fallbackLabel: (Int) -> String): List<AudioTrackUIState> =
    formats.mapIndexed { index, format ->
        val language = knownLanguageOrNull(format.language)
        val name = format.label ?: language
        AudioTrackUIState(
            index = index,
            label = name ?: fallbackLabel(index + 1),
            language = language.orEmpty(),
            isFallbackLabel = name == null,
        )
    }
