# Readiness 1: carousel-row-duplicate-cards

Verdict: **NOT READY**

Worktree HEAD ca58fa12 (base 901d820d plus the spec commit only). Plan and test design checked against the code at that HEAD.

## Checks

| Check | Result |
|---|---|
| AC to plan task | AC-1..AC-6: Task 1; AC-2 (ShowAll): Task 2; AC-8: Task 2 names it as device-only. AC-7 has no plan task (fix 3). |
| AC to Test design row | Every AC has a row (AC-7/AC-8: Manual 1/2). |
| Citations | All resolve; see the sample below. |
| Build stays green | Task 1 writes the tests, fixes and commits as one step; Task 2 is test-only after Task 1; the base-VM red run in Task 2 is reverted before commit. |
| Order | Task 2 consumes Task 1's restore; correct. |
| P0 rows | All name a test, `red`/`guard`, and a mutation; two named mutations are not caught under the planned code (fixes 1-2). |
| Out of scope | Production change limited to `restoreLoadedDepth`; `Paginator`, interactor, pager, Compose and Home untouched. |
| Blocking questions | Q1, Q3 answered; plan follows Q3 (counter beside `maxOf` cursor). |

## Citation sample

| Citation | Resolves to |
|---|---|
| `ContentListPagingVM.kt:104` | `fun refreshFirstPage()` |
| `ContentListPagingVM.kt:191,199,202,208,215,218,223` | `restoreLoadedDepth`, `toMutableList`, ceiling comment, `interactor.loadPage`, `items += next.items`, empty-chain break, `return PaginatedResponse(...)` |
| `ContentListPagingVM.kt:269,286` | `currentPage = response.pagination.current`; `replace(response.items, ...)` |
| `ContentListPagingVM.kt:379,399` | `MAX_EMPTY_PAGE_CHAIN = 3`; `MAX_RESTORED_PAGES = 10` |
| `ContentListInteractor.kt:125-168` | `fetchFilteredPage`; `check(current == requested)` at 145-148, returns the last server page's pagination |
| `Paginator.kt:214-227`, `:370-389` | `executeReplaceAction` stores as is; `NextPage` merges by comparator |
| `FreshSectionPager.kt:22,29` | `loadPage`; `check(requestedPage == nextLogicalPage)` |
| `SectionRow.kt:151`, `ShowAllScreenContent.kt:114` | `itemsIndexed(..., key = { _, item -> item.id })` |
| `ShowAllVM.kt:76-77`, `:123-125` | `LoadMore -> notifyLoadNextPage()`, `RetryClicked -> refreshFirstPage()`; `Content(mapItems(...))` |
| `RDT:32,66,116,154,189`; `FIX:76,98,119,124,132,141`; `SAT:191-195,267,274,289,291` | the four existing tests and `pageOf`; fixture helpers; ShowAll polling and helpers |
| `Models.kt:47`, `SectionState.kt:11-14` | `val id: Int`; `Content(items: List<VideoItemUIState>)` |

## Fix list

1. `refresh_whosePagesReportALowerPageThanRequested_makesAtMostNineRestoreRequests` (P0 guard) claims it fails if the `maxOf` is dropped. With the Q3 request counter, `page = next.pagination.current` re-requests page 2 nine times and still makes 9 calls, so the test passes; no other planned test catches it (AC-1 data reports `current` ahead). Add to the plan's Assert and the test-design row: after the walk, pages 2..10 are each requested exactly once.
2. `refresh_ofARowDeeperThanTheRestoreCeiling_makesAtMostNineRestoreRequests` (P0 guard) claims it fails if the `MAX_RESTORED_PAGES` coercion is lost. The counter alone still stops at page 10, so it does not. Restate the mutation in the test-design row to one it catches (counter and coercion both lost; a counter bound that stops below page 10).
3. Plan: name AC-7 as device-only (qa-device, Manual 1) in a task's **Covers**, as Task 2 does for AC-8.
