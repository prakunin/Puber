# Sort and genre filter for the whole-type catalogue grid - plan

**Spec:** `docs/superpowers/specs/2026-11-24-catalogue-filters.md`
**Goal:** Browse grids get a «Фильтры» button that opens a side panel with sort (field + direction) and one genre; closing the panel with a change re-pages the grid with the filtered query, outside the persistent first-page cache.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/dev/orca/workspaces/Puber/catalogue-filters && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `cd /home/dev/orca/workspaces/Puber/catalogue-filters && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Never `./gradlew` in the worktree. The full gate runs green after Task 7.
- Touched unit tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`. A task that touches `app/src/androidTest` also runs `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
- Instrumented tests run only as `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>` (`Makefile:23` `GRADLE ?= ./gradlew`), by qa-device holding the device lease; the implementer writes and compiles them. Manual checks 1-7 of the spec's Test design are qa-device's.
- One task = green fast gate + green tests of the touched code + one commit; message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- Unit tests marked `red` in the spec's Traceability are written first and seen failing on the code before the task (a compile failure on a missing symbol counts); record it under Red run. Instrumented `red` tests are written in their task and first run by qa-device.
- Visual numbers: `docs/design/showall/README.md:120-187` («catalogue-filters starting values (2026-11-24)») and the spec's UX section (Elements A-C, Focus). The stand is already published (1928ce5f); do not edit it.
- Out of scope: country, year, «Статус» and `getCountries` (`data/api/KinoPubApiClient.kt:641-643`); persistence across restarts; rating ranges, quality and `SortField.KINOPOISK` / `IMDB` in the panel; a button on `all_cartoon` / `all_anime`; new `getItems` params; baseline profile regeneration; any change to `SectionConfig`, `ShowAllScreen` args or its key.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Unit tests are under `app/src/test/kotlin/com/kino/puber/`, instrumented under `app/src/androidTest/kotlin/com/kino/puber/`.

---

## Tasks

### Task 1: Decode the genres list from its `{status, items}` body

**Covers:** AC-10 (decode, type param)
**Files:**
- Modify: `data/api/KinoPubApiClient.kt:632-636` - `getGenres` becomes `apiCall<ApiResponseList<Genre>> { … }.map { it.items.orEmpty() }`, as `getBookmarks` does (`:479-482`); signature unchanged. `ApiResponseList` is `data/api/models/Models.kt:11-15` (already imported, `:24`); the item's extra `type` field is dropped by `ignoreUnknownKeys` (`:131`) into `Genre` (`Models.kt:197-200`)
- Create test: `data/api/KinoPubApiClientGenresTest.kt` - copy the companion (`Log` / `Base64` static mocks), `client`, `withServer` and `respond` of `KinoPubApiClientFailureTest` (`data/api/KinoPubApiClientFailureTest.kt:36-56`, `:179-231`); path `/v1/genres`

| Test | Priority | Body |
|---|---|---|
| `getGenres_unwrapsTheItemsOfAStatusItemsBody` | P0 red | `{"status":200,"items":[{"id":9,"title":"Драма","type":"movie"}]}` → `[Genre(9, "Драма")]` |
| `getGenres_sendsTheTypeParameter` | P1 guard | handler records `exchange.requestURI.query`; `getGenres("serial")` sends `type=serial` |
| `getGenres_refusedRead_failsInsteadOfAnEmptyList` | P1 guard | 422 → `ApiError.Http` with status 422, as `:58-74` |

**Consumes:** nothing new. **Produces:** `KinoPubApiClient.getGenres(type: String?): Result<List<Genre>>` that decodes the live body; `GenreInteractor.getGenres(type)` (`domain/interactor/genre/GenreInteractor.kt:16-26`, Koin `singleOf` at `domain/di/modules.kt:42`) now works unchanged.

- [x] Write the three tests; run them on the unchanged client: the P0 one fails (bare-list decode)
- [x] Change `getGenres`; tests green; fast gate; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.api.KinoPubApiClientGenresTest'` passes 3 tests.

---

### Task 2: Map a filter onto a browse config and name the filterable grids

**Covers:** AC-2 (config → filterable), AC-5 (query shape)
**Files:**
- Modify: `domain/model/ContentFilter.kt:6-16` - add a body to the data class: `val isActive: Boolean get() = this != ContentFilter()`. `SortField` (`:18-26`) and `SortDirection` (`:28-31`, ASC = `""`) unchanged
- Create: `ui/feature/showall/model/ContentFilterQuery.kt` - `applyTo` (domain must not import the UI config)
- Modify: `ui/feature/contentlist/model/TabTypeConfig.kt` - add `isFilterable` beside `browseEntryFor` (`:12-25`); the browse configs are `all_$type` (`:102-108`, `:145-150`) and `all_4k` (`:210-215`); `all_cartoon` (`:169-175`) and `all_anime` (`:194-200`) have no entry and stay unfilterable
- Create test: `ui/feature/showall/model/ContentFilterQueryTest.kt`
- Modify test: `ui/feature/contentlist/model/TabTypeConfigTest.kt` (class at `:15`)

**Produces:**
```kotlin
// ContentFilterQuery.kt
internal fun ContentFilter.applyTo(base: SectionConfig): SectionConfig =
    if (!isActive) base
    else base.copy(sort = sort.apiValue + sortDirection.apiValue, genre = genre?.id?.toString() ?: base.genre)

// TabTypeConfig
fun isFilterable(config: SectionConfig): Boolean =
    TabType.entries.any { browseEntryFor(it)?.config?.id == config.id }
```

