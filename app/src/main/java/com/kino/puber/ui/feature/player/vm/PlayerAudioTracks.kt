package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Format
import com.kino.puber.ui.feature.player.model.AudioTrackUIState

/** Rows for the audio picker: the manifest name, else its language, else a localized "Track N". */
internal fun buildAudioRows(formats: List<Format>, fallbackLabel: (Int) -> String): List<AudioTrackUIState> =
    formats.mapIndexed { index, format ->
        val name = format.label ?: format.language
        AudioTrackUIState(
            index = index,
            label = name ?: fallbackLabel(index + 1),
            language = format.language.orEmpty(),
            isFallbackLabel = name == null,
        )
    }
