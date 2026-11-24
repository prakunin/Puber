# Brief: catalogue-filters

Kind: feature   Size: L   Tracks: ui   Device verification: yes (TV UI, focus, D-pad, overlay, ViewModel state, networking)

## Request

Owner, 2026-11-24: «нет фильтрации». A D-pad friendly filter and sort control on the browse-all grid driven by `ContentFilter`, re-paging after each change. Part 3 of 3, after `catalogue-browse-all` (merge 28db3a78).

## Findings

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

## Size

L: a new data flow (filter → API params, cache bypass, genre/country lists), a new overlay with its own focus paths, persistence, and API params that are `unverified` (ratings, quality ids). Not planned here; see Split.

## Split

| # | Item | Size | Goal |
|---|---|---|---|
| 1 | `catalogue-filters` (this run, narrowed) | M | Changeable query on browse grids; «Фильтры» button in `ShowAllHeader` with active-filter summary and «Сброс»; side panel (`TvDialogOverlay`) with Sort (field + direction: updated, created, year, rating, views) and Genre (one); applies on close; genres unwrap fix; filtered first pages bypass the persistent cache; filter lives for the screen instance |
| 2 | `catalogue-filters-country-year-status` | M | Country (countries unwrap fix), year presets and range, «Статус» (finished) on series types; new `getItems` params |
| 3 | `catalogue-filters-persist` | M | Filter persisted per tab (config id) across app restarts |
| 4 | `catalogue-filters-ratings-quality` | M | Kinopoisk / IMDb rating ranges and quality, only after the box API check confirms `conditions[]` and quality values; otherwise remove those fields and `SortField.KINOPOISK` / `IMDB` from `ContentFilter` |

Order: 1, 2, 3, 4. 3 and 4 are independent of each other.

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

## Risks

- **TV focus.** A focusable header adds an Up target from row 1 and a Left path to the rail next to three open Show all focus runs (`research.md: Related work`); panel close must restore the button, Back from details the card. Guarded by AC-1, AC-3, AC-4, AC-11.
- **Paging race.** The first-page collect at `ContentListPagingVM.kt:120` has no generation guard; a query swap during a cached read or a hide-watched walk must drop the old result (AC-8).
- **Silent API no-ops.** An unsupported param returns the unfiltered list and looks like a working filter. Item 1 uses only documented params (`sort` fields, `genre`); the box check in design confirms `genres?type=` per type and the 4K tab's list.
- **Anime + genre.** With show-anime off a non-anime filter still walks client-side; a narrow genre with hide-watched gives short steps (`ContentListInteractor.kt:142-163`), covered by AC-12.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Accept the split and narrow this run to item 1 | Yes: items 2-4 become idea runs with `source: catalogue-filters` | yes |
| Q2 | How long a filter lives in item 1 | For the screen instance: survives details / player and back, cleared when the grid is closed; persistence across restarts is item 3 | yes |
| Q3 | 4K tab genre list | `genres?type=4k` if the box check returns a non-empty list, else the untyped list; no «Статус» there | no |

Assumed from `research.md: Open questions`: Q1 (browse grids only), Q2 (Option A, apply on close), Q5 (hide «Аниме» when show-anime is off), Q7 (filtered queries bypass the persistent cache).
