# Design (architect): catalogue-filters-country-year-status

Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Base 90e69aff. API facts: `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md` (below: evidence).

## Decisions

| Question | Decision | Why |
|---|---|---|
| Year model | Keep `ContentFilter.yearRange: IntRange?` (`domain/model/ContentFilter.kt:11`, set nowhere today) as the picked value; the page offers `yearOptions(currentYear): List<IntRange>` in `ShowAllFilterState.kt` beside `FILTER_SORT_OPTIONS` (`:10-16`): `y..y`, `(y-1)..(y-1)`, each decade from the current one down to `1980..1989`, `1900..1979` | Labels are numbers (design-ux.md «Год» page), so a stored single year keeps its number next year; the range is the only fact to store and send |
| Year options source | `ShowAllVM` computes `yearOptions` once at construction from `java.time.Year.now()` into `ShowAllFilterState`; tests set the state's options or assert on ranges | One clock read per screen; `java.time` is desugared (`app/build.gradle.kts:128`) |
| Year wire form | `first == last` → `"y"`, else `"first-last"` (`1900-1979` for «до 1980») | Evidence: `y1-y2` inclusive, upper bound past the current year accepted, `-1979` is 502, `1980-` reads as one year |
| Year label | `yearLabel(range)`: single year → the number; `D..D+9` → «D-е»; `1900..1979` → «до 1980»; `null` → «Любой год»; any other range → «first–last» | A stored year that left the options list (e.g. 2025 in 2027) still reads correctly; the page then selects no row and opens on «Любой год» |
| Status values | One door «Статус», last on the root, page «Любой статус» / «Завершённые»; `ContentFilter.finished` (`ContentFilter.kt:15`) is only ever `true` or `null`; «Выходящие» dropped | Q2 + evidence: `finished=0` ignored on every target |
| Status grids | Door shown when `base.type` maps to an `ItemType` with `isSeriesLike()` (`data/api/models/Models.kt:145`): `all_serial`, `all_docuserial`, `all_tvshow`; new `ShowAllFilterState.statusAvailable` | Q2, AC-5; `all_4k` has no `type` (`ui/feature/contentlist/model/TabTypeConfig.kt:213-218`) so it falls out |
| Country | Single `Country?` (existing `ContentFilter.country`, `ContentFilter.kt:8`), list shown in API order | Q3; evidence: 102 items, already alphabetical |
| Countries source | New `CountryInteractor` mirroring `GenreInteractor` (`domain/interactor/genre/GenreInteractor.kt:11-27`), payload-cached under `CacheKeys.countries()`, `CacheTtl.Countries = 30.minutes`; loaded on panel open beside genres, kept in the VM for its life | Same failure, retry and cache path as genres; the list is not type-dependent |
| Countries list UI | Country page is a `LazyColumn` (item `key = country.id`) in its own scroll area under the title, outside the outer `Column` + `verticalScroll` (`ShowAllFilterPanel.kt:88`); the panel takes a fixed width on that page (320 dp start value, design-ux.md) | 103 rows; a lazy list has no intrinsic width and fails with infinite height inside `verticalScroll` |
| Panel width | `TvSidePanelSurface` (`core/ui/uikit/component/panel/TvSidePanel.kt:54`) gains `fixedWidth: Dp? = null`: non-null replaces `widthIn` + `width(IntrinsicSize.Max)` (`:65-66`) with `width(fixedWidth)`; `null` keeps today's rule | Player settings, About and Episodes panels keep their sizing (design-ux.md Shared constants) |
| Country first focus | Page opens with `rememberLazyListState(initialFirstVisibleItemIndex = selected index + 1)` and calls `scrollToItem` when the list lands while loading, before `firstFocus.requestFocus()` | The selected row must be composed to take focus; no open on «Все страны» then jump |
| Request params | `SectionConfig` grows `country: Int?`, `year: String?` (wire form), `finished: Boolean?`, all `= null`; `getItems` grows the same three after `conditions`, `finished` sent as `1`/`0` | The query stays a parcelable value the interactor already keys on; appended defaults keep the positional mocks in `ContentListInteractorTest` (e.g. `:155`) matching |
| Cache key | `cacheKey` appends `config.country`, `config.year`, `config.finished` | AC-6; every field that changes a page joins the key (`domain/interactor/contentlist/ContentListInteractor.kt:119-135`) |
| Store | `StoredFilter` gains `countryId`, `countryTitle`, `yearFrom`, `yearTo`, `finished`, all defaulted `null`; year restored only when both bounds are present and `from <= to` | AC-7: summary from the first frame without a countries call; old entries decode with defaults (`ignoreUnknownKeys`, `data/preferences/CatalogueFilterRepository.kt:21`) |
| Summary order | genre · country · year · «Завершённые» · sort (sort only when not default), one line with ellipsis | design-ux.md summary order; extends `filterSummary` (`ui/feature/showall/content/ShowAllFilterLabels.kt:40-51`) |
| Root door order | Сортировка, Жанр, Страна, Год, Статус (last, conditional) | Existing doors keep their position and first focus |

