# Research: catalogue-browse-all

## Request

> Browse a whole content type (all movies, all series) as a paged grid, not just one shelf
>
> 2026-11-24 created (stage idea); owner request: today Show all only opens one shelf from ContentListVM.kt:141 (ShowAllScreen(config: SectionConfig)); there is no way to see all series or all movies. Add an entry per content type that pages the whole catalogue in the grid of catalogue-poster-grid. Part 2 of 3, after catalogue-poster-grid; catalogue-filters builds on it.

## Understanding

Each catalogue tab (Movies, Series, Concerts, Doc. movies, Doc. series, TV shows, 4K) shows shelves, and every shelf ends in a Show all tile that opens the 6-column poster grid for that shelf. The last shelf of every tab, «Все», is already "the whole type, newest updated first" (`GET items?type=<t>&sort=updated-&page=n`), so the grid can already page a whole type. What is missing is a direct, named entry: the user has to go down four shelf pages to «Все», scroll right past its first page of cards to reach Show all, and the grid that opens has no title saying what it shows. The run is therefore an entry-point and identity change on top of an existing paging path, plus the seam that catalogue-filters will turn into a filter bar.

## Current state in code

| Where | What it does now |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/TabTypeConfig.kt:25-42` | Tab to sections: Movies `movie`, Series `serial` (both `AnimeFilterMode.FollowPreference`), Concerts `concert`, DocMovies `documovie` (5 shelves); DocSeries `docuserial`, TvShows `tvshow` (no 4K shelf); Cartoons, Anime (genre-based, tabs disabled); For4k (quality only, no type) |
| `TabTypeConfig.kt:77-83`, `:120-125`, `:186-190` | «Все» shelf `all_<type>` / `all_4k`: `type`, `sort = "updated-"`, no shortcut: already a whole-type query |
| `app/src/main/java/com/kino/puber/domain/model/TabType.kt:16-34` | Enabled catalogue tabs: Movies, Series, For4k, Concerts, DocMovies, DocSeries, TvShows; Cartoons, Anime, Collections, SportTV disabled. No 3D tab |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/SectionConfig.kt:8-29` | Parcelable shelf description (id, title/titleRes, type, shortcut, shortcutTypes, sort, quality, genre, requiredGenreId, animeFilterMode); also the nav argument of `ShowAllScreen` |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/ContentListScreen.kt:41-82` | Builds a `SectionVM` per section in the screen scope; key `ContentListScreen_<tab>` |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt:163-226` | Details panel on top, `LazyColumn` of hero + sections below; each section fills the viewport (`itemsFillViewport = true`, `:153`), so «Все» is the 5th page down on Movies |
| `ContentListScreenContent.kt:390-415` | Every section gets `onShowAll`; «Все» (last section) does not page in more (`loadsMore = !isLastSection`) because its Show all grid "is the whole catalogue anyway" |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/SectionRow.kt:187-205` | Show all is a `Button` after the last card of the `LazyRow`; in paging shelves it is reachable only when paging is exhausted |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt:138-150` | `ContentListAction.ShowAll(config)` navigates for result to `ShowAllScreen(config)`; returned `ContentChangeSet` refreshes the tab |
| `app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt:27-63` | Screen key `ShowAllScreen_<config.id>`; own scope with its own `ContentListInteractor` and `ShowAllVM` |
| `app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:49-71` | Re-pages on display-setting changes; on watch-state changes re-pages only when hide-watched is on, else remaps |
| `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:47-161` | 6-column `LazyVerticalGrid` of `VideoItemPoster`, load more 4 rows before the end, focus memory; no header or title |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:31-155` | Shared paging for shelf and grid; `config` is a private `val` (fixed for the VM's life); `refreshFirstPage` keeps depth, `resetPaging` clears |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:56-96` | First page through `ContentCacheRepository.sectionPage` keyed by every `SectionConfig` field + `showAnime` + `hideWatched` (`:107-123`); later pages from network |
| `ContentListInteractor.kt:125-178` | Client-side filtering: anime by `Item.isAnime()` per `AnimeFilterMode`; hide-watched via `WatchStateRepository.isFullyWatched`; with hide-watched on, at most 5 server pages per step (`MAX_PAGES_PER_STEP`, `:202`), may return a short or empty page |
| `ContentListInteractor.kt:180-188` | `shortcut` → `getItemsByShortcut` (`items/<shortcut>`), else `getItems(type, sort, page, quality, genre)` |
| `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:253-268` | `items` supports `type, sort, page, quality, genre, conditions[]`; no `country`, `year`, `finished`, `perpage` parameters |
| `KinoPubApiClient.kt:632`, `:641` | `getGenres(type)`, `getCountries()` exist (genres used by `GenreInteractor.kt:16`) |
| `app/src/main/java/com/kino/puber/data/api/models/Models.kt:38-43` | `Pagination(current, perpage, total, total_items)`: a total count for a grid title is available |
| `app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6-31` | `ContentFilter`, `SortField`, `SortDirection`: no usages anywhere in `app/src` |
| `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt:16-27` | `showAnime` (default true), `hideWatched` (default false) |
| Tests | `app/src/test/.../contentlist/model/TabTypeConfigTest.kt` (section shapes, anime modes), `.../showall/vm/ShowAllVMTest.kt`, `.../contentlist/vm/ContentListVMTest.kt`, `.../domain/interactor/contentlist/ContentListInteractorTest.kt`, `app/src/androidTest/.../SectionRowFocusTraversalTest.kt` |

