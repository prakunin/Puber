# Test design: catalogue-filters-country-year-status

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

## Risk profile

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

## Traceability

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

## Failing-first test

`getCountries_unwrapsTheItemsOfAStatusItemsBody` (Client). Base `git show 90e69aff:app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:642-644` decodes `countries` as `List<Country>`; a `{"status":200,"items":[...]}` body fails to decode, so the test fails on base and passes after the unwrap. The other `red` rows fail on base by not compiling (new fields, actions, functions); the implementer records that and writes the Client test first.

## Manual checks

Box `192.168.1.110:5555` only, `com.kino.puber.stage`, worktree dev APK via `tools/android-apk-install-preserve`, under a lease. Keys through `adb -s 192.168.1.110:5555 shell input keyevent`. Screens to the evidence file `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-device.md`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | «Фильмы» → «Все» grid, Up to «Фильтры», OK; Down ×2, OK on «Страна»; Down to «США», OK; Back; Back | Root shows 4 doors (no «Статус»); page lists «Все страны» + 102 countries; header reads «США»; first cards hold США in details; focus returns to «Страна» then «Фильтры», then the first card when the list lands | Crash on page open; focus lost to nothing; stale unfiltered grid |
| M2 | Reopen panel, «Страна»: focus on «США»; DPAD_DOWN until the last country (Япония), then Down once more | Focus on Япония, row whole on screen; extra Down stays | Focus jumps to «Все страны»; row half-cut while focused; panel width jumps more than once |
| M3 | «Сериалы» → «Все»: set «Драма», «США», «2010-е», «Статус» → «Завершённые», sort «по году ↓»; close | 5 doors; one-line header in order genre · country · year · status · sort (ellipsis allowed at the end); grid years 2010-2019, finished series | «Статус» absent on serials; summary wraps to two lines |
| M4 | Force-stop the app, launch, open «Сериалы» → «Все» | Summary present on the first frame; focus on the first card; then «Сброс» clears all, grid reloads unfiltered | Summary appears late; focus on «Сброс» on cold open; filter survives reset |
| M5 | Open a title, start playback, open player settings; open About; open a series' Episodes panel | Panel widths match the master build screenshots taken in the same session | Any of them wider, narrower or clipped |
| M6 | «4K» grid → «Фильтры» | «Страна», «Год» present, no «Статус» | `finished` door on 4K |

## Perf

N/A: track `perf` is off.

## UNTESTABLE

None.
