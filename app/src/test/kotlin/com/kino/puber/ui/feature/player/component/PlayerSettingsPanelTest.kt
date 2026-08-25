package com.kino.puber.ui.feature.player.component

import com.kino.puber.ui.feature.player.model.ActivePanel
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
                SettingsDoor.Info,
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
    fun settingsDoors_keepsSubtitlesSpeedAdvancedAndInfo_whenNothingIsAvailable() {
        assertEquals(
            listOf(
                SettingsDoor.Subtitles,
                SettingsDoor.Speed,
                SettingsDoor.Advanced,
                SettingsDoor.Info,
            ),
            settingsDoors(hasAudioTracks = false, hasQualities = false),
        )
    }

    @Test
    fun initialSettingsDoor_opensAudio_forTheAudioButton() {
        assertEquals(
            SettingsDoor.Audio,
            initialSettingsDoor(ActivePanel.AudioSubtitles, settingsDoors(true, true)),
        )
    }

    @Test
    fun initialSettingsDoor_opensQuality_forTheVideoButton() {
        assertEquals(
            SettingsDoor.Quality,
            initialSettingsDoor(ActivePanel.VideoSettings, settingsDoors(true, true)),
        )
    }

    @Test
    fun initialSettingsDoor_opensInfo_forTheInfoButton() {
        assertEquals(
            SettingsDoor.Info,
            initialSettingsDoor(ActivePanel.Info, settingsDoors(true, true)),
        )
    }

    @Test
    fun initialSettingsDoor_fallsBackToSubtitles_whenThereAreNoAudioTracks() {
        assertEquals(
            SettingsDoor.Subtitles,
            initialSettingsDoor(ActivePanel.AudioSubtitles, settingsDoors(false, true)),
        )
    }

    @Test
    fun initialSettingsDoor_fallsBackToSpeed_whenThereAreNoQualities() {
        assertEquals(
            SettingsDoor.Speed,
            initialSettingsDoor(ActivePanel.VideoSettings, settingsDoors(true, false)),
        )
    }

    @Test
    fun initialSettingsDoor_opensTheFirstDoor_whenThePanelIsNotOneOfTheThree() {
        assertEquals(
            SettingsDoor.Audio,
            initialSettingsDoor(ActivePanel.None, settingsDoors(true, true)),
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