| Test | Priority |
|---|---|
| `ContentFilterQueryTest.applyTo_ascending_sendsTheBareFieldName` (YEAR + ASC → `sort == "year"`) | P0 red |
| `ContentFilterQueryTest.applyTo_descendingWithGenre_keepsIdTypeAndQuality` (on `all_movie` and `all_4k`: `id`, `type`, `quality`, `animeFilterMode` kept, `genre == "9"`) | P0 red |
| `ContentFilterQueryTest.applyTo_defaultFilter_returnsTheBaseConfig` (also `ContentFilter().isActive == false`) | P0 red |
| `TabTypeConfigTest.isFilterable_trueForEveryBrowseEntryConfig` | P0 red |
| `TabTypeConfigTest.isFilterable_falseForShelvesAnd4kTypeGrids` (fresh / hot / popular / `4k_<type>` / `popular_4k` / `all_cartoon` / `all_anime`) | P0 red |
| `TabTypeConfigTest.browseEntryConfigs_haveTheDefaultFilterQuery` (every `browseEntryFor` config: `sort == "updated-"`, `genre == null`) | P0 guard |

- [x] Write the tests; red run; implement; tests green; fast gate; commit

**Done when:** `ContentFilterQueryTest` (3) and `TabTypeConfigTest` (existing + 3) pass.

---

### Task 3: Uncached first pages and the show-anime preference in the interactor

**Covers:** AC-5 (request shape guard), AC-7, AC-9 (preference read)
**Files:**
- Modify: `domain/interactor/contentlist/ContentListInteractor.kt`
  - `observeFirstPage` (`:56-86`) gains `cacheable: Boolean = true` as third param; `cacheable == false` returns `flow { emit(Cached.Value(loadPage(config, page = FIRST_PAGE), isStale = false)) }`, the shortcut path's shape (`:63-67`), before the store is touched. `cacheKey` (`:107-123`) unchanged
  - add `val showAnimeEnabled: Boolean get() = navigationPreferencesRepository.contentPreferences.value.showAnime` beside `hideWatchedEnabled` (`:48-49`)
- Modify test: `domain/interactor/contentlist/ContentListInteractorTest.kt` - class `ContentListInteractorTest` (`:142`) on the fixture (`:46-140`: mocked `api`, `contentPreferences`, `ContentCacheRepository` over `FakePayloadStore` `:71`, `observedPages` `:125`); `observedPages` gains `cacheable: Boolean = true` passed through

| Test | Priority | Proves |
|---|---|---|
| `filteredBrowseConfig_sendsSortAndGenreWithTheBaseType` | P0 guard | `ContentFilter(genre = Genre(9,…), sort = YEAR, ASC).applyTo(all_movie)` → `api.getItems("movie", "year", 1, null, "9")` |
| `filtered4kConfig_sendsSortAndGenreWithTheBaseQuality` | P1 guard | same on `all_4k` → `getItems(null, "rating-", 1, "4k", "9")` |
| `observeFirstPage_notCacheable_neitherReadsNorWritesTheStore` | P0 red | a stored entry for the same config is not served; after the read the store holds no entry written by it |
| `observeFirstPage_afterAFilteredRead_theUnfilteredPageStillComesFromTheStore` | P0 red | base read, filtered uncached read, base read again: the third makes no server call |
| `showAnimeEnabled_followsThePreference` | P1 red | flips with `contentPreferences.value.showAnime` |

- [x] Write the tests; red run; implement; tests green (whole `ContentListInteractorTest`); fast gate; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.domain.interactor.contentlist.*'` passes.

---

### Task 4: Changeable query in the paging VM and the filter state of `ShowAllVM`

**Covers:** AC-2 (VM), AC-4 (no re-page), AC-5 (VM), AC-6 (VM), AC-7 (VM), AC-8, AC-9, AC-10 (VM), AC-12 (VM)
**Files:**
- Modify: `ui/feature/contentlist/vm/ContentListPagingVM.kt`
  - constructor param `config` (`:33`) becomes `initialConfig: SectionConfig` (both subclasses pass it positionally: `ShowAllVM.kt:32-39`, `SectionVM.kt:31-39`); body gets `@Volatile private var config = initialConfig`, `@Volatile private var cacheable = true`, `private val queryGeneration = AtomicInteger(0)` beside `walkGeneration` (`:72`)
  - add `protected fun changeQuery(config: SectionConfig, cacheable: Boolean)`: set both, `queryGeneration.incrementAndGet()`, `walkGeneration.incrementAndGet()`, then `resetPaging(null)` (`:111-114`)
  - `onLoadFirstPage` (`:116-148`): read `val query = config`, `val queryCacheable = cacheable`, `val generation = queryGeneration.get()` beside `force`, before `pagingLaunch`; call `interactor.observeFirstPage(query, force = force, cacheable = queryCacheable)`; skip any emission once `generation != queryGeneration.get()`; pass `query` / `generation` on
  - `publishFirstPage` (`:165-171`) takes `query` and `generation`; `restoreLoadedDepth` (`:194-233`) reads pages with `query` (`:216`); publish only while `generation == queryGeneration.get()`, checked after the restore returns
  - `walkStep` (`:249-251`), `publishWalkStep` (`:253-257`) and `resumeWalkAfterPause` (`:321-341`) capture `config` with the generation at the call and load with the captured config; the existing `walkGeneration` checks (`:255`, `:337`) drop old-query steps because `changeQuery` moved it
- Create: `ui/feature/showall/model/ShowAllFilterState.kt`, `ui/feature/showall/model/ShowAllAction.kt` (shapes below)
- Modify: `ui/feature/showall/vm/ShowAllVM.kt` - constructor (`:24-39`) gains `private val genreInteractor: GenreInteractor` after `interactor` and `pagingCoroutineContext: CoroutineContext = Dispatchers.Default` last, passed to the super call as `SectionVM` does; filter fields and handling below; `handleAction` (`:73-92`) routes `is ShowAllAction` to it after the `closing` check (`:74`)
- Modify: `ui/feature/showall/ShowAllScreen.kt:43-53` - `genreInteractor = get()` (global single). Key (`:36`) and args unchanged
- Modify: `docs/architecture/paging-and-filters.md:128` - replace the line with: a grid changes its query through `ContentListPagingVM.changeQuery(config, cacheable)`, which drops pages of the old query and calls `resetPaging`; `ShowAllVM` builds the query with `ContentFilter.applyTo(base)` and reads a filtered first page with `cacheable = false`
- Modify test: `ui/feature/showall/vm/ShowAllVMTest.kt` - `createVM` (`:333-346`) gains `genreInteractor: GenreInteractor` (a `mockk` field set in `setup` `:58-64`) and `pagingCoroutineContext`; `servesFirstPageFromTheNetwork` (`:326-331`) stubs `observeFirstPage(any(), any(), any())`

