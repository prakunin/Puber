# Country, year and series-status filters in the catalogue filter panel - plan

**Spec:** `docs/superpowers/specs/2026-11-25-catalogue-filters-country-year-status.md`
**Goal:** Add «Страна», «Год» and «Статус» to the browse-grid filter panel, send them as `country`, `year`, `finished` on `items`, store them per grid, and fix the countries decode.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Worktree `/home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status`, branch `prakunin/catalogue-filters-country-year-status`, base `90e69aff`. Every Gradle call runs there as `AGENT_GRADLE_SERIAL=1 ./tools/agentw <task>`; never `./gradlew`.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched unit tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Path prefixes: `M/` = `app/src/main/java/com/kino/puber/`, `UT/` = `app/src/test/kotlin/com/kino/puber/`, `IT/` = `app/src/androidTest/kotlin/com/kino/puber/`. Line numbers are at base `90e69aff`.
- Unit tests are JUnit 5 (`@RegisterExtension MainDispatcherExtension` where a VM runs). Magic numbers in main code go into named constants (detekt `MagicNumber`). Every new string goes into both `app/src/main/res/values/main_screen_strings.xml` and `app/src/main/res/values-en/strings.xml` (lint `MissingTranslation`).
- Out of scope (spec): free year range, «Выходящие» / `finished=0`, a «Популярные» country section, ratings and quality filters. `finished` is only ever `true` or `null`.
- AC-1 is settled by `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md`; no task. Manual checks M1-M6 (spec «Manual checks») are run by qa-device in verify.
- Instrumented tests (`IT/`) are compiled here with `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; they run on the box in verify (qa-device: `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest` under a lease), never through `connected*AndroidTest`.

---

## Tasks

### Task 1: Send country, year and finished on items and unwrap the countries body

**Covers:** AC-2, AC-3 (wire)
**Files:**
- Modify: `M/data/api/KinoPubApiClient.kt` - `getItems` (`:253-269`) gains `country: Int? = null, year: String? = null, finished: Boolean? = null` after `conditions`; each sent only when non-null as `country`, `year`, `finished` (`finished` as `1` / `0`, never `true`). `getCountries` (`:642-644`) becomes `apiCall<ApiResponseList<Country>> { ... }.map { it.items.orEmpty() }`, as `getGenres` (`:632-637`; `ApiResponseList` at `M/data/api/models/Models.kt:11`, `Country` at `:203`).
- Test (new): `UT/data/api/KinoPubApiClientCountriesTest.kt` - copy the rig of `UT/data/api/KinoPubApiClientGenresTest.kt` (`setUpAndroidLogging` `:35-50`, `client` `:98-125`, `withServer` `:127-143`, `respond` `:145-150`), path `/v1/countries`.
- Test (new): `UT/data/api/KinoPubApiClientItemsFilterTest.kt` - same rig, path `/v1/items`, body `{"items":[],"pagination":{"total":1,"current":1,"perpage":50}}`; the handler stores `exchange.requestURI.query` and the test splits it on `&` into a set.

**Consumes:** nothing new.
**Produces:** `getItems(type, sort, page, quality, genre, conditions, country: Int? = null, year: String? = null, finished: Boolean? = null)`; `getCountries(): Result<List<Country>>` reading `{status, items}`.

| Test | Asserts |
|---|---|
| `getCountries_unwrapsTheItemsOfAStatusItemsBody` (P0, failing-first) | Body `{"status":200,"items":[{"id":1,"title":"США"}]}` gives `listOf(Country(1, "США"))` |
| `getCountries_refusedRead_failsInsteadOfAnEmptyList` (P1) | 422 gives `ApiError.Http` with `statusCode == 422` |
| `getItems_sendsCountryYearAndFinishedAsTheServerReadsThem` (P0) | `getItems(type = "serial", page = 1, country = 1, year = "2010-2019", finished = true)`: the set holds `country=1`, `year=2010-2019`, `finished=1` |
| `getItems_withoutFilterParams_sendsNoneOfThem` (P1) | `getItems(type = "movie", page = 1)`: no key `country`, `year` or `finished` in the query |

- [x] Write `getCountries_unwrapsTheItemsOfAStatusItemsBody`, run it on the unchanged client, record the failure (decode of a bare list) in the Implementation record
- [x] Change `getCountries` and `getItems`; write the other three tests
- [x] Run the fast gate and both test classes; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.api.KinoPubApiClientCountriesTest' --tests 'com.kino.puber.data.api.KinoPubApiClientItemsFilterTest' --tests 'com.kino.puber.data.api.KinoPubApiClientGenresTest'` is green.

---

### Task 2: Carry the three filters through the section query and its cache key

