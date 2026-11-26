# Research: catalogue-filters-country-year-status

## Request

> Country, year and series-status filters in the catalogue filter panel (part 2 of the L split of run catalogue-filters): country filter (fix the countries list decoding), year presets/range, "finished" status for series types; add the API params getItems lacks.

## Understanding

The browse grid's filter panel (shipped by `catalogue-filters`, persisted by `catalogue-filters-persist`) offers only sort and one genre. `ContentFilter` already has `country`, `yearRange` and `finished`, but nothing sets them, the query mapping drops them, `SectionConfig` has no field to carry them, `getItems` cannot send them, the countries call decodes the wrong shape, and the persisted filter does not store them. This run adds three panel doors (Страна, Год, Статус on series types), carries the values through the query, request, header summary and store, and keeps the existing cache, focus and paging guarantees.

## Current state in code

Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Base 90e69aff.

| Where | What it does now |
|---|---|
| `domain/model/ContentFilter.kt:6-18` | Fields `country: Country?`, `yearRange: IntRange?`, `finished: Boolean?` exist; `isActive` = differs from `ContentFilter()`, so setting any of them already counts as active |
| `ui/feature/showall/model/ContentFilterQuery.kt:6-14` | `applyTo(base)` maps only `sort` and `genre`; country, year, finished are dropped |
| `ui/feature/contentlist/model/SectionConfig.kt:9-21` | Parcelable query: type, shortcut, sort, quality, genre, requiredGenreId, animeFilterMode; no country, year, finished |
| `data/api/KinoPubApiClient.kt:253-269` | `getItems(type, sort, page, quality, genre, conditions)`; no `country`, `year`, `finished` |
| `domain/interactor/contentlist/ContentListInteractor.kt:197-205` | `fetchPage` calls `getItems` positionally with SectionConfig fields; the only caller |
| `ContentListInteractor.kt:60-71`, `:119-135` | `cacheable = false` bypasses the payload cache (filtered queries); `cacheKey` lists every SectionConfig field, so new fields must join it |
| `data/api/KinoPubApiClient.kt:642-644` | `getCountries()` decodes a bare `List<Country>`; never called. `getGenres` (`:632-637`) is the fixed pattern: `apiCall<ApiResponseList<Genre>>` + `items.orEmpty()` |
| `data/api/models/Models.kt:11-15`, `:203-206` | `ApiResponseList<T>` wrapper; `Country(id, title)` |
| `domain/interactor/genre/GenreInteractor.kt:11-27`, `data/cache/CacheKeys.kt:22`, `:39`, `:64` | Payload-cached genres (5 min TTL); no country interactor, key or TTL |
| `ui/feature/showall/vm/ShowAllVM.kt:160-171` | Filter actions: panel open/close, sort field, direction, genre, genres retry, reset |
| `ShowAllVM.kt:177-232` | Open seeds draft from `applied`; close writes the store and `changeQuery(applied.applyTo(base), cacheable = !applied.isActive)`; genres loaded per `base.type ?: base.quality` |
| `ShowAllVM.kt:206-211`, `ui/feature/showall/model/ShowAllFilterState.kt:24-38` | Header summary state carries genre title, sort, direction only |
| `ShowAllVM.kt:370-381` | `restoreFilter` reads the store before the super call (first load is the filtered query) |
| `ui/feature/showall/model/ShowAllAction.kt:8-22` | No country, year or status actions |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:40`, `:45`, `:107-126` | Panel max height 500 dp; pages `Root, Sort, Genre`; root has two doors with current values; value pages are `Column` + `verticalScroll` (non-lazy) |
| `ui/feature/showall/content/ShowAllFilterLabels.kt:40-51` | `filterSummary`: genre, then sort when not default |
| `data/preferences/CatalogueFilterRepository.kt:23-62` | Stores genre id/title, sort, direction per config id; `ignoreUnknownKeys`; malformed reads as no filter |
| `ui/feature/contentlist/model/TabTypeConfig.kt:12-28`, `:206-216` | `isFilterable` = the seven `all_*` browse configs; `all_4k` has no `type`, only `quality = "4k"` |
| `data/api/models/Models.kt:145-146` | `ItemType.isSeriesLike`: serial, docuserial, tvshow (the candidate grids for Статус) |
| `data/api/models/Models.kt:50`, `:53`, `:81` | `Item.year`, `countries`, `finished` decoded: a unit or box check can assert the filter on returned items |
| `app/src/main/res/values/main_screen_strings.xml:44-63` | Filter strings; none for country, year, status |
| Tests | `app/src/test/.../showall/vm/ShowAllVMTest.kt`, `.../showall/model/ContentFilterQueryTest.kt`, `.../data/api/KinoPubApiClientGenresTest.kt` (local `HttpServer` pattern to copy for countries), `.../data/preferences/CatalogueFilterRepositoryTest.kt`, `.../domain/interactor/contentlist/ContentListInteractorTest.kt:803-843`; `app/src/androidTest/.../showall/content/ShowAllFilterFocusTest.kt` |

## Related work

| Item | Relation |
|---|---|
| `catalogue-filters` (done, 948783d1) | Parent; `brief.md#Split` item 2 defines this run; spec `docs/superpowers/specs/2026-11-24-catalogue-filters.md` (panel, actions, cache bypass, focus rules this run extends) |
| `catalogue-filters-persist` (done, e5bfd7d1) | Store must grow: its brief Risks names country, year, finished; spec `docs/superpowers/specs/2026-11-25-catalogue-filters-persist.md:44` |
| `docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md` | Method for a read-only box API check; showed the official doc's bare-array genres shape is really `{status, items}` |
| `catalogue-filters-ratings-quality` (idea) | Same panel and `ContentFilter`; may remove `kinopoiskRating`, `imdbRating`, `quality` and the two sort fields; `conditions[]` belongs to it |
| `catalogue-filters-cartoon-anime-grids` (idea) | Same panel on `all_cartoon` / `all_anime`; untouched here |
| `docs/design/showall/README.md:120-174` | Stand scene «catalogue-filters»: header and two-door panel; new doors need a ux delta |
| `dead-code-sweep` (idea) | Lists `getCountries` as a candidate; this run makes it live |
| Commits b4dd70d4 (genres decode, `applyTo`, `changeQuery`), d52cf9be (panel), cdb191ef / 102d6764 (store, restore) | The seams this run extends |