**Produces:**
```kotlin
@Immutable
internal data class ShowAllFilterState(
    val available: Boolean,
    val isActive: Boolean = false,                 // applied filter
    val genreTitle: String? = null,                // applied
    val sort: SortField = SortField.UPDATED,       // applied
    val direction: SortDirection = SortDirection.DESC, // applied
    val panelOpen: Boolean = false,
    val draft: ContentFilter = ContentFilter(),
    val sortOptions: List<SortField> = FILTER_SORT_OPTIONS, // UPDATED, CREATED, YEAR, RATING, VIEWS
    val genres: FilterGenres = FilterGenres.Loading,
) { companion object { val Unavailable = ShowAllFilterState(available = false) } }

@Immutable
internal sealed interface FilterGenres {
    data object Loading : FilterGenres
    data class Loaded(val genres: List<Genre>) : FilterGenres
    data object Failed : FilterGenres
}

internal sealed interface ShowAllAction : UIAction {   // UIAction: core/ui/uikit/model/Actions.kt:5
    data object FilterPanelOpened : ShowAllAction
    data object FilterPanelClosed : ShowAllAction
    data class SortFieldChosen(val field: SortField) : ShowAllAction
    data class SortDirectionChosen(val direction: SortDirection) : ShowAllAction
    data class GenreChosen(val genre: Genre?) : ShowAllAction
    data object GenresRetry : ShowAllAction
    data object FilterReset : ShowAllAction
}

// ShowAllVM
val filterState: StateFlow<ShowAllFilterState>
```

**`ShowAllVM` behaviour** (`base` = the constructor config; `filterable = TabTypeConfig.isFilterable(base)`; initial state `ShowAllFilterState(available = true)` or `Unavailable`; every `ShowAllAction` is ignored when `!filterable`):

| Action | Effect |
|---|---|
| `FilterPanelOpened` | `draft = applied`, `panelOpen = true`; `hideAnime = base.animeFilterMode == FollowPreference && !interactor.showAnimeEnabled` read now; genres already loaded → `Loaded` without `ANIME_GENRE_ID` (`data/api/models/ContentGenres.kt:4`) when `hideAnime`; else start the load |
| genre load | one job at a time (`genresJob?.isActive` → return); `Loading`, then `genreInteractor.getGenres(base.type ?: base.quality)` in `launch`: success keeps the raw list in the VM and publishes it filtered as above, failure → `Failed`; the list state is not touched |
| `SortFieldChosen` / `SortDirectionChosen` / `GenreChosen` | update `draft` only |
| `GenresRetry` | the genre load |
| `FilterPanelClosed` | `panelOpen = false`; `draft != applied` → `applied = draft`, one `changeQuery(applied.applyTo(base), cacheable = !applied.isActive)`; equal → nothing else |
| `FilterReset` | `applied` active → `applied = draft = ContentFilter()`, `changeQuery(base, cacheable = true)`; inactive → nothing |

The summary fields (`isActive`, `genreTitle`, `sort`, `direction`) always come from `applied`, never `draft`.

| Test (`ShowAllVMTest`) | Priority |
|---|---|
| `filterState_isUnavailableForAShelfConfig` | P1 |
| `filterActions_areIgnoredOnAShelfGrid` | P1 |
| `filterPanelClosed_withoutAChange_doesNotRepage` (counts `observeFirstPage` calls; also choose-then-revert) | P0 red |
| `filterPanelClosed_afterSortAndGenreChosen_repagesOnceWithTheFilteredQuery` (captures the `SectionConfig`) | P0 red |
| `filterReset_restoresTheBaseQueryAndRepagesCached` | P0 red |
| `filterState_summarisesTheAppliedGenreAndSort` | P1 red |
| `filterPanelClosed_withAFilter_readsTheFirstPageUncached` | P0 red |
| `filterChange_dropsAnOldFirstPageStillInFlight` | P0 red |
| `filterChange_dropsAnOldWalkStepStillInFlight` | P0 red |
| `filterChange_dropsAnOldDepthRestoreStillInFlight` | P1 red |
| `genreList_withAnimeHidden_omitsAnime` | P0 red |
| `genreList_withAnimeShown_keepsAnime` | P1 red |
| `genreLoadFailure_showsFailedAndKeepsTheGrid` | P0 red |
| `genresRetry_loadsAgainAfterAFailure` | P1 red |
| `genreList_forA4kGrid_requestsThe4kType` | P1 red |
| `filteredEmptyPage_keepsTheFilterActive` | P1 red |

Filterable tests use `TabTypeConfig.browseEntryFor(TabType.Movies)!!.config` (`all_4k` for the 4K one); shelf tests keep the default `popular` config. The AC-8 tests use the delayed-fake pattern of `SectionVMRefreshDepthTest.kt:35-66`: `runTest`, `StandardTestDispatcher(testScheduler)` as `pagingCoroutineContext` and as the `Paginator.Store` context, an old-query `observeFirstPage` / `loadPage` that suspends on a `CompletableDeferred` and is completed after `FilterPanelClosed`, then `advanceUntilIdle`; assert none of its ids reach `ShowAllViewState.Content`.

