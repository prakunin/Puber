package com.kino.puber.core.tvhome

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.kino.puber.MainActivity

/** What the publisher writes to the TV provider for one program, as plain data. */
internal data class TvProgramFields(
    val stableKey: String,
    val title: String,
    val artworkUri: String,
    val intentUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastEngagementTimeMs: Long,
)

/** The TV provider calls [AndroidTvHomePublisher] needs, so its decisions run on the JVM. */
internal interface TvProviderGateway {
    fun channelExists(id: Long): Boolean

    /** Returns the new channel ID; throws when the provider refuses the channel. */
    fun publishChannel(displayName: String, logoUri: String?): Long
    fun previewExists(id: Long): Boolean
    fun publishPreview(channelId: Long, program: TvProgramFields): Long
    fun updatePreview(id: Long, channelId: Long, program: TvProgramFields)
    fun deletePreview(id: Long)
    fun watchNextExists(id: Long): Boolean
    fun publishWatchNext(program: TvProgramFields): Long
    fun updateWatchNext(id: Long, program: TvProgramFields)
    fun deleteWatchNext(id: Long)
}

/** The publisher's own persisted state: the channel ID and the published program IDs. */
internal interface TvHomeStore {
    var channelId: Long?
    var programs: String?
}

// androidx.tvprovider marks the builder setters @RestrictTo(LIBRARY_GROUP) even though
// assembling programs is the only thing the library is there for; every app that publishes to
// the Android TV home row calls them. Suppressed on this class alone rather than disabling
// the check, so a genuinely restricted call elsewhere still fails the build.
@SuppressLint("RestrictedApi")
internal class PreviewChannelTvProviderGateway(private val context: Context) : TvProviderGateway {
    private val helper = PreviewChannelHelper(context)

    override fun channelExists(id: Long): Boolean = helper.getPreviewChannel(id) != null

    override fun publishChannel(displayName: String, logoUri: String?): Long {
        // Two different `toUri` in a row read as a typo: the first is Intent's, which serialises
        // the intent to a string, and the second parses that string back into a Uri.
        val appLink = Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME)
        val builder = PreviewChannel.Builder()
            .setDisplayName(displayName)
            .setAppLinkIntentUri(appLink.toUri())
        if (logoUri != null) builder.setLogo(logoUri.toUri())
        return helper.publishDefaultChannel(builder.build())
    }

    override fun previewExists(id: Long): Boolean = helper.getPreviewProgram(id) != null

    override fun publishPreview(channelId: Long, program: TvProgramFields): Long =
        helper.publishPreviewProgram(previewOf(channelId, program))

    override fun updatePreview(id: Long, channelId: Long, program: TvProgramFields) {
        helper.updatePreviewProgram(id, previewOf(channelId, program))
    }

    override fun deletePreview(id: Long) {
        helper.deletePreviewProgram(id)
    }

    override fun watchNextExists(id: Long): Boolean = helper.getWatchNextProgram(id) != null

    override fun publishWatchNext(program: TvProgramFields): Long =
        helper.publishWatchNextProgram(watchNextOf(program))

    override fun updateWatchNext(id: Long, program: TvProgramFields) {
        helper.updateWatchNextProgram(watchNextOf(program), id)
    }

    override fun deleteWatchNext(id: Long) {
        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
    }

    private fun previewOf(channelId: Long, program: TvProgramFields): PreviewProgram =
        PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setTitle(program.title)
            .setPosterArtUri(program.artworkUri.toUri())
            .setIntentUri(program.intentUri.toUri())
            .setInternalProviderId(program.stableKey)
            .setDurationMillis(program.durationMs.toInt())
            .setLastPlaybackPositionMillis(program.positionMs.toInt())
            .build()

    private fun watchNextOf(program: TvProgramFields): WatchNextProgram =
        WatchNextProgram.Builder()
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(program.title)
            .setPosterArtUri(program.artworkUri.toUri())
            .setIntentUri(program.intentUri.toUri())
            .setInternalProviderId(program.stableKey)
            .setDurationMillis(program.durationMs.toInt())
            .setLastPlaybackPositionMillis(program.positionMs.toInt())
            .setLastEngagementTimeUtcMillis(program.lastEngagementTimeMs)
            .build()
}

internal class PreferencesTvHomeStore(context: Context) : TvHomeStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var channelId: Long?
        get() = if (preferences.contains(KEY_CHANNEL_ID)) preferences.getLong(KEY_CHANNEL_ID, 0L) else null
        set(value) = preferences.edit {
            if (value == null) remove(KEY_CHANNEL_ID) else putLong(KEY_CHANNEL_ID, value)
        }

    override var programs: String?
        get() = preferences.getString(KEY_PROGRAMS, null)
        set(value) = preferences.edit {
            if (value == null) remove(KEY_PROGRAMS) else putString(KEY_PROGRAMS, value)
        }

    private companion object {
        const val PREFERENCES_NAME = "tv_home_android"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_PROGRAMS = "programs"
    }
}
