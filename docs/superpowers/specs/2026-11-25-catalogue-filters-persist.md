# Keep the catalogue filter per tab across restarts

Status: approved
Date: 2026-11-25
Slug: catalogue-filters-persist
Kind: feature
Size: M
Tracks: none

## Problem / Why

Part 3 of the `catalogue-filters` split (`../catalogue-filters/brief.md#Split`): keep the filter applied on a browse grid across closing the grid and restarting the app. Supersedes catalogue-filters Q2 (lifetime = screen instance) and R19 (`docs/superpowers/specs/2026-11-24-catalogue-filters.md:319`, `:416`).

Code map, related runs and options: `research.md: Current state in code`, `Related work`, `Options` (option A recommended). Checked at ef5987ec; the facts this brief relies on:

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:60-69` | `applied = ContentFilter()` is the only holder of the filter; `filterable` gates it |
| `ShowAllVM.kt:154-167` | The two write points: `closeFilterPanel` (changed draft) and `resetFilter` |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:46-51` | `config = initialConfig`, `cacheable = true` fixed: a VM cannot start on an uncached filtered query without a `changeQuery` |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:143-190` | On a `FollowPreference` grid with show-anime off every anime item is dropped client-side, so a restored «Аниме» genre (id 25) pages an empty grid |
| `app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6-33` | Not `@Serializable`; `SortField.KINOPOISK` / `IMDB` may be removed by part 4 |
| `app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt:36`, `:42-55` | Key `ShowAllScreen_${config.id}`; `buildModule` is where a new singleton is injected |
| `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt:64-67`, `data/di/modules.kt:109` | Pattern: SharedPreferences repo, enum by `name` with fallback, `singleOf` |

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | genre «Драма» and sort «по году ↑» applied on `all_movie` | the app process is killed and restarted, and the Movies browse grid is opened | the first request carries `sort=year` and `genre=<drama id>` with the base `type`; the header shows the summary and «Сброс» from the first frame; first focus lands on card 1 (VM unit test for the request and state; device for frame and focus) |
| AC-2 | a filter applied on a grid | the grid is closed and reopened in the same session (entry or «Все» tile) | it opens with that filter (VM unit test) |
| AC-3 | a filter applied on `all_movie` | `all_serial` and `all_4k` are opened | each opens with its own stored filter or unfiltered; none inherits Movies' (VM unit test) |
| AC-4 | a restored filter | the first page loads | exactly one first-page load runs, on the filtered query, and it neither reads nor writes the persistent first-page cache (VM unit test with a fake interactor) |
| AC-5 | a restored filter | «Сброс» is pressed, then the grid is reopened | the stored entry is removed; the reopened grid is unfiltered and uses the first-page cache (VM and repository unit tests) |
| AC-6 | the panel is opened on a restored filter | — | the draft equals the restored filter and its genre and sort rows show as chosen; closing unchanged writes nothing and does not re-page (VM unit test) |
| AC-7 | a stored value that is malformed JSON, has unknown keys, or names an unknown sort field or direction | the grid opens | no crash; unknown keys are ignored, an unknown enum falls back to its default, malformed JSON yields no filter (repository unit test) |
| AC-8 | «Аниме» stored on `all_movie` / `all_serial` and show-anime turned off | the grid opens | the genre is not applied (sort and direction still are); the grid is not empty because of it (VM unit test) |
| AC-9 | a shelf grid (`fresh_*`, `hot_*`, `popular_*`, `4k_<type>`) | opened and used | the store is neither read nor written (VM unit test) |

### Risks (analyst)

- Starting paging on the filtered query needs a cacheable flag at construction (`ContentListPagingVM.kt:46-51`); a `changeQuery` from `onStart` instead would double the first load and can read the unfiltered cached page (breaks catalogue-filters AC-7).
- The stored genre title is shown in the summary before genres reload; a genre the API no longer returns pages an empty grid, which falls back to the existing filtered-empty state with «Сброс» (catalogue-filters AC-12).
- Stored shape must grow with parts 2 and 4 (country, year, finished; possible removal of rating sorts): a DTO with defaults, decoded with `ignoreUnknownKeys`, separate from `ContentFilter`.

## Out of scope

The brief lists none separately; parts 2 and 4 of the catalogue-filters split (country/year/status, ratings/quality, cartoon/anime grids) are separate runs.

## Design

Base ef5987ec. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. ACs: `brief.md#Acceptance criteria`. Owner answers Q1-Q3 of the brief are taken as decided.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where filters are stored | New `data/preferences/CatalogueFilterRepository(context)`: SharedPreferences `catalogue_filters`, key `filter_<config.id>`, value JSON; `singleOf(::CatalogueFilterRepository)` beside `NavigationPreferencesRepository` (`data/di/modules.kt:109`) | Same shape as the other preference repos and their `FakeSharedPreferences` test double (`app/src/test/kotlin/com/kino/puber/util/FakeSharedPreferences.kt:15`); per config id (Q1); a separate file keeps `NavigationPreferencesRepository` (246 lines) out of it |
| Repository API | `fun read(configId: String): ContentFilter` (default when absent or undecodable); `fun write(configId: String, filter: ContentFilter)` removes the key when `!filter.isActive`; `fun clear(configId: String)` | One write path; an inactive filter is never stored, so «Сброс» and "chose the defaults by hand" leave the same file (AC-5) |
| Stored shape (AC-7) | Private `@Serializable data class StoredFilter(val genreId: Int? = null, val genreTitle: String? = null, val sort: String? = null, val direction: String? = null)` in the repository file; decoded with `Json { ignoreUnknownKeys = true }` inside `runCatching`; `sort` / `direction` matched by `name` against `SortField` / `SortDirection` (`domain/model/ContentFilter.kt:20-33`) with `UPDATED` / `DESC` fallback, as `getStartupTab` does (`NavigationPreferencesRepository.kt:64-67`); genre kept only when both id and title are present | Enum names as strings, not `@Serializable` enums, so a value part 4 removes decodes to the default instead of failing the whole entry; parts 2 and 4 add nullable fields with defaults. `ContentFilter` stays unannotated |
| Which grids read and write | Only when `filterable` (`ui/feature/showall/vm/ShowAllVM.kt:61`); shelves never touch the repository | AC-9 |
| How the restored filter reaches the first load (AC-1, AC-4) | `ContentListPagingVM` gains a last constructor param `initialCacheable: Boolean = true` backing `cacheable` (`ui/feature/contentlist/vm/ContentListPagingVM.kt:50-51`). `ShowAllVM` passes `initialConfig = restored.applyTo(config)` and `initialCacheable = !restored.isActive`; `onLoadFirstPage` (`:167-174`) already captures both | The first load runs once on the filtered query with `observeFirstPage(cacheable = false)` (`domain/interactor/contentlist/ContentListInteractor.kt:60-70`), which neither reads nor writes `cached_payload`. No `changeQuery` at start, so no double load and no unfiltered cached page drawn first. `SectionVM` keeps the default |
| Where restoration is computed | `ShowAllVM` gets a private primary constructor taking `restored: ContentFilter` and a public secondary constructor with today's parameters plus `filterRepository: CatalogueFilterRepository`, delegating with `restored = restoreFilter(config, filterRepository, interactor)` (companion function) | The super-constructor call needs the value before any `ShowAllVM` property exists; the Koin factory (`ui/feature/showall/ShowAllScreen.kt:44-54`) and the test factory (`app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:741-755`) keep named arguments and add one |
| Stored «Аниме» with show-anime off (AC-8, Q3) | `restoreFilter` drops the genre when it is `ANIME_GENRE_ID` (`data/api/models/ContentGenres.kt:4`), `config.animeFilterMode == FollowPreference` and `!interactor.showAnimeEnabled` (`ContentListInteractor.kt:52-53`); sort and direction stay. The stored entry is not rewritten at open | With the genre applied, `fetchFilteredPage` drops every item and walks with no page budget when hide-watched is off (`ContentListInteractor.kt:144-178`). Not rewriting keeps the genre for when show-anime is back on |
| Initial header state (AC-1) | `mutableFilterState` (`ShowAllVM.kt:62-64`) starts as `ShowAllFilterState(available = true).withApplied(restored)` and `applied` (`:69`) starts as `restored` | Summary and «Сброс» are in the first composed state, before any page; `openFilterPanel` (`:144-145`) then seeds the draft with the restored filter (AC-6) |
| Write points | `closeFilterPanel` (`:154-160`): inside `if (changed)`, `filterRepository.write(base.id, applied)` before `changeQuery`. `resetFilter` (`:162-167`): `filterRepository.clear(base.id)` after the `isActive` guard | Unchanged close writes nothing (AC-6); reset removes the entry (AC-5). A changed close replaces the whole entry, so a skipped stored «Аниме» is dropped only when the user applies another filter |
| Same-session reopen (AC-2, AC-3) | Nothing beyond the above: every `ShowAllVM` reads the repository at construction; entry and «Все» tile share `config.id` (`ui/feature/contentlist/vm/ContentListVM.kt:146`) | One rule for reopen and restart |
| Logout | Not cleared (Q2); `ui/feature/root/component/App.kt:78-110` unchanged | Per-device setting, like the other preference repos |
| «Сброс» on a cold open (AC-1 focus) | The header «Сброс» gets the same `canFocus` gate as «Фильтры» (`ui/feature/showall/content/ShowAllHeader.kt:93-99` gains `this.canFocus = canFocus`) | Today the gate covers only «Фильтры» (`ShowAllHeader.kt:76-77`, latch `ShowAllScreenContent.kt:84-87`) because «Сброс» never existed on a cold open; with a restored filter it is the only focusable during `Loading` and would take first focus instead of card 1 |

