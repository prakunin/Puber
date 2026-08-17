# Content-list stale-while-revalidate cache — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make catalogue sections, the watching list and history render their first page from local storage immediately and revalidate in the background, so switching tabs stops showing seconds of skeletons.

**Architecture:** A new `ContentPageCache` singleton wraps three `CachedFeed` instances over the existing `PersistentPayloadStore`. The three screen interactors — all `scopedOf`, hence unable to hold a feed themselves — gain `observe*` methods returning `Flow<Cached<…>>`. `ContentListPagingVM` splits its first-page load so the same publication path runs for the cached emission and the fresh one; `FavoriteVM` and `HistoryVM` collect their own flows.

**Tech Stack:** Kotlin, Coroutines/Flow, kotlinx.serialization, Koin, Room 3, Compose TV Material3. Tests: JUnit 6 (`org.junit.jupiter`), MockK, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-08-17-content-list-swr-cache-design.md`

## Global Constraints

- JDK 21 runs the compilers (`Versions.ToolchainJavaVersion`); bytecode target stays 17 (`Versions.JvmTargetVersion`). Never hardcode either — read from `buildSrc/src/main/kotlin/Versions.kt`.
- Dependency versions come from `gradle/libs.versions.toml`. No new dependencies are needed by this plan.
- Use `./gradlew` in the main checkout; use `./tools/agentw <task>` in any git worktree.
- `./gradlew :app:detektAll` must pass. **The Detekt baseline is empty and must stay empty** — fix the finding, or record a deliberate exception as a `@Suppress` at the declaration with a comment saying why.
- File content and commit messages are English.
- Do not add a `Co-Authored-By` trailer to commits in this repository.
- Unit tests run with `./gradlew testDevDebugUnitTest`; a single class with `--tests "<fqcn>"`.
- Keep credentials, raw authenticated responses and broad UI/log dumps out of Git and out of task evidence.

## File Structure

**Created:**
- `app/src/main/java/com/kino/puber/data/cache/ContentPageCache.kt` — the singleton owning the three feeds and the watch-state version rule. Knows about keys, TTLs and serializers; knows nothing about screens.
- `app/src/test/kotlin/com/kino/puber/data/cache/ContentPageCacheTest.kt`
- `app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt` — the in-memory `PersistentPayloadStore` double, promoted out of `CachedFeedTest` so both cache tests share one.

**Modified:**
- `app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt` — three prefixes and their builders, three TTLs.
- `app/src/main/java/com/kino/puber/data/di/modules.kt` — register `ContentPageCache` in `repositoryModule`.
- `app/src/main/java/com/kino/puber/core/ui/PuberVM.kt` — one `@VisibleForTesting` state-sequence hook.
- `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt` — `observeFirstPage`, removal of the static `firstPageCache`.
- `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt` — `loadPage` split into `publish`, first page collected from the flow.
- `app/src/main/java/com/kino/puber/domain/interactor/favorites/FavoritesInteractor.kt` and `app/src/main/java/com/kino/puber/ui/feature/favorites/vm/FavoriteVM.kt`
- `app/src/main/java/com/kino/puber/domain/interactor/history/HistoryInteractor.kt` and `app/src/main/java/com/kino/puber/ui/feature/history/vm/HistoryVM.kt`
- Tests: `CachedFeedTest.kt`, `ContentListInteractorTest.kt`, `SectionVMTest.kt`, and the favorites/history test packages.

No screen Koin module changes are needed: `ContentListInteractor`, `FavoritesInteractor` and `HistoryInteractor` are all registered with `scopedOf`, which resolves new constructor parameters from the parent scope, and `ContentPageCache` is a global `single`.

---

### Task 1: `ContentPageCache`, keys and TTLs

Wired to nothing. Ends with a tested cache object that no production code calls yet.

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/cache/ContentPageCache.kt`
- Create: `app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt`
- Create: `app/src/test/kotlin/com/kino/puber/data/cache/ContentPageCacheTest.kt`
- Modify: `app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt`
- Modify: `app/src/main/java/com/kino/puber/data/di/modules.kt:75-100` (`repositoryModule`)
- Modify: `app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt:598` (drop the private fake, import the shared one)

