package com.kino.puber.util

import com.kino.puber.data.preferences.NavigationPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow

/** Preferences that never report a display-setting change, for tests that are about something else. */
internal fun stubNavigationPreferences(): NavigationPreferencesRepository = mockk {
    every { displaySettingsChanges } returns emptyFlow()
}
