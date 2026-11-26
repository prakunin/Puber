# Readiness - catalogue-filters-country-year-status - round 1

<!-- owner: final-judge, mode readiness. Checked against base 90e69aff via `git show 90e69aff:<path>`. -->

**Decision:** READY
**Base:** 90e69aff   **Spec commit:** 2de3a556   **Plan:** uncommitted in the worktree

## AC coverage

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

## Citations sampled

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

## Build order

| Task | Stays green because |
|---|---|
| 1 | new `getItems` params appended with `null` defaults; existing mockk stubs still match |
| 2 | `SectionConfig` fields default `null`; `fetchPage` passes them by name |
| 3 | new class + binding only |
| 4 | `StoredFilter` fields defaulted |
| 5 | constructor change lands with its only two callers (`ShowAllScreen.kt:45`, `ShowAllVMTest.kt:947`, `git grep "ShowAllVM("`); new state fields defaulted, so the focus test compiles |
| 6 | `FilterPage` `when`s (title, body) edited in the same task; androidTest compile in Done-when |
| 7 | docs only |

## P0 rows

All P0 rows are `red` or `guard`, name the automated test in backticks, and state the assertion that fails on the defect. AC-1 is the one P0 manual row; its evidence exists.

## Scope and questions

Plan stays inside Out of scope (`finished` only `true`/`null`; no free range, no «Популярные», no ratings/quality). No open blocking question: Q1-Q3, D1, D2, UX1, UX2 and Plan Q1 answered in `state.md`.

## Notes (non-blocking)

1. AC-11 points at M3, which checks the header summary, not the stand. At verify, qa-device should add screenshots of the root (4 and 5 doors) and the three new pages for the owner to set against `docs/design/showall/`.
2. The spec's Test design row for `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus` still shows lower-case `year`/`asc`; Plan Task 4 and Plan Q1 (`state.md`) supersede it.
3. Spec Architect table names `CacheKeys.countries()`; Plan Task 3 uses `const val Countries`. Follow the plan.
4. `yearLabel` in Task 6 uses literals `10` and `9`; reuse `DECADE` from Task 5 to keep detekt `MagicNumber` quiet.

## Must fix

none