**Interfaces:**
- Consumes: `CachedFeed`, `Cached`, `PersistentPayloadStore`, `CacheKeys`, `CacheTtl` — all existing.
- Produces:
  ```kotlin
  class ContentPageCache(
      store: PersistentPayloadStore,
      clock: () -> Long = System::currentTimeMillis,
  ) {
      fun sectionPage(
          key: String,
          watchStateVersion: Long,
          force: Boolean = false,
          loader: suspend () -> PaginatedResponse<Item>,
      ): Flow<Cached<PaginatedResponse<Item>>>

      fun watchlist(
          force: Boolean = false,
          loader: suspend () -> List<Item>,
      ): Flow<Cached<List<Item>>>

      fun historyFirstPage(
          force: Boolean = false,
          loader: suspend () -> PaginatedResponse<History>,
      ): Flow<Cached<PaginatedResponse<History>>>
  }
  ```
  `CacheKeys.section(id)`, `CacheKeys.watchlist()`, `CacheKeys.historyPage(page)`; `CacheTtl.CatalogueSection`, `CacheTtl.Watchlist`, `CacheTtl.HistoryPage`.

- [ ] **Step 1: Promote the fake payload store into a shared test util**

Create `app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt` by moving the class currently private inside `CachedFeedTest` (line 598) and making it public to the test source set:

```kotlin
package com.kino.puber.util

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload

/** In-memory [PersistentPayloadStore] for tests: same semantics, no Room. */
class FakePayloadStore : PersistentPayloadStore {

    private val rows = mutableMapOf<String, StoredPayload>()

    override var generation: Long = 0L
        private set

    override suspend fun read(key: String): StoredPayload? = rows[key]

    override suspend fun write(key: String, payload: String, updatedAt: Long) {
        rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
    }

    override suspend fun touch(key: String, updatedAt: Long) {
        rows[key]?.let { row -> rows[key] = row.copy(updatedAt = updatedAt) }
    }

    override suspend fun remove(key: String) {
        rows.remove(key)
    }

    override suspend fun removeByPrefix(prefix: String) {
        rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
    }

    override suspend fun clear() {
        rows.clear()
        generation += 1
    }
}
```

Then delete the private `FakePayloadStore` from `CachedFeedTest.kt` and add `import com.kino.puber.util.FakePayloadStore`. If the private class carries behaviour the version above lacks, keep that behaviour — the moved class must be a superset, never a rewrite.

- [ ] **Step 2: Run the existing cache suite to prove the move changed nothing**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.CachedFeedTest"`
Expected: PASS, same test count as before the move.

- [ ] **Step 3: Commit the move on its own**

```bash
git add app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt
git commit -m "Share the fake payload store between cache tests"
```

- [ ] **Step 4: Add the keys and TTLs**

In `app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt`, add to `CacheKeys`:

```kotlin
    const val SectionPrefix = "section:"
    const val WatchlistPrefix = "watchlist:"
    const val HistoryPrefix = "history:"

    fun section(id: String): String = SectionPrefix + id

    fun watchlist(): String = WatchlistPrefix + "subscribed"

    fun historyPage(page: Int): String = HistoryPrefix + page
```

and to `CacheTtl`:

```kotlin
    /** Catalogue rows move on the server's schedule, like the editorial rows on home. */
    val CatalogueSection: Duration = 30.minutes

    /** Both of these are rewritten by the user's own playback, so they revalidate quickly. */
    val Watchlist: Duration = 2.minutes
    val HistoryPage: Duration = 2.minutes
```

- [ ] **Step 5: Write the failing test for `ContentPageCache`**

Create `app/src/test/kotlin/com/kino/puber/data/cache/ContentPageCacheTest.kt`:

```kotlin
package com.kino.puber.data.cache

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.util.FakePayloadStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ContentPageCacheTest {

    private val store = FakePayloadStore()
    private var now = 1_000_000L
    private val subject = ContentPageCache(store = store, clock = { now })

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

    private fun item(id: Int) = Item(id = id, title = "Item $id")

    private fun page(id: Int) = PaginatedResponse(
        items = listOf(item(id)),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )

    private fun historyPage(id: Int) = PaginatedResponse(
        items = listOf(com.kino.puber.data.api.models.History(id = id)),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )
}
```

`Item` and `History` have many fields; construct them with whatever minimal set their declarations in `data/api/models/Models.kt` make required, exactly as `SectionVMTest` already does for `Item`.

- [ ] **Step 6: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.ContentPageCacheTest"`
Expected: FAIL — `Unresolved reference: ContentPageCache`.

