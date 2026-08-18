package com.kino.puber.ui.feature.player.model

import android.content.Context
import com.kino.puber.R
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.VideoUrl
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlayerUIMapperTest {

    private val context = mockk<Context> {
        every { getString(R.string.player_aspect_auto) } returns "Auto"
    }
    private val mapper = PlayerUIMapper(context)

    @Test
    fun mapQualities_excludesFilesWithoutUsableStreamUrls() {
        val files = listOf(
            VideoFile(url = null, quality = "1080p", qualityId = 4),
            VideoFile(
                url = VideoUrl(hls = "https://cdn/720.m3u8"),
                quality = "720p",
                qualityId = 3,
                w = 1280,
                h = 720,
            ),
        )

        assertEquals(
            listOf(
                QualityUIState(0, "Auto", null, null, null),
                QualityUIState(1, "720p", 3, 1280, 720),
            ),
            mapper.mapQualities(files),
        )
    }
}
