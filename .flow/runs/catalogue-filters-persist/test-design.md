# Test design: catalogue-filters-persist

Base ef5987ec. ACs: `brief.md#Acceptance criteria`. Design: `design-architect.md`. Paths under `app/src/`.

## Fixtures

| Test file | Fixture |
|---|---|
| `test/kotlin/com/kino/puber/data/preferences/CatalogueFilterRepositoryTest.kt` (new) | Real `CatalogueFilterRepository` over `FakeSharedPreferences` with a mocked `Context`, as `NavigationPreferencesRepositoryTest.kt:473-490`; malformed values are seeded as raw strings in `values["filter_<id>"]` |
| `test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt` | `createVM` and `filterRig` gain `filterRepository` (default: real repository over a fresh `FakeSharedPreferences`) and `showAnime: Boolean = true`, both set before construction because restoration runs in the constructor. Reopen = a second rig on the same repository. "Writes nothing" = `FakeSharedPreferences.transactions` unchanged. Assertions use the existing `FirstPageCall(config, cacheable)` list |
| `androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt` | Existing host without a VM; `activeFilter` + `ShowAllViewState.Loading` stands for a restored filter on a cold open |

On the base commit the new unit tests do not compile (no `CatalogueFilterRepository`, no `filterRepository` argument); that is their red. The instrumented red test compiles on base and fails on behaviour.

## Risk profile

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

## Traceability

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

## Manual checks

Device rows, run by qa-device on the box `192.168.1.110:5555`, package `com.kino.puber.stage` only, worktree dev APK installed with `tools/android-apk-install-preserve`. `adb` below means `adb -s 192.168.1.110:5555`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 (AC-1) | 1. Open Movies browse grid, `KEYCODE_DPAD_UP` to «Фильтры», `KEYCODE_DPAD_CENTER`; choose genre «Драма», sort «по году ↑»; `KEYCODE_BACK` to apply. 2. `adb shell am force-stop com.kino.puber.stage`. 3. Launch the app, navigate to Movies, open the browse grid. 4. Screenshot as soon as the header draws, and after the grid lands | Header shows the summary («Драма», year ascending) and «Сброс» in the first screenshot; cards ordered by year ascending; focus ring on card 1 | Crash; «Сброс» or «Фильтры» focused; unfiltered cards first then a swap; empty grid |
| M2 (AC-2, AC-5) | 1. From M1, `KEYCODE_BACK` out of the grid and reopen it via the «Все» tile. 2. `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_RIGHT` to «Сброс», `KEYCODE_DPAD_CENTER`. 3. `KEYCODE_BACK`, reopen the grid | Step 1 opens filtered; after step 3 the grid is unfiltered with no summary and no «Сброс» | Filter survives the reset; focus lost after reset |
| M3 (AC-3) | With Movies filtered, open the Series and 4K browse grids | Each opens unfiltered (or with its own stored filter) and focus lands on card 1 | Movies' genre or sort on another grid; crash on open |
| M4 (AC-8) | 1. Apply genre «Аниме» on Movies. 2. Turn show-anime off in settings. 3. Reopen the Movies grid | Grid shows cards with the stored sort, no genre in the summary | Empty grid; endless loading |

## Perf

N/A: perf track is off.

## UNTESTABLE

None.