**Covers:** AC-3 (interactor), AC-4 (wire form), AC-6
**Files:**
- Modify: `M/ui/feature/contentlist/model/SectionConfig.kt:9-21` - append `val country: Int? = null`, `val year: String? = null`, `val finished: Boolean? = null` after `animeFilterMode`. Defaults must be `null` so a base config and an unfiltered query stay equal (parcelable).
- Modify: `M/ui/feature/showall/model/ContentFilterQuery.kt:6-14` - `applyTo` also sets `country = country?.id`, `year = yearRange?.toYearParam()`, `finished = finished`. Add `internal fun IntRange.toYearParam(): String` = `"$first"` when `first == last`, else `"$first-$last"` (evidence: `-1979` is 502, `1980-` reads as one year).
- Modify: `M/domain/interactor/contentlist/ContentListInteractor.kt` - `fetchPage` (`:197-205`): the `getItems` call at `:202` passes `country = config.country, year = config.year, finished = config.finished` by name; the shortcut branch is unchanged. `cacheKey` (`:119-135`) appends `config.country`, `config.year`, `config.finished` before `showAnime`.
- Test: `UT/ui/feature/showall/model/ContentFilterQueryTest.kt` (bases at `:16-17`).
- Test: `UT/domain/interactor/contentlist/ContentListInteractorTest.kt` (fixture `:50-146`, `config(filterMode)` `:91-97` gives type `movie`, sort `updated`; pattern `firstPageCache_isSeparatedByFilterModeAndAnimePreference` `:586-604`). Existing five-argument `api.getItems(...)` stubs (e.g. `:155`) keep matching because the new arguments default to `null`.

**Consumes:** Task 1 `getItems` signature.
**Produces:** `SectionConfig.country/year/finished`; `IntRange.toYearParam()`; `ContentFilter.applyTo` mapping all three.

| Test | Asserts |
|---|---|
| Query `applyTo_yearRange_sendsTheExactYearOrAnInclusiveRange` (P0) | `2020..2020` → `"2020"`, `2010..2019` → `"2010-2019"`, `1900..1979` → `"1900-1979"` (through `ContentFilter(yearRange = ...).applyTo(allMovie).year`) |
| Query `applyTo_countryYearFinished_keepsIdTypeAndQuality` (P1) | On `allMovie` and `all4k`: `country = 1`, `year`, `finished = true` set; id, type, quality, `animeFilterMode` kept; `ContentFilter().applyTo(base) == base` |
| Interactor `filteredBrowseConfig_sendsCountryYearAndFinishedWithTheBaseType` (P0) | `config(AnimeFilterMode.None).copy(country = 1, year = "2010-2019", finished = true)` through `observedPages`: `coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null, null, 1, "2010-2019", true) }` |
| Interactor `firstPageCache_isSeparatedByCountryYearAndFinished` (P0) | Base `config(AnimeFilterMode.None)` and three copies each setting one new field, all `cacheable = true`, read in turn: `api.getItems` (stubbed with `any()` for all nine arguments) called four times; fails if a field is missing from `cacheKey` |

- [x] Write the four tests; change `SectionConfig`, `applyTo`, `fetchPage`, `cacheKey`
- [x] Run the fast gate, `ContentFilterQueryTest` and `ContentListInteractorTest` (all classes in that file); commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.showall.model.ContentFilterQueryTest' --tests 'com.kino.puber.domain.interactor.contentlist.*'` is green.

---

### Task 3: Cached countries interactor

**Covers:** AC-9 (cache)
**Files:**
- Modify: `M/data/cache/CacheKeys.kt` - `const val Countries = "countries"` beside `BookmarkFolders` (`:23`); `val Countries: Duration = 30.minutes` in `CacheTtl` beside `Genres` (`:64`).
- Create: `M/domain/interactor/country/CountryInteractor.kt` - mirror `M/domain/interactor/genre/GenreInteractor.kt:11-27`: `class CountryInteractor(api: KinoPubApiClient, contentCache: ContentCacheRepository)`, `suspend fun getCountries(): Result<List<Country>>` = `runCatchingCancellable { contentCache.getPayload(CacheKeys.Countries, ListSerializer(Country.serializer()), CacheTtl.Countries) { api.getCountries().getOrThrow() } }` (`getPayload` at `M/data/cache/ContentCacheRepository.kt:257`).
- Modify: `M/domain/di/modules.kt:42` - `singleOf(::CountryInteractor)` after `singleOf(::GenreInteractor)`.
- Test (new): `UT/domain/interactor/country/CountryInteractorTest.kt` - built as `UT/domain/interactor/bookmarks/BookmarkFoldersInteractorTest.kt:18-19` (`ContentCacheRepository(FakePayloadStore())`).

**Consumes:** Task 1 `getCountries`.
**Produces:** `CountryInteractor.getCountries(): Result<List<Country>>`, a Koin single.

| Test | Asserts |
|---|---|
| `getCountries_secondCallIsServedFromTheCache` (P2) | Two calls return the list; `coVerify(exactly = 1) { api.getCountries() }` |

- [x] Add the key, TTL, interactor, binding and test
- [x] Run the fast gate and `CountryInteractorTest`; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.domain.interactor.country.CountryInteractorTest'` is green.

---

### Task 4: Store country, year and status per grid