### Changes by layer

**API / repository.**

| File | Change |
|---|---|
| `data/preferences/CatalogueFilterRepository.kt` (new) | Repository, `StoredFilter` DTO, mapping to and from `ContentFilter` (genre, sort, direction) |
| `data/di/modules.kt:109` | `singleOf(::CatalogueFilterRepository)` |

**ViewModel state and actions.**

| File | Change |
|---|---|
| `ui/feature/contentlist/vm/ContentListPagingVM.kt:31-39`, `:50-51` | `initialCacheable: Boolean = true` param; `cacheable = initialCacheable` |
| `ui/feature/showall/vm/ShowAllVM.kt:40-58` | Private primary + public secondary constructor; super gets the restored query and `initialCacheable` |
| `ShowAllVM.kt:62-69` | Initial `filterState` and `applied` from `restored` |
| `ShowAllVM.kt:154-167` | `write` on a changed close, `clear` on reset |
| `ShowAllVM.kt` companion (new) | `restoreFilter(config, repository, interactor)`: default when not filterable; anime skip |

**Screen and DI module.** `ui/feature/showall/ShowAllScreen.kt:44-54`: `filterRepository = get()`; key `:37` unchanged.

**Compose content.** `ui/feature/showall/content/ShowAllHeader.kt:93-99`: `canFocus` on «Сброс».

