# Research: catalogue-filters

## Request

> Filter and sort panel for the catalogue grid driven by ContentFilter
>
> 2026-11-24 created (stage idea); owner request: the catalogue has no filtering. domain/model/ContentFilter.kt (genre, country, sort and direction, year range, Kinopoisk and IMDb rating ranges, quality, finished) exists but nothing uses it. Add a D-pad friendly filter and sort panel on the browse-all grid; resetPaging() after each change (docs/architecture/paging-and-filters.md). Part 3 of 3, after catalogue-browse-all.

Owner's words: «нет фильтрации».

## Understanding

The browse grid (catalogue-browse-all) pages a whole content type with a fixed query: `GET items?type=<t>&sort=updated-&page=n`. The owner wants to narrow and reorder it: by genre, country, year, rating, quality, finished, and a choice of sort. The pieces exist but are disconnected: `ContentFilter` has no caller, `getItems` sends only type, sort, page, quality, genre and conditions, the genre and country calls have never run in the app (and decode the wrong shape), and the grid's query is immutable for the VM's life. The run makes the browse grid's query changeable from a TV-friendly control in `ShowAllHeader`, maps the filter to API parameters, keeps the first-page cache and the client-side anime and hide-watched filtering correct, and decides how long a chosen filter lives.

## Current state in code

Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