- [x] Write the 16 tests; red run
- [x] Implement the paging VM change, then `ShowAllVM`, DI, the doc line
- [x] Guards green: `ShowAllVMTest` (existing), `SectionVMTest`, `SectionVMRefreshDepthTest`, `ContentListVMTest` (incl. `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle`, `:605`, AC-11 P0 guard); fast gate; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.showall.*' --tests 'com.kino.puber.ui.feature.contentlist.*'` passes.

---

### Task 5: Lift the compact button and the side-panel chrome into `core/ui/uikit`

**Covers:** spec UX «Shared constants: parameterise, do not retune in place»; no behaviour or look change
**Files:**
- Create: `core/ui/uikit/component/TvCompactButton.kt` - `TvCompactButton(text, onClick, modifier, outlined = false)`: tv-material3 `Button` / `OutlinedButton`, `.height(24.dp)`, `contentPadding = PaddingValues(horizontal = 12.dp)`, label `labelLarge` 11 / 16 sp Medium, 1 line, ellipsis - the values of `ui/feature/contentlist/content/BrowseAllEntry.kt:30-31`, `:78-101`
- Modify: `ui/feature/contentlist/content/BrowseAllEntry.kt:78-101` - draw through `TvCompactButton`, keeping its modifier chain (`padding(top = EntryTopPadding)`, `focusRequester`, `focusProperties`, `onFocusChanged`); drop `EntryHeight` / `EntryHorizontalPadding` (`:30-31`)
- Move: `ui/feature/player/component/PlayerGlass.kt` → `core/ui/uikit/component/panel/PanelGlass.kt`, renaming `PlayerGlass` (`:26-30`) → `PanelGlass` and `Modifier.playerGlass` (`:33`) → `Modifier.panelGlass`; update `ResumeDialog.kt:112-114`, `PlayerSidePanel.kt:134-139` and the KDoc link `PlayerInfoPanel.kt:125`
- Create: `core/ui/uikit/component/panel/TvSidePanel.kt` - `TvSidePanelSurface(title, modifier, minWidth = 240.dp, maxWidth = 420.dp, cornerRadius = 20.dp, padding = PaddingValues(20.dp, 20.dp, 20.dp, 16.dp), content: @Composable ColumnScope.() -> Unit)` = the `Column` and title of `ui/feature/player/component/PlayerSidePanel.kt:122-162`; `TvSidePanelSectionHeader` = body of `:222-235`; `TvSidePanelItem` = body of `:237-315`
- Modify: `ui/feature/player/component/PlayerSidePanel.kt` - `PlayerSidePanel` (`:88-165`) keeps its `AnimatedVisibility` and gradient scrim and draws `TvSidePanelSurface` inside; `PlayerPanelSectionHeader` / `PlayerPanelItem` become one-line delegates (player callers unchanged); constants `:80-86` become the surface's default arguments (no named `Panel*` vals remain)

- Merge conflict expected: branch `player-overlays-port` rewrites `ResumeDialog.kt` (no `playerGlass` call left there) and keeps calling `PlayerPanelItem` / `PlayerPanelSectionHeader`. Whichever lands second resolves `ResumeDialog.kt` by hand and re-applies the `PanelGlass` rename to any `PlayerGlass` use it adds; the delegates keep the player callers compiling.
- [x] Move and delegate; fast gate; `./tools/agentw :app:testDevDebugUnitTest` and `:app:compileDevDebugAndroidTestKotlin` green; commit

**Done when:** fast gate and unit suite green; `grep -rnE 'EntryHeight|PanelMinWidth|fun Modifier.playerGlass' app/src/main/java` finds nothing.

---

### Task 6: Header controls, filtered empty state and the grid's focus pins

**Covers:** AC-1, AC-2 (UI), AC-3, AC-6 (UI), AC-11 (UI), AC-12 (UI)
**Files:**
- Modify: `app/src/main/res/values/main_screen_strings.xml` after `show_all_title_shelf` (`:43`) and `app/src/main/res/values-en/strings.xml` after `:188` (the locale carries every string):

| Name | ru | en |
|---|---|---|
| `show_all_filters` | Фильтры | Filters |
| `show_all_filters_reset` | Сброс | Reset |
| `show_all_filters_sort` | Сортировка | Sort |
| `show_all_filters_sort_field` | Поле | Field |
| `show_all_filters_sort_order` | Порядок | Order |
| `show_all_filters_genre` | Жанр | Genre |
| `show_all_filters_genre_all` | Все жанры | All genres |
| `show_all_sort_updated` / `_created` / `_year` / `_rating` / `_views` | по обновлению / по дате добавления / по году / по рейтингу / по просмотрам | by update / by date added / by year / by rating / by views |
| `show_all_sort_desc` / `show_all_sort_asc` | по убыванию / по возрастанию | descending / ascending |
| `show_all_sort_summary` | `%1$s %2$s` (label, arrow) | same |
| `show_all_sort_arrow_desc` / `_asc` | ↓ / ↑ | ↓ / ↑ |
| `show_all_filters_summary_join` | `%1$s · %2$s` | same |
| `show_all_filters_genres_error` | Не удалось загрузить жанры | Could not load genres |
| `show_all_filters_retry` | Повторить | Retry |
| `show_all_filtered_empty` | Ничего не найдено. Активен фильтр: %1$s | Nothing found. Filter: %1$s |