- [ ] **Step 7: Implement `ContentPageCache`**

Create `app/src/main/java/com/kino/puber/data/cache/ContentPageCache.kt`:

```kotlin
package com.kino.puber.data.cache

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.repository.PersistentPayloadStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.atomic.AtomicLong

/**
 * The first page of every list surface outside home: catalogue sections, the watching list, the
 * first page of history.
 *
 * A singleton rather than a field on the interactors that use it. Those are all `scopedOf` and die
 * with their screen, and both things a [CachedFeed] carries — its memory tier and its in-flight
 * de-duplication — are per instance. Held by the screen, a feed would lose the tier on every tab
 * switch and would let two screens showing the same section issue two requests for one key.
 */
class ContentPageCache(
    store: PersistentPayloadStore,
    clock: () -> Long = System::currentTimeMillis,
) {

    private val sections = CachedFeed(
        store = store,
        serializer = PaginatedResponse.serializer(Item.serializer()),
        ttl = CacheTtl.CatalogueSection,
        keyPrefix = CacheKeys.SectionPrefix,
        clock = clock,
    )

    private val watchlist = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.Watchlist,
        keyPrefix = CacheKeys.WatchlistPrefix,
        clock = clock,
    )

    private val history = CachedFeed(
        store = store,
        serializer = PaginatedResponse.serializer(History.serializer()),
        ttl = CacheTtl.HistoryPage,
        keyPrefix = CacheKeys.HistoryPrefix,
        clock = clock,
    )

    /**
     * The watch-state index version the section feed was last read under.
     *
     * A stored page was filtered against one version of the index, so a move makes it wrong however
     * fresh the clock says it is. The version cannot go in the key — that would leave a dead row
     * behind on every write — so the move forces the next read instead, once, and the stored page is
     * still drawn first while that read is out.
     */
    private val seenWatchStateVersion = AtomicLong(Long.MIN_VALUE)

    fun sectionPage(
        key: String,
        watchStateVersion: Long,
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<Item>,
    ): Flow<Cached<PaginatedResponse<Item>>> {
        val indexMoved = seenWatchStateVersion.getAndSet(watchStateVersion) != watchStateVersion
        return sections.load(key = key, force = force || indexMoved, loader = loader)
    }

    fun watchlist(
        force: Boolean = false,
        loader: suspend () -> List<Item>,
    ): Flow<Cached<List<Item>>> = watchlist.load(key = CacheKeys.watchlist(), force = force, loader = loader)

    fun historyFirstPage(
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<History>,
    ): Flow<Cached<PaginatedResponse<History>>> =
        history.load(key = CacheKeys.historyPage(FIRST_PAGE), force = force, loader = loader)

    private companion object {
        const val FIRST_PAGE = 1
    }
}
```

Note the shadowing: the private `watchlist` feed and the public `watchlist(...)` function share a name. If the Kotlin compiler or Detekt objects, rename the field to `watchlistFeed` and leave the function name alone — the function is the API and the spec names it.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.ContentPageCacheTest"`
Expected: PASS, 7 tests.

- [ ] **Step 9: Register the singleton**

In `app/src/main/java/com/kino/puber/data/di/modules.kt`, inside `repositoryModule`, directly under the `single<PersistentPayloadStore>` line:

```kotlin
    single { ContentPageCache(store = get()) }
```

Add `import com.kino.puber.data.cache.ContentPageCache`.

- [ ] **Step 10: Compile and lint**

Run: `./gradlew :app:compileDevDebugKotlin :app:detektAll`
Expected: BUILD SUCCESSFUL, no Detekt findings.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/cache/ContentPageCache.kt app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt app/src/main/java/com/kino/puber/data/di/modules.kt app/src/test/kotlin/com/kino/puber/data/cache/ContentPageCacheTest.kt
git commit -m "Add a persistent cache for list first pages"
```

---

### Task 2: Catalogue sections and "show all"

The measured win lives here. `ShowAllVM` extends `ContentListPagingVM` and pages the same configs through the same interactor, so it inherits the change without an edit of its own.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/SectionVM.kt` (retry and refresh set the force flag)
- Modify: `app/src/main/java/com/kino/puber/core/ui/PuberVM.kt` (test hook)
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMTest.kt`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/contentlist/ContentListInteractorTest.kt`

