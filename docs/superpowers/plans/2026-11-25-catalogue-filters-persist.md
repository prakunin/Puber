# Keep the catalogue filter per tab across restarts - plan

**Spec:** `docs/superpowers/specs/2026-11-25-catalogue-filters-persist.md`
**Goal:** A browse grid stores its applied filter per config id and starts its first load on it, uncached, after a reopen or a restart.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run inside the worktree `/home/dev/orca/workspaces/Puber/catalogue-filters-persist`; never `./gradlew` there. Set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit, message per commit style "One imperative English sentence, no prefixes; no Co-Authored-By line", never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths below are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Line numbers are at base ef5987ec.
- Red first: every new test in Tasks 1 and 2 is written before production code; on the base they fail to compile (no `CatalogueFilterRepository`, no `filterRepository` argument), which is their red. Record that output under Red run. The instrumented red (Task 3) is run on the device by qa-device, not by the implementer.
- Out of scope (spec): country, year, status, rating and quality filters, cartoon and anime grids, any change to `ContentFilter` or its enums, clearing on logout.

---

## Tasks

### Task 1: Add the catalogue filter repository

**Covers:** AC-5, AC-7
**Files:**
- Create: `data/preferences/CatalogueFilterRepository.kt`
- Modify: `data/di/modules.kt:109` - add `singleOf(::CatalogueFilterRepository)` after `singleOf(::NavigationPreferencesRepository)`
- Test (new): `app/src/test/kotlin/com/kino/puber/data/preferences/CatalogueFilterRepositoryTest.kt`

**Consumes:**
- `ContentFilter`, `SortField`, `SortDirection` (`domain/model/ContentFilter.kt:6`, `:20`, `:30`); `isActive` (`:17`).
- `Genre(id: Int, title: String)` (`data/api/models/Models.kt:197`).
- Pattern: `NavigationPreferencesRepository(context)` gets prefs in its constructor (`data/preferences/NavigationPreferencesRepository.kt:21-23`), writes with `prefs.edit { }` (`:61`), matches an enum by `name` with a fallback (`:64-67`).
- Test double `FakeSharedPreferences` (`app/src/test/kotlin/com/kino/puber/util/FakeSharedPreferences.kt:15`: `values`, `transactions`, `sharedPreferences`); mocked `Context` as `NavigationPreferencesRepositoryTest.kt:487-493` (`every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns preferences.sharedPreferences`).
- `kotlinx.serialization` is already applied to `:app` (`app/build.gradle.kts:10`, `:390`).

**Produces:**

```kotlin
class CatalogueFilterRepository(context: Context) {
    fun read(configId: String): ContentFilter   // ContentFilter() when absent or undecodable
    fun write(configId: String, filter: ContentFilter) // removes the key when !filter.isActive
    fun clear(configId: String)
}
```

- Prefs file `catalogue_filters` (`Context.MODE_PRIVATE`), key `"filter_" + configId`.
- Private `@Serializable data class StoredFilter(val genreId: Int? = null, val genreTitle: String? = null, val sort: String? = null, val direction: String? = null)`; JSON field names exactly these (the tests seed raw JSON).
- `Json { ignoreUnknownKeys = true }`; decode inside `runCatching`, failure -> `ContentFilter()`.
- `sort` -> `SortField.entries.find { it.name == sort } ?: SortField.UPDATED`; `direction` -> `SortDirection.entries.find { it.name == direction } ?: SortDirection.DESC`. No `valueOf`, no `@Serializable` on the enums.
- Genre kept only when both `genreId` and `genreTitle` are non-null.
- `write` of an active filter stores `genre?.id`, `genre?.title`, `sort.name`, `sortDirection.name`.

- [x] Write the failing tests (fixture: real repository over `FakeSharedPreferences` and a mocked `Context`; malformed values seeded as raw strings in `values["filter_<id>"]`):