## Changes by layer

### API / repository

| File | Change |
|---|---|
| `data/api/KinoPubApiClient.kt:253-269` | `getItems`: add `country: Int? = null`, `year: String? = null`, `finished: Boolean? = null` after `conditions`; each sent only when non-null |
| `data/api/KinoPubApiClient.kt:642-644` | `getCountries`: `apiCall<ApiResponseList<Country>>` + `.map { it.items.orEmpty() }`, as `getGenres` (`:632-637`) (AC-2) |
| `data/cache/CacheKeys.kt:22`, `:39`, `:64` | Add `Countries = "countries"` key and `CacheTtl.Countries` next to the genres entries |
| `domain/interactor/country/CountryInteractor.kt` (new) | `suspend fun getCountries(): Result<List<Country>>` via `contentCache.getPayload`, `runCatchingCancellable` |
| `domain/di/modules.kt:42` | `singleOf(::CountryInteractor)` |
| `domain/interactor/contentlist/ContentListInteractor.kt:197-205` | `fetchPage` passes `country`, `year`, `finished` by name to `getItems` (shortcut branch unchanged) |
| `domain/interactor/contentlist/ContentListInteractor.kt:119-135` | `cacheKey` appends the three fields |
| `data/preferences/CatalogueFilterRepository.kt:23-62` | `read`/`write`/`StoredFilter` carry country (id + title, both or none, like genre), `yearFrom`/`yearTo`, `finished`; KDoc line 15 updated |

### ViewModel state and actions

| File | Change |
|---|---|
| `ui/feature/contentlist/model/SectionConfig.kt:9-21` | Three nullable fields |
| `ui/feature/showall/model/ContentFilterQuery.kt:6-14` | `applyTo(base)` maps `country?.id`, `yearRange` to the wire form, `finished` |
| `ui/feature/showall/model/ShowAllAction.kt:8-22` | `CountryChosen(country: Country?)`, `CountriesRetry`, `YearChosen(range: IntRange?)`, `StatusChosen(finishedOnly: Boolean)` |
| `ui/feature/showall/model/ShowAllFilterState.kt:24-47` | Summary fields `countryTitle`, `yearRange: IntRange?`, `finishedOnly`; `statusAvailable`; `yearOptions: List<IntRange>`; `countries: FilterCountries` with a `FilterCountries` sealed type shaped as `FilterGenres` (`:41-47`) |
| `ui/feature/showall/vm/ShowAllVM.kt:160-171` | Route the four new actions; `StatusChosen` ignored unless `statusAvailable` |
| `ui/feature/showall/vm/ShowAllVM.kt:177-185`, `:219-232` | Open also loads countries (`rawCountries`, `countriesJob`, `loadCountries()` as `loadGenres`) (AC-9) |
| `ui/feature/showall/vm/ShowAllVM.kt:206-211` | `withApplied` sets the three summary fields |
| `ui/feature/showall/vm/ShowAllVM.kt:41-91` | `countryInteractor: CountryInteractor` in both constructors; initial state (`:95-97`) sets `yearOptions` and `statusAvailable` |
| `ui/feature/showall/vm/ShowAllVM.kt:187-204` | No change: close/reset already write the store, pick `cacheable` from `isActive` and reset to `ContentFilter()` (AC-3, AC-8) |

### Screen and DI module

| File | Change |
|---|---|
| `ui/feature/showall/ShowAllScreen.kt:44-55` | `countryInteractor = get()` |

### Compose content