**Interfaces:**
- Consumes: `ContentPageCache.sectionPage(key, watchStateVersion, force, loader)` from Task 1.
- Produces:
  ```kotlin
  // ContentListInteractor
  fun observeFirstPage(
      config: SectionConfig,
      force: Boolean = false,
  ): Flow<Cached<PaginatedResponse<Item>>>

  // PuberVM
  @VisibleForTesting
  internal val testStateFlow: Flow<ViewState>
  ```

- [ ] **Step 1: Add the state-sequence test hook**

In `app/src/main/java/com/kino/puber/core/ui/PuberVM.kt`, next to the existing `testStateValue` (around line 47):

```kotlin
    /**
     * Every state this view model publishes, not just the current one.
     *
     * `testStateValue` can only be sampled between suspension points, and the claim that matters for
     * a cached-then-fresh load is about the states *between* two samples: that no loading state
     * appears once content has been drawn. That is only observable as a sequence.
     */
    @VisibleForTesting
    internal val testStateFlow: Flow<ViewState>
        get() = mutableViewState
```

- [ ] **Step 2: Write the failing view-model tests**

Add to `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMTest.kt`:

```kotlin
    @Test
    fun firstPage_drawsTheCachedPageThenTheFreshOneWithNoLoadingInBetween() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1, 2),
        )
        val states = mutableListOf<SectionState>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.testStateFlow.collect(states::add)
        }
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(page(item(1)), isStale = true, updatedAt = 0L))
        testScheduler.advanceUntilIdle()
        emissions.emit(Cached.Value(page(item(2)), isStale = false, updatedAt = 0L))
        testScheduler.advanceUntilIdle()

        val firstContentAt = states.indexOfFirst { it is SectionState.Content }
        assertEquals(
            listOf(1, 2),
            states.filterIsInstance<SectionState.Content>().map { it.items.single().id },
        )
        assertEquals(
            emptyList<SectionState>(),
            states.drop(firstContentAt).filterIsInstance<SectionState.Loading>(),
        )
        collector.cancel()
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun firstPage_aFailedBackgroundRefreshLeavesTheCachedContentStanding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(page(item(1)), isStale = true, updatedAt = 0L))
        testScheduler.advanceUntilIdle()
        emissions.emit(Cached.RefreshFailed(IllegalStateException("network")))
        testScheduler.advanceUntilIdle()

        val state = vm.testStateValue
        assertEquals(listOf(1), (state as SectionState.Content).items.map { it.id })
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun firstPage_walkCountersResetBetweenTheCachedAndTheFreshEmission() = runTest {
        // Both emissions are first pages. Counted as one walk, two empty pages in a row would look
        // like four and the section would give up early.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        coEvery { interactor.loadPage(any(), page = 2) } returns emptyPage(current = 2, total = 9)
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(emptyPage(current = 1, total = 9), isStale = true, updatedAt = 0L))
        testScheduler.advanceUntilIdle()
        emissions.emit(Cached.Value(page(item(1), current = 1, total = 9), isStale = false, updatedAt = 0L))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1), (vm.testStateValue as SectionState.Content).items.map { it.id })
        vm.testCancelScope()
        paginator.close()
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id")
```

Reuse the existing `page(...)`, `emptyPage(...)`, `mapperFor(...)`, `createVM(...)` helpers already in this file; add `item(id)` only if the file does not already have an equivalent. Imports to add: `com.kino.puber.data.cache.Cached`, `kotlinx.coroutines.flow.MutableSharedFlow`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.contentlist.vm.SectionVMTest"`
Expected: FAIL — `observeFirstPage` and `testStateFlow` unresolved.

- [ ] **Step 4: Add `observeFirstPage` to the interactor**

In `ContentListInteractor.kt`: take `contentPageCache: ContentPageCache` as a new constructor parameter, extract the existing key construction into a private function, and add the observer. The `firstPageCache` branch inside `loadPage` and `dropFirstPageCacheIfWatchStateMoved` go away; `loadPage` becomes network-only for every page.

