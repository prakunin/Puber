package com.kino.puber.core.tvhome

import com.kino.puber.core.contentlink.ContentTarget
import com.kino.puber.core.contentlink.ContentUriCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

internal class AndroidTvHomePublisherTest {
    private val gateway = FakeGateway()
    private val store = FakeStore()
    private val failures = mutableListOf<String>()
    private val publisher = publisher("com.kino.puber.stage")

    private fun publisher(packageName: String) = AndroidTvHomePublisher(
        gateway = gateway,
        store = store,
        uriCodec = ContentUriCodec(),
        channelName = { "Continue watching" },
        packageName = packageName,
        onFailure = { _, message -> failures += message },
    )

    @Test
    fun channelIsCreatedWithAppIconLogo() = runTest {
        publisher.reconcile(listOf(program(1)))

        assertEquals(listOf<String?>("android.resource://com.kino.puber.stage/mipmap/ic_launcher"), gateway.logos)
    }

    @Test
    fun channelLogoUriUsesGivenPackage() {
        assertEquals("android.resource://com.kino.puber/mipmap/ic_launcher", channelLogoUri("com.kino.puber"))
        assertEquals(
            "android.resource://com.kino.puber.stage/mipmap/ic_launcher",
            channelLogoUri("com.kino.puber.stage"),
        )
    }

    @Test
    fun publishesPreviewAndWatchNextPerProgramOnChannel() = runTest {
        publisher.reconcile(listOf(program(1), program(2)))

        val channelId = store.channelId
        assertEquals(listOf(channelId, channelId), gateway.previewChannels.values.toList())
        assertEquals(2, gateway.previews.size)
        assertEquals(2, gateway.watchNext.size)
    }

    @Test
    fun secondReconcileReusesStoredChannel() = runTest {
        publisher.reconcile(listOf(program(1)))
        publisher.reconcile(listOf(program(1)))

        assertEquals(1, gateway.publishChannelCalls)
        assertEquals(1, gateway.previews.size)
        assertEquals(1, gateway.watchNext.size)
    }

    @Test
    fun missingStoredChannelIsRecreated() = runTest {
        publisher.reconcile(listOf(program(1)))
        val first = store.channelId
        gateway.channels.clear()

        publisher.reconcile(listOf(program(1)))

        assertEquals(2, gateway.publishChannelCalls)
        assertNotEquals(first, store.channelId)
    }

    @Test
    fun channelFailureStillPublishesWatchNext() = runTest {
        gateway.channelFailure = IOException("Failed to add logo")

        publisher.reconcile(listOf(program(1), program(2)))

        assertEquals(2, gateway.watchNext.size)
        assertEquals(0, gateway.publishPreviewCalls)
    }

    @Test
    fun channelFailureIsLoggedOnce() = runTest {
        gateway.channelFailure = IOException("Failed to add logo")

        publisher.reconcile(listOf(program(1), program(2)))

        assertEquals(1, failures.size)
    }

