# Design (architect): catalogue-filters

Base b7a7d99e. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Scope is item 1 of the split (`brief.md#Split`). Sizes, colours and the panel's look are the ux-designer's delta against `docs/design/showall/`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Which grids get the control | The seven browse configs, through a new `TabTypeConfig.isFilterable(config)` = some tab's `browseEntryFor(tab)` (`ui/feature/contentlist/model/TabTypeConfig.kt:12-25`) has this `config.id` | One source for "browse grid", whether opened from the entry or the «Все» tile (same id); shelves and `4k_<type>` return false (AC-1, AC-2). `all_cartoon` / `all_anime`: Open question 1 |
| How a filter becomes a query | `ContentFilter.applyTo(base: SectionConfig)` = `base.copy(sort = sort.apiValue + sortDirection.apiValue, genre = genre?.id?.toString() ?: base.genre)`; same `id`. Ascending is the bare field (`SortDirection.ASC.apiValue = ""`, `domain/model/ContentFilter.kt:30`); never `+`, which the server ignores (api-check row 3) | `fetchPage` already sends `sort` and `genre` with the base `type` / `quality` (`domain/interactor/contentlist/ContentListInteractor.kt:180-188`, `data/api/KinoPubApiClient.kt:253-269`): no new API params (AC-5) |
| What "no filter" is | `ContentFilter()` (UPDATED, DESC, no genre); active = `filter != ContentFilter()` | Every filterable config has `sort = "updated-"` and `genre = null` (`TabTypeConfig.kt:102-108`, `:145-150`, `:210-215`); `TabTypeConfigTest` asserts it so a retuned base cannot drift from the default |
| How the paging VM's query changes | `ContentListPagingVM.config` (`ui/feature/contentlist/vm/ContentListPagingVM.kt:33`) becomes a constructor param backing `@Volatile private var config` plus `@Volatile private var cacheable = true`; new `protected fun changeQuery(config: SectionConfig, cacheable: Boolean)` sets both, increments a new `queryGeneration` and `walkGeneration` (`:72`), then `resetPaging(null)` (`:111-114`) | The seam the browse-all spec agreed (`docs/superpowers/specs/2026-11-24-catalogue-browse-all.md` Open question 2); `SectionVM` never calls it |
| Old-query pages (AC-8) | `onLoadFirstPage` (`:116-148`) captures `config`, `cacheable` and `queryGeneration` before `pagingLaunch`; `publishFirstPage` (`:165-171`) and `restoreLoadedDepth` (`:194-233`) use the captured config and drop a publication whose generation has moved; `walkStep` (`:249-251`) captures the config beside the generation it already reads | `resetPaging` cancels paging jobs (`core/paginator/PagingVM.kt:160-169`), but the tail after the last suspension point still runs; the store applies actions in send order (`core/paginator/Paginator.kt:535-570`), so an old `Replace` sent after `Restart` would draw. The first-page collect had no guard |
| Filtered first pages and the cache (AC-7) | `observeFirstPage(config, force, cacheable: Boolean = true)` (`ContentListInteractor.kt:56-86`); `cacheable = false` returns a single-emission flow of `loadPage(config, 1)`, as the shortcut path does (`:63-67`). `cacheKey` (`:107-123`) unchanged | A filtered page neither reads nor writes `cached_payload`; the unfiltered browse page keeps its entry because the base config is unchanged |
| Where the filter lives | `ShowAllVM` fields: `applied`, `draft`, `panelOpen`, genres load state. Not in `ShowAllScreen` args or key (`ui/feature/showall/ShowAllScreen.kt:36`) | Lives with the screen's Koin scope: survives details / player and Back, dies with the grid (state.md Q2, AC-11) |
| How filter state reaches the UI | Second flow on `ShowAllVM`: `val filterState: StateFlow<ShowAllFilterState>`; `ShowAllViewState` (`ui/feature/showall/model/ShowAllViewState.kt:7-30`) unchanged | List states stay as they are and `ShowAllVMTest` keeps its assertions; the header is independent of the list state |
| When a change applies | On panel close only: `draft != applied` → `applied = draft`, one `changeQuery(applied.applyTo(base), cacheable = !applied.isActive)`; equal → nothing | AC-4, AC-5; research Q2 (Option A) |
| Genre list source | `GenreInteractor.getGenres(type)` (`domain/interactor/genre/GenreInteractor.kt:16-26`, Koin single `domain/di/modules.kt:42`), loaded on first panel open, one job at a time, retry action on failure. `type = base.type ?: base.quality`: the grid's type, `"4k"` for `all_4k` (`TabTypeConfig.kt:210-215`, no `type`) | Lists differ per type (tvshow 7, documovie 47) and the no-type list mixes music genres with duplicate titles; `genres?type=4k` returns the movie set (api-check rows 1-2). Payload-cached per type for 5 min (`data/cache/CacheKeys.kt:39`, `:64`); no countries in this run |
| Genres decode (AC-10) | `getGenres` (`KinoPubApiClient.kt:632-636`) decodes `ApiResponseList<Genre>` and maps `items.orEmpty()`, as `getBookmarks` does (`:479-482`); signature unchanged. `getCountries` (`:641-643`) untouched (item 2) | The server returns `{status, items}` with `id`, `title`, `type` per item (api-check row 1); today's bare-list decode fails. `Genre`'s unmodelled `type` is skipped by `ignoreUnknownKeys` (`KinoPubApiClient.kt:131`) |
| «Аниме» hidden (AC-9) | Panel genre list drops `ANIME_GENRE_ID` (`data/api/models/ContentGenres.kt:4`) when `base.animeFilterMode == FollowPreference` and new `ContentListInteractor.showAnimeEnabled` (beside `hideWatchedEnabled`, `:48-49`) is false; evaluated at each panel open | Picking it would walk every page empty (`ContentListInteractor.kt:170-178`) |
| Actions | New `sealed interface ShowAllAction : UIAction`: `FilterPanelOpened`, `FilterPanelClosed`, `SortFieldChosen(SortField)`, `SortDirectionChosen(SortDirection)`, `GenreChosen(Genre?)`, `GenresRetry`, `FilterReset`; all ignored when `!filterable` or `closing` (`ShowAllVM.kt:74`) | Content stays pure (state + `onAction`) |
| Sort options shown | All five: UPDATED, CREATED, YEAR, RATING, VIEWS, each with DESC and ASC; `SortField.KINOPOISK` / `IMDB` (`domain/model/ContentFilter.kt:24-25`) not offered | Each field reorders in both directions (api-check row 3); KINOPOISK / IMDB unprobed; the enum's fate is item 4 |
| Header summary | `ShowAllFilterState` carries `genreTitle`, `sort`, `direction`, `isActive`; the composable maps `SortField` / `SortDirection` to strings | AC-6 is a VM test on structured state; no `ResourceProvider` in the VM |
| Header focus order | «Фильтры» is the start-most focusable of `ShowAllHeader` (`ui/feature/showall/content/ShowAllHeader.kt:19-27`), «Сброс» after it, title and summary non-focusable | Left from it reaches the rail by the same 2D search as a column-1 card; Up from grid row 1 has one target above (AC-3) |
| Cold-open focus (AC-1) | «Фильтры» has `canFocus = false` until the list state first leaves `Loading` (latched in `rememberSaveable`) | Same gate as `BrowseAllEntry` (`ui/feature/contentlist/content/BrowseAllEntry.kt:39-46`): on a cold grid the button would be the only focusable; the grid's launch request (`ShowAllScreenContent.kt:94`) stays the first claimant |
| Panel host and Back (AC-4) | `ShowAllFilterPanel` in a `TvDialogOverlay` (`core/ui/uikit/component/TvDialogOverlay.kt:40-73`, end-aligned) at the root of `ShowAllScreenContent`, outside the grid's restorer (`ShowAllScreenContent.kt:103-111`), under its own `TvDialogFocusRestorer` whose `onDialogClosed` focuses «Фильтры»; first row focused by the `TvContextMenuDialog` delayed-request pattern (`core/ui/uikit/component/TvContextMenu.kt:77-92`); dismiss → `FilterPanelClosed` | Overlay traps D-pad and handles Back; the context-menu restorer would target a card |
| Grid focus claims while the panel is open | Grid `Box` wrapped in `ContentFocusGate(active = !panelOpen)` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:40-48`) | A list landing behind the open panel would otherwise run the remounted grid's launch request (`:61-88`) and pull focus out of the trap (requests pass it, `TvDialogOverlay.kt:21-30`) |
| Focus after an applied change | Panel close focuses «Фильтры»; when the new list lands the remounted grid's launch request focuses its first card; `Empty` / `Error` leave focus on «Фильтры». «Сброс» (header and empty state) requests «Фильтры» focus before dispatching `FilterReset` | `resetPaging` takes the grid through `Loading`, so `ShowAllContentBody` and its saveables are new; «Сброс» disappears once the filter is default, which would drop focus to nothing. Open question 2 |
| Filtered empty state (AC-12) | `Empty` branch (`ShowAllScreenContent.kt:68-73`) shows a filtered-empty message and «Сброс» when `filter.isActive`, else today's text | No new list state |

## Changes by layer

**API / repository.**

| File | Change |
|---|---|
| `data/api/KinoPubApiClient.kt:632-636` | `getGenres` unwraps `ApiResponseList<Genre>` |
| `domain/interactor/contentlist/ContentListInteractor.kt:48-49`, `:56-86` | Add `showAnimeEnabled`; `observeFirstPage` gains `cacheable` |
| `domain/model/ContentFilter.kt:6-16` | Add `val isActive`; `applyTo(base: SectionConfig)` as an extension in `ui/feature/showall/model/` (domain must not import the UI config) |

**ViewModel state and actions.**

| File | Change |
|---|---|
| `ui/feature/contentlist/vm/ContentListPagingVM.kt:31-39`, `:116-171`, `:194-257` | Mutable `config` / `cacheable`, `changeQuery`, `queryGeneration`, captured query in first page, restore and walk |
| `ui/feature/contentlist/model/TabTypeConfig.kt:12` | Add `isFilterable(config)` |
| `ui/feature/showall/model/ShowAllFilterState.kt` (new) | `@Immutable` state: `available`, `applied` summary fields, `isActive`, `panelOpen`, `draft`, `sortOptions`, `genres` (`Loading` / `Loaded(List<Genre>)` / `Failed`) |
| `ui/feature/showall/model/ShowAllAction.kt` (new) | Actions from Decisions |
| `ui/feature/showall/vm/ShowAllVM.kt:24-39`, `:73-92` | Constructor gains `genreInteractor`; filter fields, `filterState`, action handling, genre load, `changeQuery` calls |

**Screen and DI module.**

| File | Change |
|---|---|
| `ui/feature/showall/ShowAllScreen.kt:43-53` | `genreInteractor = get()` |
| `ui/feature/showall/ShowAllScreen.kt:57-67` | Collect `vm.filterState`, pass to `ShowAllScreenContent`; key unchanged |

**Compose content.**

| File | Change |
|---|---|
| `ui/feature/showall/content/ShowAllScreenContent.kt:57-87` | Param `filter: ShowAllFilterState = ShowAllFilterState.Unavailable` (keeps `ShowAllScreenContentFocusTest` / `ShowAllRailCloseFocusTest` call sites compiling); root `Box` hosting header, gated grid, panel and the header restorer; filtered empty state |
| `ui/feature/showall/content/ShowAllHeader.kt:19-27` | Title + «Фильтры» + summary + «Сброс» when `available`; `FocusRequester` for «Фильтры» passed in; cold-open gate |
| `ui/feature/showall/content/ShowAllFilterPanel.kt` (new) | Overlay with pages Root / Sort / Genre, each a `Column` + `verticalScroll` (the surface measures `IntrinsicSize.Max` width, `ui/feature/player/component/PlayerSidePanel.kt:125`, which a lazy list cannot answer): Sort rows, Direction rows, «Все жанры» + genres, loading / error row with retry; rows are the player panel item lifted to `core/ui/uikit` (`PlayerSidePanel.kt:237-315`), `selected` from `draft` |

**Resources.** `app/src/main/res/values/main_screen_strings.xml` after `show_all_title_shelf` (`:43`): «Фильтры», «Сброс», panel headings (Сортировка, Направление, Жанр, Все жанры), five sort labels, two direction labels, summary separator, genres error and retry, filtered-empty text. Wording from the ux-designer.

## Box API check

Done on `192.168.1.110:5555`: `docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md`. All rows pass; it settles the genres decode, the genre `type` and the sort options above. Rows 4-5: `genre` is honoured on series and on 4K with a sort.

## Alternatives rejected

- Filter as a `SectionConfig` field or screen argument: changes the parcel and, through the key, splits Voyager state, DI scope and focus memory per filter.
- Wrapping `ShowAllViewState` in a data class with list + filter: rewrites every list-state branch and test for no behaviour gain.
- Filtered pages in the persistent cache under their own key: one `cached_payload` row per combination (research Risks, `cached-payload-row-too-big`).
- Apply on each selection: one re-page and a grid remount per D-pad press inside the panel.
- Chips row in the header (research Option B) and a separate filter screen (Option C): see `research.md: Options`.
- New `getItems` params: `sort` and `genre` already exist.

## Risks

| Area | Risk | Lives in |
|---|---|---|
| Focus | The remounted grid's launch request takes focus while the panel is open or after the user moved on from «Фильтры»; «Сброс» vanishing while focused leaves nothing focused; cold open lands on «Фильтры». Guarded by `ContentFocusGate`, the pre-reset focus hand-off and the `canFocus` latch; AC-1, AC-3, AC-4 on device | `ShowAllScreenContent.kt:94`, `FocusOnLaunchRequester.kt:40-48`, `:61-88`; `ShowAllHeader.kt` |
| Focus | Rail closed after Left from «Фильтры»: `GridFocusMemory.onHandoff` restores the remembered card, not the button | `ShowAllScreenContent.kt:267-274` |
| Concurrency | A page of the old query published after `changeQuery`: first-page tail, `restoreLoadedDepth`, walk step or resume round (AC-8) | `ContentListPagingVM.kt:116-171`, `:249-257`, `:321-341`; `Paginator.kt:535-570` |
| Correctness | A direction suffix other than `-` / empty (e.g. `+`) is silently ignored and returns the default order; `SortDirection` must stay the only source of the suffix | `domain/model/ContentFilter.kt:28-31`; `applyTo` |
| Correctness | Show-anime switched off while «Аниме» is the applied genre: the walk spends its budget and lands on the filtered-empty state | `ContentListInteractor.kt:170-178`; AC-12 |
| Lifecycle | Genre load runs in the VM scope and dies with the screen scope; a process-death restore reopens the grid unfiltered | `ShowAllVM.kt`; `ShowAllScreen.kt:39-55` |
| Recomposition | Every draft change emits `filterState` and recomposes `ShowAllScreenContent`; `ShowAllContentBody` must stay skippable (`@Immutable` filter state, `state` and `onAction` unchanged) | `ShowAllScreenContent.kt:57-99` |
| Performance | Narrow genre + hide-watched walks up to the existing budget per step | `ContentListInteractor.kt:142-163`; `ContentListPagingVM.kt:383-399` |
| Baseline profile | Header button and panel are not in the generated profile until the next regeneration | `app/src/main/generated/baselineProfiles/baseline-prof.txt` |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Do `all_cartoon` / `all_anime` grids (Cartoons / Anime «Все» tiles, `TabTypeConfig.kt:169-175`, `:194-200`) get the button | No in this run: their genre is fixed by config, and two genres in one `genre` param is unverified; record as an idea run | no |
| 2 | Focus after an applied change: first card of the new list (designed) or stay on «Фильтры» | First card when the list lands; add it to AC-5's Then as a device check | no |
| 3 | Box API check outcome | Resolved: all rows pass (api-check file); genres unwrap `items`, genre `type` = grid type (`"4k"` for `all_4k`), all five sort fields kept, ASC = empty suffix | no |