**Covers:** AC-7 (store)
**Files:**
- Modify: `M/data/preferences/CatalogueFilterRepository.kt` - `StoredFilter` (`:56-62`) gains `countryId: Int? = null`, `countryTitle: String? = null`, `yearFrom: Int? = null`, `yearTo: Int? = null`, `finished: Boolean? = null` (all defaulted; `ignoreUnknownKeys` at `:21` stays). `write` (`:36-48`) fills them from `filter.country`, `filter.yearRange?.first/last`, `filter.finished`. `read` (`:23-34`): country only when both id and title are present (as genre at `:27-30`); `yearRange = yearFrom..yearTo` only when both are present and `yearFrom <= yearTo`; `finished = true` only when stored `true`, else `null`. KDoc line `:15` names the stored fields.
- Test: `UT/data/preferences/CatalogueFilterRepositoryTest.kt` (rig `:19-26`, pattern `read_genreWithoutTitle_isDropped` `:98-103`).

**Consumes:** `ContentFilter.country/yearRange/finished` (`M/domain/model/ContentFilter.kt:8`, `:11`, `:15`).
**Produces:** a store that round-trips the three fields; old entries read unchanged.

| Test | Asserts |
|---|---|
| `write_thenRead_returnsCountryYearAndFinished` (P0) | `ContentFilter(country = Country(1, "США"), yearRange = 2010..2019, finished = true)` round-trips equal |
| `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus` (P0 guard) | Raw `{"genreId":9,"genreTitle":"Драма","sort":"YEAR","direction":"ASC"}` reads as `ContentFilter(Genre(9, "Драма"), sort = SortField.YEAR, sortDirection = SortDirection.ASC)` with country, year, finished `null` |
| `read_yearWithAMissingOrInvertedBound_isDropped` (P1) | `{"sort":"YEAR","yearFrom":2010}` and `{"sort":"YEAR","yearFrom":2019,"yearTo":2010}` give `yearRange == null`, sort kept |
| `read_countryWithoutTitle_isDropped` (P2) | `{"countryId":1}` gives `country == null` |

The guard fixture uses enum names (`YEAR`, `ASC`) because that is what the previous `write` stored (`:44-45`, `.name`); the test-design row's lowercase `year`/`asc` would read as the default sort and not prove the old entry survives.

- [x] Write the four tests; change the repository
- [x] Run the fast gate and `CatalogueFilterRepositoryTest`; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.preferences.CatalogueFilterRepositoryTest'` is green.

---

### Task 5: Filter state, actions and view model for country, year and status

**Covers:** AC-3, AC-4, AC-5, AC-6, AC-7, AC-8, AC-9 (VM)
**Files:**
- Modify: `M/ui/feature/showall/model/ShowAllFilterState.kt`:
  - Beside `FILTER_SORT_OPTIONS` (`:10-16`): constants `YEAR_FLOOR = 1900`, `OLDEST_DECADE = 1980`, `DECADE = 10`, `internal val YEARS_BEFORE_OLDEST_DECADE = YEAR_FLOOR until OLDEST_DECADE` (`1900..1979`), and `internal fun yearOptions(currentYear: Int): List<IntRange>` = `currentYear..currentYear`, `(currentYear - 1)..(currentYear - 1)`, each `d..d + 9` for `d` from `currentYear / 10 * 10` down to `1980` step 10, then `1900..1979`.
  - `ShowAllFilterState` (`:24-34`) gains `countryTitle: String? = null`, `yearRange: IntRange? = null`, `finishedOnly: Boolean = false`, `statusAvailable: Boolean = false`, `yearOptions: List<IntRange> = emptyList()`, `countries: FilterCountries = FilterCountries.Loading`; KDoc `:18-22` lists the new summary fields.
  - `FilterCountries` sealed interface shaped as `FilterGenres` (`:40-47`): `Loading`, `Loaded(val countries: List<Country>)`, `Failed`.
- Modify: `M/ui/feature/showall/model/ShowAllAction.kt:8-22` - `CountryChosen(val country: Country?)`, `data object CountriesRetry`, `YearChosen(val range: IntRange?)`, `StatusChosen(val finishedOnly: Boolean)`.
- Modify: `M/ui/feature/showall/vm/ShowAllVM.kt`:
  - Both constructors (`:41-62`, `:68-91`) take `countryInteractor: CountryInteractor` after `genreInteractor`; the private one keeps it as `private val`.
  - Initial state (`:95-97`): on a filterable grid also `yearOptions = yearOptions(java.time.Year.now().value)` and `statusAvailable = ItemType.entries.any { it.value == config.type && it.isSeriesLike() }` (`isSeriesLike` at `M/data/api/models/Models.kt:145`; `all_4k` has no type, `M/ui/feature/contentlist/model/TabTypeConfig.kt:213-218`). Keep `statusAvailable` in a private val for the action guard.
  - `handleFilterAction` (`:160-171`): `CountryChosen` → `updateDraft { copy(country = action.country) }`; `CountriesRetry` → `loadCountries()`; `YearChosen` → `copy(yearRange = action.range)`; `StatusChosen` → when `statusAvailable`, `copy(finished = if (action.finishedOnly) true else null)`, else nothing.
  - `openFilterPanel` (`:177-185`): after the genres branch, the same for countries: `rawCountries` set → `FilterCountries.Loaded`, else `loadCountries()`. New `rawCountries: List<Country>?`, `countriesJob: Job?` beside `:103-104`; `loadCountries()` copies `loadGenres` (`:219-232`) without the anime filter.
  - `withApplied` (`:206-211`) also sets `countryTitle = filter.country?.title`, `yearRange = filter.yearRange`, `finishedOnly = filter.finished == true`.
  - `closeFilterPanel` (`:187-196`) and `resetFilter` (`:198-204`) unchanged: they already write or clear the store, choose `cacheable` from `isActive` and reset to `ContentFilter()`.