    @Test
    fun cancellationFromChannelCreationPropagates() = runTest {
        gateway.channelFailure = CancellationException("cancelled")

        val thrown = runCatching { publisher.reconcile(listOf(program(1))) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(0, gateway.watchNext.size)
    }

    @Test
    fun recoveryAfterChannelFailureAddsPreviewsWithoutDuplicatingWatchNext() = runTest {
        gateway.channelFailure = IOException("Failed to add logo")
        publisher.reconcile(listOf(program(1), program(2)))
        gateway.channelFailure = null

        publisher.reconcile(listOf(program(1), program(2)))

        assertEquals(2, gateway.previews.size)
        assertEquals(2, gateway.watchNext.size)
        assertEquals(2, gateway.publishWatchNextCalls)
    }

    @Test
    fun storedPreviewIsKeptWhileChannelIsDown() = runTest {
        publisher.reconcile(listOf(program(1)))
        val storedPrograms = store.programs
        val previewId = gateway.previews.keys.single()
        gateway.channels.clear()
        gateway.channelFailure = IOException("Failed to add logo")

        publisher.reconcile(listOf(program(1)))

        assertEquals(storedPrograms, store.programs)
        assertEquals(setOf(previewId), gateway.previews.keys)
        assertEquals(emptyList<Long>(), gateway.deletedPreviews)
        assertEquals(1, gateway.publishPreviewCalls)
    }

    @Test
    fun programRemovedWhileChannelMissingSkipsPreviewDelete() = runTest {
        gateway.channelFailure = IOException("Failed to add logo")
        publisher.reconcile(listOf(program(1)))

        publisher.reconcile(emptyList())

        assertEquals(emptyList<Long>(), gateway.deletedPreviews)
        assertEquals(0, gateway.watchNext.size)
    }

    @Test
    fun noIdPreviewIsNotTreatedAsExistingProgram() = runTest {
        gateway.channelFailure = IOException("Failed to add logo")
        publisher.reconcile(listOf(program(1)))
        gateway.channelFailure = null

        publisher.reconcile(listOf(program(1)))

        assertTrue(-1L !in gateway.previewExistsQueries)
        assertEquals(1, gateway.publishPreviewCalls)
    }

    @Test
    fun storedProgramsFormatIsUnchanged() = runTest {
        store.programs = "legacy,7,8"
        gateway.previews[7] = "legacy"
        gateway.watchNext[8] = "legacy"
        gateway.channels += 1L
        store.channelId = 1L

        publisher.reconcile(listOf(program(1, key = "legacy")))
        assertEquals("legacy,7,8", store.programs)
        assertEquals(0, gateway.publishPreviewCalls)

        publisher.reconcile(listOf(program(1, key = "legacy"), program(2, key = "other")))
        val entries = store.programs!!.split(";")
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.split(",").size == 3 })
    }

    @Test
    fun clearAccountProgramsDeletesAllStoredIncludingNoIdPreview() = runTest {
        gateway.channelFailure = IOException("Failed to add logo")
        publisher.reconcile(listOf(program(1)))
        gateway.channelFailure = null
        publisher.reconcile(listOf(program(2)))
        publisher.clearAccountPrograms()

        assertEquals(0, gateway.watchNext.size)
        assertEquals(0, gateway.previews.size)
        assertTrue(-1L !in gateway.deletedPreviews)
        assertEquals(null, store.programs)
    }

    private fun program(itemId: Int, key: String = "item-$itemId") = PublishedProgram(
        stableKey = key,
        title = "Title $itemId",
        artworkUri = "http://image/$itemId.jpg",
        positionMs = 1_000L,
        durationMs = 10_000L,
        lastEngagementTimeMs = 5L,
        target = ContentTarget.Playback(itemId = itemId),
    )

    private class FakeStore : TvHomeStore {
        override var channelId: Long? = null
        override var programs: String? = null
    }

    private class FakeGateway : TvProviderGateway {
        var channelFailure: Throwable? = null
        val channels = mutableSetOf<Long>()
        val logos = mutableListOf<String?>()
        val previews = mutableMapOf<Long, String>()
        val previewChannels = mutableMapOf<Long, Long>()
        val watchNext = mutableMapOf<Long, String>()
        val deletedPreviews = mutableListOf<Long>()
        val previewExistsQueries = mutableListOf<Long>()
        var publishChannelCalls = 0
        var publishPreviewCalls = 0
        var publishWatchNextCalls = 0
        private var nextId = 100L

        override fun channelExists(id: Long) = id in channels

        override fun publishChannel(displayName: String, logoUri: String?): Long {
            publishChannelCalls++
            channelFailure?.let { throw it }
            logos += logoUri
            return nextId++.also { channels += it }
        }

        override fun previewExists(id: Long): Boolean {
            previewExistsQueries += id
            return id in previews
        }

        override fun publishPreview(channelId: Long, program: TvProgramFields): Long {
            publishPreviewCalls++
            return nextId++.also {
                previews[it] = program.stableKey
                previewChannels[it] = channelId
            }
        }

        override fun updatePreview(id: Long, channelId: Long, program: TvProgramFields) {
            previews[id] = program.stableKey
            previewChannels[id] = channelId
        }

        override fun deletePreview(id: Long) {
            deletedPreviews += id
            previews.remove(id)
            previewChannels.remove(id)
        }

        override fun watchNextExists(id: Long) = id in watchNext

        override fun publishWatchNext(program: TvProgramFields): Long {
            publishWatchNextCalls++
            return nextId++.also { watchNext[it] = program.stableKey }
        }

        override fun updateWatchNext(id: Long, program: TvProgramFields) {
            watchNext[id] = program.stableKey
        }

        override fun deleteWatchNext(id: Long) {
            watchNext.remove(id)
        }
    }
}
