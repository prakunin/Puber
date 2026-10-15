package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.model.SubtitlePreference
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitlePreferenceKeyTest {

    private val off = SubtitleTrackUIState(label = "Off", language = "", url = "")

    private fun sideLoad(url: String = "", sourceFile: String? = null, label: String? = null) =
        SubtitleTrackUIState(
            label = "",
            language = "rus",
            url = url,
            sourceFile = sourceFile,
            descriptiveLabel = label,
            playerTrackId = "rus.vtt",
            playerTrackGroupId = "1:0",
            playerTrackIndex = 0,
            fromManifest = false,
        )

    @Test
    fun keyOrder_urlThenSourceFileThenGroupIdThenTrackUriThenTrackId() {
        var row = SubtitleTrackUIState(
            label = "",
            language = "rus",
            url = "https://api/u.vtt",
            sourceFile = "file.vtt",
            playerTrackId = "track",
            playerTrackGroupId = "0:subtitle:Rus",
            playerTrackUri = "https://cdn/rus/index.m3u8",
            playerTrackIndex = 0,
            fromManifest = true,
        )
        assertEquals("https://api/u.vtt", row.subtitlePreferenceKey())
        row = row.copy(url = "")
        assertEquals("file.vtt", row.subtitlePreferenceKey())
        row = row.copy(sourceFile = null)
        assertEquals("subtitle:Rus", row.subtitlePreferenceKey())
        row = row.copy(playerTrackGroupId = null)
        assertEquals("https://cdn/rus/index.m3u8", row.subtitlePreferenceKey())
        row = row.copy(playerTrackUri = null)
        assertEquals("track", row.subtitlePreferenceKey())
        row = row.copy(playerTrackId = null)
        assertNull(row.subtitlePreferenceKey())
        assertNull(row.copy(playerTrackId = "", sourceFile = "").subtitlePreferenceKey())
    }

    @Test
    fun manifestRow_storesGroupIdWithoutMergePrefix() {
        assertEquals("subtitle:Rus", manifestRendition("rus", "Rus").subtitlePreferenceKey())
        assertEquals("subtitle:Rus", manifestRendition("rus", "Rus", merged = false).subtitlePreferenceKey())
    }

    @Test
    fun sideLoadRow_neverStoresPositionalGroupId() {
        assertEquals("https://api/rus.vtt", sideLoad(url = "https://api/rus.vtt").subtitlePreferenceKey())
        assertEquals("rus.vtt", sideLoad(sourceFile = "rus.vtt").subtitlePreferenceKey())
        assertEquals("rus.vtt", sideLoad().subtitlePreferenceKey())
        assertFalse("1:0" in sideLoad().preferenceIdentities)
    }

    @Test
    fun languageLessRow_givesNullLanguageNeverEmpty() {
        val preference = manifestRendition("", "Signs").toSubtitlePreference()

        assertNull(preference.language)
        assertEquals("subtitle:Signs", preference.url)
    }

    @Test
    fun readableLabelOnly_fileKeyLabelIsNotStored() {
        assertNull(sideLoad(label = "rus.vtt").toSubtitlePreference().descriptiveLabel)
        assertEquals("English SDH", manifestRendition("eng", "English SDH").toSubtitlePreference().descriptiveLabel)
    }

    @Test
    fun offOrNull_givesNone() {
        assertEquals(SubtitlePreference.NONE, (null as SubtitleTrackUIState?).toSubtitlePreference())
        assertEquals(SubtitlePreference.NONE, off.toSubtitlePreference())
    }

    @Test
    fun manifestIdentities_includePrefixedAndStrippedGroupId() {
        val identities = manifestRendition("rus", "Rus").preferenceIdentities

        assertTrue("0:subtitle:Rus" in identities)
        assertTrue("subtitle:Rus" in identities)
        assertTrue("subs:Rus" in identities)
    }
}