- Modify: `M/ui/feature/showall/ShowAllScreen.kt:45-55` - `countryInteractor = get()` after `genreInteractor = get()`.
- Test (new): `UT/ui/feature/showall/model/YearOptionsTest.kt`.
- Test: `UT/ui/feature/showall/vm/ShowAllVMTest.kt`:
  - `countryInteractor: CountryInteractor` lateinit beside `genreInteractor` (`:80`); `setup` (`:89-90`) stubs `getCountries()` to `Result.success(emptyList())`; `createVM` (`:940-958`) passes it.
  - `FilterRig.applyFilter` (`:370-378`) adds `CountryChosen(filter.country)`, `YearChosen(filter.yearRange)`, `StatusChosen(filter.finished == true)` before `FilterPanelClosed`.
  - `filterReset_restoresTheBaseQueryAndRepagesCached` expectation (`:672`) becomes `ShowAllFilterState(available = true, genres = restored.genres, countries = restored.countries, yearOptions = restored.yearOptions)`.
  - Configs beside `:343-345`: `allDocSerial`, `allTvShow`, `allDocMovie`, `allConcert` from `TabTypeConfig.browseEntryFor(TabType.DocSeries / TvShows / DocMovies / Concerts)`; a `usa = Country(1, "США")` beside `drama` (`:351`).

**Consumes:** Task 2 `applyTo`, Task 3 `CountryInteractor`, Task 4 store.
**Produces:** `ShowAllFilterState` fields, `FilterCountries`, `yearOptions(Int)`, `YEARS_BEFORE_OLDEST_DECADE`, the four actions; Task 6 draws them.

| Test | Asserts |
|---|---|
| Options `yearOptions_listsThisYearLastYearDecadesAndBefore1980` (P1) | `yearOptions(2026)` = `2026..2026, 2025..2025, 2020..2029, 2010..2019, 2000..2009, 1990..1999, 1980..1989, 1900..1979` |
| Options `yearOptions_inTheFirstYearOfADecade_startsTheDecadesWithIt` (P2) | `yearOptions(2030)` decades start `2030..2039`, `2020..2029` |
| VM `countryChosen_panelClosed_readsTheCountryQueryUncachedAndSummarisesIt` (P0) | Last `FirstPageCall` on `allMovie` has `config.country == 1`, `type == "movie"`, `cacheable == false`; `filterState.countryTitle == "США"` |
| VM `yearChosen_panelClosed_sendsThePresetAndSummarisesIt` (P0) | Last call `year == "2010-2019"`; `filterState.yearRange == 2010..2019` |
| VM `statusChosen_onSeriesGrids_sendsFinished` (P0) | For `allSerial`, `allDocSerial`, `allTvShow`: `statusAvailable` true, last call `finished == true` |
| VM `statusDoor_isUnavailableOnMovieDocumovieConcertAnd4k` (P0) | `statusAvailable` false for `allMovie`, `allDocMovie`, `allConcert`, `all4k` |
| VM `statusChosen_onAMovieGrid_isIgnored` (P1) | `allMovie`: open, `StatusChosen(true)`, close: one call only, `finished` null in every call |
| VM `filterPanelClosed_withEveryFilterSet_readsOneQueryCarryingAllOfThem` (P0) | `allSerial` with genre, sort, country, year, status: exactly one call after the first, carrying all five |
| VM `restoredCountryYearAndStatus_headerShowsSummaryBeforeTheFirstPage` (P0) | Store holds country, year, finished for `allSerial`; `createVM` without start: `countryTitle`, `yearRange`, `finishedOnly` set; through `filterRig` the first call carries them with `cacheable = false`; `coVerify(exactly = 0) { countryInteractor.getCountries() }` |
| VM `filterReset_clearsCountryYearAndStatusRemovesTheEntryAndReadsCached` (P0) | `allSerial`, all three applied, `FilterReset`: `countryTitle`/`yearRange` null, `finishedOnly` false, no `filter_all_serial` key, last call `FirstPageCall(allSerial, cacheable = true)` |
| VM `countryLoadFailure_showsFailedAndKeepsGenresUsable` (P0) | `getCountries()` fails: `countries == FilterCountries.Failed`, `genres` is `Loaded`, `shownIds == [1, 2]`, one call |
| VM `countriesRetry_loadsAgainAfterAFailure` (P0) | As `genresRetry_loadsAgainAfterAFailure` (`:869`): two `CountriesRetry` while loading give one extra read, ends `Loaded(listOf(usa))` |

- [x] Write the tests (they fail by not compiling on the unchanged code; record that)
- [x] Add state, actions, VM changes and the screen binding
- [x] Run the fast gate, `YearOptionsTest` and `ShowAllVMTest`; commit

