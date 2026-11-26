# Brief: catalogue-filters-country-year-status

Kind: feature   Size: M   Tracks: ui   Device verification: yes (TV focus in the panel, ViewModel state, networking, persistence)

## Request

Part 2 of the `catalogue-filters` split (`../catalogue-filters/brief.md#Split`): country, year and series-status filters in the browse-grid filter panel; fix the countries decode; add the `getItems` params it lacks. Extends the store of `catalogue-filters-persist` (`../catalogue-filters-persist/brief.md#Risks`).

## Findings

Code map, related runs, external API notes and options: `research.md: Current state in code`, `Related work`, `External findings`, `Options` (option A recommended). Checked at 90e69aff; the facts this brief relies on (paths under `app/src/main/java/com/kino/puber/`):

| Where | Fact |
|---|---|
| `domain/model/ContentFilter.kt:6-18` | `country`, `yearRange`, `finished` exist and already count toward `isActive` |
| `ui/feature/showall/model/ContentFilterQuery.kt:6-14` | `applyTo` maps only sort and genre |
| `ui/feature/contentlist/model/SectionConfig.kt:9-21` | No field for country, year or finished (parcelable data shape grows) |
| `data/api/KinoPubApiClient.kt:253-269` | `getItems` has no `country`, `year`, `finished` params |
| `domain/interactor/contentlist/ContentListInteractor.kt:197-205`, `:119-135` | Only caller of `getItems` (positional); `cacheKey` lists every `SectionConfig` field |
| `data/api/KinoPubApiClient.kt:642-644` | `getCountries` decodes a bare list; `getGenres` (`:632-637`) is the unwrap pattern |
| `domain/interactor/genre/GenreInteractor.kt:11-27`, `domain/di/modules.kt:42` | Cached genres interactor to mirror for countries |
| `ui/feature/showall/vm/ShowAllVM.kt:160-232` | Filter actions, draft/applied, close writes store and `changeQuery(..., cacheable = !applied.isActive)`; `withApplied` carries genre and sort only |
| `ui/feature/showall/model/ShowAllFilterState.kt:24-38`, `ShowAllAction.kt:8-22` | No country, year, status state or actions |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:40`, `:45`, `:76-126` | Max height 500 dp; pages `Root, Sort, Genre`; non-lazy `Column` + `verticalScroll`; focus returns to the last door |
| `ui/feature/showall/content/ShowAllFilterLabels.kt:40-51` | Header summary: genre, then non-default sort |
| `data/preferences/CatalogueFilterRepository.kt:23-62` | `StoredFilter` has genre and sort only; `ignoreUnknownKeys` |
| `ui/feature/contentlist/model/TabTypeConfig.kt:27`, `:212-216` | `isFilterable` = browse `all_*` grids; `all_4k` has `quality = "4k"`, no type |
| `data/api/models/Models.kt:145-146` | `ItemType.isSeriesLike`: serial, docuserial, tvshow |

## Size

M: one goal across API, interactor, cache key, ViewModel state, persistence and TV panel focus (risk areas in `.flow/roles/analyst.md`). Production files: about 12 plus strings, tests and `docs/architecture/paging-and-filters.md`; the showall design stand needs a ux delta (`docs/design/showall/README.md:120-174`).

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | the owner's box with a valid session | a read-only probe calls `countries` and `getItems` with `country`, `year=y`, `year=y1-y2`, `finished=1`, `finished=0` on each filterable type and on `quality=4k` | the response shape, list size and, per param, whether every returned item satisfies it and the total drops are recorded in `docs/superpowers/evidence/`, before the spec is approved |
| AC-2 | a `countries` response `{status, items:[{id,title}]}` | `getCountries()` is called | it returns the items list (client unit test, local `HttpServer` as in `KinoPubApiClientGenresTest`) |
| AC-3 | `all_movie` unfiltered | country X is picked and the panel closes | the first request carries `country=<X id>` with the base `type`, bypasses the first-page cache, and the header summary names X (VM and interactor unit tests; device: returned items list X) |
| AC-4 | `all_movie` | a year preset is picked and the panel closes | the request carries `year` as `y` or `y1-y2` for that preset and the summary names the preset (VM unit test) |
| AC-5 | `all_serial`, `all_docuserial`, `all_tvshow` (minus any type AC-1 shows ignoring `finished`) | «Статус» → «Завершённые» is picked | the request carries `finished=1`; on `all_movie`, `all_documovie`, `all_concert` and `all_4k` the «Статус» door is absent and `finished` is never sent (VM unit test; device for the door) |
| AC-6 | genre, sort, country, year and status all set | the panel closes | one request carries all of them; two configs differing only in country, year or finished get different `cacheKey`s (VM and interactor unit tests) |
| AC-7 | country, year and status applied | the app is restarted and the grid opened; separately, an entry written by the previous store version is read | the filter is restored with summary from the first frame; the old entry reads as genre/sort only with no country, year or status (VM and repository unit tests) |
| AC-8 | country, year and status applied | «Сброс» is pressed | all three clear, the store entry is removed, the grid reloads unfiltered and cached (VM unit test) |
| AC-9 | the countries request fails | the «Страна» page opens | it shows the load error and «Повторить», which reloads; the other doors still work (VM unit test) |
| AC-10 | the panel open on the TV | D-pad through every root door, into each new page, to the last country, and Back | every row is reachable; Back returns focus to the door it came from; a restored filter does not steal first focus on cold open (`ShowAllFilterFocusTest` extended, run on the box) |
| AC-11 | the new doors and pages | seen on the TV | they match the stand delta in sizes and materials (owner judges) |

## Risks

- An ignored param returns the unfiltered list and looks like a working filter (as `sort=...+` did, `docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md` check 3); AC-1 asserts on returned `Item.year`, `countries`, `finished` (`Models.kt:50`, `:53`, `:81`).
- A long country list in the non-lazy `Column` (`ShowAllFilterPanel.kt:88`) composes every row on page open; the list size from AC-1 decides lazy vs plain.
- `SectionConfig` is parcelable and the cache key source: a field missed in `cacheKey` (`ContentListInteractor.kt:119-135`) lets a later cacheable query share an entry.
- Narrow country + year + status with hide-watched can walk the step budget (`ContentListInteractor.kt:156`) and land on the filtered-empty state.
- Part 4 (`catalogue-filters-ratings-quality`) edits the same `ContentFilter`, panel and store; land this first or rebase on it.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Year: presets only, or presets plus a custom from-to range | Presets only, each sent as `y` or `y1-y2` (this year, last year, decades, «до 1980», «Любой»); a free range as a follow-up idea | yes |
| Q2 | Status values and grids | «Все» / «Завершённые» / «Выходящие» on serial, docuserial, tvshow; drop «Выходящие» if AC-1 shows `finished=0` ignored, drop a type if `finished` is ignored there; never on 4K | yes |
| Q3 | Country: one or several | One, like genre; API order, alphabetical if unordered | no |