## Related work

| Item | Relation |
|---|---|
| Run `catalogue-poster-grid` (done, 0c22bc33), spec `docs/superpowers/specs/2026-11-24-catalogue-poster-grid.md` (out of scope line 63, constants line 265), stand `docs/design/showall/` | Part 1: the grid and `VideoItemPoster` this run reuses; grid constants named for reuse |
| Run `catalogue-filters` (idea) | Part 3: filter and sort panel on this run's grid, `resetPaging()` after each change |
| Run `dead-code-sweep` (idea) | Title proposes removing `ContentFilter`; conflicts with catalogue-filters |
| Run `catalogue-card-audio-subtitle-badges` (idea) | Card badges; independent |
| Runs `showall-rail-back-exits-app` (idea), `showall-focus-restore-hardening`, `showall-first-entry-rail-focus`, `showall-entry-restore-foreign-landing`, `showall-rail-close-focus-shift`, `grid-focus-restore-after-back` | Show all focus history; a new entry path inherits it |
| Commit 133d31fe | Gave every shelf a Show all and paging, kept «Все» unpaged |
| Commit 6b75a325 | First pages served from the payload cache (key in `ContentListInteractor.cacheKey`) |
| Run `catalogue-single-carousel` (done) | Current tab layout |

Possible duplicate: none. The «Все» shelf's Show all already pages a whole type; that is an existing feature, not a run.

## External findings

| Finding | Source |
|---|---|
| `GET /v1/items`: `type` one of `movie, serial, 3D, concert, documovie, docuserial, tvshow`; `genre`, `country` (ids, comma list), `year` (`y` or `y1-y2`), `finished` (0/1), `actor`, `director`, `letter`, `conditions` (array, AND), `force`, `quality` (array of quality ids); `sort` default `updated-`, fields `id, year, title, created, updated, rating, views, watchers`, `-` prefix = desc (doc puts it as suffix in the default); `page`/`perpage` not documented for `items` | https://kinoapi.com/api_video.html (API 1.3, read 2026-11-24) |
| Shortcuts `/v1/items/fresh|hot|popular`: `type`, `page` (doc says default 0), `perpage` (default 25) | same |
| `/v1/types` lists types; `/v1/genres?type=` accepts `movie, documovie, serial, docuserial, tvshow, concert, 3d, 4k`; `/v1/countries` | same |
| Unofficial OpenAPI mirror of the docs exists (not read in detail) | https://github.com/aqualhume/kinopub-api |