**Done when:** `./tools/agentw :app:compileDevDebugKotlin` and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.showall.*'` are green.

---

### Task 6: Draw the three doors and pages

**Covers:** AC-3 (summary), AC-4 (labels), AC-5 (door), AC-9 (page), AC-10, AC-11
**Files:**
- Modify: `M/core/ui/uikit/component/panel/TvSidePanel.kt` - `TvSidePanelSurface` (`:54-62`) gains `fixedWidth: Dp? = null`; non-null replaces `.widthIn(min, max).width(IntrinsicSize.Max)` (`:65-66`) with `.width(fixedWidth)`; `null` keeps them byte for byte. KDoc `:45-52` names the lazy-page case. `PlayerSidePanel.kt:96` stays untouched.
- Modify: `M/ui/feature/showall/content/ShowAllFilterPanel.kt`:
  - `FilterPage` (`:45`) = `Root, Sort, Genre, Country, Year, Status`; titles at `:78-82`; KDoc `:47-51` names five doors.
  - `private val CountryPageWidth = 320.dp` beside `PanelMaxHeight` (`:40`); `TvSidePanelSurface(fixedWidth = CountryPageWidth.takeIf { page == FilterPage.Country })` at `:77-83`.
  - First-focus key (`:64`): `Genre` → `filter.genres::class`, `Country` → `filter.countries::class`, else `null`; the retry loop `:65-71` unchanged.
  - Body (`:88-101`): `Country` renders `CountryPage(modifier = Modifier.weight(1f, fill = false), ...)` directly in the surface's `ColumnScope`; every other page stays inside the existing `Column(Modifier.verticalScroll(...))`. A `LazyColumn` inside `verticalScroll` crashes at measure.
  - `RootPage` (`:107-126`): after «Жанр», doors «Страна» (trailing `draft.country?.title` or «Все страны»), «Год» (trailing `yearLabel(draft.yearRange)`), and, only when `filter.statusAvailable`, «Статус» (trailing «Завершённые» when `draft.finished == true`, else «Любой статус»); each takes `firstFocus` when it is `door`.
  - `CountryPage`: `LazyColumn(state = listState, modifier)` with item `key = "all"` «Все страны», then by `filter.countries`: Loading → spinner row, Failed → error text «Не удалось загрузить страны» + «Повторить» (`CountriesRetry`, takes `firstFocus`), Loaded → `items(countries, key = { it.id })` as `GenrePage` rows (`:154-203`). `selectedIndex` = index of `draft.country` in the loaded list; `listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex + 1)` when found, else 0; `LaunchedEffect(filter.countries::class)` calls `listState.scrollToItem(selectedIndex + 1)` when the list lands with a selected row, so the row is composed before the panel's 100 ms focus request (`:41-43`). First focus: the selected row; «Все страны» when nothing is selected or the list is not loaded; «Повторить» on failure.
  - `YearPage`: «Любой год» (selected when `draft.yearRange == null`; first focus when null or not in `filter.yearOptions`), then one row per `filter.yearOptions` with `yearLabel(range)` → `YearChosen(range)`. `StatusPage`: «Любой статус» → `StatusChosen(false)`, «Завершённые» → `StatusChosen(true)`; focus on the selected one.
- Modify: `M/ui/feature/showall/content/ShowAllFilterLabels.kt` - `@Composable internal fun yearLabel(range: IntRange?): String`: `null` → «Любой год»; single year → the number; `YEARS_BEFORE_OLDEST_DECADE` → «до 1980»; `first % 10 == 0 && last == first + 9` → «%d-е»; else «%1$d–%2$d». `filterSummary` (`:40-51`) parts: genre, country, year label, «Завершённые» when `finishedOnly`, then sort as today; KDoc `:36-39` updated. Used by `ShowAllHeader.kt:105` and `ShowAllScreenContent.kt:206` unchanged.
- Modify: `app/src/main/res/values/main_screen_strings.xml:44-64` and `app/src/main/res/values-en/strings.xml:188-209`:

| Name | ru | en |
|---|---|---|
| `show_all_filters_country` | Страна | Country |
| `show_all_filters_country_all` | Все страны | All countries |
| `show_all_filters_countries_error` | Не удалось загрузить страны | Could not load countries |
| `show_all_filters_year` | Год | Year |
| `show_all_filters_year_any` | Любой год | Any year |
| `show_all_filters_year_decade` | %d-е | %ds |
| `show_all_filters_year_before_1980` | до 1980 | before 1980 |
| `show_all_filters_year_range` | %1$d–%2$d | %1$d–%2$d |
| `show_all_filters_status` | Статус | Status |
| `show_all_filters_status_any` | Любой статус | Any status |
| `show_all_filters_status_finished` | Завершённые | Finished |

