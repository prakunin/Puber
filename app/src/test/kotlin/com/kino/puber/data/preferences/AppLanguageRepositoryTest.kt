package com.kino.puber.data.preferences

import android.content.Context
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.system.AppLocale
import com.kino.puber.util.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val PREFS_NAME = "language_preferences"
private const val LANGUAGE_KEY = "app_language"

internal class AppLanguageRepositoryTest {

    @Test
    fun languageDefaultsToTheDeviceOneWithoutWritingPreferences() {
        val fixture = fixture()

        assertEquals(AppLanguage.System, fixture.repository.getLanguage())
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun languagePersistsTheChoiceUnderItsOwnName() {
        val fixture = fixture()

        fixture.repository.setLanguage(AppLanguage.English)

        assertEquals(AppLanguage.English, fixture.repository.getLanguage())
        assertEquals(AppLanguage.English.name, fixture.preferences.values[LANGUAGE_KEY])
    }

    @Test
    fun storingALanguagePublishesItSoTheInterfaceChangesOverWithoutARestart() {
        val fixture = fixture()

        fixture.repository.setLanguage(AppLanguage.English)
        assertEquals(AppLanguage.English, AppLocale.current.value)

        fixture.repository.setLanguage(AppLanguage.System)
        assertEquals(AppLanguage.System, AppLocale.current.value)
    }

    @Test
    fun languageFallsBackToTheDeviceOneForAnUnreadableStoredValue() {
        // A value written by a version that named the languages differently, or a file that has
        // been tampered with: the interface has to come up in something rather than crash.
        val fixture = fixture(storedLanguage = "Klingon")

        assertEquals(AppLanguage.System, fixture.repository.getLanguage())
    }

    @Test
    fun goingBackToTheDeviceLanguageIsStoredRatherThanLeftBehind() {
        val fixture = fixture(storedLanguage = AppLanguage.Russian.name)

        fixture.repository.setLanguage(AppLanguage.System)

        assertEquals(AppLanguage.System, fixture.repository.getLanguage())
        assertEquals(AppLanguage.System.name, fixture.preferences.values[LANGUAGE_KEY])
    }

    private fun fixture(storedLanguage: String? = null): Fixture {
        val preferences = FakeSharedPreferences()
        storedLanguage?.let { preferences.values[LANGUAGE_KEY] = it }
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        return Fixture(
            repository = AppLanguageRepository(context),
            preferences = preferences,
        )
    }

    private data class Fixture(
        val repository: AppLanguageRepository,
        val preferences: FakeSharedPreferences,
    )
}