Possible duplicate: none.

## External findings

| Finding | Source |
|---|---|
| `GET /v1/items`: `country` = country id, comma list; `year` = one year or `year1-year2`; `finished` = `0` / `1` (series status, 1 = finished); `conditions` AND-combined, fields as for `sort` | https://kinoapi.com/api_video.html (API 1.3, read 2026-11-25) |
| `GET /v1/countries`: no params; doc shows a bare array of `{id, title}` (the same doc shows genres as a bare array, which the box disproved) | same |
| Unofficial: `/v1/countries` returns `{status, items: [{id, title}]}`; `year` "often `YYYY` or `YYYY-YYYY`"; `finished` "typically for series"; `conditions[]=year>=1900` observed in the official app | https://github.com/aqualhume/kinopub-api `api/v1/content.md` (commit 5899671d, 2026-01-14; not affiliated) |

`unverified`: the countries wrapper and list size (drives the picker: scroll length, order, whether a lazy list is needed); `country`, `year` range and `finished` honoured together with `type` and with `quality=4k`; `finished` on `docuserial` and `tvshow`; whether `finished=0` means "airing" or is ignored. A read-only box check like the parent's settles all of these before the spec.

## Options

| | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Three new doors on the panel root: Страна (single, «Все страны» + list), Год (presets only: e.g. this year, decades back to «до 1980», «Любой»), Статус (Все / Завершённые / Выходящие; series grids only, not 4K). `SectionConfig` gains `country`, `year` (string `y` or `y1-y2`), `finished`; `applyTo` fills them; `getItems` sends them; `cacheKey` includes them; `CountryInteractor` payload-cached; `getCountries` unwraps; store, restore and summary extended | Same panel pattern (pick, apply on close), one-press year choice on a remote; covers the request except a free range. Root grows from 2 to 5 rows; parcel shape of `SectionConfig` grows | M |
| B | A plus a custom year range («с … по …» two steppers on the Год page) | Exact ranges; a stepper is a new D-pad control with its own focus and repeat-key behaviour, more ux and itest work | M (upper) |
| C | Pass the new values through `conditions[]` (`year>=…`) instead of new params | Avoids `SectionConfig`/`getItems` changes for year only; country and finished have no documented condition form, so params are needed anyway; mixes in part 4's unverified mechanism | M |

## Risks

| Area | Risk | Lives in |
|---|---|---|
| Silent API no-op | An ignored param returns the unfiltered list and looks like a working filter (as `sort=…+` did) | box check; assert on returned `Item.year` / `countries` / `finished` |
| Decode | `getCountries` fails on the wrapper exactly as genres did | `KinoPubApiClient.kt:642-644` |
| Panel length | A long country list in a non-lazy `Column` composes every row on open; max height 500 dp | `ShowAllFilterPanel.kt:40`, `:88` |
| Cache | New `SectionConfig` fields missing from `cacheKey` would let a cacheable query share an entry; filtered queries bypass it today | `ContentListInteractor.kt:119-135` |
| Store | Old entries lack the new keys (defaults must read as "none"); a stored country id the API drops pages an empty grid, which falls to the filtered-empty state | `CatalogueFilterRepository.kt:56-62` |
| Status on mixed grids | `all_4k` mixes movies and series; `finished` there is undefined | `TabTypeConfig.kt:214-216` |
| Narrow result + hide-watched | Country + year + status can leave few items; hide-watched then walks up to the step budget | `ContentListInteractor.kt:156` |
| Focus | Root page now has five doors; door-return focus and the restored-filter cold-open gate must hold for the new rows | `ShowAllFilterPanel.kt:57-71`; `ShowAllFilterFocusTest` |

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Year control: presets only, or presets plus a custom from-to range | Presets only (Option A); custom range as a follow-up idea if the owner misses it |
| Q2 | Which grids show Статус | Serial, DocSeries, TvShows browse grids (`isSeriesLike` types); drop a type if the box check shows `finished` ignored there; never on 4K |
| Q3 | Status values | Tri-state: Все / Завершённые (`finished=1`) / Выходящие (`finished=0`), if the box check confirms `0` filters; else Все / Завершённые |
| Q4 | Country: one or several | One, like genre; order as the API returns it, alphabetical if it is unordered |
| Q5 | Box API check before the spec | Yes: countries shape and size, `country`, `year=y1-y2`, `finished=0/1` per browse type and on 4K |

## Sources

- Files: those cited above; `docs/architecture/paging-and-filters.md` (Filters), `docs/architecture/api.md:53`; `AGENTS.md`; `.flow/config.yaml`
- Commits: b4dd70d4, d52cf9be, cdb191ef, 102d6764, 67862289, 88cdf071 (`getCountries` added)
- Runs: catalogue-filters (brief, state, research, design-architect), catalogue-filters-persist (brief, state), catalogue-filters-ratings-quality, catalogue-filters-cartoon-anime-grids, dead-code-sweep, cached-payload-row-too-big
- Evidence: `docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md`; stand `docs/design/showall/README.md`
- URLs: https://kinoapi.com/api_video.html, https://github.com/aqualhume/kinopub-api (`api/v1/content.md`)