- Test: `IT/ui/feature/showall/content/ShowAllFilterFocusTest.kt` - the `onAction` fake (`:86-95`) also applies `CountryChosen`, `YearChosen`, `StatusChosen` to `filter.draft`; fixture `countries = (1..102).map { Country(it, "Страна %03d".format(it)) }`; helpers `openPanel` (`:368-375`), `press`, `pressBack`, `awaitFocusOn` reused. `activeFilter` (`:59`) stays as is (`filteredEmpty_showsResetAndDownFromFiltersReachesIt` at `:236` reads its summary).

| Test | Asserts |
|---|---|
| `filterSummary_namesGenreCountryYearStatusThenSort` (P1) | `filter = activeFilter.copy(countryTitle = "США", yearRange = 2010..2019, finishedOnly = true, sort = SortField.YEAR)`: header shows «Драма · США · 2010-е · Завершённые · по году ↓» |
| `yearLabel_namesSingleYearDecadeBefore1980AndAnOffListRange` (P1) | «2020», «2010-е», «до 1980», «Любой год» for null, «2003–2007» for `2003..2007` |
| `statusDoor_absentWhenStatusUnavailable` (P1) | `statusAvailable = false`: no «Статус» node; Down on «Год» stays on «Год» |
| `countriesFailed_showsTheErrorRowAndFocusableRetry` (P1) | `countries = FilterCountries.Failed`: error text shown, focus on «Повторить», OK emits `CountriesRetry` |
| `rootDoors_downReachesCountryYearAndStatus` (P0) | `statusAvailable = true`: Down from «Сортировка» focuses «Жанр», «Страна», «Год», «Статус»; Down on «Статус» stays |
| `newPagesBack_returnToTheDoorTheyCameFrom` (P0) | For «Страна», «Год», «Статус»: OK, focus on the first row, Back, focus on that door, `filter.panelOpen` |
| `countryPage_opensFocusedOnTheSelectedCountryFarDown` (P0) | Draft country = the 100th of 102, `Loaded`: page opens with that row focused and displayed |
| `countryPage_downReachesTheLastCountry` (P0) | From «Все страны», Down 102 times focuses «Страна 102»; one more Down stays |
| `countryPage_listLandingAfterLoading_movesFocusToTheSelectedCountry` (P1) | Open with `Loading` (focus «Все страны»), then `Loaded`: focus on the selected row |
| `yearPage_downReachesBefore1980` (P1) | `yearOptions = yearOptions(2026)`: last row «до 1980» reachable; OK emits `YearChosen(1900..1979)` |
| `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset` (P0 guard, `:112`) | Its `filter = activeFilter` becomes `activeFilter.copy(countryTitle = "США", yearRange = 2010..2019, finishedOnly = true, statusAvailable = true)`; assertions unchanged |