```kotlin
internal class ContentListInteractor(
    private val api: KinoPubApiClient,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val watchStateRepository: WatchStateRepository,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val contentPageCache: ContentPageCache,
) {

    /**
     * The first page of a section: whatever is stored is emitted at once, and the network is
     * consulted behind it when the entry is stale, when the watch-state index has moved, or when
     * [force] says the caller knows the server's answer has changed.
     */
    fun observeFirstPage(
        config: SectionConfig,
        force: Boolean = false,
    ): Flow<Cached<PaginatedResponse<Item>>> {
        val preferences = navigationPreferencesRepository.contentPreferences.value
        return contentPageCache.sectionPage(
            key = CacheKeys.section(cacheKey(config, preferences.showAnime, preferences.hideWatched)),
            watchStateVersion = watchStateRepository.version.value,
            force = force,
        ) {
            loadPage(config, page = FIRST_PAGE)
        }
    }

    suspend fun loadPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        if (config.shortcutTypes.isNotEmpty()) {
            return freshPagers
                .computeIfAbsent(config.id) { FreshSectionPager(api, config) }
                .loadPage(page)
        }
        val preferences = navigationPreferencesRepository.contentPreferences.value
        return fetchFilteredPage(config, page, preferences.showAnime, preferences.hideWatched)
    }

    private fun cacheKey(
        config: SectionConfig,
        showAnime: Boolean,
        hideWatched: Boolean,
    ): String = listOf(
        config.id,
        config.shortcut.orEmpty(),
        config.type,
        config.shortcutTypes.joinToString(separator = ",") { it.value },
        config.sort,
        config.quality,
        config.genre.orEmpty(),
        config.requiredGenreId,
        config.animeFilterMode,
        showAnime,
        hideWatched,
    ).joinToString(separator = "_")
```

`KinoPubConfig.CURRENT_API_DOMAIN` leaves the key deliberately: a domain switch goes through `ApiDomainInteractor.clearDomainSensitiveCaches`, which calls `store.clear()` — the whole table plus a generation bump that every `CachedFeed` already answers. Delete the now-unused import if nothing else in the file uses it.

Delete the `firstPageCache` and `cachedWatchStateVersion` members from the `companion object`, and `dropFirstPageCacheIfWatchStateMoved()` with its call site. Keep `invalidateFirstPageCache()`, reduced to what still has work to do:

```kotlin
    /** Drops the pagination cursors the fresh-section pagers hold. */
    fun invalidateFirstPageCache() {
        freshPagers.clear()
    }
```

Add `const val FIRST_PAGE = 1` to the companion object if the file does not already have one.

- [ ] **Step 5: Split `loadPage` in the paging view model**

In `ContentListPagingVM.kt`, replace `onLoadFirstPage`, `onLoadNextPage` and `loadPage` with:

```kotlin
    /**
     * Set by the callers that know the server's answer has changed — a retry, a return from details
     * with a content change, a display-setting flip. Consumed by the next first-page load, which
     * still draws the stored page first and merely guarantees the request behind it.
     */
    protected var forceNextFirstPage = false

    final override fun onLoadFirstPage() {
        val force = forceNextFirstPage
        forceNextFirstPage = false
        pagingLaunch(errorHandlerGeneral) {
            interactor.observeFirstPage(config, force = force).collect { cached ->
                when (cached) {
                    is Cached.Value -> publish(cached.value, isFirstPage = true)
                    // Nobody asked for this refresh and there is already content on screen, so a
                    // failure is not the user's problem: the stored page stands.
                    is Cached.RefreshFailed -> log(
                        cached.error,
                        "$logName ${config.id}: background refresh failed",
                    )
                }
            }
        }
    }

    final override fun onLoadNextPage(key: Item?) {
        pagingLaunch(errorHandlerPaging) { loadNextPage(page = currentPage + 1) }
    }

    private suspend fun loadNextPage(page: Int) {
        publish(interactor.loadPage(config, page), isFirstPage = false)
    }

    /**
     * @param isFirstPage a page-one publication — of which there are now two per load, the stored
     * one and the fresh one. Each starts its own walk, so the counters reset here rather than once
     * per load: counted together, two pages emptied by filtering would read as four.
     */
    private fun publish(response: PaginatedResponse<Item>, isFirstPage: Boolean) {
        if (isFirstPage) {
            currentPage = 0
            emptyPageChain = 0
            resumeRounds = 0
            publishedAnyItems = false
        }
        currentPage = response.pagination.current
        val serverHasMore = currentPage < response.pagination.total
        if (response.items.isEmpty()) {
            emptyPageChain += 1
        } else {
            emptyPageChain = 0
            resumeRounds = 0
        }
        val keepWalking = serverHasMore && emptyPageChain in 1..MAX_EMPTY_PAGE_CHAIN
        val budgetIsSpent = response.items.isEmpty() && serverHasMore && !keepWalking
        isFullDataNext = !serverHasMore
        if (response.items.isNotEmpty()) publishedAnyItems = true
        if (isFirstPage || (!publishedAnyItems && !keepWalking)) {
            replace(response.items, hasMorePages = keepWalking)
        } else {
            setNextPage(response.items, hasMorePages = keepWalking)
        }
        if (budgetIsSpent) resumeWalkAfterPause()
    }
```