- Modify: `ui/feature/showall/content/ShowAllHeader.kt:15-27` - `HeaderPadding` top 12 → 10; one `Row` 24 dp high, centred: title, then when `filter.available`: 16 dp, «Фильтры» (`TvCompactButton`, filled), and when `filter.isActive`: 8 dp, «Сброс» (outlined), 12 dp, summary (11 / 16 sp Regular, `onSurfaceVariant`, 1 line, ellipsis). Shelf grids get the same row with the title only. Add `@Composable internal fun filterSummary(filter: ShowAllFilterState): String`: genre title first, then sort label + arrow only when not UPDATED / DESC, joined by `show_all_filters_summary_join`
- Modify: `ui/feature/showall/content/ShowAllScreenContent.kt`
  - `ShowAllScreenContent` (`:57-87`) gains `filter: ShowAllFilterState = ShowAllFilterState.Unavailable` as the last param (callers use named args); owns `filtersFocus`, `rowOneFocus`, `emptyResetFocus` (`FocusRequester`s) and the cold-open latch `filtersCanFocus` (`rememberSaveable`, set true by a `LaunchedEffect(state is Loading)` once state is not `Loading`), the gate `BrowseAllEntry.kt:39-46` uses
  - Empty branch (`:68-73`): with `filter.isActive`, `show_all_filtered_empty` with `filterSummary`, 12 dp, outlined «Сброс» carrying `emptyResetFocus`; else today's text
  - `ShowAllContentBody` (`:89-172`) gains `upTarget: FocusRequester?` (`filtersFocus` when `filter.available && filtersCanFocus`) and `rowOneFocus`; cards at `index < SHOW_ALL_GRID_COLUMNS` (`:147-163`) get `focusProperties { up = upTarget }` when non-null; the body remembers the last focused row-1 card id (updated in the card's `onFocusChanged`) and attaches `rowOneFocus` to it, or to the first card when that id is not in row 1
- Focus pins (spec UX «Focus»), all via `focusProperties`:

| Node | up | down | left | right |
|---|---|---|---|---|
| «Фильтры» | `Cancel` | `rowOneFocus` in `Content`, `emptyResetFocus` in filtered `Empty`, else `Default` | `Default` (rail) | «Сброс» when shown, else `Cancel` |
| header «Сброс» | `Cancel` | as «Фильтры» | `filtersFocus` | `Cancel` |

  «Фильтры» also has `canFocus = filtersCanFocus` and `onClick = onAction(FilterPanelOpened)`. Both «Сброс» buttons run `runCatching { filtersFocus.requestFocus() }` before `onAction(FilterReset)`; when the reset list lands (`Loading` → `Content`), the remounted `ShowAllContentBody`'s launch request (`ShowAllScreenContent.kt:94`, `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:60-88`) moves focus to card 1 (plan Q1).
- Modify: `ui/feature/showall/ShowAllScreen.kt:57-67` - `val filter by vm.filterState.collectAsStateWithLifecycle()` (as `core/ui/PuberVM.kt:7` imports it), passed as `filter`
- Create test: `ui/feature/showall/content/ShowAllFilterFocusTest.kt` - pattern of `ShowAllScreenContentFocusTest.kt:31-60`, `:240-268` (`createComposeRule`, `PuberTheme`, `LocalContentFocusActive provides true`, `performKeyInput`); state and filter in test-held `mutableStateOf`s, `onAction` records actions and applies `FilterReset` / `FilterPanelOpened` / `FilterPanelClosed` to the held filter
- Modify test: `ui/feature/showall/content/ShowAllScreenContentFocusTest.kt` - add after `:76-100` (same steps, active filter)
- Modify test: `ui/feature/showall/content/ShowAllRailCloseFocusTest.kt` - `RailWithShowAll` (`:249`, call at `:288`) and `setUpScreen` (`:189-203`) take a `filter` (default `Unavailable`)

| Test | Priority |
|---|---|
| `ShowAllFilterFocusTest.coldOpen_focusesTheFirstCardNotFilters` (state `Loading` then `Content`) | P0 red |
| `ShowAllFilterFocusTest.unavailableFilter_showsNoFiltersButton` | P1 |
| `ShowAllFilterFocusTest.upFromFirstAndLastRowOneCard_landsOnFilters` (columns 1 and 6, filter active so «Сброс» is shown) | P0 red |
| `ShowAllFilterFocusTest.downFromFilters_returnsToTheRowOneCardItCameFrom` | P0 red |
| `ShowAllFilterFocusTest.resetPressed_focusesFiltersThenFirstCardWhenListLands` (filter active, «Сброс» OK; the test then sets state `Loading`, then `Content`): focus on «Фильтры» while `Loading`, on card 1 after `Content` | P0 red |
| `ShowAllFilterFocusTest.filteredEmpty_showsResetAndDownFromFiltersReachesIt` | P1 red |
| `ShowAllScreenContentFocusTest.activeFilter_focusReturnsToTheFocusedCardAfterReentry` (active filter, card 3 of row 2 focused, handoff and re-entry): fails on card 1 or «Фильтры»; red on base by compile (no `filter` param) | P0 red |
| `ShowAllRailCloseFocusTest.leftFromFilters_opensTheRail` (drawer `currentValue == Open`) | P1 |

- [x] Strings, header, content, screen wiring; write the tests; fast gate; `:app:compileDevDebugAndroidTestKotlin`; unit suite of `showall`; commit

**Done when:** fast gate and androidTest compile green; `ShowAllScreenContentFocusTest` existing tests unchanged (AC-1 guard, run by qa-device with `ShowAllFilterFocusTest` and `ShowAllRailCloseFocusTest`).

---

### Task 7: The filter panel

**Covers:** AC-4, AC-5 (focus), AC-9 (UI), AC-10 (UI)
**Files:**
- Create: `ui/feature/showall/content/ShowAllFilterPanel.kt` - `ShowAllFilterPanel(filter: ShowAllFilterState, onAction: (UIAction) -> Unit)`:
  - `TvDialogOverlay(onDismiss = { onAction(FilterPanelClosed) }, contentAlignment = Alignment.CenterEnd)` (`core/ui/uikit/component/TvDialogOverlay.kt:39-73`) around `TvSidePanelSurface` (Task 5) with `heightIn(max = 500.dp)`
  - pages `Root` / `Sort` / `Genre` in `rememberSaveable`; `BackHandler(enabled = page != Root)` inside the content returns to `Root` (registered after the overlay's `:56-58`, so it wins) and focuses the door it came from
  - Root: doors «Сортировка» (trailing: sort label + arrow of `draft`) and «Жанр» (trailing: `draft.genre?.title` or «Все жанры»); Sort: section «Поле» with `filter.sortOptions`, section «Порядок» DESC / ASC; Genre: «Все жанры» (`GenreChosen(null)`), then `FilterGenres.Loaded` rows; `Loading`: one row with a 20 dp progress indicator; `Failed`: a read-only text row and a focusable «Повторить» (`GenresRetry`). Rows are `TvSidePanelItem` with `selected` from `draft`; value OK dispatches and stays on the page
  - each page is a `Column` + `verticalScroll`, not a `LazyColumn`: the surface measures `IntrinsicSize.Max` width (`PlayerSidePanel.kt:125`), which a lazy list cannot answer; ~31 rows at most
  - first focus per page (root: «Сортировка»; sub-page: the selected row, `Failed`: «Повторить») by the delayed-request loop of `core/ui/uikit/component/TvContextMenu.kt:77-92` keyed on the page
- Modify: `ui/feature/showall/content/ShowAllScreenContent.kt` - root becomes a `Box` holding the Task 6 `Column` and, when `filter.panelOpen`, the panel under `CompositionLocalProvider(LocalTvDialogFocusRestorer provides headerRestorer)` (`TvDialogOverlay.kt:32-37`), `headerRestorer = TvDialogFocusRestorer(onDialogOpening = {}, onDialogClosed = { runCatching { filtersFocus.requestFocus() } })`; outside the grid's restorer (`:103-111`). The grid area's content is wrapped in `ContentFocusGate(active = !filter.panelOpen)` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:39-48`) so a list landing behind the panel cannot run the launch request (`:60-88`)
- Modify test: `ShowAllFilterFocusTest` (Task 6)

| Test | Priority |
|---|---|
| `filtersOk_opensThePanelOnItsFirstRow` | P0 red |
| `panelBack_closesWithFocusOnFilters` | P0 red |
| `panelOpen_dpadLeftAndRightStayInsideThePanel` | P1 |
| `subPageBack_returnsToTheDoorItCameFrom` (Back via `Espresso.pressBack` or the activity's dispatcher) | P1 |
| `newListLanding_whilePanelOpen_keepsFocusInThePanel` (state `Loading` → `Content` with the panel open) | P0 red |
| `newListLanding_afterApply_focusesTheFirstCard` (close, `Loading`, then `Content`) | P0 red |
| `genresFailed_showsTheErrorRowAndFocusableRetry` | P1 |

- [x] Panel, wiring, tests; fast gate; androidTest compile; full gate; commit

**Done when:** full gate green; androidTest compiles; qa-device's `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest` (then `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest`) is the instrumented settle.

---

## Coverage

| AC | Tasks | Test-design rows |
|---|---|---|
| AC-1 | 6 | `coldOpen_focusesTheFirstCardNotFilters`, `ShowAllScreenContentFocusTest` guard, Manual 1 |
| AC-2 | 2, 4, 6 | `isFilterable_*` (2), `filterState_isUnavailableForAShelfConfig`, `filterActions_areIgnoredOnAShelfGrid`, `unavailableFilter_showsNoFiltersButton` |
| AC-3 | 6 | `upFromFirstAndLastRowOneCard_landsOnFilters`, `downFromFilters_returnsToTheRowOneCardItCameFrom`, `leftFromFilters_opensTheRail`, Manual 2 |
| AC-4 | 4, 7 | `filterPanelClosed_withoutAChange_doesNotRepage`, `filtersOk_*`, `panelBack_*`, `panelOpen_*`, `subPageBack_*`, Manual 3 |
| AC-5 | 2, 3, 4, 7 | `ContentFilterQueryTest` (3), `browseEntryConfigs_haveTheDefaultFilterQuery`, `filterPanelClosed_afterSortAndGenreChosen_*`, `filtered*Config_sends*` (2), `newListLanding_*` (2), Manual 3 |
| AC-6 | 4, 6 | `filterReset_*`, `filterState_summarises*`, `resetPressed_focusesFiltersThenFirstCardWhenListLands`, Manual 4 |
| AC-7 | 3, 4 | `observeFirstPage_notCacheable_*`, `observeFirstPage_afterAFilteredRead_*`, `filterPanelClosed_withAFilter_readsTheFirstPageUncached` |
| AC-8 | 4 | `filterChange_drops*` (3), existing VM suites as guard |
| AC-9 | 3, 4, 7 | `genreList_withAnime*` (2), `showAnimeEnabled_followsThePreference` |
| AC-10 | 1, 4, 7 | `KinoPubApiClientGenresTest` (3), `genreLoadFailure_*`, `genresRetry_*`, `genreList_forA4kGrid_*`, `genresFailed_*` |
| AC-11 | 4, 6 | `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle`, `activeFilter_focusReturnsToTheFocusedCardAfterReentry`, Manual 5 |
| AC-12 | 4, 6 | `filteredEmptyPage_keepsTheFilterActive`, `filteredEmpty_showsResetAndDownFromFiltersReachesIt`, Manual 6 |
| AC-13 | 5, 6, 7 | Manual 7 |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| Task | Commit |
|---|---|
| 1 | 842b2dce Decode the genres list from its status-and-items body |
| 2 | a35c8c84 Map a content filter onto a browse config and name the filterable grids |
| 3 | 4cab2ac3 Read filtered first pages outside the cache and expose the show-anime preference |
| 4 | 248c29f5 Let the show-all grid change its query and keep a sort and genre filter in its view model |
| 5 | b7e5645a Lift the compact button and the side-panel chrome into the shared uikit |
| 6 | d8b7fcae Add the filter buttons, the filtered empty state and the row-one focus pins to the show-all grid |
| 7 | 7c1d018a Add the filter side panel with sort and genre pages to the show-all grid |

**Red run** (P0 `red` tests on the unchanged code; where a symbol did not exist a stub with a default result was added first):

- red: getGenres_unwrapsTheItemsOfAStatusItemsBody - AssertionFailedError at KinoPubApiClientGenresTest.kt:54 (bare-list decode returned a failure, not [Genre(9, "Драма")])
- red: ContentFilterQueryTest.applyTo_ascending_sendsTheBareFieldName - failed on assertion (stub applyTo returned the base config, sort "updated-" != "year")
- red: ContentFilterQueryTest.applyTo_descendingWithGenre_keepsIdTypeAndQuality - failed on assertion (stub returned base: sort "updated-" != "rating-")
- red: TabTypeConfigTest.isFilterable_trueForEveryBrowseEntryConfig - failed on assertion (stub returned false)
- green on stub: ContentFilterQueryTest.applyTo_defaultFilter_returnsTheBaseConfig, TabTypeConfigTest.isFilterable_falseForShelvesAnd4kTypeGrids - the stubs (identity / false) already give the asserted answer; both reject a naive implementation (always rewriting the query / always true), so they stay as guards against that
- red: ContentListInteractorTest.observeFirstPage_notCacheable_neitherReadsNorWritesTheStore - failed on assertion with the param accepted but ignored (stored page served instead of live)
- red: ContentListInteractorTest.showAnimeEnabled_followsThePreference - failed on assertion with the property not reading showAnime
- green on base: ContentListInteractorTest.observeFirstPage_afterAFilteredRead_theUnfilteredPageStillComesFromTheStore - cacheKey already contains sort and genre, so even a cached filtered read lands under its own key; kept as a guard that the base entry survives
- red (against a ShowAllVM stub with constructor and a fixed Unavailable filterState, no action handling; each failed on its assertion): ShowAllVMTest.filterPanelClosed_afterSortAndGenreChosen_repagesOnceWithTheFilteredQuery (1 observeFirstPage call, not 2), filterReset_restoresTheBaseQueryAndRepagesCached, filterState_summarisesTheAppliedGenreAndSort, filterPanelClosed_withAFilter_readsTheFirstPageUncached, filterChange_dropsAnOldFirstPageStillInFlight, filterChange_dropsAnOldWalkStepStillInFlight, filterChange_dropsAnOldDepthRestoreStillInFlight, genreList_withAnimeHidden_omitsAnime, genreList_withAnimeShown_keepsAnime, genreLoadFailure_showsFailedAndKeepsTheGrid, genresRetry_loadsAgainAfterAFailure, genreList_forA4kGrid_requestsThe4kType, filteredEmptyPage_keepsTheFilterActive
- green on stub: ShowAllVMTest.filterPanelClosed_withoutAChange_doesNotRepage - the stub never re-pages, so the assertion (1 call) holds trivially; it guards the implementation against re-paging on draft == applied (also choose-then-revert); filterState_isUnavailableForAShelfConfig and filterActions_areIgnoredOnAShelfGrid (P1) and filterReset_withoutAnActiveFilter_doesNothing (extra) likewise guard the shelf and no-op paths
- instrumented (box 192.168.1.110:5555, class ShowAllFilterFocusTest 13/13, ShowAllScreenContentFocusTest 9/9, ShowAllRailCloseFocusTest 7/7 green with the implementation; XML in docs/superpowers/evidence/2026-11-24-catalogue-filters-itest-impl/). Red on base is a compile failure for every new row: ShowAllFilterState, the `filter` parameter of ShowAllScreenContent and ShowAllFilterPanel do not exist on base.
- mutation checks on the box (implementation with one guard removed, then restored): removing the `up` pin on row-1 cards failed upFromFirstAndLastRowOneCard_landsOnFilters and downFromFilters_returnsToTheRowOneCardItCameFrom (focus not on Filters); emptying the header restorer failed panelBack_closesWithFocusOnFilters and newListLanding_afterApply_focusesTheFirstCard (timeout); removing ContentFocusGate did NOT fail newListLanding_whilePanelOpen_keepsFocusInThePanel in the compose-test host (it has no LocalScreenKey, so the grid body of the remounted list does not re-arm a first-focus request there) - that row is a guard in this harness, the gate is still needed on the real navigation stack and Manual 3 covers it

Gate: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` green after Task 7 (BUILD SUCCESSFUL). Instrumented classes run on the box 192.168.1.110:5555 (lease held, released): `ShowAllFilterFocusTest` 13/13, `ShowAllScreenContentFocusTest` 9/9, `ShowAllRailCloseFocusTest` 7/7; XML in `docs/superpowers/evidence/2026-11-24-catalogue-filters-itest-impl/`. Manual checks 1-7 are not run (qa-device's).

**Review round 1 fixes** (triage-1 rows 1-6; XML in `docs/superpowers/evidence/2026-11-24-catalogue-filters-itest-r2/`, all three classes whole on the box: ShowAllFilterFocusTest 14/14, ShowAllScreenContentFocusTest 9/9, ShowAllRailCloseFocusTest 8/8; lease held and released):

| Row | Commit | Fix |
|---|---|---|
| 1 | 3d913e2e | `isVisible` takes `showAnime`; `FollowPreference` keeps anime while it is on |
| 3, 5 | 68abf4dc | `changeQuery` and every publication run under `publishLock`; `changeQuery` resets `currentPage`, the blank-page chain, resume rounds and `publishedAnyItems` |
| 2, 4, 6 | c0afd1af | header latch `headerHoldsFocus` makes the grid ignore a rail handoff; the grid body is keyed on `ShowAllViewState.Content.queryGeneration`; filter test host provides `LocalScreenKey` |

- red: ContentListInteractorTest.followPreference_whenAnimeIsShownAndWatchedHidden_keepsUnwatchedAnime - AssertionFailedError at ContentListInteractorTest.kt:285 (anime dropped)
- red: ShowAllVMTest.filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne - AssertionFailedError at ShowAllVMTest.kt:591 (old-depth page two of the filtered query was read)
- red (box, with the `queryGeneration` field stubbed and unused): ShowAllFilterFocusTest.resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard - ComposeTimeoutException, focus stayed on «Фильтры»
- green on base: ShowAllRailCloseFocusTest.railOpenedFromFilters_closesWithFocusBackOnFilters - in the compose-test host the handoff settles in the frame the tab box's `focusRestorer` returns focus to «Фильтры», before the grid's latch sees the id; the finding was a trace of `onHandoff` and could not be reproduced here. The latch stays as the guard for the route where the grid does answer the id; the test is a guard
- green on base: ShowAllVMTest.filterChange_marksTheNewListWithAnotherQueryGeneration - new test for the field the `Content` state gained, written with it
- row 6: with `LocalScreenKey` provided, removing `ContentFocusGate` still leaves newListLanding_whilePanelOpen_keepsFocusInThePanel green on the box (only the new reset test failed, as above), so the test is a guard and Manual 3 gained a step for the gate
- row 3: no deterministic red exists for the check-then-publish window (the unit tests run on one scheduler thread); the lock is the fix and the spec rows of the two cancellation-carried tests now say so

**Deviations from the plan:**

- Task 4: `ShowAllVMTest` has 17 new tests, not 16: `filterReset_withoutAnActiveFilter_doesNothing` was added for the no-op branch.
- Task 4: the paging VM drops an old query's work by outcome, and the AC-8 tests assert the outcome. Mutation check: removing the `queryGeneration` checks fails only `filterChange_dropsAnOldDepthRestoreStillInFlight`; the first-page and walk-step reads are also cancelled by `resetPaging`, so `filterChange_dropsAnOldFirstPageStillInFlight` and `filterChange_dropsAnOldWalkStepStillInFlight` hold through cancellation as well (the fakes read under `NonCancellable`, and the walk generation bump in `changeQuery` is covered by the same cancellation). The generation checks stay as the defence for a read that ignores cancellation.
- Task 3 / Task 2: `observeFirstPage_afterAFilteredRead_theUnfilteredPageStillComesFromTheStore` is green on base (cache keys already carry sort and genre); kept as a guard.
- Task 7: `PANEL_FOCUS_*` constants copy the values of `TvContextMenu.kt:47-49` (private there) instead of sharing them.

**Files touched outside the plan:**

- `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMTest.kt`: `observeFirstPage(any(), any())` stubs became `(any(), any(), any())` for the new third parameter.
- `ContentListInteractorTest.kt`: the fixture exposes its `FakePayloadStore` as `payloadStore`.
- `ShowAllFilterLabels.kt` (new, `ui/feature/showall/content/`): the sort / direction / summary strings shared by the header and the panel.

**For the reviewers:**

- `ContentListPagingVM`: ordering of the reads in `walkStep` (generation read before the query, so a step with a stale pair is dropped by the generation check) and `changeQuery` (config and cacheable set before the generation moves).
- `ShowAllScreenContent`: `filtersCanFocus` is `rememberSaveable` as planned, so on re-entry «Фильтры» is focusable at once; `activeFilter_focusReturnsToTheFocusedCardAfterReentry` passes on the box, but the real navigation stack is Manual 5.
- `ContentFocusGate` around the list: removing it did not fail `newListLanding_whilePanelOpen_keepsFocusInThePanel` in the compose-test host (no `LocalScreenKey`), so that test does not prove the gate; Manual 3 and a look at the remounted grid on the real stack do.
- `TvCompactButton` height is now applied inside the button, after the caller's modifier; the browse entry's layout is otherwise unchanged (same padding, label, height).
- Merge risk from the plan: `PlayerGlass` / `PlayerSidePanel` / `ResumeDialog` against `player-overlays-port`.

**Blocked:** none.

## Readiness

Verdict: **READY**

Inputs: spec and plan at `/home/dev/orca/workspaces/Puber/catalogue-filters` HEAD bd05ca22, `test-design.md` (mtime 2026-11-24 17:09), `state.md`. Checks that passed in [readiness-1.md](readiness-1.md) are not repeated; bd05ca22 touches only the spec and the plan.

## Round-1 must-fix

| # | Status | Evidence |
|---|---|---|
| 1 | Fixed | Every non-heading line of `test-design.md` appears verbatim in the spec's `## Test design`; spec rows 356 (`resetPressed_focusesFiltersThenFirstCardWhenListLands`) and Manual 4 (row 396) match; plan Task 6 table line 266 and Coverage AC-6 line 315 name the new test |
| 2 | Fixed | `AC-5 \| device \| P1 \| Manual 3` and `AC-11 \| device \| P1 \| Manual 5` (test-design 63, 87); AC-11 P0 carried by `ShowAllScreenContentFocusTest.activeFilter_focusReturnsToTheFocusedCardAfterReentry`, now `P0 red` with its defect (test-design 86), in plan Task 6 (line 268) and Coverage AC-11 (line 320) |

## Round-1 notes

| Note | Status |
|---|---|
| Spec said `LazyColumn` for panel pages | Fixed: spec line 127 says `Column` + `verticalScroll` with the `PlayerSidePanel.kt:125` reason; no `LazyColumn` left in the spec |
| Merge conflict with player-overlays-port on `PlayerSidePanel.kt` / `PlayerGlass` | Open risk for whichever lands second; not blocking |

## New citation sampled

| Citation | Claimed | Found |
|---|---|---|
| `PlayerSidePanel.kt:125` | `IntrinsicSize.Max` width | yes |
| `PlayerSidePanel.kt:237-315` | panel item to lift to `core/ui/uikit` | yes: `PlayerPanelItem` starts at 238 and ends at 315 |

