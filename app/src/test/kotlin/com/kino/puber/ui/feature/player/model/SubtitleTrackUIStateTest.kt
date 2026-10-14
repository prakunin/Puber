package com.kino.puber.ui.feature.player.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleTrackUIStateTest {

    @Test
    fun isOff_isTrueForTheMapperOffRow() {
        assertTrue(SubtitleTrackUIState(label = "Off", language = "", url = "").isOff)
    }

    @Test
    fun isOff_isFalse_forManifestRowWithEmptyUrl() {
        val row = SubtitleTrackUIState(
            label = "English",
            language = "en",
            url = "",
            playerTrackId = "subs:English",
            fromManifest = true,
        )

        assertFalse(row.isOff)
    }

    @Test
    fun isOff_isFalse_forManifestRowWithoutLanguageOrUrl() {
        val row = SubtitleTrackUIState(
            label = "",
            language = "",
            url = "",
            playerTrackGroupId = "0:subs",
            fromManifest = true,
        )

        assertFalse(row.isOff)
    }

    @Test
    fun isOff_isFalse_forApiRowWithUrl() {
        assertFalse(SubtitleTrackUIState(label = "Russian", language = "rus", url = "https://api.test/a.srt").isOff)
    }
}
