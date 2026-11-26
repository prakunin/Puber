# Country, year and series-status filters in the catalogue filter panel

Status: approved
Date: 2026-11-25
Slug: catalogue-filters-country-year-status
Kind: feature
Size: M
Tracks: ui

## Problem / Why

Part 2 of the `catalogue-filters` split (`../catalogue-filters/brief.md#Split`): country, year and series-status filters in the browse-grid filter panel; fix the countries decode; add the `getItems` params it lacks. Extends the store of `catalogue-filters-persist` (`../catalogue-filters-persist/brief.md#Risks`).

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

### Risks (analyst)

- An ignored param returns the unfiltered list and looks like a working filter (as `sort=...+` did, `docs/superpowers/evidence/2026-11-24-catalogue-filters-api-check.md` check 3); AC-1 asserts on returned `Item.year`, `countries`, `finished` (`Models.kt:50`, `:53`, `:81`).
- A long country list in the non-lazy `Column` (`ShowAllFilterPanel.kt:88`) composes every row on page open; the list size from AC-1 decides lazy vs plain.
- `SectionConfig` is parcelable and the cache key source: a field missed in `cacheKey` (`ContentListInteractor.kt:119-135`) lets a later cacheable query share an entry.
- Narrow country + year + status with hide-watched can walk the step budget (`ContentListInteractor.kt:156`) and land on the filtered-empty state.
- Part 4 (`catalogue-filters-ratings-quality`) edits the same `ContentFilter`, panel and store; land this first or rebase on it.

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

AC-1 is done: `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md`.

## Out of scope

- A free from-to year range (Q1: presets only; follow-up idea).
- «Выходящие» (`finished=0` is ignored by the server, AC-1 evidence).
- A «Популярные» country section at the top of the list (design-ux open item 3).
- Ratings and quality filters (part 4, `catalogue-filters-ratings-quality`).

## Design

### Architect

Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Base 90e69aff. API facts: `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md` (below: evidence).

#### Decisions

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

#### Changes by layer

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

#### Alternatives rejected

- Plain `Column` country page: composes and intrinsically measures 103 rows on every open (ux chose lazy + fixed width).
- Drop `IntrinsicSize.Max` for every side panel: resizes the player panels (design-ux.md Shared constants).
- `YearPreset` enum (THIS_YEAR, LAST_YEAR, ...): a stored relative pick rolls to the new year, against the numeric labels.
- Generic `FilterOptions<T>` replacing `FilterGenres`: refactors shipped genre paths and tests for no AC.
- Put countries on `GenreInteractor`: mixes two dictionaries under one name.
- Insert the new `getItems` params before `conditions`: no gain, and a positional caller of `conditions` would bind wrongly.
- Status on `all_documovie`: `finished=1` filters there (evidence) but AC-5 excludes it.

#### Risks

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

#### Open questions

N/A: D1 and D2 settled by the conductor on the owner's standing instruction (numeric year labels; one-line ellipsis).

### UX delta (ux-designer)

#### Governing stand

`docs/design/showall/` governs the change: section «catalogue-filters starting values (2026-11-24)» (`docs/design/showall/README.md:120-187`) draws the header, the panel and its two doors. It has no «Страна», «Год», «Статус» doors or pages, so the run updates the stand: the «catalogue-filters» scene in `docs/design/showall/index.src.html` gets the three doors and their pages with a slider for each new number below, the README gets a section «catalogue-filters-country-year-status starting values», and `python3 docs/design/build.py showall` rebuilds it. `designing-a-screen` does not have to run before planning. The owner dials it at brief.md#AC-11.

The catalogue-filters numbers were never dialled on the box (README:122-123), so everything below, inherited or new, is a starting value.

#### Numbers that apply (dp / sp at 1080p, 1 dp = 2 px)

Inherited unchanged, quoted from README «catalogue-filters starting values», in code in `core/ui/uikit/component/panel/TvSidePanel.kt`:

