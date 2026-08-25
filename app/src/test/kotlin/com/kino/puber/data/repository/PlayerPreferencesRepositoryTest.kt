package com.kino.puber.data.repository

import android.content.Context
import com.kino.puber.util.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
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

    private fun fixture(): Fixture {
        val preferences = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns
            preferences.sharedPreferences
        return Fixture(context, PlayerPreferencesRepository(context))
    }

    private data class Fixture(
        val context: Context,
        val repository: PlayerPreferencesRepository,
    )
}