| Test | Asserts |
|---|---|
| `write_thenRead_returnsGenreSortAndDirection` | `write("all_movie", ContentFilter(Genre(9, "Драма"), sort = YEAR, sortDirection = ASC))` then `read` equals it |
| `write_inactiveFilterRemovesTheEntry` | After an active write, `write("all_movie", ContentFilter())` leaves no `filter_all_movie` in `values` |
| `clear_removesOnlyThatGrid` | Entries for `all_movie` and `all_serial`; `clear("all_movie")` keeps `filter_all_serial` |
| `read_malformedJson_yieldsNoFilter` | `"{not json"` reads as `ContentFilter()`, no throw |
| `read_unknownKeys_areIgnored` | JSON with an extra `"country": 3` still yields genre, sort, direction |
| `read_unknownSortField_fallsBackToUpdatedKeepsTheRest` | `"sort":"METACRITIC"` reads as `UPDATED`; genre and direction intact |
| `read_unknownDirection_fallsBackToDesc` | `"direction":"UP"` reads as `DESC` |
| `read_genreWithoutTitle_isDropped` | `{"genreId":9}` yields `genre = null` |

- [x] Run them once to record the red (compile failure).
- [x] Implement the repository and the DI line.
- [x] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.preferences.CatalogueFilterRepositoryTest'`; commit.

**Done when:** the fast gate is green and the eight `CatalogueFilterRepositoryTest` tests pass.

---

### Task 2: Restore the stored filter when a browse grid opens