| File | Change |
|---|---|
| `core/ui/uikit/component/panel/TvSidePanel.kt:54-66` | `fixedWidth: Dp? = null` per Decisions; KDoc (`:48-52`) names the lazy-page case |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:45` | `FilterPage` gains `Country`, `Year`, `Status`; titles at `:78-82`; `fixedWidth` passed at `:83` only on `Country` |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:88-101` | The outer `Column` + `verticalScroll` wraps every page except `Country`, which renders its own `LazyColumn` with a bounded height under the 500 dp `PanelMaxHeight` (`:40`) |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:64-65` | First-focus key also tracks `filter.countries::class` on the Country page |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:107-126` | Three doors with current value as `trailingText`; Статус only when `statusAvailable`; each takes `firstFocus` when it is `door` |
| `ui/feature/showall/content/ShowAllFilterPanel.kt:154-203` | `CountryPage`: lazy, rows as `GenrePage` (all row, loading, failed + Retry, rows keyed by id), first focus per Decisions; `YearPage` («Любой год» + `yearOptions`) and `StatusPage` («Любой статус», «Завершённые») in the scrolling `Column`, focus on the selected row or the first |
| `ui/feature/showall/content/ShowAllFilterLabels.kt:40-51` | `yearLabel(range)`, `filterSummary` order per Decisions |

### Resources and docs

| File | Change |
|---|---|
| `app/src/main/res/values/main_screen_strings.xml:44-64` | Door titles, «Все страны», «Любой год», «%d-е», «до 1980», «Любой статус», «Завершённые», «Не удалось загрузить страны» |
| `docs/architecture/paging-and-filters.md:99-128` | The three new `SectionConfig` fields, store fields |
| `docs/design/showall/README.md:120-174` | Stand delta by ux-designer (AC-11) |

### Tests touched by the shape change

| File | Change |
|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:947-958` | Factory passes a `CountryInteractor` mock |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt` | Extended for AC-10 (test-designer owns the rows) |

## Alternatives rejected

- Plain `Column` country page: composes and intrinsically measures 103 rows on every open (ux chose lazy + fixed width).
- Drop `IntrinsicSize.Max` for every side panel: resizes the player panels (design-ux.md Shared constants).
- `YearPreset` enum (THIS_YEAR, LAST_YEAR, ...): a stored relative pick rolls to the new year, against the numeric labels.
- Generic `FilterOptions<T>` replacing `FilterGenres`: refactors shipped genre paths and tests for no AC.
- Put countries on `GenreInteractor`: mixes two dictionaries under one name.
- Insert the new `getItems` params before `conditions`: no gain, and a positional caller of `conditions` would bind wrongly.
- Status on `all_documovie`: `finished=1` filters there (evidence) but AC-5 excludes it.

## Risks

| Area | Risk | Lives in |
|---|---|---|
| Focus | Root grows to five doors and three new pages; door-return and the restored-filter cold-open gate must hold. On «Страна» the selected row is off-screen until the list is scrolled: focus must follow `scrollToItem` (or the initial index), including when the list lands after loading or Retry, within the retry budget (`PANEL_FOCUS_ATTEMPTS`) | `ShowAllFilterPanel.kt:41-43`, `:57-71`; AC-10 |
| Layout | Lazy list under `animateContentSize` and a fixed width: width animates root → 320 on page change; an unbounded height inside the 500 dp cap or a stray outer `verticalScroll` crashes at measure | `TvSidePanel.kt:64-74`, `ShowAllFilterPanel.kt:83-88` |
| Shared component | `fixedWidth` default must leave player settings, About and Episodes panels byte-identical in sizing | `TvSidePanel.kt:54-66` |
| Cache | A new field missed in `cacheKey` lets a later cacheable query share an entry; appending fields also changes every existing section key once (one cold load per shelf after update, old rows age out) | `ContentListInteractor.kt:119-135` |
| Data shape | `SectionConfig` is parcelable; new fields must default to `null` so a base config and an unfiltered query stay equal | `SectionConfig.kt:9-21` |
| Persistence | Country stored as id + title: a server-side rename shows the old title until re-picked; a dropped id pages to the filtered-empty state, which offers «Сброс». A stored year outside this year's options selects no row on the page | `CatalogueFilterRepository.kt:23-34` |
| Paging | Narrow country + year + status with hide-watched can spend the step budget and land filtered-empty early | `ContentListInteractor.kt:156`, `:219` |
| Header | Five summary parts on one ellipsised line can cut the status or sort (accepted, design-ux.md) | `ShowAllFilterLabels.kt:40-51` |
| Rollout | Part 4 (`catalogue-filters-ratings-quality`) edits `ContentFilter`, the panel and the store; whichever lands second rebases | `ContentFilter.kt`, `ShowAllFilterPanel.kt`, `CatalogueFilterRepository.kt` |

## Open questions

N/A: D1 and D2 settled by the conductor on the owner's standing instruction (numeric year labels; one-line ellipsis).
