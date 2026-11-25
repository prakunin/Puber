# Sort and genre filter for the whole-type catalogue grid

Status: approved
Date: 2026-11-24
Slug: catalogue-filters
Kind: feature
Size: M (item 1 of an L split)
Tracks: ui

## Problem / Why

Owner, 2026-11-24: «нет фильтрации». A D-pad friendly filter and sort control on the browse-all grid driven by `ContentFilter`, re-paging after each change. Part 3 of 3, after `catalogue-browse-all` (merge 28db3a78).

Current state, API, related runs and options: `research.md: Current state in code`, `External findings`, `Related work`, `Options`. Checked against the code at b7a7d99e; the facts this brief relies on:

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6-31` | Model has every requested field; no caller; not Parcelable |
| `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:253-269` | `getItems` sends type, sort, page, quality, genre, `conditions[]`; no `country`, `year`, `finished` |
| `KinoPubApiClient.kt:632-643` | `getGenres` / `getCountries` decode `List<…>`, not the `{status, items}` wrapper; never called |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:33` | `config` is `private val`, read by first page (`:120`), reload (`:216`), walk step (`:254`); walk steps have a generation guard (`:249-256`), the first-page collect does not |
| `ContentListPagingVM.kt:111-114` | `resetPaging` clears the list (no depth kept): the hook a query change uses |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:110-123` | First-page cache key is built from `SectionConfig` + `showAnime` + `hideWatched`; a filter outside it would read the unfiltered entry |
| `ContentListInteractor.kt:180-188` | `fetchPage` passes only `SectionConfig` fields to `getItems` |
| `app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt:36` | Key `ShowAllScreen_${config.id}`: filter must stay out of it |
| `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllHeader.kt:19-27` | Non-focusable title: the seam the browse-all spec reserved for this run |
| `app/src/main/java/com/kino/puber/core/ui/uikit/component/TvDialogOverlay.kt:40-60` | Focus-trapping overlay with Back and focus restore, already wired on the grid (`ShowAllScreenContent.kt:103-111`) |
| `docs/superpowers/specs/2026-11-24-catalogue-browse-all.md:145` | Agreed seam: `config` becomes changeable only through a `ShowAllVM` method that calls `resetPaging`; key stays the base config id |

## Acceptance criteria

For item 1. Items 2-4 get theirs at their own triage.

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | a browse grid (`all_*` config) open from the entry or the «Все» tile | the screen opens | header shows title and a «Фильтры» button; first focus lands on the first card, not the button (device) |
| AC-2 | a shelf grid (fresh / hot / popular / `4k_<type>`) | opened | no filter button (unit test on the config → header state; device) |
| AC-3 | focus on any card of grid row 1 | D-pad Up, then Down | Up lands on «Фильтры»; Down returns to a row-1 card; Left from the button opens the side rail as from a card (device) |
| AC-4 | «Фильтры» focused | OK, then Back | panel opens with focus on its first row and traps D-pad; Back closes it with focus on «Фильтры»; nothing re-pages when nothing changed (device; VM unit test) |
| AC-5 | panel open | a sort field / direction or a genre is chosen and the panel is closed | `ShowAllVM` swaps the query and calls `resetPaging` once; the next request carries `sort=<field><suffix>` (suffix `-` for descending, empty for ascending, never `+`) and `genre=<id>` with the base `type` / `quality`; when the new list lands, focus goes to its first card (VM and interactor unit tests with a fake API; device for focus) |
| AC-6 | a filter active | grid shown | header summarises it (e.g. «Драма · по году ↓») and shows «Сброс»; «Сброс» restores the base config's query and re-pages (unit test; device) |
| AC-7 | a filtered query | first page loads, then the filter is reset | the filtered page never comes from or goes into the persistent first-page cache; the unfiltered page still uses it (interactor unit test) |
| AC-8 | an old query's first page or walk step still in flight | the filter changes | no page of the old query reaches the grid (VM unit test with a delayed fake) |
| AC-9 | Movies / Series with «show anime» off | genre list opened | «Аниме» (id 25) is absent (unit test) |
| AC-10 | the panel opens on a grid of type T (`4k` for the 4K grid, else the grid's type) | `GET genres?type=T` returns the documented `{status, items}` body | genres decode and show, requested per grid type, not the untyped list; a failed load shows an error row in the panel, the grid stays (unit test on the API client decode and on the type passed; device) |
| AC-11 | a filter active, a card opened and Back pressed | grid returns | same filter, same card focused; the screen key is still `ShowAllScreen_<config.id>` (unit test on key; `ShowAllScreenContentFocusTest` via `make DEVICE=<serial> itest`) |
| AC-12 | a filter that matches nothing (or hide-watched leaves nothing) | grid loads | empty state says a filter is active and offers «Сброс» (device; VM unit test) |
| AC-13 | the button, summary and panel | seen on the TV box | look matches the ux-designer delta to `docs/design/showall/` (owner judges) |

## Out of scope

| # | Item | Size | Goal |
|---|---|---|---|
| 1 | `catalogue-filters` (this run, narrowed) | M | Changeable query on browse grids; «Фильтры» button in `ShowAllHeader` with active-filter summary and «Сброс»; side panel (`TvDialogOverlay`) with Sort (field + direction: updated, created, year, rating, views) and Genre (one); applies on close; genres unwrap fix; filtered first pages bypass the persistent cache; filter lives for the screen instance |
| 2 | `catalogue-filters-country-year-status` | M | Country (countries unwrap fix), year presets and range, «Статус» (finished) on series types; new `getItems` params |
| 3 | `catalogue-filters-persist` | M | Filter persisted per tab (config id) across app restarts |
| 4 | `catalogue-filters-ratings-quality` | M | Kinopoisk / IMDb rating ranges and quality, only after the box API check confirms `conditions[]` and quality values; otherwise remove those fields and `SortField.KINOPOISK` / `IMDB` from `ContentFilter` |

Order: 1, 2, 3, 4. 3 and 4 are independent of each other.

## Design

### Architect


Base b7a7d99e. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Scope is item 1 of the split (`brief.md#Split`). Sizes, colours and the panel's look are the ux-designer's delta against `docs/design/showall/`.