**Resources.** N/A: no new strings.

**Docs.** `docs/architecture/paging-and-filters.md:128`: one sentence that a browse grid's applied filter is stored per config id in `CatalogueFilterRepository` and restored at VM construction with `initialCacheable = false`.

**Tests.**

| File | Change |
|---|---|
| `app/src/test/kotlin/com/kino/puber/data/preferences/CatalogueFilterRepositoryTest.kt` (new) | Round trip; inactive write removes; `clear`; malformed JSON, unknown keys, unknown enum names (AC-5, AC-7) on `FakeSharedPreferences` with a mocked `Context`, as `NavigationPreferencesRepositoryTest.kt:473-490` |
| `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:741-755` | Factory gains `filterRepository` (mockk, `read` returns `ContentFilter()` by default); new cases for AC-1 to AC-6, AC-8, AC-9 asserting the `observeFirstPage(config, force, cacheable)` arguments and repository calls |

### Alternatives rejected

- `changeQuery` from `onStart` on a restored filter: two first-page loads, the first of them on the cached unfiltered page.
- Keys inside `NavigationPreferencesRepository`: mixes a per-grid query into app navigation settings (research Option B).
- Room table: a migration per new filter field (research Option C).
- `@Serializable` on `ContentFilter` or its enums: an enum removed by part 4 fails the whole decode; `IntRange` / float ranges need custom serializers.
- Filter in `ShowAllScreen` args or key: splits Voyager state, DI scope and focus memory per filter, and does not survive a reopen.
- Restoration computed in the Koin factory: puts VM logic in DI and outside `ShowAllVMTest`.

### Risks