| Where | What it does now |
|---|---|
| `domain/model/ContentFilter.kt:6-16` | `ContentFilter(genre: Genre?, country: Country?, sort, sortDirection, yearRange: IntRange?, kinopoiskRating, imdbRating: ClosedFloatingPointRange<Float>?, quality: String?, finished: Boolean?)`; no usages in `app/src`; not Parcelable |
| `domain/model/ContentFilter.kt:18-31` | `SortField`: updated, year, created, rating, views, `kinopoisk_rating`, `imdb_rating` (last two not in the official doc, see External); no `title`, `watchers`. `SortDirection` DESC = "-", ASC = "" |
| `data/api/KinoPubApiClient.kt:253-269` | `getItems(type, sort, page, quality, genre, conditions)`: `conditions[]` supported but no caller passes it; no `country`, `year`, `finished`, `perpage` |
| `data/api/KinoPubApiClient.kt:281-291` | `getItemsByShortcut(shortcut, type, page, genre)`: fresh/hot/popular shelves |
| `data/api/KinoPubApiClient.kt:632-643` | `getGenres(type)`, `getCountries()` decode the body straight into `List<…>`; working list calls unwrap `ApiResponseList` (`:479-482`, `data/api/models/Models.kt:11-15`). Against the documented `{status, items}` shape these two would fail to decode (`unverified` at runtime: never called) |
| `domain/interactor/genre/GenreInteractor.kt:11-26`, `domain/di/modules.kt:42` | Payload-cached genres (`CacheKeys.genres(type)`, `data/cache/CacheKeys.kt:39`; TTL 5 min, `CacheTtl.Genres` `:64`); registered in Koin, no caller (stale import in `app/src/test/.../ContentListVMTest.kt:24`). No country interactor |
| `data/api/models/Models.kt:197-206` | `Genre(id, title)`, `Country(id, title)`; API also returns `type` on genres, not modelled |
| `core/ui/uikit/component/GenreChipBar.kt:21-52` | Unused `LazyRow` of genre chips («Все» + genres, `focusRestorer()`); only its preview references it |
| `ui/feature/showall/ShowAllScreen.kt:30-36` | Args `config: SectionConfig`, `title: ShowAllTitle`; key `ShowAllScreen_${config.id}` |
| `ui/feature/showall/ShowAllScreen.kt:39-55` | Screen-scoped `ContentListInteractor` and `ShowAllVM(config = config, …)` |
| `ui/feature/contentlist/vm/ContentListPagingVM.kt:33` | `private val config: SectionConfig`: used by first-page observe (`:120`), reload (`:216`), walk steps (`:254`) |
| `ui/feature/showall/vm/ShowAllVM.kt:49-71` | Re-pages on display-setting changes; on watch-state changes re-pages only with hide-watched on |
| `ui/feature/showall/content/ShowAllScreenContent.kt:57-87` | `Column { ShowAllHeader(title); Box { state switch } }`; grid takes launch focus (`:94`, `:136`) |
| `ui/feature/showall/content/ShowAllHeader.kt:19-27` | Non-focusable one-line `Text`; the seam named by the browse-all spec |
| `domain/interactor/contentlist/ContentListInteractor.kt:56-86` | First page through `ContentCacheRepository.sectionPage` keyed by `cacheKey` |
| `ContentListInteractor.kt:107-123` | Cache key = every `SectionConfig` field + `showAnime` + `hideWatched`; a filter not in `SectionConfig` would hit the unfiltered entry |
| `ContentListInteractor.kt:125-178` | Client-side anime filter (`AnimeFilterMode`) and hide-watched; with hide-watched on at most 5 server pages per step (`:144`, `:202`); asserts `pagination.current == page` (`:147`) |
| `ContentListInteractor.kt:180-188` | Shortcut configs go to `items/<shortcut>`, others to `getItems(type, sort, page, quality, genre)` |
| `ui/feature/contentlist/model/SectionConfig.kt:8-21` | Parcelable nav argument; fields type, shortcut, sort, quality, genre (String), requiredGenreId, animeFilterMode |
| `ui/feature/contentlist/model/TabTypeConfig.kt:12-25` | `browseEntryFor(tab)`: the tab's `all_*` config (Movies, Series, Concerts, DocMovies, DocSeries, TvShows, For4k) |
| `TabTypeConfig.kt:69-108`, `:126-150`, `:203-216` | `all_<type>`: `type`, `sort = "updated-"`, Movies/Series `FollowPreference`; `all_4k`: `quality = "4k"`, no type. Shelves fresh/popular/hot are shortcuts; `4k_<type>`, `popular_4k` use `items` with `quality = "4k"` |
| `data/api/models/ContentGenres.kt:3-8` | `ANIME_GENRE_ID = 25`, `CARTOON_GENRE_ID = 23`; `isAnime()` by genre id |
| `data/preferences/NavigationPreferencesRepository.kt:15-31`, `:48-52` | `showAnime` (default true), `hideWatched`; SharedPreferences; `displaySettingsChanges` for re-paging. No per-tab filter storage anywhere |
| `core/ui/uikit/component/TvDialogOverlay.kt:40-60` | Scrimmed overlay that traps D-pad, handles Back, restores focus through `LocalTvDialogFocusRestorer` (already provided by the grid, `ShowAllScreenContent.kt:103-111`) |
| `ui/feature/player/component/PlayerSidePanel.kt:80-90` | Existing side-panel look (240-420 dp, rounded, gradient scrim); player-only |
| `core/ui/uikit/component/TvContextMenu.kt:61` | `TvContextMenuDialog`: list-of-actions dialog on the grid |
| Tests | `app/src/test/.../showall/vm/ShowAllVMTest.kt`, `.../contentlist/model/TabTypeConfigTest.kt`, `.../domain/interactor/contentlist/ContentListInteractorTest.kt`, `app/src/androidTest/.../showall/content/ShowAllScreenContentFocusTest.kt`, `ShowAllRailCloseFocusTest.kt` |

## Related work

| Item | Relation |
|---|---|
| Run `catalogue-browse-all` (done, merge 28db3a78), spec `docs/superpowers/specs/2026-11-24-catalogue-browse-all.md` (Decisions "Seam for catalogue-filters"; architect Q2) | Header seam; `config` to become changeable only through a `ShowAllVM` method that calls `resetPaging`; key stays the base config id |
| Run `catalogue-poster-grid` (done, 0c22bc33), stand `docs/design/showall/` | Grid and card; stand has no filter controls yet |
| Run `dead-code-sweep` (idea) | Title still names `ContentFilter`; its log (state.md:33) excludes it while this run is open. `GenreChipBar`, `getCountries`, `GenreInteractor` are candidates there too |
| Run `cached-payload-row-too-big` (idea) | 4K «Все» grid breaks on an oversized `cached_payload` row; filtered 4K pages go through the same cache |
| Run `layer-deps-cleanup` (idea) | Moves `SectionConfig`/`AnimeFilterMode` to `domain/model`; touches the same types |
| Runs `showall-first-entry-rail-focus`, `showall-entry-restore-foreign-landing`, `showall-rail-back-exits-app` (idea) | Open Show all focus defects; a focusable header adds a new focus path next to them |
| Run `tab-refresh-while-covered-flash` (idea) | Show-anime toggle while a screen is covered; same re-page signal |
| Commit 6b75a325 | First pages from the payload cache keyed by `ContentListInteractor.cacheKey` |