Unverified against the live API: `kinopoisk_rating` / `imdb_rating` as sort fields (`ContentFilter.SortField` has them, the doc does not); `quality=4k` as a string (doc says quality ids; catalogue-poster-grid found 4K items carry `quality 2160`); whether `items` honours `perpage`. The app sends `page=1` first and asserts `pagination.current == page` (`ContentListInteractor.kt:147`), which works in production despite the doc's "default 0".

## Options

| | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Tab-level entry: a single "Все фильмы / Все сериалы / …" button as the first focus stop above the first shelf of each catalogue tab, opening the grid with a `browse_<type>` config (type, `sort = updated-`, the tab's anime mode) and a title header ("Фильмы · N" from `total_items`). The header is where catalogue-filters puts its filter/sort button. The «Все» shelf's Show all opens the same screen | One press from tab entry; named grid; header seam for part 3. Touches TV focus in `ContentListScreenContent` (a non-page item in a page layout, Up from first shelf, `sectionListItemIndex` offset, `SectionRowFocusTraversalTest`) | M |
| B | Reuse «Все»: move the shelf's Show all tile to its first position (or the shelf heading), rename the heading per type, add a title to the grid | Smallest; no new focus stop. Still four shelf pages down; no natural place for a filter entry | S |
| C | New `BrowseScreen` + `BrowseVM` keyed by a browse query built on `ContentFilter` from day one; entry as in A; `ShowAllScreen` stays per shelf | Clean contract for part 3 (mutable query, `resetPaging`), no change to shelf Show all; duplicates grid wiring and the Show all focus fixes, or forces extracting them first | M-L |

Notes that apply to A and C: `ContentListPagingVM.config` is immutable, so part 3 needs either a mutable query in the VM or a new screen per filter; the first-page cache key grows with every filter field, which suits only the unfiltered browse page.

## Risks

- **Focus.** A non-page item above the shelves breaks the "one list item per page" rule (`ContentListScreenContent.kt:150-153`) and shifts section list indices (`sectionListItemIndex`, `:425`); the entry restore, rail handoff and Back paths of Show all have a long bug history (runs above). Needs device evidence on the TV box.
- **Hide-watched.** With hide-watched on, a whole-type grid walks up to 5 server pages per load step and can show short or empty steps on a heavily watched account (`ContentListInteractor.kt:142-163`).
- **Anime.** Movies/Series with `showAnime = false` filter anime client-side; combined with a server genre filter in part 3 (e.g. genre anime) the grid can come out empty after budgeted walks.
- **4K tab** is cross-type (`quality = 4k`, no `type`); a per-type browse there has no single type.
- **Screen identity.** `ShowAllScreen` key is `ShowAllScreen_<config.id>`; a browse config needs an id distinct from `all_<type>`, or reusing it means both entries share Voyager state and focus memory (which may be wanted).
- **ContentFilter removal** by `dead-code-sweep` would take part 3's model.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Where the entry sits | Option A: one button above the first shelf of each catalogue tab |
| Q2 | Fate of the «Все» shelf | Keep it; its Show all opens the same browse screen (same config, same key) |
| Q3 | Which tabs get the entry | All enabled catalogue tabs including 4K (cross-type, quality 4K); no 3D tab is added |
| Q4 | Default order of the browse grid | `updated-`, as «Все» and the API default; sort choice is part 3 |
| Q5 | Grid header now | Yes: tab title and total count; part 3 adds filter/sort controls to it |
| Q6 | `dead-code-sweep` and `ContentFilter` | Keep `ContentFilter` out of the sweep while catalogue-filters is open |

## Sources

- Files: those cited above; `docs/architecture/paging-and-filters.md`; `AGENTS.md`; `app/src/main/res/values/main_screen_strings.xml:6-35`
- Commits: 0c22bc33, 9e73f81e, 133d31fe, 6b75a325, f1f083a5
- Runs: catalogue-poster-grid (state, research, brief), catalogue-filters, dead-code-sweep, catalogue-card-audio-subtitle-badges, catalogue-single-carousel, showall-rail-back-exits-app
- URLs: https://kinoapi.com/api_video.html, https://github.com/aqualhume/kinopub-api