| Area | Risk | Lives in |
|---|---|---|
| Focus | Cold open with a restored filter: «Сброс» focusable before the grid lands takes first focus; device check of AC-1 | `ShowAllHeader.kt:84-99`, `ShowAllScreenContent.kt:84-87` |
| Correctness | A restored first page read through the cache path would show unfiltered cards under a filtered header, or store a filtered page as the unfiltered one (catalogue-filters AC-7) | `ContentListPagingVM.kt:50-51`, `:170-174`; `ContentListInteractor.kt:67-70` |
| Correctness | A stored genre the API no longer returns, or «Аниме» on a grid whose mode is not `FollowPreference`, pages an empty grid; the filtered-empty state with «Сброс» is the exit | `ShowAllScreenContent.kt:181-196` |
| Data shape | Parts 2 and 4 change fields or enum values; a strict decode would drop every stored filter | `CatalogueFilterRepository.kt` |
| Lifecycle | Show-anime turned off while a grid with «Аниме» applied stays open: not re-evaluated until the grid is reopened (same as today) | `ShowAllVM.kt` `restoreFilter` |
| Main-thread work | First `read` blocks on loading `catalogue_filters.xml` during VM construction on the main thread; one small file, once per process | `CatalogueFilterRepository.kt`; `ShowAllScreen.kt:44-54` |
| Data shape | Stored genre title is shown in the summary until the panel reloads genres; stale after a server rename | `ShowAllVM.kt:169-174` |
| Rollout | `allowBackup="true"` (`app/src/main/AndroidManifest.xml:23`) carries stored filters to a restored install | `catalogue_filters` prefs |
| Baseline profile | New repository class and the restored-filter start path are not in the generated profile until regeneration | `app/src/main/generated/baselineProfiles/baseline-prof.txt` |

### Open questions

N/A: brief Q1-Q3 answered by the owner; no new question.

## Test design

Base ef5987ec. ACs: `brief.md#Acceptance criteria`. Design: `design-architect.md`. Paths under `app/src/`.

### Fixtures

| Test file | Fixture |
|---|---|
| `test/kotlin/com/kino/puber/data/preferences/CatalogueFilterRepositoryTest.kt` (new) | Real `CatalogueFilterRepository` over `FakeSharedPreferences` with a mocked `Context`, as `NavigationPreferencesRepositoryTest.kt:473-490`; malformed values are seeded as raw strings in `values["filter_<id>"]` |
| `test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt` | `createVM` and `filterRig` gain `filterRepository` (default: real repository over a fresh `FakeSharedPreferences`) and `showAnime: Boolean = true`, both set before construction because restoration runs in the constructor. Reopen = a second rig on the same repository. "Writes nothing" = `FakeSharedPreferences.transactions` unchanged. Assertions use the existing `FirstPageCall(config, cacheable)` list |
| `androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt` | Existing host without a VM; `activeFilter` + `ShowAllViewState.Loading` stands for a restored filter on a cold open |