#### Decisions

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

#### Changes by layer

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

#### Box API check

Done on `192.168.1.110:5555`: `docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md`. All rows pass; it settles the genres decode, the genre `type` and the sort options above. Rows 4-5: `genre` is honoured on series and on 4K with a sort.

#### Alternatives rejected

- Filter as a `SectionConfig` field or screen argument: changes the parcel and, through the key, splits Voyager state, DI scope and focus memory per filter.
- Wrapping `ShowAllViewState` in a data class with list + filter: rewrites every list-state branch and test for no behaviour gain.
- Filtered pages in the persistent cache under their own key: one `cached_payload` row per combination (research Risks, `cached-payload-row-too-big`).
- Apply on each selection: one re-page and a grid remount per D-pad press inside the panel.
- Chips row in the header (research Option B) and a separate filter screen (Option C): see `research.md: Options`.
- New `getItems` params: `sort` and `genre` already exist.

#### Risks

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

#### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Do `all_cartoon` / `all_anime` grids (Cartoons / Anime «Все» tiles, `TabTypeConfig.kt:169-175`, `:194-200`) get the button | No in this run: their genre is fixed by config, and two genres in one `genre` param is unverified; record as an idea run | no |
| 2 | Focus after an applied change: first card of the new list (designed) or stay on «Фильтры» | First card when the list lands; add it to AC-5's Then as a device check | no |
| 3 | Box API check outcome | Resolved: all rows pass (api-check file); genres unwrap `items`, genre `type` = grid type (`"4k"` for `all_4k`), all five sort fields kept, ASC = empty suffix | no |


### UX


#### Governing stand

