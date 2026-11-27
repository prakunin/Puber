package com.kino.puber.ui.feature.player.vm

import android.content.Context
import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import com.kino.puber.domain.interactor.player.VideoSizeCap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VideoCapParametersTest {

    /** A preferred language is normalised through `Util`, which calls the android-only `TextUtils`. */
    @BeforeEach
    fun stubTextUtils() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @AfterEach
    fun unstubTextUtils() {
        unmockkStatic(TextUtils::class)
    }

    private val context = mockk<Context>(relaxed = true)

    private fun builder() = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()

    @Test
    fun applyVideoCap_cap_setsMaxSizeAndForcesHighestBitrate() {
        val params = builder().applyVideoCap(VideoSizeCap(1280, 720), context).build()

        assertEquals(1280, params.maxVideoWidth)
        assertEquals(720, params.maxVideoHeight)
        assertTrue(params.forceHighestSupportedBitrate)
    }

    @Test
    fun applyVideoCap_cap_clearsTheDisplayViewportConstraint() {
        val onDisplay = builder().setViewportSize(1920, 1080, true).build()

        val params = onDisplay.buildUpon().applyVideoCap(VideoSizeCap(3840, 2160), context).build()

        assertEquals(Int.MAX_VALUE, params.viewportWidth)
        assertEquals(Int.MAX_VALUE, params.viewportHeight)
    }

    @Test
    fun applyVideoCap_null_clearsEarlierCap() {
        val capped = builder().setMaxVideoSize(1280, 720).setForceHighestSupportedBitrate(true).build()

        val params = capped.buildUpon().applyVideoCap(null, context).build()

        assertEquals(Int.MAX_VALUE, params.maxVideoWidth)
        assertEquals(Int.MAX_VALUE, params.maxVideoHeight)
        assertFalse(params.forceHighestSupportedBitrate)
    }

    @Test
    fun applyVideoCap_keepsUnrelatedParameters() {
        val params = builder()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setPreferredAudioLanguage("es")
            .applyVideoCap(VideoSizeCap(1280, 720), context)
            .build()

        assertTrue(params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals("es", params.preferredAudioLanguages.first())
        assertEquals(720, params.maxVideoHeight)
    }
}