| What | Value |
|---|---|
| panel width | 240 - 420, by the widest row (`TvSidePanelSurface` `IntrinsicSize.Max`) |
| panel height | content, max 500, vertically centred; a longer page scrolls under its title (`ShowAllFilterPanel.kt:40`) |
| corner / padding start, top, end, bottom | 20 start side only / 20, 20, 20, 16 |
| material | `panelGlass(Strong)`, elevation 20; scrim 62 % flat |
| title | 14 / 18 sp SemiBold, 12 to the rows |
| row | min 40, padding 12 / 6, radius 12, label 14 / 18 sp Medium (2 lines max), trailing 13 sp at 76 %, 16 before trailing |
| selected / focused | primary 16 % + check 18 primary, 12 before it / onSurface 30 % |
| loading / failure | one row with a 20 spinner / read-only error 14 sp onSurfaceVariant, then focusable «Повторить» |
| header summary | 11 / 16 sp Regular, #CAC4D0, 1 line, ellipsis, parts joined by « · » |

New:

| What | Start value | Why |
|---|---|---|
| root doors | «Сортировка», «Жанр», «Страна», «Год», «Статус» in this order; «Статус» only on `all_serial`, `all_docuserial`, `all_tvshow` (brief.md#AC-5) | existing doors keep their place; the conditional door is last, so its absence moves nothing |
| root trailing values | «Все страны» or the country; «Любой год» or the preset label; «Любой статус» or «Завершённые» | trailing = the selected row's label, as «Все жанры» on the genre door |
| root height | 5 doors: 18 + 12 + 5 × 40 + 36 = 266; 4 doors: 226 | no scroll |
| «Страна» page | «Все страны», then 102 countries in API order (alphabetical, AC-1 evidence) | Q3 |
| «Страна» page width | fixed **320** (slider 280 - 420); a lazy page has no intrinsic width | see Shared constants; rows wrap to 2 lines past it |
| «Страна» visible rows | (500 - 20 - 16 - 30) / 40 = 10.85: ten whole rows and a cut eleventh, the cut row is the scroll cue | |
| «Год» page | «Любой год», «2026», «2025», «2020-е», «2010-е», «2000-е», «1990-е», «1980-е», «до 1980» | Q1. Years are numbers, not «этот год» / «прошлый год», so a stored filter reads the same next year. Values sent: `y`, `2020-2029` ... `1980-1989`, `1900-1979` (AC-1 evidence: `-1979` is 502, `y1-` is a single year) |
| «Год» page height | 18 + 12 + 9 × 40 + 36 = 426 | no scroll; no section headers |
| «Статус» page | «Любой статус», «Завершённые» | Q2: «Выходящие» dropped, `finished=0` is ignored on every target (AC-1 evidence) |
| «Страна» failure text | «Не удалось загрузить страны», then «Повторить» | as the genre page (brief.md#AC-9) |
| summary order | genre · country · year · status · sort (sort only when not «по обновлению ↓»): «Драма · США · 2010-е · Завершённые · по году ↓» | filters before ordering; 1 line, ellipsis |

Open, for the owner on the stand:

1. «Статус» with two values: a door with a page (start, the AC-5 wording and the existing pattern) or a single toggle row «Только завершённые» on the root (one press less). Switch «статус».
2. Country page width 320, or the widest country title (a measured constant).
3. Reaching «Япония» takes about 103 presses down. A short «Популярные» section above the alphabet is a follow-up idea, not this run (Q3 fixed API order).

#### Focus

Inherits the catalogue-filters focus table (`.flow/runs/catalogue-filters/design-ux.md#Focus`). New rows:

| From | Key | To |
|---|---|---|
| panel opened | (none) | root, «Сортировка», as today; a restored country / year / status does not move it (brief.md#AC-10) |
| «Страна» / «Год» / «Статус» door | OK | its page, focus on the selected value; none selected: the first row («Все страны», «Любой год», «Любой статус») |
| «Страна» page, selected country far down | open | the list is scrolled so the selected row is visible before focus is requested; it must not open on «Все страны» and jump |
| «Страна» page, countries loading | open | «Все страны»; when the list lands, focus moves to the selected country if there is one |
| «Страна» page, load failed | open | «Повторить»; OK reloads and focus lands as for loading |
| any page | Up on the first row / Down on the last | stays (no wrap), as Sort and Genre |
| any page | Up / Down | next row; the list scrolls to keep the focused row whole, the title does not scroll |
| value row | OK | selects it, check moves, focus stays on the page (as Sort and Genre today) |
| new page | Back | root, on the door it came from (`door` state extended to the three pages) |

Focus visibility: every new focusable row is a `TvSidePanelItem`, so focused = onSurface 30 %, selected = primary 16 % + check. On «Страна» the selected row and the focused row usually differ (open on selected, then move), so both states must be visible at once; at the cut eleventh row the focused row is always scrolled whole.

#### Shared constants: parameterise, do not retune in place

| Constant | Lives in | Also drawn by | What to do |
|---|---|---|---|
| `TvSidePanelSurface` width rule `widthIn(240, 420)` + `width(IntrinsicSize.Max)` | `core/ui/uikit/component/panel/TvSidePanel.kt` | player settings, About, Episodes panels | Add an optional fixed-width parameter for a page whose content is a lazy list; the default keeps the intrinsic rule. Do not drop `IntrinsicSize.Max` for everyone |
| `TvSidePanelItem` metrics (40, 12 / 6, 14 / 18 sp, check 18) | same | player panels | Use as is; no new row style |
| `PanelMaxHeight` 500 | `ShowAllFilterPanel.kt:40` | filter panel only | Unchanged; the country list scrolls inside it |
| Page scroll | `ShowAllFilterPanel.kt:88` (`Column` + `verticalScroll` around every page) | Sort, Genre pages | The country page must not sit inside the outer `verticalScroll` (a lazy list in it fails with infinite height); give it its own scrolling container under the title |
| `DialogScrimAlpha` 0.62 | `TvDialogOverlay.kt:17` | every context menu | Do not change |

## Test design

Inputs: `brief.md`, `design-architect.md`, `design-ux.md`, AC-1 evidence `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md`. Base 90e69aff. Levels from `.flow/roles/test-designer.md`. Test files (under `app/src/`):

| Short | File | Status |
|---|---|---|
| Client | `test/kotlin/com/kino/puber/data/api/KinoPubApiClientCountriesTest.kt` | new, built like `KinoPubApiClientGenresTest` (local `HttpServer`, `@TempDir`) |
| Items | `test/kotlin/com/kino/puber/data/api/KinoPubApiClientItemsFilterTest.kt` | new, same rig; asserts the query string the server received |
| Query | `test/kotlin/com/kino/puber/ui/feature/showall/model/ContentFilterQueryTest.kt` | extended |
| Options | `test/kotlin/com/kino/puber/ui/feature/showall/model/YearOptionsTest.kt` | new |
| Interactor | `test/kotlin/com/kino/puber/domain/interactor/contentlist/ContentListInteractorTest.kt` | extended |
| Country | `test/kotlin/com/kino/puber/domain/interactor/country/CountryInteractorTest.kt` | new |
| Repo | `test/kotlin/com/kino/puber/data/preferences/CatalogueFilterRepositoryTest.kt` | extended |
| VM | `test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt` | extended through `filterRig` (`:390`), a `countryInteractor` mock beside `genreInteractor`; `FilterRig.applyFilter` grows the three new actions |
| Focus | `androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt` | extended; its `onAction` fake (`:86`) handles `CountryChosen`, `YearChosen`, `StatusChosen`; run as `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest` |

### Risk profile

| # | Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|---|
| R1 | An ignored param returns the unfiltered list and looks like a working filter | brief Risks | 1 | 3 | 3 | AC-1 evidence (done: country, year honoured everywhere; `finished=0` ignored, so not sent) |
| R2 | `getCountries` decodes a bare list, so the «Страна» page always fails | brief Findings | 3 | 3 | 9 | `getCountries_unwrapsTheItemsOfAStatusItemsBody` |
| R3 | A new field missing from `cacheKey` lets a filtered cacheable read share an unfiltered entry | design Risks Cache | 2 | 3 | 6 | `firstPageCache_isSeparatedByCountryYearAndFinished` |
| R4 | `getItems` param dropped or misnamed between `SectionConfig` and the wire (positional call, `finished` sent as `true` instead of `1`) | design API | 2 | 3 | 6 | `getItems_sendsCountryYearAndFinishedAsTheServerReadsThem`, `filteredBrowseConfig_sendsCountryYearAndFinishedWithTheBaseType` |
| R5 | Year wire form wrong («до 1980» as `-1979` is HTTP 502; `1980-` reads as one year) | evidence Range forms | 2 | 3 | 6 | `applyTo_yearRange_sendsTheExactYearOrAnInclusiveRange` |
| R6 | Lazy country list inside the outer `verticalScroll` crashes at measure, or the selected row is off-screen and focus falls back to «Все страны» | design Risks Focus, Layout | 2 | 3 | 6 | AC-10 instrumented rows |
| R7 | Door return broken for the three new pages; restored filter steals first focus | brief AC-10 | 2 | 3 | 6 | AC-10 instrumented rows |
| R8 | «Статус» shown on a non-series grid, or `finished` sent there | brief AC-5 | 2 | 2 | 4 | `statusDoor_isUnavailableOnMovieDocumovieConcertAnd4k`, `statusChosen_onAMovieGrid_isIgnored` |
| R9 | Old store entry fails to decode after the shape grows, losing the user's genre/sort | design Store | 1 | 3 | 3 | `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus` |
| R10 | `fixedWidth` default changes the player settings, About, Episodes panels | design Shared component | 1 | 3 | 3 | Manual M5 |
| R11 | Narrow country + year + status with hide-watched spends the step budget and lands filtered-empty | brief Risks | 2 | 1 | 2 | Accepted: existing `filteredEmptyPage_keepsTheFilterActive` and «Сброс» on the empty state |
| R12 | Five summary parts ellipsise status or sort | design Risks Header | 3 | 1 | 3 | Accepted by design-ux (one line, ellipsis); M3 observes it |
| R13 | Part 4 lands first and conflicts | brief Risks | 2 | 1 | 2 | Accepted: rebase; the suite below re-runs |

### Traceability

| AC | Level | Priority | Kind | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | manual | P0 | guard | Evidence `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md` | Done before the spec; records which params the server honours |
| AC-2 | unit | P0 | red | Client `getCountries_unwrapsTheItemsOfAStatusItemsBody` | Fails on base: the bare-list decode of `{status, items}` returns a failure |
| AC-2 | unit | P1 | - | Client `getCountries_refusedRead_failsInsteadOfAnEmptyList` | Fails if a non-200 maps to `Result.success(emptyList())` |
| AC-3 | unit | P0 | red | Items `getItems_sendsCountryYearAndFinishedAsTheServerReadsThem` | Server sees `country=1`, `year=2010-2019`, `finished=1`; fails if a param is not sent or `finished` goes as `true` |
| AC-3 | unit | P1 | - | Items `getItems_withoutFilterParams_sendsNoneOfThem` | Fails if a null param is sent as empty or `0` |
| AC-3 | unit | P0 | red | Interactor `filteredBrowseConfig_sendsCountryYearAndFinishedWithTheBaseType` | `api.getItems` gets `type="movie"` and the three values; fails if `fetchPage` drops one or passes it positionally into `conditions` |
| AC-3 | unit | P0 | red | VM `countryChosen_panelClosed_readsTheCountryQueryUncachedAndSummarisesIt` | Last `FirstPageCall` has `country` = id, base `type`, `cacheable=false`; `filterState.countryTitle` = title; fails if `withApplied` or `applyTo` skips country |
| AC-3 | instrumented | P1 | - | Focus `filterSummary_namesGenreCountryYearStatusThenSort` | Header text «Драма · США · 2010-е · Завершённые · по году ↓»; fails on a wrong order or a missing part |
| AC-4 | unit | P0 | red | Query `applyTo_yearRange_sendsTheExactYearOrAnInclusiveRange` | `2020..2020` → `"2020"`, `2010..2019` → `"2010-2019"`, `1900..1979` → `"1900-1979"`; fails on `-1979` or `y-` |
| AC-4 | unit | P0 | red | VM `yearChosen_panelClosed_sendsThePresetAndSummarisesIt` | Last call `year="2010-2019"`, `filterState.yearRange = 2010..2019` |
| AC-4 | unit | P1 | - | Options `yearOptions_listsThisYearLastYearDecadesAndBefore1980` | `yearOptions(2026)` = 2026, 2025, 2020s ... 1980s, 1900..1979 in that order |
| AC-4 | unit | P2 | - | Options `yearOptions_inTheFirstYearOfADecade_startsTheDecadesWithIt` | `yearOptions(2030)` starts the decades at `2030..2039`, no gap at 2020s |
| AC-4 | instrumented | P1 | - | Focus `yearLabel_namesSingleYearDecadeBefore1980AndAnOffListRange` | «2020», «2010-е», «до 1980», «Любой год» for null, «2003–2007» for an off-list range |
| AC-5 | unit | P0 | red | VM `statusChosen_onSeriesGrids_sendsFinished` | For `TabType.Series`, `DocSeries`, `TvShows`: last call `finished=true`, `statusAvailable=true` |
| AC-5 | unit | P0 | red | VM `statusDoor_isUnavailableOnMovieDocumovieConcertAnd4k` | `statusAvailable=false` for `Movies`, `DocMovies`, `Concerts`, `For4k`; fails if the check uses `type != null` or includes `documovie` |
| AC-5 | unit | P1 | - | VM `statusChosen_onAMovieGrid_isIgnored` | After `StatusChosen(true)` and close on `all_movie`: no repage, `finished` null in every call |
| AC-5 | instrumented | P1 | - | Focus `statusDoor_absentWhenStatusUnavailable` | No «Статус» node with `statusAvailable=false`; Down from «Год» stays on «Год» |
| AC-6 | unit | P0 | red | VM `filterPanelClosed_withEveryFilterSet_readsOneQueryCarryingAllOfThem` | Exactly one new `FirstPageCall` with genre, sort, country, year, finished on a serial grid |
| AC-6 | unit | P0 | red | Interactor `firstPageCache_isSeparatedByCountryYearAndFinished` | Four cacheable configs differing only in one new field each give four server reads; fails if any field is dropped from `cacheKey` |
| AC-6 | unit | P1 | - | Query `applyTo_countryYearFinished_keepsIdTypeAndQuality` | On `allMovie` and `all4k` base id, type, quality survive; `applyTo` of `ContentFilter()` still equals the base (new fields default null) |
| AC-7 | unit | P0 | red | Repo `write_thenRead_returnsCountryYearAndFinished` | Round trip of country (id + title), `yearRange`, `finished` |
| AC-7 | unit | P0 | guard | Repo `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus` | A raw `{"genreId":9,"genreTitle":"Драма","sort":"year","direction":"asc"}` entry reads as genre + sort only; fails if a new field is non-defaulted |
| AC-7 | unit | P1 | - | Repo `read_yearWithAMissingOrInvertedBound_isDropped` | `yearFrom` only, or `from > to`, gives `yearRange=null`, rest kept |
| AC-7 | unit | P2 | - | Repo `read_countryWithoutTitle_isDropped` | Mirrors `read_genreWithoutTitle_isDropped` |
| AC-7 | unit | P0 | red | VM `restoredCountryYearAndStatus_headerShowsSummaryBeforeTheFirstPage` | Filter state before the first page lands carries country title, year range, `finishedOnly`; first call uses them uncached; no `countryInteractor` call |
| AC-8 | unit | P0 | red | VM `filterReset_clearsCountryYearAndStatusRemovesTheEntryAndReadsCached` | After reset: summary fields null/false, store has no `filter_all_serial`, last call equals the base config with `cacheable=true` |
| AC-9 | unit | P0 | red | VM `countryLoadFailure_showsFailedAndKeepsGenresUsable` | `countries = FilterCountries.Failed`, `genres` loaded, grid unchanged, no repage |
| AC-9 | unit | P0 | red | VM `countriesRetry_loadsAgainAfterAFailure` | Two retries while loading give one extra read; ends `Loaded` (as `genresRetry_loadsAgainAfterAFailure`) |
| AC-9 | unit | P2 | - | Country `getCountries_secondCallIsServedFromTheCache` | One API call for two reads within `CacheTtl.Countries` |
| AC-9 | instrumented | P1 | - | Focus `countriesFailed_showsTheErrorRowAndFocusableRetry` | «Не удалось загрузить страны» shown, focus on «Повторить», OK emits `CountriesRetry` |
| AC-10 | instrumented | P0 | red | Focus `rootDoors_downReachesCountryYearAndStatus` | Down from «Сортировка» focuses «Жанр», «Страна», «Год», «Статус» in order, Down on «Статус» stays |
| AC-10 | instrumented | P0 | red | Focus `newPagesBack_returnToTheDoorTheyCameFrom` | For «Страна», «Год», «Статус»: OK, focus on the first row, Back, focus on that door, panel open |
| AC-10 | instrumented | P0 | red | Focus `countryPage_opensFocusedOnTheSelectedCountryFarDown` | Draft country = the 100th of 102: page opens with that row focused and displayed; fails if it opens on «Все страны» or crashes at measure |
| AC-10 | instrumented | P0 | red | Focus `countryPage_downReachesTheLastCountry` | Down from «Все страны» 102 times focuses the last country; one more Down stays |
| AC-10 | instrumented | P1 | - | Focus `countryPage_listLandingAfterLoading_movesFocusToTheSelectedCountry` | Open while `Loading` (focus «Все страны»), then `Loaded`: focus moves to the selected row |
| AC-10 | instrumented | P1 | - | Focus `yearPage_downReachesBefore1980` | Last row «до 1980» reachable and OK emits `YearChosen(1900..1979)` |
| AC-10 | instrumented | P0 | guard | Focus `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset` (`:112`) extended with country, year, status in `activeFilter` | Restored new fields do not move first focus off the first card |
| AC-10 | device | P1 | - | Manual M1, M2 | Same paths on the box with the real list |
| AC-11 | manual | P1 | - | Manual M3 | Owner judges sizes and materials against the stand delta |

### Failing-first test

`getCountries_unwrapsTheItemsOfAStatusItemsBody` (Client). Base `git show 90e69aff:app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:642-644` decodes `countries` as `List<Country>`; a `{"status":200,"items":[...]}` body fails to decode, so the test fails on base and passes after the unwrap. The other `red` rows fail on base by not compiling (new fields, actions, functions); the implementer records that and writes the Client test first.

### Manual checks

Box `192.168.1.110:5555` only, `com.kino.puber.stage`, worktree dev APK via `tools/android-apk-install-preserve`, under a lease. Keys through `adb -s 192.168.1.110:5555 shell input keyevent`. Screens to the evidence file `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-device.md`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | «Фильмы» → «Все» grid, Up to «Фильтры», OK; Down ×2, OK on «Страна»; Down to «США», OK; Back; Back | Root shows 4 doors (no «Статус»); page lists «Все страны» + 102 countries; header reads «США»; first cards hold США in details; focus returns to «Страна» then «Фильтры», then the first card when the list lands | Crash on page open; focus lost to nothing; stale unfiltered grid |
| M2 | Reopen panel, «Страна»: focus on «США»; DPAD_DOWN until the last country (Япония), then Down once more | Focus on Япония, row whole on screen; extra Down stays | Focus jumps to «Все страны»; row half-cut while focused; panel width jumps more than once |
| M3 | «Сериалы» → «Все»: set «Драма», «США», «2010-е», «Статус» → «Завершённые», sort «по году ↓»; close | 5 doors; one-line header in order genre · country · year · status · sort (ellipsis allowed at the end); grid years 2010-2019, finished series | «Статус» absent on serials; summary wraps to two lines |
| M4 | Force-stop the app, launch, open «Сериалы» → «Все» | Summary present on the first frame; focus on the first card; then «Сброс» clears all, grid reloads unfiltered | Summary appears late; focus on «Сброс» on cold open; filter survives reset |
| M5 | Open a title, start playback, open player settings; open About; open a series' Episodes panel | Panel widths match the master build screenshots taken in the same session | Any of them wider, narrower or clipped |
| M6 | «4K» grid → «Фильтры» | «Страна», «Год» present, no «Статус» | `finished` door on 4K |

### Perf

N/A: track `perf` is off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Year: presets only, or presets plus a custom range | yes | answered | Presets only |
| Q2 | Status values and grids | yes | answered | «Любой статус» / «Завершённые» on serial, docuserial, tvshow; «Выходящие» dropped (finished=0 ignored) |
| Q3 | Country: one or several | no | answered | One; API order (alphabetical) |
| D1 | Year labels: words or numbers | no | answered | Numbers; a stored single year keeps its number |
| D2 | Summary overflow | no | answered | One line with cut-off; order genre · country · year · status · sort |
| UX1 | «Статус» as door or root toggle | no | answered | Door with a two-row page, last on the root |
| UX2 | Country page width | no | answered | Fixed 320 dp (starting value), lazy list |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | Q1: year presets only, sent as y or y1-y2 (this year, last year, decades, «до 1980», «Любой»); free range as a follow-up idea | owner (standing instruction: recommended) |
| 2026-11-25 | Q2: status «Все» / «Завершённые» / «Выходящие» on serial, docuserial, tvshow; drop «Выходящие» if finished=0 is ignored, drop a type where finished is ignored; never on 4K | owner (standing instruction: recommended) |
| 2026-11-25 | Q3: country single-select like genre; API order, alphabetical if unordered | owner (standing instruction: recommended) |
| 2026-11-25 | D1/D2/UX1/UX2 as in Open questions; country page is a LazyColumn with fixed width via an optional TvSidePanelSurface parameter | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved | owner (standing instruction: recommended) |