`resumeWalkAfterPause()` keeps its body; its `loadPage(page = resumeFrom, isFirstPage = false)` call becomes `loadNextPage(page = resumeFrom)`. Add imports for `Cached` and `kotlinx.coroutines.flow.collect` as the compiler requires.

- [ ] **Step 6: Route the existing refresh signals through the force flag**

In `SectionVM.kt`, `refreshFirstPage()` and the `RetryClicked` branch now set the flag before restarting:

```kotlin
    fun refreshFirstPage() {
        forceNextFirstPage = true
        resetPaging()
    }
```

and in `onAction`, replace the bare `is CommonAction.RetryClicked -> resetPaging()` with `is CommonAction.RetryClicked -> refreshFirstPage()`. Make the same change in `ShowAllVM` if it has its own retry branch; if it delegates to the base class, nothing to do there.

- [ ] **Step 7: Run the view-model tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.contentlist.vm.SectionVMTest"`
Expected: PASS, including the three new tests.

- [ ] **Step 8: Fix the interactor suite for the removed static cache**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.contentlist.ContentListInteractorTest"`
Expected: FAIL where tests assert the old 3-minute first-page memoisation, and where the constructor now needs a fifth argument.

Update those tests: construct the interactor with `ContentPageCache(store = FakePayloadStore(), clock = { now })`, and move any assertion about "the second call did not hit the network" onto `observeFirstPage`, which is where that behaviour now lives. Tests covering filtering, the anime walk and pagination assertions stay exactly as they are — `loadPage` still does that work.

- [ ] **Step 9: Run the full unit suite, compile and lint**

Run: `./gradlew testDevDebugUnitTest :app:detektAll`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/SectionVM.kt app/src/main/java/com/kino/puber/core/ui/PuberVM.kt app/src/test/kotlin
git commit -m "Serve catalogue section first pages from the payload cache"
```

- [ ] **Step 11: Check it on the device**

Build and install the dev APK on the Fire TV following `AGENTS.md` and `.kent/commands/smoke-test.md`, then switch between Movies, Series, 4K and TV shows twice each. Expected: on the second visit to each tab, rows are drawn without a skeleton pass; D-pad focus does not jump when the fresh emission replaces the cached one. Numbers come later, in Task 5 — this step is a yes/no on regressions.

---

### Task 3: Watching

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/favorites/FavoritesInteractor.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/favorites/vm/FavoriteVM.kt`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/favorites/` and `app/src/test/kotlin/com/kino/puber/ui/feature/favorites/`

**Interfaces:**
- Consumes: `ContentPageCache.watchlist(force, loader)` from Task 1.
- Produces: `FavoritesInteractor.observeWatchlist(force: Boolean = false): Flow<Cached<List<Item>>>` — emitting the list **unsorted**, exactly as stored.

- [ ] **Step 1: Write the failing interactor test**

In the favorites test package:

```kotlin
    @Test
    fun theWatchlistIsCachedUnsortedSoTheOrderIsRecomputedOnEveryRead() = runTest {
        // Stored sorted, the recently-played order would freeze at write time — which is the one
        // thing this list exists to express.
        val api = mockk<KinoPubApiClient>()
        coEvery { api.getWatchingList(onlySubscribed = true) } returns
            Result.success(watchingListOf(item(1), item(2)))
        val order = mockk<RecentlyPlayedOrder>()
        every { order.sort(any()) } answers { firstArg<List<Item>>().sortedByDescending(Item::id) }
        val subject = FavoritesInteractor(
            api = api,
            itemDetailsRepository = mockk(relaxed = true),
            recentlyPlayedOrder = order,
            contentPageCache = ContentPageCache(store = store, clock = { now }),
        )

        subject.observeWatchlist().toList()
        val emissions = subject.observeWatchlist().toList()

        assertEquals(listOf(1, 2), (emissions.single() as Cached.Value).value.map(Item::id))
    }
