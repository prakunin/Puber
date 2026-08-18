package com.kino.puber.core.tvhome

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kino.puber.core.contentlink.ContentUriCodec

internal object TvHomePublisherFactory {
    private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"

    fun create(context: Context, uriCodec: ContentUriCodec): TvHomePublisher = when {
        context.packageManager.hasSystemFeature(FIRE_TV_FEATURE) -> FireTvHomePublisher(context, uriCodec)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ->
            AndroidTvHomePublisher(context, uriCodec)
        else -> NoOpTvHomePublisher
    }
}
