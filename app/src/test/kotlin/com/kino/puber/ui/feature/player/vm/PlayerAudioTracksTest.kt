package com.kino.puber.ui.feature.player.vm

import android.text.TextUtils
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.kino.puber.R
import com.kino.puber.util.FakeResourceProvider
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PlayerAudioTracksTest {

    private val fake = FakeResourceProvider()

    /** `Format` normalises a language through `Util`, which calls the android-only `TextUtils`. */
    @BeforeEach
    fun stubTextUtils() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @AfterEach
    fun unstubTextUtils() {
        unmockkStatic(TextUtils::class)
    }

    private fun format(language: String? = null, label: String? = null): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_AAC)
        .setLanguage(language)
        .setLabel(label)
        .build()

    private fun rows(vararg formats: Format) =
        buildAudioRows(formats.toList()) { n -> fake.getString(R.string.player_audio_track_fallback, n) }

    @Test
    fun buildAudioRows_unlabelledGroup_usesFallbackString() {
        val rows = rows(format(), format())

        assertEquals(
            listOf(
                fake.getString(R.string.player_audio_track_fallback, 1),
                fake.getString(R.string.player_audio_track_fallback, 2),
            ),
            rows.map { it.label },
        )
        assertTrue(rows.all { it.isFallbackLabel })
        assertEquals(listOf("", ""), rows.map { it.language })
    }

    @Test
    fun buildAudioRows_undeterminedLanguage_usesFallbackString() {
        val rows = rows(format("und"), format("UND"), format("und-Latn"))

        assertEquals(
            listOf(1, 2, 3).map { fake.getString(R.string.player_audio_track_fallback, it) },
            rows.map { it.label },
        )
        assertTrue(rows.all { it.isFallbackLabel })
        assertEquals(listOf("", "", ""), rows.map { it.language })
    }

    @Test
    fun buildAudioRows_labelledUndeterminedLanguage_keepsLabelWithoutLanguage() {
        val row = rows(format("und", "English")).single()

        assertEquals("English", row.label)
        assertFalse(row.isFallbackLabel)
        assertEquals("", row.language)
    }

    @Test
    fun buildAudioRows_labelThenLanguage_keepsManifestName() {
        val rows = rows(format("en", "English"), format("es"), format())

        assertEquals(listOf("English", "es"), rows.take(2).map { it.label })
        assertEquals(listOf("en", "es"), rows.take(2).map { it.language })
        assertFalse(rows[0].isFallbackLabel)
        assertFalse(rows[1].isFallbackLabel)
        assertTrue(rows[2].isFallbackLabel)
        assertEquals(listOf(0, 1, 2), rows.map { it.index })
    }
}
