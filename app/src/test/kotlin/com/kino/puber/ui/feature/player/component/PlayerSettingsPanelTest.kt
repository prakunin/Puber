package com.kino.puber.ui.feature.player.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerSettingsPanelTest {

    @Test
    fun settingsDoors_listsEveryDoorInOrder_whenTracksAndQualitiesArePresent() {
        assertEquals(
            listOf(
                SettingsDoor.Audio,
                SettingsDoor.Subtitles,
                SettingsDoor.Quality,
                SettingsDoor.Speed,
                SettingsDoor.Advanced,
                SettingsDoor.Stream,
            ),
            settingsDoors(hasAudioTracks = true, hasQualities = true),
        )
    }

    @Test
    fun settingsDoors_dropsAudioDoor_whenStreamHasNoAudioTracks() {
        val doors = settingsDoors(hasAudioTracks = false, hasQualities = true)

        assertFalse(doors.contains(SettingsDoor.Audio))
        assertTrue(doors.contains(SettingsDoor.Subtitles))
    }

    @Test
    fun settingsDoors_dropsQualityDoor_whenStreamHasNoQualities() {
        val doors = settingsDoors(hasAudioTracks = true, hasQualities = false)

        assertFalse(doors.contains(SettingsDoor.Quality))
        assertTrue(doors.contains(SettingsDoor.Speed))
    }

    @Test
    fun settingsDoors_keepsSubtitlesSpeedAdvancedAndStream_whenNothingIsAvailable() {
        assertEquals(
            listOf(
                SettingsDoor.Subtitles,
                SettingsDoor.Speed,
                SettingsDoor.Advanced,
                SettingsDoor.Stream,
            ),
            settingsDoors(hasAudioTracks = false, hasQualities = false),
        )
    }

    @Test
    fun pageOf_andDoorOf_agreeForEveryDoor() {
        SettingsDoor.entries.forEach { door ->
            assertEquals(door, doorOf(pageOf(door)))
        }
    }

    @Test
    fun doorOf_returnsNullForTheRoot() {
        assertEquals(null, doorOf(SettingsPage.Root))
    }
}
