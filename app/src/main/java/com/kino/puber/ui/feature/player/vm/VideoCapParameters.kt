package com.kino.puber.ui.feature.player.vm

import android.content.Context
import androidx.media3.common.TrackSelectionParameters
import com.kino.puber.domain.interactor.player.VideoSizeCap

/**
 * Pins video to [cap], or lifts an earlier pin when it is null. The generic receiver keeps a
 * `DefaultTrackSelector.Parameters.Builder` chainable.
 *
 * Media3 also trims variants to the physical display by default; that constraint would keep a
 * pick above the display size from playing at the picked size, so a cap clears it and Auto
 * restores the default for [context].
 */
internal fun <B : TrackSelectionParameters.Builder> B.applyVideoCap(cap: VideoSizeCap?, context: Context): B = apply {
    if (cap != null) {
        setMaxVideoSize(cap.width, cap.height)
        setForceHighestSupportedBitrate(true)
        clearViewportSizeConstraints()
    } else {
        clearVideoSizeConstraints()
        setForceHighestSupportedBitrate(false)
        setViewportSizeToPhysicalDisplaySize(context, true)
    }
}
