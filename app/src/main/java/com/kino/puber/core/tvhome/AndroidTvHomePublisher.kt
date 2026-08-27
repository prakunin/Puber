package com.kino.puber.core.tvhome

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.kino.puber.R
import com.kino.puber.MainActivity
import com.kino.puber.core.contentlink.ContentUriCodec

internal class AndroidTvHomePublisher(
    private val context: Context,
    private val uriCodec: ContentUriCodec,
) : TvHomePublisher {
    private val helper = PreviewChannelHelper(context)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun reconcile(programs: List<PublishedProgram>) {
        val channelId = ensureChannel()
        val previous = storedPrograms()
        val next = buildMap {
            programs.forEach { program ->
                val stored = previous[program.stableKey]
                put(program.stableKey, publish(channelId, program, stored))
            }
        }
        (previous.keys - next.keys).forEach { key -> delete(previous.getValue(key)) }
        storePrograms(next)
    }

    override suspend fun clearAccountPrograms() {
        storedPrograms().values.forEach(::delete)
        preferences.edit().remove(KEY_PROGRAMS).apply()
    }

    private fun ensureChannel(): Long {
        val storedId = preferences.getLong(KEY_CHANNEL_ID, NO_ID)
        if (storedId != NO_ID && helper.getPreviewChannel(storedId) != null) return storedId
        val channel = PreviewChannel.Builder()
            .setDisplayName(context.getString(R.string.tv_home_continue_watching))
            .setAppLinkIntentUri(
                Uri.parse(Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME)),
            )
            .build()
        return helper.publishDefaultChannel(channel).also { channelId ->
            preferences.edit().putLong(KEY_CHANNEL_ID, channelId).apply()
        }
    }

    // androidx.tvprovider marks the builder setters @RestrictTo(LIBRARY_GROUP) even though
    // assembling programs is the only thing the library is there for; every app that publishes to
    // the Android TV home row calls them. Suppressed on this function alone rather than disabling
    // the check, so a genuinely restricted call elsewhere still fails the build.
    @SuppressLint("RestrictedApi")
    private fun publish(
        channelId: Long,
        program: PublishedProgram,
        stored: StoredProgram?,
    ): StoredProgram {
        val intentUri = Uri.parse(uriCodec.internalUri(program.target))
        val preview = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setTitle(program.title)
            .setPosterArtUri(Uri.parse(program.artworkUri))
            .setIntentUri(intentUri)
            .setInternalProviderId(program.stableKey)
            .setDurationMillis(program.durationMs.toInt())
            .setLastPlaybackPositionMillis(program.positionMs.toInt())
            .build()
        val watchNext = WatchNextProgram.Builder()
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(program.title)
            .setPosterArtUri(Uri.parse(program.artworkUri))
            .setIntentUri(intentUri)
            .setInternalProviderId(program.stableKey)
            .setDurationMillis(program.durationMs.toInt())
            .setLastPlaybackPositionMillis(program.positionMs.toInt())
            .setLastEngagementTimeUtcMillis(program.lastEngagementTimeMs)
            .build()

        val previewId = stored?.previewId?.takeIf { helper.getPreviewProgram(it) != null }
        val nextPreviewId = if (previewId == null) {
            helper.publishPreviewProgram(preview)
        } else {
            helper.updatePreviewProgram(previewId, preview)
            previewId
        }
        val watchNextId = stored?.watchNextId?.takeIf { helper.getWatchNextProgram(it) != null }
        val nextWatchNextId = if (watchNextId == null) {
            helper.publishWatchNextProgram(watchNext)
        } else {
            helper.updateWatchNextProgram(watchNext, watchNextId)
            watchNextId
        }
        return StoredProgram(nextPreviewId, nextWatchNextId)
    }

    private fun delete(program: StoredProgram) {
        helper.deletePreviewProgram(program.previewId)
        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(program.watchNextId), null, null)
    }

    private fun storedPrograms(): Map<String, StoredProgram> = preferences
        .getString(KEY_PROGRAMS, null)
        ?.split(ENTRY_SEPARATOR)
        ?.mapNotNull { entry ->
            val pieces = entry.split(FIELD_SEPARATOR)
            if (pieces.size != STORED_FIELD_COUNT) return@mapNotNull null
            val previewId = pieces[1].toLongOrNull() ?: return@mapNotNull null
            val watchNextId = pieces[2].toLongOrNull() ?: return@mapNotNull null
            pieces[0] to StoredProgram(previewId, watchNextId)
        }
        ?.toMap()
        .orEmpty()

    private fun storePrograms(programs: Map<String, StoredProgram>) {
        val value = programs.entries.joinToString(ENTRY_SEPARATOR) { (key, ids) ->
            listOf(key, ids.previewId, ids.watchNextId).joinToString(FIELD_SEPARATOR)
        }
        preferences.edit().putString(KEY_PROGRAMS, value).apply()
    }

    private data class StoredProgram(
        val previewId: Long,
        val watchNextId: Long,
    )

    private companion object {
        const val PREFERENCES_NAME = "tv_home_android"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_PROGRAMS = "programs"
        const val NO_ID = -1L
        const val ENTRY_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ","
        const val STORED_FIELD_COUNT = 3
    }
}