**Covers:** AC-1 (request and state), AC-2, AC-3, AC-4, AC-5, AC-6, AC-8, AC-9
**Files:**
- Modify: `ui/feature/contentlist/vm/ContentListPagingVM.kt` - constructor `:31-39` gains a last param `initialCacheable: Boolean = true`; `:51` becomes `private var cacheable = initialCacheable`. `onLoadFirstPage` (`:167-174`) already captures `config` and `cacheable`; unchanged.
- Modify: `ui/feature/showall/vm/ShowAllVM.kt` - constructors `:40-58`, initial state `:62-69`, write points `:154-167`, new companion.
- Modify: `ui/feature/showall/ShowAllScreen.kt:45-54` - add `filterRepository = get()` to the `ShowAllVM(...)` call; key `:37` unchanged.
- Modify: `docs/architecture/paging-and-filters.md:128` - append one sentence: a browse grid's applied filter is stored per config id in `CatalogueFilterRepository` and restored at VM construction, the first load running with `initialCacheable = false`.
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt`

**Consumes:**
- Task 1 `CatalogueFilterRepository.read/write/clear`.
- `ContentFilter.applyTo(base)` (`ui/feature/showall/model/ContentFilterQuery.kt:6`); `TabTypeConfig.isFilterable` (`ui/feature/contentlist/model/TabTypeConfig.kt:27`); `ANIME_GENRE_ID` (`data/api/models/ContentGenres.kt:4`); `AnimeFilterMode.FollowPreference`, `SectionConfig.animeFilterMode`; `ContentListInteractor.showAnimeEnabled` (`domain/interactor/contentlist/ContentListInteractor.kt:52-53`); `observeFirstPage(config, force, cacheable)` (`:60-70`).
- `ShowAllVM.withApplied` (`ShowAllVM.kt:169-174`), `openFilterPanel` (`:144-145` seeds the draft from `applied`).

**Produces:** `ShowAllVM` public constructor = today's parameters plus `filterRepository: CatalogueFilterRepository` (before `pagingCoroutineContext`); `ContentListPagingVM(..., initialCacheable: Boolean = true)`.

Constructor shape (the super call needs the restored value before any property exists):

```kotlin
internal class ShowAllVM private constructor(
    paginator: Paginator.Store<Item>,
    config: SectionConfig,
    interactor: ContentListInteractor,
    private val genreInteractor: GenreInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
    private val filterRepository: CatalogueFilterRepository,
    pagingCoroutineContext: CoroutineContext,
    restored: ContentFilter,
) : ContentListPagingVM<ShowAllViewState>(
    paginator, restored.applyTo(config), interactor, mapper, router, errorHandler,
    pagingCoroutineContext, initialCacheable = !restored.isActive,
) {
    constructor(
        /* today's params */, filterRepository: CatalogueFilterRepository,
        pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
    ) : this(/* same */, filterRepository, pagingCoroutineContext,
        restored = restoreFilter(config, filterRepository, interactor))
```

- `private companion object { fun restoreFilter(config, repository, interactor): ContentFilter }`: `ContentFilter()` without calling `read` when `!TabTypeConfig.isFilterable(config)`; otherwise `repository.read(config.id)`, with `genre = null` when `genre?.id == ANIME_GENRE_ID && config.animeFilterMode == FollowPreference && !interactor.showAnimeEnabled`. The stored entry is not rewritten.
- `private val base = config` keeps the unfiltered config.
- `mutableFilterState` starts as `ShowAllFilterState(available = true).withApplied(restored)` when filterable; `applied` starts as `restored`.
- `closeFilterPanel`: `if (changed) { filterRepository.write(base.id, applied); changeQuery(...) }`. `resetFilter`: `filterRepository.clear(base.id)` after the `isActive` guard. Both are unreachable on a shelf (`handleFilterAction` returns at `:128`).

Test fixture changes (`ShowAllVMTest.kt`):
- Helper `filterRepositoryOn(prefs: FakeSharedPreferences): CatalogueFilterRepository` over a mocked `Context`.
- `createVM` (`:741-757`) gains `filterRepository: CatalogueFilterRepository = filterRepositoryOn(FakeSharedPreferences())` and passes it.
- `filterRig` (`:380-404`) gains `filterRepository` (same default) and `showAnime: Boolean = true`, replacing the literal at `:396`; both are set before `createVM` because restoration runs in the constructor.
- Add `allSerial = TabTypeConfig.browseEntryFor(TabType.Series)!!.config` beside `allMovie` / `all4k` (`:339-340`).
- Reopen = `close()` the first rig, then a second rig on the same repository. "Writes nothing" = `FakeSharedPreferences.transactions` size unchanged. Assertions use `FirstPageCall(config, cacheable)` (`:345`). Seed a stored filter with `repository.write(...)` (or raw JSON for AC-9) before the rig. `interactor` is a relaxed mock (`:83`), so a test that builds a filterable VM without the rig stubs `showAnimeEnabled` itself.

- [x] Write the failing tests:

| Test | Asserts |
|---|---|
| `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached` | Stored `dramaByYear` on `allMovie`: `calls == [FirstPageCall(dramaByYear.applyTo(allMovie), cacheable = false)]` |
| `restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage` | `createVM` without `testOnStart`: `filterState.value` has `isActive`, `genreTitle == "Драма"`, `YEAR` / `ASC` |
| `filterApplied_reopenedGridStartsWithIt` | `applyFilter(dramaByYear)`, close, second rig on the same repository: its first call is `FirstPageCall(dramaByYear.applyTo(allMovie), cacheable = false)` |
| `filterOnMovies_isNotInheritedBySerialOr4k` | After applying on `allMovie`, rigs for `allSerial` and `all4k` page their base config with `cacheable = true`; `filterState.value.isActive` false |
| `filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached` | After `FilterReset`, `values` has no `filter_all_movie`; a reopened rig's calls are `[FirstPageCall(allMovie, cacheable = true)]` |
| `restoredFilter_panelDraftEqualsIt` | After `FilterPanelOpened`, `filterState.value.draft == dramaByYear` |
| `restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage` | Open and close: one first-page call; `transactions` size unchanged |
| `restoredAnime_withShowAnimeOff_skipsTheGenreKeepsTheSort` | Stored `Genre(ANIME_GENRE_ID, "Аниме")` + `YEAR`/`ASC`, `showAnime = false`: first call is `ContentFilter(sort = YEAR, sortDirection = ASC).applyTo(allMovie)`; `filterState.value.genreTitle == null` |
| `restoredAnime_withShowAnimeOn_isApplied` | Same store, `showAnime = true`: first call's `genre == "25"` |
| `restoredAnime_closedUnchanged_keepsTheStoredGenre` | Genre skipped; open and close the panel: `repository.read("all_movie").genre?.id == ANIME_GENRE_ID` |
| `shelfGrid_neverReadsOrWritesTheStore` | `values["filter_popular"]` seeded with a JSON filter; a `SectionConfig(id = "popular", title = "Popular")` rig: `calls == [FirstPageCall(popular, cacheable = true)]`; after `FilterPanelOpened`, `FilterPanelClosed`, `FilterReset`, `transactions` is empty |

- [x] Run them once to record the red (compile failure).
- [x] Implement `ContentListPagingVM`, `ShowAllVM`, the Koin factory and the docs sentence.
- [x] Guards stay green, unchanged: `filterReset_restoresTheBaseQueryAndRepagesCached`, `filterPanelClosed_withAFilter_readsTheFirstPageUncached`, `filterState_isUnavailableForAShelfConfig`, `filteredEmptyPage_keepsTheFilterActive`, `retry_forcesTheFirstPageRead_whereOpeningTheGridDoesNot`, `SectionVMTest` (`SectionVM` keeps the default `initialCacheable`).
- [x] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.showall.vm.ShowAllVMTest' --tests 'com.kino.puber.ui.feature.contentlist.vm.SectionVMTest'`; commit.

**Done when:** the fast gate is green; the eleven new and all existing `ShowAllVMTest` tests and `SectionVMTest` pass; `grep -n "filterRepository = get()" app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt` matches.

---

### Task 3: Keep «Сброс» from taking first focus on a cold open

**Covers:** AC-1 (first focus)
**Files:**
- Modify: `ui/feature/showall/content/ShowAllHeader.kt:94-99` - add `this.canFocus = canFocus` to the «Сброс» `focusProperties`, as «Фильтры» has at `:77`. Update the KDoc at `:42-43` to say `canFocus` gates both buttons.
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt`

**Consumes:** the `canFocus` parameter (`ShowAllHeader.kt:51`), fed by the `filtersCanFocus` latch (`ui/feature/showall/content/ShowAllScreenContent.kt:84-87`, passed at `:110`); test host fields `state`, `filter`, `activeFilter` (`ShowAllFilterFocusTest.kt:51-55`) and helpers `setScreen`, `awaitAnyFocus`, `text`.
**Produces:** nothing consumed later.

- [x] Write the failing test `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`, modelled on `coldOpen_focusesTheFirstCardNotFilters` (`:93-104`): `filter = activeFilter`, `state = ShowAllViewState.Loading`, `setScreen()`, advance `SettleMillis`; then `state = ShowAllViewState.Content(items)`, `awaitAnyFocus()`, advance `SettleMillis`. Assert card 1 `assertIsFocused()`, `onAllNodesWithText(text(R.string.show_all_filters_reset))[0]` `assertIsDisplayed()` and `assertIsNotFocused()` (import `androidx.compose.ui.test.assertIsNotFocused`).
- [x] Add the `canFocus` gate on «Сброс».
- [x] Run the fast gate and `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit.
- [x] Run the full gate.

**Done when:** the fast gate, `:app:compileDevDebugAndroidTestKotlin` and the full gate are green. The red and green runs of `ShowAllFilterFocusTest` (new test plus guards `coldOpen_focusesTheFirstCardNotFilters`, `resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard`) and manual checks M1-M4 belong to qa-device: `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** see `git log ef5987ec..`: repository (Task 1), restore at open (Task 2), «Сброс» focus gate (Task 3), plus the `LargeClass` suppression on `ShowAllVMTest` in the last commit.

**Red run** (P0 `red` tests on the unchanged code):

- Tasks 1 and 2: red by compile failure, as the plan states. `CatalogueFilterRepositoryTest`: `Unresolved reference 'CatalogueFilterRepository'`. `ShowAllVMTest`: `No parameter with name 'filterRepository' found`. All new rows of both files share that red.
- Task 3: `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset` is compiled only (`:app:compileDevDebugAndroidTestKotlin`); its device red and green runs belong to qa-device.
- green on base: none.

**Deviations from the plan:** `@Suppress("LargeClass")` added on `ShowAllVMTest` (detekt `LargeClass` fired after the eleven new tests; same pattern as `CachedFeedTest`).

**Files touched outside the plan:** none.

**For the reviewers:** `ShowAllVM` primary constructor is private and the public secondary one computes `restored` before the super call; `restoreFilter` reads the store only for filterable configs. Stored entry is not rewritten when the anime genre is skipped.

**Verify round 1, Defect 1 (instrumented test was green without the gate).** Cause: the test never put initial focus on the header while the grid was loading. In the host the window is already focused, nothing in the Loading state asks for focus, and the grid's own request fires 100 ms after Content mounts (`rememberFocusRequesterOnLaunch`), by which time `filtersCanFocus` is already true and the grid's request wins, so «Сброс» never held focus and the gate was never exercised. The theft the gate prevents is the window's initial focus entering the first focusable node during Loading, which a test host does not repeat. Fix: the test now captures the host view and, during Loading, does `clearFocus()` then `requestFocus()` on it (the entry a window gaining focus makes), then asserts «Сброс» and «Фильтры» are not focused before Content lands. Without the gate «Сброс» is the only focusable node and takes that entry, so the assertion fails. Compiled only (`:app:compileDevDebugAndroidTestKotlin` green); the red run without the gate on the box is qa-device's. Caveat: the final assertion (card 1 focused after Content) holds either way, because the grid's request lands later and moves focus; the discriminating assertion is the one during Loading.

**Review round 1 fixes** (triage-1.md items 1-5):

| Item | Commit | Result |
|---|---|---|
| 1 | b1f0cd09 | `ShowAllEmpty` requests focus on «Сброс» when the filter is active, content focus is active and `headerHoldsFocus` is false (nothing was focused: cold open). After a panel close the header held focus, so «Фильтры» keeps it. Itest cases added: `coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset` and `panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset` (no panel-close-to-empty case existed; this is it). Compiled only; device red/green is qa-device's. |
| 2 | 87c7be8c | KDoc of `restoreFilter` says the entry stays until the next changed filter replaces it, «Аниме» included; behaviour unchanged. |
| 3 | 87c7be8c | `filterReset_ofARestoredFilter_readsTheBaseQueryCachedAndRemovesTheEntry`. |
| 4 | 87c7be8c | `restoredAnime_onANonFollowPreferenceGrid_isApplied` on all_4k. |
| 5 | b1f0cd09 | Comment in `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset` marks the Loading-phase assertions as the guard. |

Red run for 3 and 4: green on base (the behaviour exists; both are guards). Item 1 reviewers: the focus request keys on `headerHoldsFocus`, which stays true after the panel opened from «Фильтры»; a user who moved to the side rail during Loading is not distinguished from a cold open. `check.fast` and `ShowAllVMTest` green.

**Review round 2 fixes** (triage-2.md items 1 and 3):

| Item | Result |
|---|---|
| 1 | `ShowAllEmpty` skips the «Сброс» focus request while `LocalContentFocusHandoff.current?.expectsNewContent == true` (the guard pattern of `ContentFocusHandoffEffect`). The flag keys the effect, so the request still fires once the handoff settles with the empty state shown. Cold open (no handoff) and rail Dismiss (`expectsNewContent` false) are unchanged. Round 3 narrowed the skip to the outgoing content and added tests (see Fix round 3). |
| 3 | Renamed the test to `panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset`; plan references updated. |

`check.fast` and `:app:compileDevDebugAndroidTestKotlin` green.

**Blocked:** none.

## Readiness

Verdict: **READY**

Checked against base ef5987ec (`git show ef5987ec:<path>`), spec and plan in the worktree, `test-design.md` in the run folder.

### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 2 (request, state), Task 3 (focus) | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached`, `restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage`, `ShowAllFilterFocusTest.coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`, M1 |
| AC-2 | Task 2 | `filterApplied_reopenedGridStartsWithIt`, M2 |
| AC-3 | Task 2 | `filterOnMovies_isNotInheritedBySerialOr4k`, M3 |
| AC-4 | Task 2 | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached` (exact call list) |
| AC-5 | Task 1, Task 2 | `filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached`, `CatalogueFilterRepositoryTest.write_inactiveFilterRemovesTheEntry`, `clear_removesOnlyThatGrid`, `write_thenRead_returnsGenreSortAndDirection` |
| AC-6 | Task 2 | `restoredFilter_panelDraftEqualsIt`, `restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage` |
| AC-7 | Task 1 | `CatalogueFilterRepositoryTest.read_*` (four rows) |
| AC-8 | Task 2 | `restoredAnime_withShowAnimeOff_skipsTheGenreKeepsTheSort`, `restoredAnime_withShowAnimeOn_isApplied`, `restoredAnime_closedUnchanged_keepsTheStoredGenre`, M4 |
| AC-9 | Task 2 | `shelfGrid_neverReadsOrWritesTheStore` |

### Citations sampled

| Citation | Resolves to |
|---|---|
| `ShowAllVM.kt:40-58`, `:60-69`, `:144-145`, `:154-167`, `:169-174` | constructor and super call; `base`, `filterable`, `mutableFilterState`, `applied`; `openFilterPanel` seeds draft from `applied`; `closeFilterPanel` / `resetFilter`; `withApplied` |
| `ContentListPagingVM.kt:31-39`, `:50-51`, `:167-174` | abstract constructor; `cacheable = true`; `onLoadFirstPage` captures `config` and `cacheable` |
| `ContentListInteractor.kt:52-53`, `:60-70` | `showAnimeEnabled`; `observeFirstPage` uncached branch emits `loadPage` only |
| `ContentFilter.kt:6`, `:17`, `:20`, `:30`; `ContentFilterQuery.kt:6`; `TabTypeConfig.kt:27`; `ContentGenres.kt:4`; `Models.kt:197` | `ContentFilter`, `isActive`, `SortField`, `SortDirection`; `applyTo` (genre as id string); `isFilterable`; `ANIME_GENRE_ID = 25`; `Genre` |
| `ShowAllScreen.kt:37`, `:45-54`; `modules.kt:109`; `NavigationPreferencesRepository.kt:21-23`, `:61`, `:64-67` | key; Koin `ShowAllVM(...)` with named args; `singleOf(::NavigationPreferencesRepository)`; prefs, `edit {}`, enum by name with fallback |
| `ShowAllHeader.kt:42-43`, `:51`, `:77`, `:94-99`; `ShowAllScreenContent.kt:84-87`, `:110` | KDoc; `canFocus`; gate on «Фильтры» only; «Сброс» focusProperties without gate; latch; passed in |
| `ShowAllVMTest.kt:83`, `:339-345`, `:380-404`, `:396`, `:741-757`; `ShowAllFilterFocusTest.kt:51-55`, `:93-104`; `FakeSharedPreferences.kt:15`; `app/build.gradle.kts:10`, `:390`; `docs/architecture/paging-and-filters.md:128` | all resolve as claimed (relaxed interactor, `allMovie`/`all4k`/`FirstPageCall`, `filterRig`, `showAnimeEnabled` literal, `createVM`, host fields, cold-open test, `values`/`transactions`, serialization plugin and json lib, `changeQuery` paragraph) |

### Build order

Task 1 (new class + DI line) and Task 2 (`ShowAllVM` constructor, Koin factory and test factory together) each compile on their own; Task 3 is independent. `SectionVM.kt:31-39` calls the super positionally, so the defaulted last param keeps it green. Detekt `LongParameterList` is inactive (`config/detekt/detekt.yml:121-122`).

### P0 rows

Every P0 row names its mutation, is marked `red` or `guard`, and names a backticked test; M1 is the device row for AC-1. Scope stays inside Out of scope (no change to `ContentFilter` or its enums, no logout clear). Q1-Q3 answered; none blocks.

### Non-blocking notes for the implementer

1. `test-design.md` cites the mocked-Context pattern as `NavigationPreferencesRepositoryTest.kt:473-490`; the mock is at `:487-493` (the plan is right).
2. Spec Design says the test factory default is a mockk repository; Test design and plan use a real repository over `FakeSharedPreferences`. Follow the plan.
3. `FakeSharedPreferences` stubs only `apply()`, not `commit()`; write through `prefs.edit { }` as planned.

**Fix round 3 (triage-3 items 1, 2).**
- Item 1: `ShowAllEmpty` captures `bornDuringRequest` at composition and skips the «Сброс» request only when `expectsNewContent` and it was not born during the pending request (the test of `ContentFocusHandoffEffect`, `ContentFocusHandoff.kt:108,115`). An arriving Empty keeps its request; cold open and rail Dismiss are unchanged.
- Item 2: the round 2 statement "no host drives a TabPick handoff" was wrong: `ShowAllRailCloseFocusTest.RailWithShowAll` has the real `DrawerState` and `ContentFocusHandoff`. `setUpScreen` and `Host` now take a view state (default Content). Added `tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus` (Empty + active filter, «Сброс» focused, Left opens the rail, `beginHandoff(TabPick)`, frames, «Сброс» not focused, HandingOff) and `tabPickHandoff_arrivingEmptyResetTakesFocus` (handoff begins over Content, host swaps to Empty during the request, «Сброс» takes focus).
- Compiled only (`:app:compileDevDebugKotlin`, `:app:compileDevDebugAndroidTestKotlin` green); neither test has run, red/green is qa-device's. The round-3 arriving test called `beginHandoff` while the rail was Closed, which is refused (`ModalNavigationDrawer.kt:164`), so it tested nothing (superseded by round 4).

**Fix round 4 (triage-4 items 1, 2; test-only, commit below).**
- Both cases in `ShowAllRailCloseFocusTest` now address the empty-state «Сброс»: assert 2 nodes, then `[1]` (header one is `[0]`), as `ShowAllFilterFocusTest` does.
- The rail is opened with `drawerState.reveal()` in `runOnIdle`, not Left, because Left from the centred empty-state «Сброс» may land on «Фильтры» instead of the rail (unproven). Both cases wait for `DrawerValue.Open`.
- Arriving: one `runOnIdle` does `beginHandoff(TabPick)`, `assertNotNull(id)`, `host.state = Empty`; then asserts `HandingOff` and waits for the empty-state «Сброс» focused. Outgoing: asserts the `beginHandoff` id is non-null.
- Red-run mutations for qa-device: outgoing = delete `!outgoingDuringTabPick`; arriving = restore the round-2 condition (skip the request while `expectsNewContent`, no `bornDuringRequest`). Not run here; compiled only (`:app:compileDevDebugAndroidTestKotlin`).

**QA round 3 red/green (qa-device, owner box 192.168.1.110:5555, build 3e5248d0).**

| Mutation (one at a time, file restored after each) | Test | Result |
|---|---|---|
| a. ShowAllEmpty LaunchedEffect request removed | ShowAllFilterFocusTest#coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset | RED: ComposeTimeoutException "Condition still not satisfied after 5000 ms" |
| b. "&& !headerHoldsFocus.value" removed | ShowAllFilterFocusTest#panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset | RED: AssertionError "Failed to assert the following: (Focused = 'true')" |
| c. "&& !outgoingDuringTabPick" removed | ShowAllRailCloseFocusTest#tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus | RED: AssertionError "Failed to assert the following: (Focused = 'false')" |
| d. condition replaced by the round-2 one (handoff?.expectsNewContent != true, no bornDuringRequest) | ShowAllRailCloseFocusTest#tabPickHandoff_arrivingEmptyResetTakesFocus | RED: ComposeTimeoutException "Condition still not satisfied after 12000 ms" |

Green on the branch (head 3e5248d0), same device: ShowAllFilterFocusTest 17 tests, 0 failures, 0 errors, 0 skipped; ShowAllRailCloseFocusTest 10 tests, 0 failures, 0 errors, 0 skipped. `git status` clean after the mutations.

**Fix round 5 (qa-report Round 3, detekt ComplexCondition at `ShowAllScreenContent.kt:198`).** The `LaunchedEffect` condition in `ShowAllEmpty` is split with a local `mayTakeFocus = contentFocusActive && !outgoingDuringTabPick`; behaviour unchanged, no suppression. Full gate in the worktree (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:compileDevDebugAndroidTestKotlin`): BUILD SUCCESSFUL in 2m 12s.