```

Match the surrounding file's fixtures for `watchingListOf` and `item` — build them from the response type `getWatchingList` actually returns.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.favorites.*"`
Expected: FAIL — `observeWatchlist` unresolved.

- [ ] **Step 3: Implement the observer**

```kotlin
internal class FavoritesInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val recentlyPlayedOrder: RecentlyPlayedOrder,
    private val contentPageCache: ContentPageCache,
) {

    /**
     * The watching list, stored unsorted: [RecentlyPlayedOrder] runs on each emission instead, so a
     * cached list is ordered by what the index knows now rather than by what it knew at write time.
     */
    fun observeWatchlist(force: Boolean = false): Flow<Cached<List<Item>>> =
        contentPageCache.watchlist(force = force) {
            api.getWatchingList(onlySubscribed = true).getOrThrow().items.orEmpty()
        }

    fun sortByRecentlyPlayed(items: List<Item>): List<Item> = recentlyPlayedOrder.sort(items)
```

`getWatchlist()` stays for any caller that still wants a one-shot list; delete it only if nothing references it after Step 4.

- [ ] **Step 4: Collect it in the view model**

In `FavoriteVM.kt`, `loadData()` becomes:

```kotlin
    private fun loadData(force: Boolean = false) {
        launch {
            interactor.observeWatchlist(force = force).collect { cached ->
                when (cached) {
                    is Cached.Value -> publish(interactor.sortByRecentlyPlayed(cached.value))
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh the watching list")
                }
            }
        }
    }

    private suspend fun publish(items: List<Item>) {
        val selectedItem = items.firstOrNull()?.let { item -> interactor.getItemDetails(item.id) }
        updateViewState(
            favoriteItemUIMapper.mapToState(
                items = items,
                selectedItem = selectedItem,
            )
        )
    }
```

`RetryClicked` calls `loadData(force = true)`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.favorites.*" --tests "com.kino.puber.ui.feature.favorites.*"`
Expected: PASS.

- [ ] **Step 6: Compile, lint, commit**

Run: `./gradlew :app:compileDevDebugKotlin :app:detektAll`

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/favorites/FavoritesInteractor.kt app/src/main/java/com/kino/puber/ui/feature/favorites/vm/FavoriteVM.kt app/src/test/kotlin
git commit -m "Serve the watching list from the payload cache"
```

---

### Task 4: History — with a decision point

**Read this before starting.** `HistoryPageLoader.loadDepth()` reads pages in a `do/while` until it has renderable rows, and `HistoryRuntimeState` (570 lines) runs a state machine with reconciliation over the result. The shape below adds a cached publication *before* that walk and leaves the walk itself untouched. **If it cannot be added without restructuring the state machine, stop, leave the branch as it stands after Task 3, and report back** — restructuring that machine is its own change with its own design, not something to absorb here.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/history/HistoryInteractor.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/history/vm/HistoryVM.kt`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/history/`

**Interfaces:**
- Consumes: `ContentPageCache.historyFirstPage(force, loader)` from Task 1.
- Produces: `HistoryInteractor.observeFirstPage(force: Boolean = false): Flow<Cached<PaginatedResponse<History>>>`.

- [ ] **Step 1: Write the failing view-model test**

```kotlin
    @Test
    fun theStoredFirstPageIsDrawnBeforeTheDepthWalkFinishes() = runTest {
        val interactor = mockk<HistoryInteractor>(relaxed = true)
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(historyPage(row(1)), isStale = true, updatedAt = 0L),
        )
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        coEvery { interactor.getPage(1) } coAnswers { walk.await() }
        val vm = createVM(interactor)

        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1), vm.testStateValue.rowIds())
        walk.complete(historyPage(row(2)))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(2), vm.testStateValue.rowIds())
        vm.testCancelScope()
    }
```

Build `createVM`, `historyPage`, `row` and `rowIds()` from the fixtures already in the history test package rather than inventing new ones.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.history.*"`
Expected: FAIL — `observeFirstPage` unresolved.

- [ ] **Step 3: Add the observer to the interactor**

```kotlin
    /**
     * The first page of history, stored and revalidated. The rest of the depth the list needs is
     * read straight from the server by [getPage] as before — only the page the user looks at first
     * is worth keeping.
     */
    fun observeFirstPage(force: Boolean = false): Flow<Cached<PaginatedResponse<History>>> =
        contentPageCache.historyFirstPage(force = force) { api.getHistoryData(FIRST_PAGE).getOrThrow() }
```

