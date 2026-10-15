package com.kino.puber.data.repository

import android.content.Context
import com.kino.puber.util.FakeSharedPreferences
import com.kino.puber.domain.model.SubtitlePreference
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerPreferencesRepositoryTest {

    @Test
    fun media3PlaybackPreferences_useSafeDefaults() {
        val repository = fixture().repository

        assertTrue(repository.discardEmbeddedArtworkMetadata)
        assertFalse(repository.hagcPlaybackEnabled)
    }

    @Test
    fun media3PlaybackPreferences_persistIndependentValues() {
        val fixture = fixture()

        fixture.repository.discardEmbeddedArtworkMetadata = false
        fixture.repository.hagcPlaybackEnabled = true

        val restoredRepository = PlayerPreferencesRepository(fixture.context)
        assertFalse(restoredRepository.discardEmbeddedArtworkMetadata)
        assertTrue(restoredRepository.hagcPlaybackEnabled)
    }

    @Test
    fun subtitlePreference_roundTripsAllFourValues() {
        val fixture = fixture()
        val full = SubtitlePreference("eng", "subtitle:English SDH", true, "English SDH")

        fixture.repository.saveTrackPreferences(42, null, null, full)

        assertEquals(full, PlayerPreferencesRepository(fixture.context).getPreferredSubtitlePreference(42))

        fixture.repository.saveTrackPreferences(42, null, null, full.copy(isForced = false))

        assertEquals(false, PlayerPreferencesRepository(fixture.context).getPreferredSubtitlePreference(42).isForced)
    }

    @Test
    fun subtitlePreference_noneClearsAllFourKeys() {
        val fixture = fixture()
        fixture.repository.saveTrackPreferences(42, null, null, SubtitlePreference("eng", "u", true, "L"))

        fixture.repository.saveTrackPreferences(42, null, null, SubtitlePreference.NONE)

        assertEquals(emptySet<String>(), fixture.subtitleKeys())
        assertEquals(SubtitlePreference.NONE, fixture.repository.getPreferredSubtitlePreference(42))
    }

    @Test
    fun subtitlePreference_partialValuesRemoveOnlyNullKeys() {
        val fixture = fixture()
        fixture.repository.saveTrackPreferences(42, null, null, SubtitlePreference("eng", "u", true, "L"))

        fixture.repository.saveTrackPreferences(42, null, null, SubtitlePreference("rus", null, null, null))

        assertEquals(setOf("subtitle_lang_42"), fixture.subtitleKeys())
    }

    @Test
    fun legacyKeysOnly_readAsLangUrlWithNullForcedAndLabel() {
        val fixture = fixture()
        fixture.preferences.values["subtitle_lang_42"] = "rus"
        fixture.preferences.values["subtitle_url_42"] = "https://test/subtitles/rus.vtt"

        assertEquals(
            SubtitlePreference("rus", "https://test/subtitles/rus.vtt", null, null),
            fixture.repository.getPreferredSubtitlePreference(42),
        )
    }

    private fun Fixture.subtitleKeys(): Set<String> =
        preferences.values.keys.filter { it.startsWith("subtitle_") }.toSet()

    private fun fixture(): Fixture {
        val preferences = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns
            preferences.sharedPreferences
        return Fixture(context, PlayerPreferencesRepository(context), preferences)
    }

    private data class Fixture(
        val context: Context,
        val repository: PlayerPreferencesRepository,
        val preferences: FakeSharedPreferences,
    )
}
