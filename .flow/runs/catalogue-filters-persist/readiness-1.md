# Readiness 1: catalogue-filters-persist

Verdict: **READY**

Checked against base ef5987ec (`git show ef5987ec:<path>`), spec and plan in the worktree, `test-design.md` in the run folder.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 2 (request, state), Task 3 (focus) | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached`, `restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage`, `ShowAllFilterFocusTest.coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`, M1 |
| AC-2 | Task 2 | `filterApplied_reopenedGridStartsWithIt`, M2 |
| AC-3 | Task 2 | `filterOnMovies_isNotInheritedBySerialOr4k`, M3 |
| AC-4 | Task 2 | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached` (exact call list) |
| AC-5 | Task 1, Task 2 | `filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached`, `CatalogueFilterRepositoryTest.write_inactiveFilterRemovesTheEntry`, `clear_removesOnlyThatGrid`, `write_thenRead_returnsGenreSortAndDirection` |
| AC-6 | Task 2 | `restoredFilter_panelDraftEqualsIt`, `restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage` |
| AC-7 | Task 1 | `CatalogueFilterRepositoryTest.read_*` (four rows) |
| AC-8 | Task 2 | `restoredAnime_withShowAnimeOff_skipsTheGenreKeepsTheSort`, `restoredAnime_withShowAnimeOn_isApplied`, `restoredAnime_closedUnchanged_keepsTheStoredGenre`, M4 |
| AC-9 | Task 2 | `shelfGrid_neverReadsOrWritesTheStore` |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `ShowAllVM.kt:40-58`, `:60-69`, `:144-145`, `:154-167`, `:169-174` | constructor and super call; `base`, `filterable`, `mutableFilterState`, `applied`; `openFilterPanel` seeds draft from `applied`; `closeFilterPanel` / `resetFilter`; `withApplied` |
| `ContentListPagingVM.kt:31-39`, `:50-51`, `:167-174` | abstract constructor; `cacheable = true`; `onLoadFirstPage` captures `config` and `cacheable` |
| `ContentListInteractor.kt:52-53`, `:60-70` | `showAnimeEnabled`; `observeFirstPage` uncached branch emits `loadPage` only |
| `ContentFilter.kt:6`, `:17`, `:20`, `:30`; `ContentFilterQuery.kt:6`; `TabTypeConfig.kt:27`; `ContentGenres.kt:4`; `Models.kt:197` | `ContentFilter`, `isActive`, `SortField`, `SortDirection`; `applyTo` (genre as id string); `isFilterable`; `ANIME_GENRE_ID = 25`; `Genre` |
| `ShowAllScreen.kt:37`, `:45-54`; `modules.kt:109`; `NavigationPreferencesRepository.kt:21-23`, `:61`, `:64-67` | key; Koin `ShowAllVM(...)` with named args; `singleOf(::NavigationPreferencesRepository)`; prefs, `edit {}`, enum by name with fallback |
| `ShowAllHeader.kt:42-43`, `:51`, `:77`, `:94-99`; `ShowAllScreenContent.kt:84-87`, `:110` | KDoc; `canFocus`; gate on «Фильтры» only; «Сброс» focusProperties without gate; latch; passed in |
| `ShowAllVMTest.kt:83`, `:339-345`, `:380-404`, `:396`, `:741-757`; `ShowAllFilterFocusTest.kt:51-55`, `:93-104`; `FakeSharedPreferences.kt:15`; `app/build.gradle.kts:10`, `:390`; `docs/architecture/paging-and-filters.md:128` | all resolve as claimed (relaxed interactor, `allMovie`/`all4k`/`FirstPageCall`, `filterRig`, `showAnimeEnabled` literal, `createVM`, host fields, cold-open test, `values`/`transactions`, serialization plugin and json lib, `changeQuery` paragraph) |

## Build order

Task 1 (new class + DI line) and Task 2 (`ShowAllVM` constructor, Koin factory and test factory together) each compile on their own; Task 3 is independent. `SectionVM.kt:31-39` calls the super positionally, so the defaulted last param keeps it green. Detekt `LongParameterList` is inactive (`config/detekt/detekt.yml:121-122`).

## P0 rows

Every P0 row names its mutation, is marked `red` or `guard`, and names a backticked test; M1 is the device row for AC-1. Scope stays inside Out of scope (no change to `ContentFilter` or its enums, no logout clear). Q1-Q3 answered; none blocks.

## Non-blocking notes for the implementer

1. `test-design.md` cites the mocked-Context pattern as `NavigationPreferencesRepositoryTest.kt:473-490`; the mock is at `:487-493` (the plan is right).
2. Spec Design says the test factory default is a mockk repository; Test design and plan use a real repository over `FakeSharedPreferences`. Follow the plan.
3. `FakeSharedPreferences` stubs only `apply()`, not `commit()`; write through `prefs.edit { }` as planned.
