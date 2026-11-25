package com.kino.puber.data.cache

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.util.FakePayloadStore
import com.kino.puber.util.MainDispatcherExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ContentCacheRepositoryTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension(StandardTestDispatcher())

    private val store = FakePayloadStore()
    private var now = 1_000_000L
    private val subject = ContentCacheRepository(store = store, clock = { now })

    @Test
    fun sparseListResponseDoesNotEraseFieldsLoadedByDetails() = runTest {
        val details = item(7).copy(plot = "Full plot", year = 2024)
        subject.observeItemDetails(7) { details }.toList()

        val emissions = subject.observeItems("home", CacheTtl.HomeSection) {
            listOf(item(7))
        }.toList()

        assertEquals(details, (emissions.single() as Cached.Value).value.single())
    }

    @Test
    fun detailsResponseDoesNotEraseCardFieldsItOmits() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) {
            listOf(item(7).copy(inWatchlist = true, ratingPercentage = 80))
        }.toList()

        val emissions = subject.observeItemDetails(7) {
            item(7).copy(plot = "Full plot")
        }.toList()

        assertEquals(
            item(7).copy(plot = "Full plot", inWatchlist = true, ratingPercentage = 80),
            (emissions.single() as Cached.Value).value,
        )
    }

    @Test
    fun responseFromBeforeAStoreWipeCannotRepopulateItemRecords() = runTest {
        val result = runCatching {
            subject.observeItems("home", CacheTtl.HomeSection) {
                store.clear()
                listOf(item(7))
            }.toList()
        }

        assertTrue(result.isFailure)
        assertNull(store.read(ContentCacheRepository.itemKey(7)))
    }

    @Test
    fun differentQueriesReadTheSameMergedItemRecord() = runTest {
        subject.observeItems("first", CacheTtl.HomeSection) {
            listOf(item(7).copy(year = 2024))
        }.toList()
        subject.observeItems("second", CacheTtl.HomeSection) {
            listOf(item(7).copy(plot = "Plot"))
        }.toList()

        val emissions = subject.observeItems("first", CacheTtl.HomeSection) {
            error("fresh query must come from cache")
        }.toList()

        assertEquals(
            item(7).copy(year = 2024, plot = "Plot"),
            (emissions.single() as Cached.Value).value.single(),
        )
    }

    @Test
    fun aFreshSectionPageIsServedWithoutTouchingTheLoader() = runTest {
        var loads = 0
        subject.sectionPage("s", watchStateVersion = 1L) { loads++; page(1) }.toList()

        now += 1.minutes.inWholeMilliseconds
        val emissions = subject.sectionPage("s", watchStateVersion = 1L) { loads++; page(2) }.toList()

        assertEquals(1, loads)
        assertEquals(listOf(1), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aStaleSectionPageEmitsTheStoredValueThenTheFreshOne() = runTest {
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()
        now += 31.minutes.inWholeMilliseconds

        val emissions = subject.sectionPage("s", watchStateVersion = 1L) { page(2) }.toList()

        assertEquals(listOf(1, 2), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aMovedWatchStateVersionRevalidatesAPageThatIsStillFresh() = runTest {
        // A filtered page is baked against an index version, so a page that is fresh by the clock
        // can still be wrong. The stored value is kept and drawn first — the refresh follows it.
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()

        val emissions = subject.sectionPage("s", watchStateVersion = 2L) { page(2) }.toList()

        assertEquals(listOf(1, 2), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aMovedWatchStateVersionForcesOnlyTheFirstReadAfterTheMove() = runTest {
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()
        subject.sectionPage("s", watchStateVersion = 2L) { page(2) }.toList()

        var loads = 0
        subject.sectionPage("s", watchStateVersion = 2L) { loads++; page(3) }.toList()

        assertEquals(0, loads)
    }

    @Test
    fun aMovedWatchStateVersionRevalidatesEverySectionKeyNotJustTheFirstAsked() = runTest {
        // A catalogue tab renders several sections at once. A move must not be consumed by
        // whichever one happens to ask first, leaving the rest to serve stale-by-index pages.
        subject.sectionPage("a", watchStateVersion = 1L) { page(1) }.toList()
        subject.sectionPage("b", watchStateVersion = 1L) { page(11) }.toList()

        val emissionsA = subject.sectionPage("a", watchStateVersion = 2L) { page(2) }.toList()
        val emissionsB = subject.sectionPage("b", watchStateVersion = 2L) { page(12) }.toList()

        assertEquals(listOf(1, 2), emissionsA.map { (it as Cached.Value).value.items.single().id })
        assertEquals(listOf(11, 12), emissionsB.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun buildingASectionPageFlowWithoutCollectingItDoesNotConsumeTheMove() = runTest {
        // The version check has to run when the flow is collected, not when sectionPage() is called
        // — building a flow and dropping it is not a read, and must not burn the one forced read.
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()

        subject.sectionPage("s", watchStateVersion = 2L) { page(2) } // built, never collected

        val emissions = subject.sectionPage("s", watchStateVersion = 2L) { page(3) }.toList()

        assertEquals(listOf(1, 3), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aSectionPagePastTheHardCeilingCountsAsAbsent() = runTest {
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()
        now += 8.days.inWholeMilliseconds

        val emissions = subject.sectionPage("s", watchStateVersion = 1L) { page(2) }.toList()

        assertEquals(listOf(2), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun theWatchlistIsStoredAndServedAsAList() = runTest {
        subject.watchlist { listOf(item(7)) }.toList()

        val emissions = subject.watchlist { listOf(item(8)) }.toList()

        assertEquals(listOf(7), (emissions.single() as Cached.Value).value.map(Item::id))
    }

    @Test
    fun theHistoryFirstPageRevalidatesAfterItsShortTtl() = runTest {
        subject.historyFirstPage { historyPage(1) }.toList()
        now += 3.minutes.inWholeMilliseconds

        val emissions = subject.historyFirstPage { historyPage(2) }.toList()

        assertEquals(2, emissions.size)
    }

    @Test
    fun aQueryReferencingAnUnreadableCardPayloadRepairsItselfInsteadOfFailing() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7), item(8)) }.toList()
        // What an app update that changes the Item model leaves behind: the list still decodes, the
        // shared card payload it names no longer does. Failing the whole list here would take the row
        // down inside a collector the screens only log, on this launch and every one after it.
        store.write(ContentCacheRepository.itemKey(8), "{\"item\":", updatedAt = now)

        val restarted = ContentCacheRepository(store = store, clock = { now })
        val emissions = restarted.observeItems("home", CacheTtl.HomeSection) {
            listOf(item(7), item(8))
        }.toList()

        assertEquals(
            listOf(listOf(7), listOf(7, 8)),
            emissions.map { (it as Cached.Value).value.map(Item::id) },
        )
    }

    @Test
    fun listWhoseItemRowsWereSweptRefetchesThem() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7), item(8)) }.toList()
        // Only the list row is newer than the item rows, so a cap of one keeps it and drops them.
        store.touch(ContentCacheRepository.queryKey("home"), now + 1)
        store.sweep(olderThan = 0, keepNewest = 1)

        var loads = 0
        val restarted = ContentCacheRepository(store = store, clock = { now })
        val emissions = restarted.observeItems("home", CacheTtl.HomeSection) {
            loads += 1
            listOf(item(7), item(8))
        }.toList()

        assertEquals(1, loads)
        assertEquals(listOf(7, 8), (emissions.last() as Cached.Value).value.map(Item::id))
    }

    @Test
    fun detailsOverACardOnlyRecordReportTheFailureInsteadOfEmittingNothing() = runTest {
        // Browsing home caches the card; opening the title offline has nothing to draw from it, so a
        // quiet RefreshFailed would leave the details screen spinning with no error and no retry.
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7)) }.toList()

        assertThrows<IllegalStateException> {
            subject.observeItemDetails(7) { error("offline") }.toList()
        }
    }

    @Test
    fun aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow() = runTest {
        subject.observeItemDetails(7) { series(7) }.toList()
        val fullLength = store.read(ContentCacheRepository.itemKey(7))!!.payload.length
        assertTrue(fullLength > ROW_LIMIT)
        store.maxPayloadLength = ROW_LIMIT

        subject.mergeItems(listOf(item(7).copy(title = "Merged")))

        val row = store.read(ContentCacheRepository.itemKey(7))
        assertNotNull(row)
        assertTrue(row!!.payload.length <= ROW_LIMIT)
        val record = Json.parseToJsonElement(row.payload).jsonObject
        val stored = record.getValue("item").jsonObject
        assertEquals("Merged", stored.getValue("title").jsonPrimitive.content)
        assertFalse(stored.containsKey("videos"))
        assertFalse(record.containsKey("detailsUpdatedAt"))
        val seasons = stored.getValue("seasons").jsonArray
        assertEquals(SEASONS, seasons.size)
        seasons.forEach { season ->
            val fields = season.jsonObject
            assertTrue(fields.containsKey("id") && fields.containsKey("number") && fields.containsKey("title"))
            assertFalse(fields.containsKey("episodes"))
        }
    }

    @Test
    fun anOversizedDetailsRecordIsStillServedInFullInTheSameSession() = runTest {
        subject.observeItemDetails(7) { series(7) }.toList()
        store.maxPayloadLength = ROW_LIMIT
        subject.mergeItems(listOf(item(7).copy(title = "Merged")))

        val emissions = subject.observeItemDetails(7) { error("memory") }.toList()

        val served = (emissions.single() as Cached.Value).value
        assertEquals(EPISODES, served.seasons!!.first().episodes!!.size)
        assertTrue(served.videos!!.isNotEmpty())
    }

    @Test
    fun aCardOnlyRowServesTheListAfterARestartWithoutTheLoader() = runTest {
        subject.observeItemDetails(7) { series(7) }.toList()
        store.maxPayloadLength = ROW_LIMIT
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7).copy(title = "Merged")) }.toList()

        var loads = 0
        val restarted = ContentCacheRepository(store = store, clock = { now })
        val emissions = restarted.observeItems("home", CacheTtl.HomeSection) {
            loads += 1
            listOf(item(7))
        }.toList()

        assertEquals(0, loads)
        val card = (emissions.last() as Cached.Value).value.single()
        assertEquals("Merged", card.title)
        assertEquals(SEASONS, card.seasons?.size)
    }

    @Test
    fun detailsOverACardOnlyRowAfterARestartCallTheLoaderFirst() = runTest {
        subject.observeItemDetails(7) { series(7) }.toList()
        store.maxPayloadLength = ROW_LIMIT
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7)) }.toList()

        val restarted = ContentCacheRepository(store = store, clock = { now })
        val emissions = restarted.observeItemDetails(7) { series(7) }.toList()

        assertEquals(listOf(series(7)), emissions.map { (it as Cached.Value).value })
    }

    @Test
    fun aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart() = runTest {
        subject.observeItemDetails(7) { series(7) }.toList()
        store.maxPayloadLength = ROW_LIMIT
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7).copy(title = "Merged")) }.toList()
        now += CacheTtl.HomeSection.inWholeMilliseconds + 1

        val restarted = ContentCacheRepository(store = store, clock = { now })
        val emissions = mutableListOf<Cached<List<Item>>>()
        runCatching {
            restarted.observeItems("home", CacheTtl.HomeSection) { error("offline") }.collect { emissions += it }
        }

        val first = emissions.firstOrNull() as? Cached.Value
        assertEquals(listOf("Merged"), first?.value?.map(Item::title))
    }

    @Test
    fun markingACardOnlyRecordStaleStillMakesDetailsLoadFirst() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7)) }.toList()
        subject.markItemDetailsStale(7)

        val emissions = subject.observeItemDetails(7) { series(7) }.toList()

        assertEquals(listOf(series(7)), emissions.map { (it as Cached.Value).value })
    }

    @Test
    fun markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow() = runTest {
        subject.observeItemDetails(7) { series(7) }.toList()
        store.maxPayloadLength = ROW_LIMIT

        subject.markItemDetailsStale(7)

        val row = store.read(ContentCacheRepository.itemKey(7))
        assertNotNull(row)
        val record = Json.parseToJsonElement(row!!.payload).jsonObject
        assertFalse(record.containsKey("detailsUpdatedAt"))
        assertFalse(record.getValue("item").jsonObject.getValue("seasons").jsonArray.first().jsonObject
            .containsKey("episodes"))
    }

    @Test
    fun aPayloadWrittenByAnIncompatibleBuildIsDroppedAndFetchedAgain() = runTest {
        val serializer = ListSerializer(String.serializer())
        subject.getPayload("genres", serializer, 10.minutes) { listOf("Drama") }
        // A row whose JSON is not the payload's shape: the feed must drop it on decode and fetch
        // again, or every later read fails on the same row.
        store.write(ContentCacheRepository.payloadKey("genres"), "\"not a genre list\"", updatedAt = now)

        val restarted = ContentCacheRepository(store = store, clock = { now })
        val value = restarted.getPayload("genres", serializer, 10.minutes) { listOf("Comedy") }

        assertEquals(listOf("Comedy"), value)
    }

    @Test
    fun firstUseLeavesLegacyRowsToTheMigration() = runTest {
        val legacyKeys = listOf(
            "home:hot",
            "item:7",
            "similar:7",
            "section:popular",
            "watchlist:subscribed",
            "history:1",
        )
        legacyKeys.forEach { key -> store.write(key, "legacy", updatedAt = now) }
        store.write("unrelated", "keep", updatedAt = now)

        subject.getPayload("probe", String.serializer(), 10.minutes) { "fresh" }

        legacyKeys.forEach { key -> assertEquals("legacy", store.read(key)?.payload, key) }
        assertEquals("keep", store.read("unrelated")?.payload)
        assertTrue(store.removedPrefixes.isEmpty())
    }

    @Test
    fun anInvalidationCrossingADetailsWriteIsNotUndoneByIt() = runTest {
        subject.observeItemDetails(7) { item(7).copy(plot = "Fresh") }.toList()
        // The bookmark toggle lands while the revalidation's row is already in the database but its
        // writer has not resumed. What it writes says "these details are no longer to be trusted",
        // and the write it crossed must not put freshness back.
        store.onWrite = { subject.invalidateItemDetails(7) }
        subject.observeItemDetails(7, force = true) { item(7).copy(plot = "In flight") }.toList()

        var loads = 0
        val emissions = subject.observeItemDetails(7) {
            loads++
            item(7).copy(plot = "Reloaded")
        }.toList()

        assertEquals(1, loads)
        assertEquals("Reloaded", (emissions.last() as Cached.Value).value.plot)
    }

    @Test
    fun aDetailsWriteThatOutlandsAnInvalidationIsStillTakenBackOut() = runTest {
        subject.observeItemDetails(7) { item(7).copy(plot = "Fresh") }.toList()
        // The other ordering: the toggle's row lands first and the revalidation overwrites it, so
        // the record left in the database claims details the toggle has just discarded.
        store.onBeforeWrite = { subject.invalidateItemDetails(7) }
        subject.observeItemDetails(7, force = true) { item(7).copy(plot = "In flight") }.toList()

        var loads = 0
        subject.observeItemDetails(7) {
            loads++
            item(7).copy(plot = "Reloaded")
        }.toList()

        assertEquals(1, loads)
    }

    @Test
    fun aListServedFromTheMemoryTierReturnsTheSameDecodedCards() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7), item(8)) }.toList()
        val first = subject.observeItems("home", CacheTtl.HomeSection) { error("fresh") }.toList()
        val readsAfterFirst = store.readCount

        val second = subject.observeItems("home", CacheTtl.HomeSection) { error("fresh") }.toList()

        val before = (first.single() as Cached.Value).value
        val after = (second.single() as Cached.Value).value
        assertEquals(before.size, after.size)
        before.indices.forEach { index -> assertSame(before[index], after[index]) }
        assertEquals(readsAfterFirst, store.readCount)
    }

    @Test
    fun detailsServedFromTheMemoryTierAreNotDecodedAgain() = runTest {
        subject.observeItemDetails(7) { item(7).copy(plot = "Plot") }.toList()
        val first = subject.observeItemDetails(7) { error("fresh") }.toList()
        val readsAfterFirst = store.readCount

        val second = subject.observeItemDetails(7) { error("fresh") }.toList()

        assertSame((first.single() as Cached.Value).value, (second.single() as Cached.Value).value)
        assertEquals(readsAfterFirst, store.readCount)
    }

    @Test
    fun aListOnlyInRoomReadsEachRowExactlyOnce() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7), item(8)) }.toList()
        val reads = mutableMapOf<String, Int>()
        store.onRead = { key -> reads.merge(key, 1, Int::plus) }

        val restarted = ContentCacheRepository(store = store, clock = { now })
        restarted.observeItems("home", CacheTtl.HomeSection) { error("fresh") }.toList()

        val expected = listOf(
            ContentCacheRepository.queryKey("home"),
            ContentCacheRepository.itemKey(7),
            ContentCacheRepository.itemKey(8),
        )
        assertEquals(expected.associateWith { 1 }, reads)
    }

    @Test
    fun detailsOnlyInRoomReadTheItemRowExactlyOnce() = runTest {
        subject.observeItemDetails(7) { item(7).copy(plot = "Plot") }.toList()
        val reads = mutableMapOf<String, Int>()
        store.onRead = { key -> reads.merge(key, 1, Int::plus) }

        val restarted = ContentCacheRepository(store = store, clock = { now })
        restarted.observeItemDetails(7) { error("fresh") }.toList()

        assertEquals(mapOf(ContentCacheRepository.itemKey(7) to 1), reads)
    }

    @Test
    fun aPayloadInTheMemoryTierIsNotDecodedAgain() = runTest {
        val loaded = CountingSerializer()
        subject.getPayload("genres", loaded, 10.minutes) { listOf("Drama") }
        assertEquals(0, loaded.deserializations)

        val fromRoom = CountingSerializer()
        val restarted = ContentCacheRepository(store = store, clock = { now })
        restarted.getPayload("genres", fromRoom, 10.minutes) { error("fresh") }
        restarted.getPayload("genres", fromRoom, 10.minutes) { error("fresh") }

        assertEquals(1, fromRoom.deserializations)
    }

    @Test
    fun aStaleListRefetchedAfterARoomReadStillRevalidates() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7)) }.toList()
        now += CacheTtl.HomeSection.inWholeMilliseconds + 1

        val restarted = ContentCacheRepository(store = store, clock = { now })
        val emissions = restarted.observeItems("home", CacheTtl.HomeSection) { listOf(item(8)) }.toList()

        assertEquals(
            listOf(listOf(7), listOf(8)),
            emissions.map { (it as Cached.Value).value.map(Item::id) },
        )
    }

    @Test
    fun storedRecordsArePlainJsonUnderTheV2Namespace() = runTest {
        subject.observeItems("list", CacheTtl.HomeSection) { listOf(item(7)) }.toList()
        subject.observeItemPage("page", CacheTtl.HomeSection) { page(8) }.toList()
        subject.historyFirstPage { historyPage(9) }.toList()
        subject.getPayload("genres", ListSerializer(String.serializer()), 10.minutes) { listOf("Drama") }

        val keys = listOf(
            ContentCacheRepository.itemKey(7),
            ContentCacheRepository.queryKey("list"),
            ContentCacheRepository.queryKey("page"),
            ContentCacheRepository.queryKey(CacheKeys.historyPage(1)),
            ContentCacheRepository.payloadKey("genres"),
        )
        keys.forEach { key -> assertTrue(key.startsWith("content:v2:"), key) }
        val rows = keys.map { key -> Json.parseToJsonElement(store.read(key)!!.payload) }

        assertEquals(item(7), Json.decodeFromJsonElement(Item.serializer(), rows[0].jsonObject.getValue("item")))
        assertEquals(listOf(7), rows[1].jsonObject.getValue("itemIds").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(listOf(8), rows[2].jsonObject.getValue("itemIds").jsonArray.map { it.jsonPrimitive.int })
        val historyEntry = rows[3].jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals(9, historyEntry.getValue("itemId").jsonPrimitive.int)
        assertEquals(listOf("Drama"), Json.decodeFromJsonElement(ListSerializer(String.serializer()), rows[4]))
    }

    @Test
    fun v1RowsAreIgnoredAndRefetchedIntoV2() = runTest {
        val v1 = mapOf(
            "content:v1:item:7" to v1ItemRecord(item(7), detailsUpdatedAt = null),
            "content:v1:item:8" to v1ItemRecord(item(8), detailsUpdatedAt = now),
            "content:v1:query:home" to """{"itemIds":[7]}""",
            "content:v1:payload:genres" to """["Drama"]""",
        )
        v1.forEach { (key, record) ->
            store.write(key, Json.encodeToString(String.serializer(), record), updatedAt = now)
        }
        val genres = ListSerializer(String.serializer())

        val list = subject.observeItems("home", CacheTtl.HomeSection) {
            listOf(item(7).copy(title = "Loaded"))
        }.toList()
        val details = subject.observeItemDetails(8) { item(8).copy(title = "Loaded") }.toList()
        val payload = subject.getPayload("genres", genres, 10.minutes) { listOf("Comedy") }

        assertEquals(listOf("Loaded"), list.map { (it as Cached.Value).value.single().title })
        assertEquals(listOf("Loaded"), details.map { (it as Cached.Value).value.title })
        assertEquals(listOf("Comedy"), payload)
        assertTrue(store.read(ContentCacheRepository.queryKey("home"))!!.payload.startsWith("{"))
        v1.forEach { (key, record) ->
            assertEquals(Json.encodeToString(String.serializer(), record), store.read(key)?.payload, key)
        }
    }

    @Test
    fun itemTierHoldsSixteenMegabytesOfEncodedJson() = runTest {
        val plot = "p".repeat(BIG_PLOT_CHARS)
        val items = (1..BIG_ITEM_COUNT).map { id -> item(id).copy(plot = plot) }
        subject.observeItems("big", CacheTtl.HomeSection) { items }.toList()
        val reads = mutableListOf<String>()
        store.onRead = { key -> reads += key }

        val held = subject.observeItems("big", CacheTtl.HomeSection) { error("fresh") }.toList()
        val again = subject.observeItems("big", CacheTtl.HomeSection) { error("fresh") }.toList()

        assertEquals(emptyList<String>(), reads)
        val before = (held.single() as Cached.Value).value
        val after = (again.single() as Cached.Value).value
        before.indices.forEach { index -> assertSame(before[index], after[index]) }

        subject.mergeItems(listOf(item(BIG_ITEM_COUNT + 1).copy(plot = plot)))
        reads.clear()
        subject.observeItems("big", CacheTtl.HomeSection) { error("fresh") }.toList()

        assertEquals(listOf(ContentCacheRepository.itemKey(1)), reads)
    }

    @Test
    fun clearDropsEveryItemFromMemory() = runTest {
        subject.observeItemDetails(7) { item(7).copy(plot = "Before") }.toList()
        subject.clear()

        val emissions = subject.observeItemDetails(7) { item(7).copy(plot = "After") }.toList()

        assertEquals(listOf("After"), emissions.map { (it as Cached.Value).value.plot })
    }

    @Test
    fun clearDropsEveryListFromMemory() = runTest {
        subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(7)) }.toList()
        subject.clear()

        val emissions = subject.observeItems("home", CacheTtl.HomeSection) { listOf(item(8)) }.toList()

        assertEquals(listOf(listOf(8)), emissions.map { (it as Cached.Value).value.map(Item::id) })
    }

    @Test
    fun clearDropsEveryPageFromMemory() = runTest {
        subject.observeItemPage("page", CacheTtl.HomeSection) { page(7) }.toList()
        subject.clear()

        val emissions = subject.observeItemPage("page", CacheTtl.HomeSection) { page(8) }.toList()

        assertEquals(listOf(8), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun clearDropsEveryHistoryPageFromMemory() = runTest {
        subject.historyFirstPage { historyPage(7) }.toList()
        subject.clear()

        val emissions = subject.historyFirstPage { historyPage(8) }.toList()

        assertEquals(listOf(8), emissions.map { (it as Cached.Value).value.items.single().item.id })
    }

    @Test
    fun clearDropsEveryPayloadFromMemory() = runTest {
        val genres = ListSerializer(String.serializer())
        subject.getPayload("genres", genres, 10.minutes) { listOf("Before") }
        subject.clear()

        val value = subject.getPayload("genres", genres, 10.minutes) { listOf("After") }

        assertEquals(listOf("After"), value)
    }

    @Test
    fun invalidateQueryOnAFreshRepositoryRemovesThePayloadRow() = runTest {
        val genres = ListSerializer(String.serializer())
        subject.getPayload("genres", genres, 10.minutes) { listOf("Before") }

        val restarted = ContentCacheRepository(store = store, clock = { now })
        restarted.invalidateQuery("genres")

        assertNull(store.read(ContentCacheRepository.payloadKey("genres")))
        assertEquals(listOf("After"), restarted.getPayload("genres", genres, 10.minutes) { listOf("After") })
    }

    @Test
    fun flowsDecodeAndLoadOnTheInjectedDispatcher() = runTest {
        withDecodeThread { threads, decode ->
            val genres = CountingSerializer(threads)
            val flows = listOf<Pair<String, (ContentCacheRepository) -> Flow<*>>>(
                "observeItems" to { repo ->
                    repo.observeItems("l", CacheTtl.HomeSection) { threads += currentThread(); listOf(item(1)) }
                },
                "observeItemPage" to { repo ->
                    repo.observeItemPage("p", CacheTtl.HomeSection) { threads += currentThread(); page(2) }
                },
                "historyFirstPage" to { repo ->
                    repo.historyFirstPage { threads += currentThread(); historyPage(3) }
                },
                "observeItemDetails" to { repo ->
                    repo.observeItemDetails(4) { threads += currentThread(); item(4) }
                },
                "observePayload" to { repo ->
                    repo.observePayload("g", genres, 10.minutes) { threads += currentThread(); listOf("Drama") }
                },
            )
            flows.forEach { (name, flowOf) ->
                // Cold: the loader and the store write run; warm: a fresh repository reads and decodes.
                listOf(false, true).forEach { warm ->
                    threads.clear()
                    store.onRead = { threads += currentThread() }
                    flowOf(ContentCacheRepository(store, { now }, decodeDispatcher = decode)).toList()
                    assertTrue(threads.isNotEmpty(), "$name warm=$warm")
                    assertEquals(setOf(DECODE_THREAD), threads.toSet(), "$name warm=$warm")
                }
            }
        }
    }

    @Test
    fun getPayloadAndOneShotWritesRunOnTheInjectedDispatcher() = runTest {
        withDecodeThread { threads, decode ->
            val genres = CountingSerializer(threads)
            val repo = { ContentCacheRepository(store, { now }, decodeDispatcher = decode) }
            repo().getPayload("g", genres, 10.minutes) { threads += currentThread(); listOf("Drama") }
            repo().getPayload("g", genres, 10.minutes) { error("fresh") }
            store.onRead = { threads += currentThread() }
            repo().mergeItems(listOf(item(5)))
            repo().markItemDetailsStale(5)
            repo().invalidateItemDetails(5)

            assertTrue(threads.size >= 5, threads.toString())
            assertEquals(setOf(DECODE_THREAD), threads.toSet())
        }
    }

    private suspend fun withDecodeThread(
        block: suspend (threads: MutableList<String>, decode: CoroutineDispatcher) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, DECODE_THREAD) }
        try {
            block(CopyOnWriteArrayList(), executor.asCoroutineDispatcher())
        } finally {
            executor.shutdown()
        }
    }

    private fun v1ItemRecord(item: Item, detailsUpdatedAt: Long?): String {
        val itemJson = Json.encodeToString(Item.serializer(), item)
        val details = detailsUpdatedAt?.let { ""","detailsUpdatedAt":$it""" }.orEmpty()
        return """{"item":$itemJson,"cardUpdatedAt":$now$details}"""
    }

    private fun currentThread(): String = Thread.currentThread().name.substringBefore(" @")

    private class CountingSerializer(
        private val threads: MutableList<String>? = null,
        private val delegate: KSerializer<List<String>> = ListSerializer(String.serializer()),
    ) : KSerializer<List<String>> {
        var deserializations = 0
            private set

        override val descriptor: SerialDescriptor = delegate.descriptor

        override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)

        override fun deserialize(decoder: Decoder): List<String> {
            deserializations += 1
            threads?.add(Thread.currentThread().name.substringBefore(" @"))
            return delegate.deserialize(decoder)
        }
    }

    private companion object {
        const val DECODE_THREAD = "decode"
        const val BIG_PLOT_CHARS = 1_000_000
        const val BIG_ITEM_COUNT = 16
        const val ROW_LIMIT = 4_000
        const val SEASONS = 3
        const val EPISODES = 20
    }

    private fun series(id: Int) = item(id).copy(
        type = ItemType.SERIAL,
        seasons = (1..SEASONS).map { season ->
            Season(
                id = season,
                number = season,
                title = "Season $season",
                episodes = (1..EPISODES).map { episode ->
                    Episode(
                        id = season * 100 + episode,
                        number = episode,
                        title = "Episode $episode",
                        files = listOf(VideoFile(quality = "1080p", codec = "h264")),
                    )
                },
            )
        },
        videos = listOf(Video(id = 1, title = "Extra", files = listOf(VideoFile(quality = "720p")))),
    )

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun page(id: Int) = PaginatedResponse(
        items = listOf(item(id)),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )

    private fun historyPage(id: Int) = PaginatedResponse(
        items = listOf(History(item = item(id))),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )
}