- [x] Change `TvSidePanelSurface`, the panel, the labels and the strings
- [x] Extend the focus test
- [x] Run the fast gate and `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit

**Done when:** both compile tasks are green and `git diff 90e69aff -- app/src/main/java/com/kino/puber/ui/feature/player` is empty.

---

### Task 7: Architecture doc and design stand

**Covers:** AC-11 (stand delta)
**Files:**
- Modify: `docs/architecture/paging-and-filters.md` - `SectionConfig` paragraph (`:121-126`) names `country`, `year`, `finished` and that each joins `ContentListInteractor.cacheKey`; the store sentence (`:128`) names the stored fields (genre, sort, country, year bounds, finished) and that the year is sent as `y` or `y1-y2`.
- Modify: `docs/design/showall/index.src.html` - the «catalogue-filters» scene: `fPanel` switch (`:439`) gains «Страна», «Год», «Статус»; `pageTitle` (`:933`) and `pageItems` (`:934-951`) get the three doors and pages per spec «UX delta» «New»; a slider «ширина: Страна» 280-420, start 320; a switch «статус» (door / toggle row «Только завершённые», start door); `summaryOf` (`:842-846`) orders genre · country · year · status · sort. Country titles on the stand are placeholders in alphabetical order.
- Modify: `docs/design/showall/README.md` - new section «catalogue-filters-country-year-status starting values (2026-11-25)» before «Open items» (`:189`): the «New» table, focus rows and the three owner items from spec «UX delta», marked not dialled.
- Rebuild: `docs/design/showall/index.html` with `python3 docs/design/build.py showall`.

**Consumes:** Tasks 1-6 as built.
**Produces:** the stand the owner judges at AC-11.

- [x] Edit the doc, the stand source and its README; rebuild
- [x] Run the fast gate; commit

**Done when:** `python3 docs/design/build.py showall` exits 0, `git status --short docs/design/showall` lists `index.html`, and opening `docs/design/showall/index.html` with «панель» = «Страна» draws the 320 dp page.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** none; the brief says the conductor commits. Everything is uncommitted in the worktree, grouped by task below.

| Task | Files |
|---|---|
| 1 | `KinoPubApiClient.kt`, `KinoPubApiClientCountriesTest.kt`, `KinoPubApiClientItemsFilterTest.kt` |
| 2 | `SectionConfig.kt`, `ContentFilterQuery.kt`, `ContentListInteractor.kt`, `ContentFilterQueryTest.kt`, `ContentListInteractorBrowseFilterTest.kt` |
| 3 | `CacheKeys.kt`, `CountryInteractor.kt`, `modules.kt`, `CountryInteractorTest.kt` |
| 4 | `CatalogueFilterRepository.kt`, `CatalogueFilterRepositoryTest.kt` |
| 5 | `ShowAllFilterState.kt`, `ShowAllAction.kt`, `ShowAllVM.kt`, `ShowAllScreen.kt`, `ShowAllVMTest.kt`, `YearOptionsTest.kt` |
| 6 | `TvSidePanel.kt`, `ShowAllFilterPanel.kt`, `ShowAllFilterLabels.kt`, both strings files, `ShowAllFilterFocusTest.kt` |
| 7 | `paging-and-filters.md`, `docs/design/showall/{index.src.html,index.html,README.md}` |

**Red run** (P0 `red` tests on the unchanged code):

- red: `getCountries_unwrapsTheItemsOfAStatusItemsBody` - AssertionFailedError at `KinoPubApiClientCountriesTest.kt:54` (a `{status, items}` body does not decode as a bare list, result is a failure)
- red: `getItems_sendsCountryYearAndFinishedAsTheServerReadsThem` - written together with the `getItems` change, not run on the unchanged client (it does not compile without the new parameters)
- red (SectionConfig fields added as the stub): `applyTo_yearRange_sendsTheExactYearOrAnInclusiveRange` and `applyTo_countryYearFinished_keepsIdTypeAndQuality` - AssertionFailedError (`year` / `country` stay `null`); `filteredBrowseConfig_sendsCountryYearAndFinishedWithTheBaseType` - MockKException (no call with the new arguments); `firstPageCache_isSeparatedByCountryYearAndFinished` - AssertionError (one read, not four)
- red: `write_thenRead_returnsCountryYearAndFinished` - AssertionFailedError at `CatalogueFilterRepositoryTest.kt:98` (the three fields are lost)
- green on base: `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus` - guard row, the old entry already reads as the genre and sort alone
- Task 5 VM and options rows: do not compile on the unchanged code (missing actions, state fields, constructor argument), as the plan allows; no stub run.
- Task 6 focus rows: instrumented, compiled only here; they run on the box in verify.

**Deviations from the plan:**

- The two new interactor tests live in `ContentListInteractorBrowseFilterTest` (same fixture), not in `ContentListInteractorTest`: adding them tripped detekt `LargeClass` on that class.
- The one-time `check.full` ran with `:app:compileDevDebugAndroidTestKotlin` appended; all green.
- Nothing is committed (brief: the conductor commits), so "one commit per task" is not met by this run.

**Files touched outside the plan:** `ContentListInteractorBrowseFilterTest.kt` (new, see Deviations).

**For the reviewers:**

- `ShowAllFilterPanel.kt`: `CountryPage` is a `LazyColumn` outside the panel's `verticalScroll` column; its first focus relies on `rememberLazyListState(initialFirstVisibleItemIndex)` plus `scrollToItem` on list landing. Only the box run (M1, M2) proves it.
- `awaitFocusOnRow` in the focus test excludes root doors by their exact label, because a door's merged text carries the selected value.
- The stand's `Статус` toggle row and country titles are placeholders per the plan.

**Blocked:**

## Readiness

<!-- owner: final-judge, mode readiness. Checked against base 90e69aff via `git show 90e69aff:<path>`. -->

**Decision:** READY
**Base:** 90e69aff   **Spec commit:** 2de3a556   **Plan:** uncommitted in the worktree

### AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | none (done) | evidence `2026-11-25-...-api-check.md` (read: countries wrapped, 102; `finished=0` ignored) |
| AC-2 | 1 | `getCountries_unwrapsTheItemsOfAStatusItemsBody`, `getCountries_refusedRead_failsInsteadOfAnEmptyList` |
| AC-3 | 1, 2, 5, 6 | Items, Interactor, VM `countryChosen_...`, Focus `filterSummary_...`, M1 |
| AC-4 | 2, 5, 6 | `applyTo_yearRange_...`, `yearChosen_...`, `yearOptions_...`, `yearLabel_...` |
| AC-5 | 5, 6 | `statusChosen_onSeriesGrids_sendsFinished`, `statusDoor_isUnavailableOn...`, `statusChosen_onAMovieGrid_isIgnored`, M1, M6 |
| AC-6 | 2, 5 | `filterPanelClosed_withEveryFilterSet_...`, `firstPageCache_isSeparatedByCountryYearAndFinished` |
| AC-7 | 4, 5 | four Repo rows, `restoredCountryYearAndStatus_...`, M4 |
| AC-8 | 5 | `filterReset_clearsCountryYearAndStatus...`, M4 |
| AC-9 | 3, 5, 6 | `countryLoadFailure_...`, `countriesRetry_...`, `countriesFailed_...` |
| AC-10 | 6 | seven Focus rows, M1, M2 |
| AC-11 | 6, 7 | M3 (see Notes 1) |

### Citations sampled

| Citation | Resolves to |
|---|---|
| `KinoPubApiClient.kt:253-269` | `getItems(type, sort, page, quality, genre, conditions)` |
| `KinoPubApiClient.kt:642-644` / `:632-637` | `getCountries` bare decode / `getGenres` unwrap; client Json `isLenient` (`:132`) so numeric `status` decodes |
| `ContentListInteractor.kt:119-135`, `:197-205` | `cacheKey` field list; `fetchPage` positional `getItems` at `:202` |
| `SectionConfig.kt:9-21` | parcelable data class ending at `animeFilterMode` |
| `ContentFilter.kt:8`, `:11`, `:15` | `country`, `yearRange`, `finished` |
| `Models.kt:11`, `:145`, `:203` | `ApiResponseList`, `isSeriesLike`, `Country` |
| `CatalogueFilterRepository.kt:21`, `:44-45`, `:56-62` | `ignoreUnknownKeys`; enum `.name` stored (supports Plan Q1 upper-case fixture); `StoredFilter` |
| `CacheKeys.kt:23`, `:64`; `modules.kt:42` | `BookmarkFolders`, `CacheTtl.Genres`; `singleOf(::GenreInteractor)` |
| `ShowAllVM.kt:41-91`, `:160-171`, `:177-232`, `:370` | constructors, filter actions, open/close/reset/`withApplied`/`loadGenres`, `restoreFilter` returns the whole stored filter |
| `ShowAllFilterPanel.kt:40-45`, `:64`, `:88`, `:107-126`, `:154-203` | `PanelMaxHeight`, `FilterPage`, focus key, outer `verticalScroll`, `RootPage`, `GenrePage` |
| `TvSidePanel.kt:54-66`, `:94` | `widthIn` + `IntrinsicSize.Max`; `content()` in the outer `Column`, so `Modifier.weight` on `CountryPage` is valid |
| `TabTypeConfig.kt:213-218` | `all_4k` with `quality = "4k"`, no type |
| `ShowAllVMTest.kt:80`, `:370-378`, `:672`, `:869`, `:940-958` | `genreInteractor`, `applyFilter`, the only whole-state equality, `genresRetry_...`, `createVM` |
| `ShowAllFilterFocusTest.kt:59`, `:86-95`, `:112`, `:236`, `:368-375` | `activeFilter`, `onAction` fake, cold-open guard, filtered-empty test, `openPanel` |
| `ContentListInteractorTest.kt:91-97`, `:155`, `:586-604` | `config(filterMode)`, five-arg stub, cache-separation pattern |
| `main_screen_strings.xml:44-63`, `values-en/strings.xml:189-208` | existing `show_all_filters_*` strings |

All resolve.

### Build order

| Task | Stays green because |
|---|---|
| 1 | new `getItems` params appended with `null` defaults; existing mockk stubs still match |
| 2 | `SectionConfig` fields default `null`; `fetchPage` passes them by name |
| 3 | new class + binding only |
| 4 | `StoredFilter` fields defaulted |
| 5 | constructor change lands with its only two callers (`ShowAllScreen.kt:45`, `ShowAllVMTest.kt:947`, `git grep "ShowAllVM("`); new state fields defaulted, so the focus test compiles |
| 6 | `FilterPage` `when`s (title, body) edited in the same task; androidTest compile in Done-when |
| 7 | docs only |

### P0 rows

All P0 rows are `red` or `guard`, name the automated test in backticks, and state the assertion that fails on the defect. AC-1 is the one P0 manual row; its evidence exists.

### Scope and questions

Plan stays inside Out of scope (`finished` only `true`/`null`; no free range, no «Популярные», no ratings/quality). No open blocking question: Q1-Q3, D1, D2, UX1, UX2 and Plan Q1 answered in `state.md`.

### Notes (non-blocking)

1. AC-11 points at M3, which checks the header summary, not the stand. At verify, qa-device should add screenshots of the root (4 and 5 doors) and the three new pages for the owner to set against `docs/design/showall/`.
2. The spec's Test design row for `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus` still shows lower-case `year`/`asc`; Plan Task 4 and Plan Q1 (`state.md`) supersede it.
3. Spec Architect table names `CacheKeys.countries()`; Plan Task 3 uses `const val Countries`. Follow the plan.
4. `yearLabel` in Task 6 uses literals `10` and `9`; reuse `DECADE` from Task 5 to keep detekt `MagicNumber` quiet.

### Must fix

none

### Fix round 1

Triage rows 2-5 (row 1 is device work).

| Row | Fix | Test |
|-----|-----|------|
| 2 | `ContentListInteractor.cacheKey` appends country, year and finished only when one is set | red: firstPageCache_withNoBrowseFilter_keepsTheKeyShapeOfBeforeTheFilters - no row under the pre-change key on the old code (confirmed by reverting the fix) |
| 3 | `YearPage` adds one selected row for a stored year outside the presets, with first focus | androidTest yearPage_aStoredYearOutsideThePresets_isTheCheckedRowAndTakesFocus (compiled only, not run on a device) |
| 4 | none, the VM already clears `finished` | ShowAllVMTest statusChosen_anyStatusOnASeriesGrid_clearsFinished (green on base: behaviour existed) |
| 5 | last assertion now checks focus on the last country's row by text | ShowAllFilterFocusTest countryPage_downReachesTheLastCountry |
