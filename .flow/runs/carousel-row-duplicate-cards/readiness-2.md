# Readiness 2: carousel-row-duplicate-cards

Verdict: **READY**

Worktree HEAD ca58fa12 (base 901d820d plus the spec commit; spec edited and plan untracked in the working tree). Plan and test design checked against the code at that HEAD.

## Round 1 fixes

| # | Status | Where |
|---|---|---|
| 1 | Fixed | Plan Task 1 table row `..._whosePagesReportALowerPageThanRequested_...`: pages 2..10 once each after the walk, counted through `pagesAfterWalk`; test-design row says the same. Dropping `maxOf` gives page 2 nine times, caught. |
| 2 | Fixed | Test-design and plan row `..._ofARowDeeperThanTheRestoreCeiling_...` name mutations the asserts catch: counter and coercion lost (`callsAfterWalk` 12), counter bound below page 10 (page 10 missing). |
| 3 | Fixed | Plan Task 1 **Covers** names AC-7 as device-only (Manual 1). |

## Checks

| Check | Result |
|---|---|
| AC to plan task | AC-1..AC-7: Task 1; AC-2 (ShowAll), AC-8: Task 2. |
| AC to Test design row | Every AC has a row. |
| Citations | All sampled resolve; table below. |
| Build stays green | Task 1 writes tests, fixes, commits as one green step; Task 2 is test-only, the base-VM red run is reverted before commit. |
| Order | Task 2 consumes Task 1's restore. |
| P0 rows | Each names a backticked test (device rows: Manual 1/2), `red`/`guard`, and a mutation the planned asserts catch; traced each new AC-1/AC-2/AC-4/AC-5 fixture through the planned loop and through base `VM:199-223`. |
| Counting method | Plan's `pagesAfterWalk` + `callsAfterWalk` (reset after the walk, first-page call included) agrees with the test-design's "per-page counter reset after the walk". |
| Out of scope | Production change limited to `restoreLoadedDepth`; `Paginator`, interactor, `FreshSectionPager`, Compose, Home untouched. |
| Blocking questions | Q1, Q3 answered; plan states that Q3 overrides the spec Design row "No second counter variable". |

## Citation sample

| Citation | Resolves to |
|---|---|
| `VM:191,199,202-203,208,215,223` | `restoreLoadedDepth`; `toMutableList`; ceiling comment; `interactor.loadPage`; `items += next.items`; `return PaginatedResponse(...)` |
| `VM:173-189`, `VM:269`, `VM:340-346`, `VM:379,399`, `VM:38` | KDoc; `currentPage = response.pagination.current`; `mapItems`; `MAX_EMPTY_PAGE_CHAIN = 3`, `MAX_RESTORED_PAGES = 10`; `Dispatchers.Default` |
| `RDT:32,66,116,154,189` | four existing tests; `pageOf(vararg ids, current, total)` |
| `FIX:57,76,98,119,124,132,141` | `SectionVMTestFixture`, `servesFirstPageFromTheNetwork`, `createVM`, `paginator`, `config`, `mapperFor`, `maxEmptyPageChainUnderTest = 3` |
| `SAT:191-195,267,274,289,291` | `withTimeout` polling; `servesFirstPageFromTheNetwork`, `createVM`, `videoItem`, `item` |
| `ShowAllVM.kt:32,76,77,123-125` | `: ContentListPagingVM<ShowAllViewState>(`; `LoadMore`, `RetryClicked`; `Content(mapItems(...))` |
| `Models.kt:47`, `SectionState.kt:11-14`, `ContentListInteractor.kt:153` | `val id: Int`; `Content(items, ...)`; `putIfAbsent(item.id, item)` |

## Non-blocking notes for the implementer

- Test-design Finding says every AC-4 row asserts exact pages; the page-zero row asserts only `callsAfterWalk <= 10`. Its mutation (counter removed) is still caught.