On the base commit the new unit tests do not compile (no `CatalogueFilterRepository`, no `filterRepository` argument); that is their red. The instrumented red test compiles on base and fails on behaviour.

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Restored first page goes through the cache path: unfiltered cards under a filtered header, or a filtered page stored as the unfiltered one | 2 | 3 | 6 | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached` |
| Restore through `changeQuery` at start: two first-page loads | 2 | 2 | 4 | same test (exact call list) |
| «Сброс» focusable during `Loading` takes first focus instead of card 1 | 3 | 2 | 6 | `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`; manual M1 |
| Strict decode: malformed JSON crashes the grid, or a renamed/removed enum drops the entry or throws | 2 | 3 | 6 | repository AC-7 tests |
| Restored «Аниме» with show-anime off pages an empty grid with an unbounded walk | 2 | 3 | 6 | `restoredAnime_withShowAnimeOff_skipsTheGenreKeepsTheSort` |
| Unchanged panel close rewrites the entry and drops a skipped «Аниме» | 2 | 2 | 4 | `restoredAnime_closedUnchanged_keepsTheStoredGenre` |
| Koin factory misses `filterRepository = get()`: crash on opening any grid | 2 | 3 | 6 | manual M1, M3 (no Koin module test exists) |
| Key collision: one grid inherits another's filter | 1 | 2 | 2 | `filterOnMovies_isNotInheritedBySerialOr4k` |
| Shelf grid reads or writes the store | 1 | 2 | 2 | `shelfGrid_neverReadsOrWritesTheStore` |
| Stored genre no longer returned by the API: empty grid | 1 | 2 | 2 | accepted; exit is the filtered-empty state, guarded by `filteredEmptyPage_keepsTheFilterActive` |
| First `read` loads `catalogue_filters.xml` on the main thread | 1 | 1 | 1 | accepted: one small file per process |
| Stale stored genre title after a server rename | 1 | 1 | 1 | accepted |
| `allowBackup` carries filters to a restored install | 1 | 1 | 1 | accepted |
| Restore path absent from the baseline profile | 1 | 1 | 1 | accepted until regeneration |
| Show-anime toggled while a grid with «Аниме» stays open | 1 | 1 | 1 | accepted: same as today |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1, AC-4 | unit | P0 red | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached` | Calls equal `[FirstPageCall(dramaByYear.applyTo(allMovie), cacheable = false)]`; fails if restore goes through `changeQuery` (two calls), if `initialCacheable` is not passed (`cacheable = true`), or if the restored filter is not applied (base config) |
| AC-1 | unit | P0 red | `restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage` | `filterState` right after construction, before `testOnStart`: `isActive`, genre title «Драма», `YEAR` / `ASC`; fails if `mutableFilterState` starts from `ContentFilter()` |
| AC-1 | instrumented | P0 red | `ShowAllFilterFocusTest.coldOpenWithRestoredFilter_focusesTheFirstCardNotReset` | `activeFilter` + `Loading`, then `Content`: card 1 focused, «Сброс» displayed and not focused; fails without the `canFocus` gate on «Сброс» (`ShowAllHeader.kt:93-99`) |
| AC-1 | manual (device) | P0 | M1 | Process kill and restart, first frame, focus on the box |
| AC-2 | unit | P0 red | `filterApplied_reopenedGridStartsWithIt` | Second VM on the same repository pages `dramaByYear.applyTo(allMovie)` uncached first; fails if `closeFilterPanel` does not write |
| AC-3 | unit | P1 red | `filterOnMovies_isNotInheritedBySerialOr4k` | After applying on `allMovie`, VMs for `all_serial` and `all4k` page their base config cacheable and `filterState.isActive` is false; fails if the key ignores `config.id` |
| AC-5 | unit | P0 red | `filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached` | After reset, `values` has no `filter_all_movie` and a reopened VM's calls are `[FirstPageCall(allMovie, cacheable = true)]`; fails if `resetFilter` does not clear |
| AC-5 | unit | P0 red | `CatalogueFilterRepositoryTest.write_inactiveFilterRemovesTheEntry` | Writing `ContentFilter()` over a stored entry removes the key; fails if the default is stored as JSON |
| AC-5 | unit | P1 red | `CatalogueFilterRepositoryTest.clear_removesOnlyThatGrid` | `clear("all_movie")` keeps `filter_all_serial` |
| AC-5, AC-7 | unit | P0 red | `CatalogueFilterRepositoryTest.write_thenRead_returnsGenreSortAndDirection` | Round trip equals the written filter; fails if a field is not mapped |
| AC-6 | unit | P0 red | `restoredFilter_panelDraftEqualsIt` | After `FilterPanelOpened`, `draft == dramaByYear`; fails if the draft is seeded from `ContentFilter()` |
| AC-6 | unit | P0 red | `restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage` | Open and close: one first-page call, `transactions` unchanged; fails if the write sits outside `if (changed)` or `applied` does not start as the restored filter |
| AC-7 | unit | P0 red | `CatalogueFilterRepositoryTest.read_malformedJson_yieldsNoFilter` | `"{not json"` reads as `ContentFilter()`, no throw; fails without `runCatching` |
| AC-7 | unit | P0 red | `CatalogueFilterRepositoryTest.read_unknownKeys_areIgnored` | Entry with an extra `"country": 3` still yields genre, sort, direction; fails without `ignoreUnknownKeys` |
| AC-7 | unit | P0 red | `CatalogueFilterRepositoryTest.read_unknownSortField_fallsBackToUpdatedKeepsTheRest` | `"sort":"METACRITIC"` reads as `UPDATED` with genre and direction intact; fails if enums are `@Serializable` or matched with `valueOf` |
| AC-7 | unit | P0 red | `CatalogueFilterRepositoryTest.read_unknownDirection_fallsBackToDesc` | `"direction":"UP"` reads as `DESC` |
| AC-7 | unit | P1 red | `CatalogueFilterRepositoryTest.read_genreWithoutTitle_isDropped` | `{"genreId":9}` yields `genre = null` |
| AC-8 | unit | P0 red | `restoredAnime_withShowAnimeOff_skipsTheGenreKeepsTheSort` | Stored «Аниме» + `YEAR`/`ASC` on `allMovie`, `showAnime = false`: first call is `ContentFilter(sort = YEAR, sortDirection = ASC).applyTo(allMovie)`, `filterState.genreTitle == null`; fails without the anime skip |
| AC-8 | unit | P1 red | `restoredAnime_withShowAnimeOn_isApplied` | Same store, `showAnime = true`: first call carries genre 25; fails if the skip ignores the preference |
| AC-8 | unit | P1 red | `restoredAnime_closedUnchanged_keepsTheStoredGenre` | With the genre skipped, open and close the panel: stored entry still has `genreId` 25 (brief Q3) |
| AC-9 | unit | P0 red | `shelfGrid_neverReadsOrWritesTheStore` | `filter_popular` seeded; a `popular` grid pages its base config cacheable, and after panel and reset actions `transactions` is empty; fails if `restoreFilter` or the write points skip the `filterable` gate |
| guard | unit | P0 guard | `filterReset_restoresTheBaseQueryAndRepagesCached`, `filterPanelClosed_withAFilter_readsTheFirstPageUncached`, `filterState_isUnavailableForAShelfConfig`, `filteredEmptyPage_keepsTheFilterActive` | Existing filter behaviour with an empty store |
| guard | unit | P0 guard | `retry_forcesTheFirstPageRead_whereOpeningTheGridDoesNot`, `SectionVMTest` | `initialCacheable` defaults to `true` for `SectionVM` and unfiltered grids |
| guard | instrumented | P0 guard | `ShowAllFilterFocusTest.coldOpen_focusesTheFirstCardNotFilters`, `resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard` | «Фильтры» gate and reset focus unchanged; the second fails if the «Сброс» gate also blocks it after the first list lands |