| Element | Stand | Covers it? |
|---|---|---|
| «Фильтры», «Сброс» and the summary in `ShowAllHeader` (brief.md#AC-1, AC-3, AC-6) | `docs/design/showall/` | Partly. The stand has the header title (section «catalogue-browse-all starting values») but no controls. Stand update needed |
| Filter side panel (brief.md#AC-4, AC-5, AC-10) | none | No stand draws a side panel over the grid. The nearest surface is the player settings panel (`PlayerSidePanel.kt`), which is code only: `docs/design/player-controls/README.md` «Still open» says its 420 dp cap was never dialled. Stand update needed |
| Filtered empty state (brief.md#AC-12) | `docs/design/showall/` | No. Stand update needed |

The screen has a stand, so `designing-a-screen` does not have to run before planning (same as catalogue-browse-all). The run adds a «catalogue-filters» scene to `docs/design/showall/index.src.html` with a slider for each number below, a README section «catalogue-filters starting values», and rebuilds with `python3 docs/design/build.py showall`. The owner dials it at brief.md#AC-13. Until then every number below is a starting value.

The header title numbers (top 12, `SectionTitleStyle` 16 / 20 sp, header → grid 8) are still starting values: catalogue-browse-all shipped without the owner dialling them on the box. This run moves them (below), so the stand's header section is updated in the same edit.

#### Numbers that apply (dp at 1080p, 1 dp = 2 px)

| What | Value | Source |
|---|---|---|
| Closed rail | 60 | showall README «Ground truth», `MainScreenComponent.kt:304` |
| Grid side / bottom padding | 16 / 16 | showall README (agreed 2026-11-24), `ShowAllScreenContent.kt:49` |
| Tile height, row gap | 237, 16 | showall README «What that gives» |
| Tile focus scale, overhang | 1.05, 6 dp vertical | showall README |
| Header title | `SectionTitleStyle` 16 / 20 sp Medium, left 16, 1 line, ellipsis | showall README «catalogue-browse-all starting values», `ShowAllHeader.kt:17` |
| Header → grid | 8 (must stay ≥ 6, the tile overhang) | same |
| Compact button | 24 high, 12 side padding, label 11 / 16 sp Medium, tv-material3 `Button` defaults, focus scale 1.1 | catalogue README «Browse entry «Все …»», `BrowseAllEntry.kt:29-31` |
| Panel row, title, section header, panel width and padding | see Element B | `PlayerSidePanel.kt` (code, not a stand) |
| Dialog scrim | 62 % scrim | `TvDialogOverlay.kt:17`, the card context menu on the same grid |

#### Element A: header controls (browse grids only)

One line: `[title] 16 [Фильтры] 8 [Сброс] 12 [summary]`, all on the left edge. The title is fixed for the screen's life, so «Фильтры» never moves while the screen is open; «Сброс» and the summary appear together and push nothing that is focusable. Nothing focusable sits left of «Фильтры», so Left goes to the rail (brief.md#AC-3).

| Slider (starting value) | Value |
|---|---|
| header top | **10** dp (was 12) |
| header row height | **24** dp, title centred in it: the title's centre stays at 22 dp, where it is today |
| header → grid | **8** dp, unchanged |
| title → «Фильтры» | **16** dp |
| «Фильтры» → «Сброс» | **8** dp |
| «Сброс» → summary | **12** dp |
| «Фильтры» | compact button, **filled** (Button defaults); label only, no icon (switch «иконка» = Phosphor Funnel 15 dp, the content stand's icon size) |
| «Сброс» | compact button, **outlined** (OutlinedButton defaults, the catalogue stand's «контур» switch); switch to filled |
| summary | **11 / 16** sp Regular, onSurfaceVariant #CAC4D0, 1 line. Parts joined by « · », as in the original-title line: genre first, then sort only when it is not the default (`по обновлению ↓`). «Драма · по году ↓», «по рейтингу ↑», «Драма» |
| row on shelf grids | the same **24** dp row with the title only, so row 1 sits at the same y on every grid |

What that gives: row 1 starts at 10 + 24 + 8 = 42 dp (40 today); row 2 ends at 42 + 237 + 16 + 237 = 532, 538 with the overhang, inside 540. A focused button (scale 1.1) reaches 35.2 dp; a focused row-1 tile reaches up to 36 dp. They do not overlap. A header top of 12 gives a 540 bottom with no spare, so clamp the slider at 11.

Switch «порядок»: `[Фильтры] [summary] [Сброс]` shows the alternative where «Сброс» moves with the summary's length; it starts off.

#### Element B: filter panel

A right-hand panel inside `TvDialogOverlay(contentAlignment = CenterEnd)`, built like the player settings panel: a root page with two doors, each opening a page of values.

| Page | Rows |
|---|---|
| root, title «Фильтры» | «Сортировка» (trailing: «по обновлению ↓»), «Жанр» (trailing: «Все жанры» or the genre) |
| «Сортировка» | section «Поле»: по обновлению, по дате добавления, по году, по рейтингу, по просмотрам; section «Порядок»: по убыванию, по возрастанию |
| «Жанр» | «Все жанры», then the genres; «Аниме» absent per brief.md#AC-9; while loading one row with a 20 dp progress indicator; on failure a read-only row «Не удалось загрузить жанры» and a focusable «Повторить» |

| Slider (starting value) | Value | Source |
|---|---|---|
| width | **240 – 420** dp, wraps the widest row | `PanelMinWidth` / `PanelMaxWidth` |
| max height | **500** dp (540 − 2 × 20), vertically centred; a longer page scrolls inside | new |
| corner (start side only) | **20** dp | `PanelCornerRadius` |
| padding start / top / end / bottom | **20 / 20 / 20 / 16** | `PanelPadding*` |
| material | `playerGlass(Strong)`, elevation 20 | `PlayerSidePanel.kt:150` |
| title | **14 / 18** sp SemiBold, 12 dp below | `PlayerSidePanel.kt:168` |
| section header | **11 / 14** sp SemiBold, letterSpacing 0.8, onSurfaceVariant 78 % | `PlayerPanelSectionHeader` |
| row | min **40** dp, padding 12 / 6, radius 12, label **14 / 18** sp Medium, trailing **13** sp at 76 % | `PlayerPanelItem` |
| selected value | primary 16 % container + check 18 dp primary | `PlayerPanelItem` |
| scrim | **62 %** flat (`TvDialogOverlay`, as the card menu on this grid); switch «градиент» = the player's horizontal gradient 0.52 → 42 % | `TvDialogOverlay.kt:17`, `PlayerSidePanel.kt:66-67` |

What that gives: «Сортировка» is 18 + 12 + 2 × 26 + 7 × 40 + 36 = 398 dp, so it fits without scrolling; «Жанр» (about 30 rows) scrolls. Whether the glass made for video reads well over posters under a 62 % scrim is the owner's call on the box (open item 2).

#### Element C: filtered empty state

Centred in the grid area: the existing empty text, then 12 dp, then a compact outlined «Сброс». Text: «Ничего не найдено. Активен фильтр: <summary>» (strings for the architect). Without a filter the empty state stays as it is.

#### Focus

| From | Key | To |
|---|---|---|
| grid opened | (none) | first card, as today (brief.md#AC-1) |
| any row-1 card | Up | «Фильтры», pinned with `focusProperties { up = … }`: geometric search from columns 3-6 would land on «Сброс» or nowhere |
| row 2+ card | Up | the card above, unchanged |
| «Фильтры» | Down | the row-1 card focused last (the grid's remembered child), else the first card |
| «Фильтры» | Left | side rail (brief.md#AC-3) |
| «Фильтры» | Right | «Сброс» if shown, else stays |
| «Фильтры» | Up | stays |
| «Сброс» | Left / Down / Up / Right | «Фильтры» / as «Фильтры» Down / stays / stays |
| «Сброс» | OK | the button leaves; focus goes to «Фильтры», never to the rail |
| «Фильтры» | OK | panel, root, first row «Сортировка» (brief.md#AC-4) |
| panel, any page | Up / Down | rows of the page; Left / Right are trapped (`TvDialogOverlay`) |
| root door | OK | its page, focus on the selected value |
| value row | OK | selects it (check moves), focus stays |
| sub-page | Back | root, focus on the door it came from |
| root | Back | panel closes, focus on «Фильтры» (brief.md#AC-4) |
| after a close that changed the query | (re-page) | focus stays on «Фильтры» while the list reloads; when the new list lands, focus moves to the first card (owner decision, state.md architect Q2). If the list lands empty, focus stays on «Фильтры» and Down reaches the empty-state «Сброс» |
| filtered empty state | Down from «Фильтры» | empty-state «Сброс» |

Focus visibility: header buttons use the Button focused state (onSurface container, inverseOnSurface #313033 label, scale 1.1); panel rows use `PlayerPanelItem`'s focused container, onSurface at 30 %. The selected row and the focused row must look different (primary 16 % vs onSurface 30 %); on the stand they meet when the focused row is the selected one, and there the check icon carries the selection. The always-visible filled «Фильтры» pill above the posters is checked on the box: if it outweighs the cards, the fallback is the «контур» switch, not a code guess.

#### Shared constants: parameterise, do not retune in place

| Constant | Lives in | Also drawn by | What to do |
|---|---|---|---|
| `EntryHeight` 24, `EntryHorizontalPadding` 12, label 11 / 16 sp | `BrowseAllEntry.kt:29-31` (private) | catalogue tab entry | Move to one shared compact-button metric or component in `core/ui/uikit`; the entry and both header buttons read it |
| `PanelMinWidth`, `PanelMaxWidth`, `PanelCornerRadius`, `PanelPadding*`, `PlayerPanelItem`, `PlayerPanelSectionHeader`, `playerGlass` | `ui/feature/player/component/PlayerSidePanel.kt`, `PlayerGlass.kt` | player settings, About, Episodes panels | Lift the panel chrome and rows into `core/ui/uikit` with the values as parameters; the player passes today's values. The player's `AnimatedVisibility` and gradient scrim stay in the player |
| `DialogScrimAlpha` 0.62 | `TvDialogOverlay.kt:17` | every context menu | Do not change; a different scrim for the panel is a parameter |
| `HeaderPadding` top 12 | `ShowAllHeader.kt:15` | Show all only | Change to 10 here, and in the stand README |
| `GridTopPadding` 8 | `ShowAllScreenContent.kt:50` | Show all only | Unchanged |

#### Open

1. Placement of «Сброс» relative to the summary (switch «порядок»).
2. Glass material over the poster grid vs a plain surface: the material was built for video; the owner judges on the box.
3. Value OK behaviour: stays on the page (start, the same on both pages) or returns to root after a genre pick (saves a press). Owner on the stand.
4. «Фильтры» icon: off at start.


Stand: docs/design/showall «catalogue-filters» scene, README section "catalogue-filters starting values (2026-11-24)". API check: docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md.

## Test design


Base b7a7d99e. ACs: `brief.md#Acceptance criteria`; decisions: `design-architect.md`. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, `MainDispatcherExtension`, virtual time), `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (TV box `192.168.1.110:5555`, evidence file). Test paths: `app/src/test/kotlin/com/kino/puber/` and `app/src/androidTest/kotlin/com/kino/puber/`.

AC-5 includes the state.md decision (architect Q2): when the new list lands, focus goes to its first card. The `design-ux.md` Focus row "after a close that changed the query: focus stays on «Фильтры»" is superseded by it, also after «Сброс» (plan Q1): focus is on «Фильтры» while the list loads, then on card 1.

#### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | `getGenres` decodes a bare list; the server sends `{status, items}` (api-check row 1), so every genre load fails | 3 | 3 | 9 | AC-10 unit, red |
| R2 | A first page, depth restore or walk step of the old query publishes after `changeQuery` (no guard on the first-page collect, `ContentListPagingVM.kt:120`) | 2 | 3 | 6 | AC-8 unit |
| R3 | A filtered first page is read from or written to the unfiltered `cached_payload` entry | 2 | 3 | 6 | AC-7 unit |
| R4 | Sort suffix other than `-` / empty (e.g. `+`) is ignored by the server and looks like a working sort | 2 | 3 | 6 | AC-5 unit on `applyTo` |
| R5 | Cold open lands on «Фильтры» instead of the first card | 2 | 3 | 6 | AC-1 instrumented + device |
| R6 | Up from row-1 columns 3-6 lands on «Сброс» or nowhere; Down from the button lands off row 1 | 2 | 3 | 6 | AC-3 instrumented |
| R7 | Panel close does not restore «Фильтры»; a list landing behind the open panel pulls focus out of the trap | 2 | 3 | 6 | AC-4, AC-5 instrumented |
| R8 | «Сброс» disappears while focused and focus goes nowhere | 2 | 3 | 6 | AC-6 instrumented |
| R9 | Panel close without a change re-pages (list flash, cache bypass) | 2 | 2 | 4 | AC-4 unit |
| R10 | «Аниме» offered with show-anime off; picking it walks every page empty | 2 | 2 | 4 | AC-9 unit |
| R11 | `all_4k` asks `genres` without type and gets the 115-item mixed list | 2 | 2 | 4 | AC-10 unit |
| R12 | Filter lost, or a different card focused, after details / player and Back | 2 | 2 | 4 | AC-11 instrumented + device |
| R13 | Filter leaks into the screen key and splits Voyager state / DI scope | 1 | 3 | 3 | AC-11 guard unit |
| R14 | A browse config's base query drifts from `ContentFilter()` (`updated-`, no genre), so "no filter" sends a different query | 1 | 3 | 3 | AC-5 guard unit |
| R15 | Button shown on shelf / `4k_<type>` / `all_cartoon` / `all_anime` grids | 1 | 2 | 2 | AC-2 unit |
| R16 | Each draft change recomposes `ShowAllContentBody` | 2 | 1 | 2 | Accepted: no perf track; ux visual review on the box |
| R17 | Rail closed after Left from «Фильтры» restores the remembered card, not the button | 2 | 1 | 2 | Accepted: designed behaviour (`design-architect.md` Risks) |
| R18 | Header button and panel missing from the baseline profile until regeneration | 3 | 1 | 3 | No test: next `tools/generate-baseline-profile.sh` run |
| R19 | Process-death restore reopens the grid unfiltered | 1 | 1 | 1 | Accepted: Q2 scope (screen instance) |

#### Traceability

New test classes: `data/api/KinoPubApiClientGenresTest.kt` (reuses the `HttpServer` `withServer` / `client` pattern of `KinoPubApiClientFailureTest`), `ui/feature/showall/model/ContentFilterQueryTest.kt`, androidTest `ui/feature/showall/content/ShowAllFilterFocusTest.kt` (pattern of `ShowAllScreenContentFocusTest`: `createComposeRule`, `PuberTheme`, `LocalContentFocusActive`, `performKeyInput`). `ShowAllVMTest` gains a mocked `GenreInteractor` in `createVM`; `ContentListInteractor` is mocked there as today.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 red | `ShowAllFilterFocusTest.coldOpen_focusesTheFirstCardNotFilters` | Fails if the `canFocus` latch is removed and «Фильтры» takes launch focus while the list is `Loading` |
| AC-1 | instrumented | P0 guard | `ShowAllScreenContentFocusTest` (whole class) | Fails if the new header or `ContentFocusGate` breaks first focus, re-entry or load-more on the grid |
| AC-1 | device | P1 | Manual 1 | Real launch path from the entry and the «Все» tile |
| AC-2 | unit | P0 red | `TabTypeConfigTest.isFilterable_trueForEveryBrowseEntryConfig` | Fails if a tab's `browseEntryFor` config is not filterable |
| AC-2 | unit | P0 guard | `TabTypeConfigTest.isFilterable_falseForShelvesAnd4kTypeGrids` | Fails if fresh / hot / popular / `4k_<type>` / `popular_4k` / `all_cartoon` / `all_anime` get the button (guard: the stub already returns false for every config, so it is green on base; protects the negative cases once `isFilterable` turns true for browse configs) |
| AC-2 | unit | P1 | `ShowAllVMTest.filterState_isUnavailableForAShelfConfig` | Fails if `ShowAllVM` reports `available` for a shelf config |
| AC-2 | unit | P1 | `ShowAllVMTest.filterActions_areIgnoredOnAShelfGrid` | Fails if a filter action on a shelf grid calls `observeFirstPage` again |
| AC-2 | instrumented | P1 | `ShowAllFilterFocusTest.unavailableFilter_showsNoFiltersButton` | Fails if the header draws «Фильтры» for `ShowAllFilterState.Unavailable` |
| AC-3 | instrumented | P0 red | `ShowAllFilterFocusTest.upFromFirstAndLastRowOneCard_landsOnFilters` | Fails if the `focusProperties { up }` pin is missing (last column lands on «Сброс» or nowhere) |
| AC-3 | instrumented | P0 red | `ShowAllFilterFocusTest.downFromFilters_returnsToTheRowOneCardItCameFrom` | Fails if Down lands on a card outside row 1 or on nothing |
| AC-3 | instrumented | P1 | `ShowAllRailCloseFocusTest.leftFromFilters_opensTheRail` | Fails if something focusable sits left of «Фильтры» or Left is consumed |
| AC-3 | instrumented + device | P0 guard | `ShowAllRailCloseFocusTest.railOpenedFromFilters_closesWithFocusBackOnFilters`; carrier Manual 2 (rail close) | Open the rail from «Фильтры», close it with Right and with Back: fails if focus lands on a card or nowhere instead of «Фильтры», i.e. if the `headerHoldsFocus` rail fix is reverted. The instrumented test is green on base (the compose host settles the handoff and cannot be made red), so the real stack is gated by Manual 2 |
| AC-3 | device | P1 | Manual 2 | Same paths with the real rail and remote |
| AC-4 | unit | P0 guard | `ShowAllVMTest.filterPanelClosed_withoutAChange_doesNotRepage` | Fails if close calls `changeQuery` when `draft == applied` (counts `observeFirstPage` calls; also after choose-then-revert) (guard: nothing re-pages on base, so it is green there; protects the no-op close once `changeQuery` exists) |
| AC-4 | instrumented | P0 red | `ShowAllFilterFocusTest.filtersOk_opensThePanelOnItsFirstRow` | Fails if the panel opens without focus or focus stays on the grid |
| AC-4 | instrumented | P0 red | `ShowAllFilterFocusTest.panelBack_closesWithFocusOnFilters` | Fails if the header restorer is missing and focus goes to a card or nowhere |
| AC-4 | instrumented | P1 | `ShowAllFilterFocusTest.panelOpen_dpadLeftAndRightStayInsideThePanel` | Fails if the panel is not inside `TvDialogOverlay` |
| AC-4 | instrumented | P1 | `ShowAllFilterFocusTest.subPageBack_returnsToTheDoorItCameFrom` | Fails if Back on «Сортировка» / «Жанр» closes the panel or loses the door focus |
| AC-5 | unit | P0 red | `ContentFilterQueryTest.applyTo_ascending_sendsTheBareFieldName` | Fails if ASC produces `year+` (or any suffix) instead of `year` |
| AC-5 | unit | P0 red | `ContentFilterQueryTest.applyTo_descendingWithGenre_keepsIdTypeAndQuality` | Fails if `applyTo` drops `id`, `type`, `quality` or `animeFilterMode`, or writes the genre other than as its id |
| AC-5 | unit | P0 guard | `ContentFilterQueryTest.applyTo_defaultFilter_returnsTheBaseConfig` | Fails if the default filter changes the query or `isActive` is true for `ContentFilter()` (guard: the stub `applyTo` returns the base config, green on base; protects the default path) |
| AC-5 | unit | P0 guard | `TabTypeConfigTest.browseEntryConfigs_haveTheDefaultFilterQuery` | Fails if a browse config's `sort` stops being `updated-` or gains a `genre` |
| AC-5 | unit | P0 red | `ShowAllVMTest.filterPanelClosed_afterSortAndGenreChosen_repagesOnceWithTheFilteredQuery` | Fails if close re-pages zero or two times, or passes the base config (captures the `SectionConfig` given to `observeFirstPage`) |
| AC-5 | unit | P0 guard | `ContentListInteractorTest.filteredBrowseConfig_sendsSortAndGenreWithTheBaseType` | Fails if `fetchPage` stops forwarding `sort` / `genre` with `type` to `getItems` (mocked `KinoPubApiClient`) |
| AC-5 | unit | P1 guard | `ContentListInteractorTest.filtered4kConfig_sendsSortAndGenreWithTheBaseQuality` | Same for `all_4k` (`quality = "4k"`, no type) |
| AC-5 | instrumented | P0 guard | `ShowAllFilterFocusTest.newListLanding_whilePanelOpen_keepsFocusInThePanel` | Cannot be made red in the compose host (not even with `LocalScreenKey`); real stack gated by Manual 3. Fails if `ContentFocusGate` is removed and the remounted grid's launch request takes focus |
| AC-5 | instrumented | P0 red | `ShowAllFilterFocusTest.newListLanding_afterApply_focusesTheFirstCard` | Fails if focus stays on «Фильтры» or is lost when `Loading` turns to `Content` after close |
| AC-5 | device | P1 | Manual 3 | Order and genre visibly change on the box (automated carriers: the P0 rows above) |
| AC-6 | unit | P0 red | `ShowAllVMTest.filterReset_restoresTheBaseQueryAndRepagesCached` | Fails if reset leaves the filtered config or reads with `cacheable = false` |
| AC-6 | unit | P1 guard | `ShowAllVMTest.filterChange_marksTheNewListWithAnotherQueryGeneration` | Fails if a filter change re-pages without a new query generation, so the old list's focus handoff or stale page could survive |
| AC-6 | unit | P1 red | `ShowAllVMTest.filterState_summarisesTheAppliedGenreAndSort` | Fails if `genreTitle` / `sort` / `direction` / `isActive` show the draft instead of the applied filter |
| AC-6 | instrumented | P0 red | `ShowAllFilterFocusTest.resetPressed_focusesFiltersThenFirstCardWhenListLands` | Fails if «Сброс» leaves while focused and nothing is focused, or if focus stays on «Фильтры» after `Loading` turns to `Content` |
| AC-6 | instrumented | P1 red | `ShowAllFilterFocusTest.resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard` | Reset answered at once from the cached base page (no `Loading` step): fails if focus stays on «Фильтры» or is lost instead of the first card. Red on the box with the field stubbed (recorded in the itest run), not in the compose host |
| AC-6 | device | P1 | Manual 4 | Summary text and «Сброс» on the box |
| AC-7 | unit | P0 red | `ContentListInteractorTest.observeFirstPage_notCacheable_neitherReadsNorWritesTheStore` | Fails if a filtered read is served from a stored entry or leaves one in `FakePayloadStore` |
| AC-7 | unit | P0 guard | `ContentListInteractorTest.observeFirstPage_afterAFilteredRead_theUnfilteredPageStillComesFromTheStore` | Fails if the filtered read overwrites or evicts the base entry (second base read makes no server call) (guard: no filtered read exists on base, so the base entry is never touched; protects the entry once `cacheable = false` reads exist) |
| AC-7 | unit | P0 red | `ShowAllVMTest.filterPanelClosed_withAFilter_readsTheFirstPageUncached` | Fails if `ShowAllVM` passes `cacheable = true` for an active filter |
| AC-8 | unit | P0 red | `ShowAllVMTest.filterChange_dropsAnOldFirstPageStillInFlight` | Proves the outcome carried by cancellation: old query's `observeFirstPage` flow emits after the change (delayed fake, `StandardTestDispatcher`); fails if its items reach `ShowAllViewState.Content`. Not a proof of the generation guard (see `filterChange_dropsAnOldDepthRestoreStillInFlight` and `filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne`) |
| AC-8 | unit | P0 red | `ShowAllVMTest.filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne` | Change a filter, then refresh before the first page lands: fails if the refresh walks beyond page 1 or reads the old config (generation guard / atomic change + refresh); red on base, where no filter change exists |
| AC-8 | unit | P0 red | `ShowAllVMTest.filterChange_dropsAnOldWalkStepStillInFlight` | Proves the outcome carried by cancellation: fails if a walk step of the old query still publishes after the change. Not a proof of the generation guard (see `filterChange_dropsAnOldDepthRestoreStillInFlight` and `filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne`) |
| AC-8 | unit | P1 red | `ShowAllVMTest.filterChange_dropsAnOldDepthRestoreStillInFlight` | Fails if `restoreLoadedDepth` reads with the new config or publishes after the change |
| AC-8 | unit | P0 guard | `ShowAllVMTest` existing tests, `SectionVMTest`, `SectionVMRefreshDepthTest` | Fail if the mutable `config` / `changeQuery` changes paging for grids and rows that never filter |
| AC-9 | unit | P0 red | `ShowAllVMTest.genreList_withAnimeHidden_omitsAnime` | Fails if id 25 stays in `filterState.genres` for a `FollowPreference` config with `showAnimeEnabled` false |
| AC-9 | unit | P1 red | `ShowAllVMTest.genreList_withAnimeShown_keepsAnime` | Fails if the drop ignores the preference |
| AC-9 | unit | P1 red | `ContentListInteractorTest.showAnimeEnabled_followsThePreference` | Fails if it reads a value other than `contentPreferences.showAnime` |
| AC-9 | unit | P1 red | `ContentListInteractorTest.followPreference_whenAnimeIsShownAndWatchedHidden_keepsUnwatchedAnime` | `FollowPreference` config with show-anime on: fails if the anime visibility check (`isVisible`) drops anime items while walking pages; red on the unfixed check (red recorded) |
| AC-10 | unit | P0 red | `KinoPubApiClientGenresTest.getGenres_unwrapsTheItemsOfAStatusItemsBody` | Body `{"status":200,"items":[{"id":9,"title":"Драма","type":"movie"}]}` → `[Genre(9, "Драма")]`; fails on base (bare-list decode) |
| AC-10 | unit | P1 guard | `KinoPubApiClientGenresTest.getGenres_sendsTheTypeParameter` | Fails if `type` is not sent as the query parameter |
| AC-10 | unit | P1 guard | `KinoPubApiClientGenresTest.getGenres_refusedRead_failsInsteadOfAnEmptyList` | Fails if a 422 becomes `success(emptyList())` |
| AC-10 | unit | P0 red | `ShowAllVMTest.genreLoadFailure_showsFailedAndKeepsTheGrid` | Fails if a failed load leaves `genres` in `Loading` or changes the list state |
| AC-10 | unit | P1 red | `ShowAllVMTest.genresRetry_loadsAgainAfterAFailure` | Fails if retry does nothing or starts a second concurrent load |
| AC-10 | unit | P1 red | `ShowAllVMTest.genreList_forA4kGrid_requestsThe4kType` | Fails if `all_4k` asks `getGenres(null)` |
| AC-10 | instrumented | P1 | `ShowAllFilterFocusTest.genresFailed_showsTheErrorRowAndFocusableRetry` | Fails if the failed page has no focusable row |
| AC-11 | unit | P0 guard | `ContentListVMTest.browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle` | Fails if the key stops being `ShowAllScreen_all_movie` |
| AC-11 | instrumented | P0 red | `ShowAllScreenContentFocusTest.activeFilter_focusReturnsToTheFocusedCardAfterReentry` | With an active `ContentFilter` and card 3 of row 2 focused, leave to a handoff and re-enter: fails if focus lands on card 1 or on «Фильтры» instead of the remembered card (the new `ContentFocusGate` / launch request overriding the restore). Red on base: the `filter` parameter of `ShowAllScreenContent` does not exist, so the test does not compile |
| AC-11 | device | P1 | Manual 5 | Filter and card survive details and player on the real navigation stack (automated carriers: the P0 rows above) |
| AC-12 | unit | P1 red | `ShowAllVMTest.filteredEmptyPage_keepsTheFilterActive` | Fails if an empty filtered page drops `isActive` or the list state is not `Empty` |
| AC-12 | instrumented | P1 red | `ShowAllFilterFocusTest.filteredEmpty_showsResetAndDownFromFiltersReachesIt` | Fails if the filtered-empty «Сброс» is missing or unreachable |
| AC-12 | device | P1 | Manual 6 | Real narrow genre with hide-watched |
| AC-13 | device | P1 | Manual 7 | Owner judges the look against the stand |

#### Bugs

AC-10 is a defect in base code: `git show b7a7d99e:app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt` lines 632-636 decode `genres` as `List<Genre>`. Written first: `KinoPubApiClientGenresTest.getGenres_unwrapsTheItemsOfAStatusItemsBody`; on base it returns a failure.

#### Manual checks

TV box `192.168.1.110:5555`, `com.kino.puber.stage`, worktree dev APK via `tools/android-apk-install-preserve`, lease held. Keys via `adb -s 192.168.1.110:5555 shell input keyevent`. Must never happen in any step: crash, focus on nothing (no highlight anywhere), focus on the rail without Left, old list flashing back after a change.

| # | AC | Steps | Expected |
|---|---|---|---|
| 1 | AC-1 | Movies tab: open «Все фильмы» entry, Back; open the «Все» tile, Back; same on 4K | Title + «Фильтры» in the header; first focus on card 1 each time |
| 2 | AC-3 | On a browse grid: focus card in column 1, `DPAD_UP`, `DPAD_DOWN`; column 6, `DPAD_UP`, `DPAD_DOWN`; from «Фильтры» `DPAD_LEFT` (rail opens); close the rail with `DPAD_RIGHT`; again `DPAD_UP` to «Фильтры», `DPAD_LEFT`, close with `BACK` | Up lands on «Фильтры», Down returns to the same row-1 card; Left opens the rail; after each close (Right and Back) focus is back on «Фильтры», not on a card or nowhere (gate for `railOpenedFromFilters_closesWithFocusBackOnFilters`) |
| 3 | AC-4, AC-5 | «Фильтры» `DPAD_CENTER`; `BACK` (no change); reopen, «Сортировка» → «по году», «по возрастанию», `BACK`, «Жанр» → «Драма», `BACK` `BACK`; while the panel is open and a change is being applied, watch for focus jumping to a grid card | Open: focus on «Сортировка», Left/Right do not leave; first Back: panel closes on «Фильтры», grid not reloaded; focus never leaves the panel until it closes (gate step: the new list landing under an open panel must not take focus); after the change: grid reloads oldest-year-first dramas, focus lands on card 1 |
| 4 | AC-6 | With the filter of step 3, read the header; focus «Сброс», `DPAD_CENTER` | Summary «Драма · по году ↑»; after reset: default order, «Сброс» and summary gone, focus on «Фильтры» while the list loads, then on card 1 when it lands |
| 5 | AC-11 | Filter active, open card 3 row 2, `BACK`; play a card, `BACK` to the grid | Same filter and summary, card 3 row 2 focused, no reload to the unfiltered list |
| 6 | AC-12 | Settings: hide watched on; Movies browse grid, pick a narrow genre (e.g. «Аниме» with show-anime on) until empty | Filtered-empty text with «Сброс»; Down from «Фильтры» reaches it; «Сброс» restores the grid |
| 7 | AC-13 | Screenshots of header (no filter, active filter), both panel pages, filtered-empty state into the evidence file | Owner compares to `docs/design/showall/` stand; no overlap of focused button (scale 1.1) and row-1 tile |

#### Perf track

N/A: track `perf` is off for this run.


## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Accept the split and narrow this run to item 1 | Yes: items 2-4 become idea runs with `source: catalogue-filters` | yes |
| Q2 | How long a filter lives in item 1 | For the screen instance: survives details / player and back, cleared when the grid is closed; persistence across restarts is item 3 | yes |
| Q3 | 4K tab genre list | `genres?type=4k` if the box check returns a non-empty list, else the untyped list; no «Статус» there | no |

Assumed from `research.md: Open questions`: Q1 (browse grids only), Q2 (Option A, apply on close), Q5 (hide «Аниме» when show-anime is off), Q7 (filtered queries bypass the persistent cache).

Architect Q1 (no button on all_cartoon / all_anime; idea run catalogue-filters-cartoon-anime-grids), Q2 (focus to the first card when the new list lands) and Q3 (resolved by the box API check) answered.

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | L split; this run is item 1; parts 2-4 are idea runs | owner (standing instruction: recommended) |
| 2026-11-24 | Filter lives for the screen instance; 4K genres via type=4k; ascending sort is the empty suffix | owner (standing instruction: recommended) |
| 2026-11-24 | Stand starting values used as port values; owner judges the look on the box (AC-13) | owner (standing instruction: recommended) |
| 2026-11-24 | Plan Q1: after «Сброс» focus on «Фильтры», then card 1 when the list lands | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved | owner (standing instruction: recommended) |