Possible duplicate: none.

## External findings

| Finding | Source |
|---|---|
| `GET /v1/items`: `type` (movie, serial, 3D, concert, documovie, docuserial, tvshow); `genre` and `country` = ids, comma list; `year` = `y` or `y1-y2`; `finished` 0/1 (series status); `actor`, `director`, `letter`, `title`; `conditions` = array of simple conditions, AND-combined, fields as for sort, example `year <= 100`; `force` (quality, advert, erotic); `quality` = array of quality ids; `sort` default `updated-`, fields id, year, title, created, updated, rating, views, watchers, `-` = DESC, comma for several. `page`/`perpage` not documented for `items` | https://kinoapi.com/api_video.html (API 1.3, read 2026-11-24) |
| `GET /v1/genres?type=`: one value of movie, docu, music, tvshow (grouped) or movie, documovie, serial, docuserial, tvshow, concert, 3d, 4k; returns `{id, title, type}`. `GET /v1/countries`: no params, `{id, title}` | same |
| `GET /v1/references/video-quality`: `{id, title, quality}` (doc shows 1 = 480p, 2 = 720p only); ids are what `items?quality=` takes. Responses wrap lists in `{"status", "items"}` | https://kinoapi.com/api_references.html (read 2026-11-24); https://github.com/aqualhume/kinopub-api `api/v1/references.md`, `api/v1/content.md` (commit 5899671d, 2026-01-14) |
| Unofficial: `conditions[]` keys observed in the official app: `year`, `imdb_rating`, `kinopoisk_rating`, `created`, e.g. `conditions[]=imdb_rating>=7`; `quality` sent repeated (`quality=1&quality=2`); `perpage` accepted on `items` | https://github.com/aqualhume/kinopub-api `api/v1/content.md` (unofficial, not affiliated) |
| `tv-material` 1.1.0 stable (2026-05-06), the version in `gradle/libs.versions.toml:8`. Stable since 1.0.0: `Button`, `ListItem`, `RadioButton`, `Checkbox`, `Switch`, `Surface`, `NavigationDrawer`. Chips (`FilterChip`) exist; whether they are still `@ExperimentalTvMaterial3Api` in 1.1.0 is `unverified` | https://developer.android.com/jetpack/androidx/releases/tv; https://developer.android.com/reference/kotlin/androidx/tv/material3/package-summary |
| TV modal drawer pattern: overlay on content with a scrim; navigation updates on select, not on focus | https://developer.android.com/design/ui/tv/guides/components/navigation-drawer (read 2026-11-24) |

Verified by the app in production, not by docs: `quality=4k` as a string (4K tab, `TabTypeConfig.kt:98`, `:207`, `:213`), `sort=views-` (`:166`, `:208`), `page=1` as the first page. `unverified`: sort by `kinopoisk_rating`/`imdb_rating`; rating ranges through `conditions[]`; other quality values (`1080p`, ids); whether `genres?type=serial` differs from `type=movie`; how many countries `/v1/countries` returns (list size drives the picker design). A one-off request from the main checkout on the box would settle these before the spec.

## Options

| | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | One «Фильтры» button (plus a «Сброс» when active) in `ShowAllHeader` on browse grids, with a line summarising active filters. OK opens a right-side panel (`TvDialogOverlay`, `ListItem` rows: Сортировка, Жанр, Страна, Год, Рейтинг, Качество, Статус for series types); a row opens its value list; Back closes; the query applies on close. `ShowAllVM.applyFilter(ContentFilter)` swaps the query and calls `resetPaging`; `ContentFilter` maps to `genre`, `country`, `year`, `finished`, `quality`, `sort`, `conditions[]`. Filter persisted per tab (config id) in SharedPreferences | One new focus stop above the grid; the panel traps focus and restores to the grid by the existing restorer; covers all `ContentFilter` fields; panel look reuses the player panel. Needs API fixes (genres/countries unwrap, new params), a cache-key change, a design stand delta | M |
| B | Chips row in the header: Sort chip + genre chips (`GenreChipBar`, already written), each press re-pages at once | Fastest to use for genre; no panel. Only two dimensions; a long `LazyRow` of ~30 genres above the grid makes Up from row 1 land in a wide horizontal list, and every chip press clears the grid | S-M |
| C | A separate filter screen (`FilterScreen` pushed for result) returning a `ContentFilter` to the grid | Cleanest focus (own screen); no overlay. A navigation hop and Back-result plumbing; the grid restarts behind it; more screens with key and DI scope rules | M |

