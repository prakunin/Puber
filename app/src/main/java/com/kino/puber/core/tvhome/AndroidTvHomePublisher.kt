package com.kino.puber.core.tvhome

import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.logger.log

/** The channel logo: the app icon, resolved against the running build's package (dev and stage differ). */
internal fun channelLogoUri(packageName: String): String = "android.resource://$packageName/mipmap/ic_launcher"

internal class AndroidTvHomePublisher(
    private val gateway: TvProviderGateway,
    private val store: TvHomeStore,
    private val uriCodec: ContentUriCodec,
    private val channelName: () -> String,
    private val packageName: String,
    private val onFailure: (Throwable, String) -> Unit = { error, message ->
        Unit.log(error, message, tag = LOG_TAG)
    },
) : TvHomePublisher {

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
        store.programs = null
    }

    private fun ensureChannel(): Long {
        val storedId = store.channelId
        if (storedId != null && gateway.channelExists(storedId)) return storedId
        return gateway.publishChannel(channelName(), null).also { channelId ->
            store.channelId = channelId
        }
    }

    private fun publish(
        channelId: Long,
        program: PublishedProgram,
        stored: StoredProgram?,
    ): StoredProgram {
        val fields = TvProgramFields(
            stableKey = program.stableKey,
            title = program.title,
            artworkUri = program.artworkUri,
            intentUri = uriCodec.internalUri(program.target),
            positionMs = program.positionMs,
            durationMs = program.durationMs,
            lastEngagementTimeMs = program.lastEngagementTimeMs,
        )
        val previewId = stored?.previewId?.takeIf { gateway.previewExists(it) }
        val nextPreviewId = if (previewId == null) {
            gateway.publishPreview(channelId, fields)
        } else {
            gateway.updatePreview(previewId, channelId, fields)
            previewId
        }
        val watchNextId = stored?.watchNextId?.takeIf { gateway.watchNextExists(it) }
        val nextWatchNextId = if (watchNextId == null) {
            gateway.publishWatchNext(fields)
        } else {
            gateway.updateWatchNext(watchNextId, fields)
            watchNextId
        }
        return StoredProgram(nextPreviewId, nextWatchNextId)
    }

    private fun delete(program: StoredProgram) {
        gateway.deletePreview(program.previewId)
        gateway.deleteWatchNext(program.watchNextId)
    }

    private fun storedPrograms(): Map<String, StoredProgram> = store.programs
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
        store.programs = programs.entries.joinToString(ENTRY_SEPARATOR) { (key, ids) ->
            listOf(key, ids.previewId, ids.watchNextId).joinToString(FIELD_SEPARATOR)
        }
    }

    private data class StoredProgram(
        val previewId: Long,
        val watchNextId: Long,
    )

    private companion object {
        const val LOG_TAG = "AndroidTvHomePublisher"
        const val ENTRY_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ","
        const val STORED_FIELD_COUNT = 3
    }
}