with `contentPageCache: ContentPageCache` added to the constructor and `private const val FIRST_PAGE = 1` in the file.

- [ ] **Step 4: Publish the cached page before the walk**

In `HistoryVM.onStart()`, ahead of the existing `init()`, collect the stored page and publish it through the same path reconciliation already uses to show retained rows while a refresh runs:

```kotlin
        launch {
            interactor.observeFirstPage().collect { cached ->
                when (cached) {
                    // Only the stored emission is drawn here. The fresh one is the walk's job, and
                    // the walk is already on its way — publishing it twice would fight the state
                    // machine for the same rows.
                    is Cached.Value -> if (cached.isStale) showContent(cached.value.items, isRefreshing = true)
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh the history first page")
                }
            }
        }
```

If `showContent` has a different name or arity in the current file, use the one reconciliation calls at `HistoryVM.kt:501` — that is the path proven to publish rows without disturbing the machine.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.history.*"`
Expected: PASS.

- [ ] **Step 6: Compile, lint, commit**

Run: `./gradlew testDevDebugUnitTest :app:detektAll`

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/history/HistoryInteractor.kt app/src/main/java/com/kino/puber/ui/feature/history/vm/HistoryVM.kt app/src/test/kotlin
git commit -m "Draw the stored history page before the depth walk lands"
```

---

### Task 5: Measure on the device

The change is only done when the numbers move. Same protocol as the baseline, so the two are comparable.

**Files:** none — this task produces a measurement, recorded in the final report.

- [ ] **Step 1: Build and install**

Build the dev APK and install it on the Fire TV (`192.168.1.104:5555`) through the preserve-data path in `.kent/commands/smoke-test.md`. Bump the version so the settings screen shows which build is running.

- [ ] **Step 2: Measure a first visit per tab, twice**

Force-stop and relaunch the app, then for each of Movies, Series, 4K, TV shows, History: open the rail with BACK, move focus with the D-pad, screen-record, press OK, and record the time from the press to real data. Repeat the whole pass a second time — a single run supports nothing, the spread between runs on identical code is large.

Baseline to beat, from the spec: first visit 2.8–5.7 s, revisit past the old 3-minute TTL 1.1–2.6 s.

- [ ] **Step 3: Measure a revisit per tab, twice**

Same tabs, second visit each, once within a minute and once after ten minutes. The ten-minute case is the one the old 3-minute in-memory TTL failed; it should now be served from disk.

- [ ] **Step 4: Report**

Write the before/after table into the final summary. Call out any tab that did not improve, and check `dumpsys meminfo com.kino.puber.stage` against the pre-change build so the cache's memory tier is accounted for rather than assumed.

---

## Self-Review

**Spec coverage:** `ContentPageCache` singleton and its rationale — Task 1. Keys, TTLs, serializers, unsorted watching list — Task 1. Catalogue sections and "show all" — Task 2 (ShowAll inherits; no edit needed, stated in the task). The four consequences the spec lists (walk counters, silent `RefreshFailed`, one-shot flow, `force`) — Task 2 steps 5 and 6, and the tests in step 2. Invalidation via `force` rather than deletion — Task 2 step 6 for the section signals, Task 3 step 4 for retry. Watch-state version rule — Task 1 step 7, tested in step 5. `invalidateFirstPageCache` reduced to `freshPagers` — Task 2 step 4. Domain drop from the key — Task 2 step 4. Watching — Task 3. History with its decision point — Task 4. Device verification — Task 5.

**Placeholder scan:** no TBD/TODO; every code step carries the code. Three steps deliberately defer to the file being edited rather than inventing names — the `Item`/`History` constructors in Task 1 step 5, the favorites fixtures in Task 3 step 1, and the history fixtures in Task 4 step 1 — because those fixtures already exist in those files and guessing at them would be worse than pointing at them.

**Type consistency:** `observeFirstPage(config, force)` is declared in Task 2 step 4 and used in step 5 and in the tests in step 2 with the same shape. `sectionPage(key, watchStateVersion, force, loader)` is declared in Task 1 step 7 and called in Task 2 step 4 with named arguments in that order. `watchlist(force, loader)` and `historyFirstPage(force, loader)` likewise. `forceNextFirstPage` is declared `protected` in Task 2 step 5 and set from `SectionVM` in step 6. `testStateFlow` is added in Task 2 step 1 and used in step 2.