## Risks

| Area | Risk | Lives in |
|---|---|---|
| Cache | Filtered first pages served from the unfiltered cache entry if the filter is not in `cacheKey`; a key per filter combination fills `cached_payload` | `ContentListInteractor.kt:107-123`; `cached-payload-row-too-big` |
| Anime | Movies/Series with show-anime off and genre «Аниме» chosen: every item is dropped client-side, the walk spends its budget (up to ~80 server pages, `ContentListPagingVM.kt:388-396`) and shows empty | `ContentListInteractor.kt:170-178` |
| Hide-watched | Narrow server filter + hide-watched gives short or empty steps; the empty state must say a filter is active | `ContentListInteractor.kt:142-163` |
| Focus | A focusable header is a new Up target from grid row 1 and a new Left path to the rail; the grid's launch focus must still land on a card; panel close must restore the card, not the button | `ShowAllScreenContent.kt:94-111`; open Show all focus runs above |
| Screen identity | Filter must not enter the screen key, or Voyager state, DI scope and focus memory split per filter | `ShowAllScreen.kt:36` |
| API | Genres/countries decode the wrong shape; rating and sort fields beyond the doc may be ignored silently, which looks like a working filter | `KinoPubApiClient.kt:632-643`; External |
| Paging race | `resetPaging` while a walk step or the cache's fresh read is in flight must drop the old query's page (generation guard exists for walks only) | `ContentListPagingVM.kt:249-257` |
| 4K tab | `all_4k` has no type, so genres by type and «finished» have no single meaning there | `TabTypeConfig.kt:211-215` |

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Which grids get filters | Browse grids only (the `all_*` config of each catalogue tab, from the entry and from the «Все» tile); shortcut shelves (fresh/hot/popular) cannot take server filters |
| Q2 | Control shape | Option A: one «Фильтры» button and a side panel; changes apply when the panel closes |
| Q3 | Which fields in the first version | Sort (updated, created, year, rating, views; direction), genre (one), country (one), year range (decade presets + "from-to"), finished (series types only). Ratings and quality only if a device check confirms `conditions[]` and quality ids work; otherwise drop them from `ContentFilter` |
| Q4 | How long a chosen filter lives | Persisted per tab across app restarts, with the active filter shown in the header and a reset action |
| Q5 | Genre «Аниме» while show-anime is off | Hide the anime genre from the list on Movies/Series when show-anime is off |
| Q6 | 4K tab | Filters without genre-by-type and «finished»: sort, year, country, genre from the untyped list |
| Q7 | Cache for filtered pages | Filtered queries bypass the persistent first-page cache; the unfiltered browse page keeps it |

## Sources

- Files: those cited above; `docs/architecture/paging-and-filters.md`; `gradle/libs.versions.toml:7-8`; `AGENTS.md`
- Commits: 28db3a78, 0c22bc33, 6b75a325, d051c873 (last touch of `GenreChipBar.kt`), 88cdf071 (`ContentFilter` added)
- Runs: catalogue-browse-all (state, research, spec), catalogue-poster-grid (research), dead-code-sweep, cached-payload-row-too-big, layer-deps-cleanup, showall-first-entry-rail-focus, showall-entry-restore-foreign-landing, showall-rail-back-exits-app, tab-refresh-while-covered-flash, upstream-sync-triage (no filter work found)
- URLs: https://kinoapi.com/api_video.html, https://kinoapi.com/api_references.html, https://github.com/aqualhume/kinopub-api (`api/v1/content.md`, `api/v1/references.md`), https://developer.android.com/jetpack/androidx/releases/tv, https://developer.android.com/reference/kotlin/androidx/tv/material3/package-summary, https://developer.android.com/design/ui/tv/guides/components/navigation-drawer