Instrumented rows run through `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest` (qa-device).

### Manual checks

Device rows, run by qa-device on the box `192.168.1.110:5555`, package `com.kino.puber.stage` only, worktree dev APK installed with `tools/android-apk-install-preserve`. `adb` below means `adb -s 192.168.1.110:5555`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 (AC-1) | 1. Open Movies browse grid, `KEYCODE_DPAD_UP` to «Фильтры», `KEYCODE_DPAD_CENTER`; choose genre «Драма», sort «по году ↑»; `KEYCODE_BACK` to apply. 2. `adb shell am force-stop com.kino.puber.stage`. 3. Launch the app, navigate to Movies, open the browse grid. 4. Screenshot as soon as the header draws, and after the grid lands | Header shows the summary («Драма», year ascending) and «Сброс» in the first screenshot; cards ordered by year ascending; focus ring on card 1 | Crash; «Сброс» or «Фильтры» focused; unfiltered cards first then a swap; empty grid |
| M2 (AC-2, AC-5) | 1. From M1, `KEYCODE_BACK` out of the grid and reopen it via the «Все» tile. 2. `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_RIGHT` to «Сброс», `KEYCODE_DPAD_CENTER`. 3. `KEYCODE_BACK`, reopen the grid | Step 1 opens filtered; after step 3 the grid is unfiltered with no summary and no «Сброс» | Filter survives the reset; focus lost after reset |
| M3 (AC-3) | With Movies filtered, open the Series and 4K browse grids | Each opens unfiltered (or with its own stored filter) and focus lands on card 1 | Movies' genre or sort on another grid; crash on open |
| M4 (AC-8) | 1. Apply genre «Аниме» on Movies. 2. Turn show-anime off in settings. 3. Reopen the Movies grid | Grid shows cards with the stored sort, no genre in the summary | Empty grid; endless loading |

### Perf

N/A: perf track is off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Key the store per browse config id or per TabType? | no | answered | Per config id |
| Q2 | Clear stored filters on logout? | no | answered | No, keep per device |
| Q3 | Stored «Аниме» genre while show-anime is off: drop or skip? | no | answered | Skip on restore, keep stored |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | Q1-Q3 answered with the analyst's recommendations | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved as written (size M, tracks none, 9 ACs) | owner (standing instruction: recommended) |
