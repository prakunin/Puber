# Same title repeats within one carousel row after a refresh

Status: approved
Date: 2026-11-11
Slug: carousel-row-duplicate-cards
Kind: bug
Size: M
Tracks: none

## Problem / Why

A paged catalogue row shows the same title several times while scrolling right. Owner decisions (state.md Decisions): the row is the Series tab "Popular" row; fix only the `restoreLoadedDepth` path.

### Root cause (confirmed by code trace; not reproduced on a device)

1. A refresh (`ContentListPagingVM.kt:104-108`) restores the row to `currentPage`, which `publish` sets from the last response's `pagination.current` (`ContentListPagingVM.kt:269`), i.e. a server page number.
2. With "show anime" off on a `FollowPreference` row, or "hide watched" on, `fetchFilteredPage` reads several server pages per call and answers with `pagination.current` = the last server page read (`ContentListInteractor.kt:131-167`).
3. `restoreLoadedDepth` advances its own cursor by 1 per request (`ContentListPagingVM.kt:204-208`), ignoring that the previous answer already reached further. The next request starts at a server page it already holds.
4. It appends with no id check (`ContentListPagingVM.kt:215`) and publishes through `replace` (`ContentListPagingVM.kt:285-286`); `Paginator` `Replace` stores the list as is (`Paginator.kt:214-227`).

Trace (perpage 25, half the titles filtered): walk gives page 1 = server 1+2 (current 2), page 3 = server 3+4 (current 4). Refresh, depth 4: cursor 2, request 3 gives server 3+4 (current 4); `4 < total` so request 4 gives server 4+5. Server page 4 is in the row twice, about one page apart.

Second source on the same path, `plausible`: with no filter, a reorder of `popular` between consecutive reads puts a boundary title twice, adjacently (research.md: Current state in code). Adjacent equal lazy keys (`SectionRow.kt:151`, `ShowAllScreenContent.kt:114`) can throw "Key ... was already used" (`unverified`).

The walk path is not affected: `NextPage` merges by id (`Paginator.kt:370-389`). ShowAll extends `ContentListPagingVM` (`ShowAllVM.kt:56-77`) and has the same bug.

### Risks (analyst)

- Risk area "ViewModel state and screen wiring", hence M rather than the research's S: the change is one function, but it decides the list a refresh publishes and the `currentPage` the walk continues from.
- Focus restore after a return depends on the focused id surviving the reload (`ContentListPagingVM.kt:97-102`, focus keyed by id at `SectionRow.kt:124`). De-duplication shifts indexes; AC-7 checks it.
- The request ceiling comment (`ContentListPagingVM.kt:202-203`) must stay true: the response may decide where to read, never how many reads.
- `FreshSectionPager` refuses a cursor it never issued (`ContentListInteractor.kt:60-62`); its pages have `current == requested` (research.md: Current state in code), so the new cursor sends the same requests. AC-3 guards it.

## Acceptance criteria

- AC-1. Given a row whose `loadPage` answers page 1 with `current = 2` and page 3 with `current = 4` (total > 4), walked to `currentPage = 4`, When a refresh runs, Then `loadPage(page = 4)` is not requested and the published row holds each id once. (unit, red on base)
- AC-2. Given the restore reads pages whose items overlap by id, When the restored list is published, Then each id appears once, in the order of first occurrence. (unit, red on base)
- AC-3. Given pages with `current == requested` and disjoint ids, When a refresh runs, Then the requests and the published list equal base (the four existing `SectionVMRefreshDepthTest` cases stay green unchanged).
- AC-4. Given a row deeper than `MAX_RESTORED_PAGES`, or answers that report `current` lower than requested, When a refresh runs, Then the restore makes at most `MAX_RESTORED_PAGES - 1` `loadPage` calls. (unit)
- AC-5. Given "hide watched" on and a restore page that comes back empty with `current` several pages ahead, When the empty chain exceeds `MAX_EMPTY_PAGE_CHAIN` (`:379`), Then the restore stops and publishes what it has, as on base. (unit)
- AC-6. Given a later restore page fails, When a refresh runs, Then nothing is published and the row stays as it was (existing `refresh_whoseLaterPageFails_leavesTheRowAsItWas` green).
- AC-7. Given the Series tab with "show anime" off, the Popular row scrolled past at least 3 logical pages, When the user opens a title, saves or unsaves it, returns, and scrolls right to the row's end of loaded pages, Then no title appears twice in the row and focus is on the card opened before. (device, D-pad)
- AC-8. Given the same settings, When the same steps run in the Popular "show all" grid, Then no title appears twice. (device)

## Out of scope

De-duplication in `Paginator` `Replace` / `NextPage` (run `paginator-hardening`); Home rows.

## Design

Base 901d820d. Implements research.md Option A within brief.md Scope. File paths are relative to the worktree; `ContentListPagingVM.kt` is `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where does the next restore request start? | `page = maxOf(page, deepest.pagination.current) + 1`, where `page` is the last page requested (starts at `firstPage.pagination.current`). | Skips server pages a filtered answer already read (AC-1). With `current == requested` it is `page + 1`, the same requests as base (AC-3). The `maxOf` makes the cursor strictly increasing even when a reply reports a lower `current` (AC-4). |
| When does the loop stop? | `while (maxOf(page, deepest.pagination.current) < restoreTo && deepest.pagination.current < deepest.pagination.total)`; the empty-chain break at `ContentListPagingVM.kt:217-221` stays as is. | `deepest.current >= restoreTo` ends it (AC-1); the empty chain still counts raw `next.items` (AC-5). |
| How is the request count bounded? | By the strictly increasing cursor itself: requests are at most `restoreTo - firstPage.pagination.current <= MAX_RESTORED_PAGES - 1`. No second counter variable. | The brief asks for a request ceiling independent of the response; a cursor that grows by at least 1 per request and stops at `restoreTo` is that ceiling, and a reply can only lower the count. One variable instead of two keeps the comment at `:202-203` true with one fact to maintain. |
| How are duplicates removed? | Accumulate into a `LinkedHashMap<Int, Item>` with `putIfAbsent(item.id, item)` (first occurrence wins, insertion order kept), return `items = map.values.toList()`. `Item.id` is `Int` (`app/src/main/java/com/kino/puber/data/api/models/Models.kt:47`). | Same idiom as the interactor (`ContentListInteractor.kt:141,153`); covers the overlap source and the reorder (drift) source (AC-2). |
| Which pagination is published? | Unchanged: `deepest.pagination` (`ContentListPagingVM.kt:223`). | `publish` sets `currentPage` from it (`:269`); the walk continues from the last server page actually read. |
| Failure handling | Unchanged: any failed `loadPage` returns `null`, nothing published (`:207-214`). | AC-6. |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: `ContentListInteractor.fetchFilteredPage` (`ContentListInteractor.kt:125-168`) and `FreshSectionPager.loadPage` (`FreshSectionPager.kt:22`) are untouched. |
| ViewModel state and actions | `ContentListPagingVM.restoreLoadedDepth` (`ContentListPagingVM.kt:191-224`): cursor and loop condition per Decisions, `items` becomes the id-keyed map, the comment at `:202-203` restated for the new cursor, KDoc at `:173-189` gains one sentence on first-occurrence de-duplication. `MAX_RESTORED_PAGES` (`:399`) and `MAX_EMPTY_PAGE_CHAIN` (`:379`) unchanged. `ShowAllVM` inherits the fix (`app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:32`). |
| Screen and DI module | N/A: no wiring change. |
| Compose content | N/A: lazy keys stay `item.id` (`SectionRow.kt:151`, `ShowAllScreenContent.kt:114`); focus stays keyed by id (`SectionRow.kt:124`). |
| Resources | N/A |
| Tests | New cases in `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMRefreshDepthTest.kt`, reusing `pageOf(vararg ids, current, total)` (`:189`), `mapperFor`, `servesFirstPageFromTheNetwork` from `SectionVMTestFixture` (`SectionVMTest.kt:57,76,132`); the four existing cases (`:32,66,116,154`) unchanged. Rows are the test-designer's. |

### Alternatives rejected

- Separate request counter beside a response-driven cursor: two variables encoding one bound.
- `distinctBy { it.id }` in `mapItems` (research Option C): hides the symptom, re-reads pages.
- De-duplication in `Paginator` `Replace` (Option B): owned by run `paginator-hardening` (state.md Decisions Q1).
- Cursor `deepest.current + 1` without `maxOf`: a reply reporting a lower `current` would re-request the same page until the ceiling.

### Risks

| Risk | Kind | Where | Guard |
|---|---|---|---|
| Focus lands elsewhere after a return because de-duplication shifts indexes, or the focused id was a duplicate's second copy. | Focus and D-pad | `SectionRow.kt:124` (focus by id); `ContentListPagingVM.kt:97-102` | First occurrence keeps the id present; AC-7 on device. |
| Fewer restore requests can leave the restored row shorter than the walked one, where the walk's own page `4` held titles now removed as duplicates. | Data shape | `ContentListPagingVM.kt:195-222` | Every id the walk showed is still read (same server pages, minus re-reads); AC-1 asserts each id once. |
| `currentPage` after a restore no longer equals the walked depth when a reply jumped ahead; the next walk step starts at `deepest.current + 1`. | Correctness | `ContentListPagingVM.kt:151,269` | Intended: that is the next unread server page; AC-1 covers the published pagination. |
| Hide-watched empty pages with `current` far ahead: the jump can end the loop before the empty-chain break. | Correctness | `ContentListPagingVM.kt:217-221`; `ContentListInteractor.kt:144,160-162` | Fewer requests than base, never more; AC-5. |
| `FreshSectionPager` rejects a cursor it never issued. | Lifecycle | `FreshSectionPager.kt:29` | Its pages report `current == requested`, so the cursor equals base; AC-3. |
| Map allocation per restore. | Performance | `ContentListPagingVM.kt:199` | At most `MAX_RESTORED_PAGES` pages of ~25 items; negligible. |

### Open questions

N/A: no AC needs changing.

## Test design

Base 901d820d. ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Unit tests go in `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMRefreshDepthTest.kt` (fixture `SectionVMTestFixture`, `pageOf`, `mapperFor`, `servesFirstPageFromTheNetwork`) unless the row names `ShowAllVMTest` (`app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt`, its own `servesFirstPageFromTheNetwork`, `createVM`). Row walked with `CommonAction.LoadMore` + `advanceUntilIdle`; refresh through `coordinator.requestRefresh()` (ShowAll: `CommonAction.RetryClicked`). Ids read from `SectionState.Content.items.map { it.id }`.

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Filtered page reads ahead, restore re-requests a server page it holds, ids twice in the row | 3 | 2 | 6 | AC-1 unit |
| Reorder between reads puts an id twice, adjacent; lazy key crash | 2 | 3 | 6 | AC-2 unit; manual 1 |
| ShowAll grid keeps the bug (own override or path) | 3 | 2 | 6 | ShowAll unit; manual 2 |
| Request ceiling lost: a reply drives the request count (or loops), or a read-ahead deep row restores past `MAX_RESTORED_PAGES` | 2 | 2 | 4 | AC-4 unit |
| Restore with first page reporting `current = 0` makes `MAX_RESTORED_PAGES` requests, not `- 1` (Finding) | 1 | 2 | 2 | AC-4 unit, P1 |
| Walk after the restore continues from the wrong page | 2 | 2 | 4 | AC-1 walk-on unit |
| Empty-chain stop skipped by a jumping cursor (hide watched) | 2 | 2 | 4 | AC-5 unit |
| Focus lands elsewhere after de-duplication shifts indexes | 2 | 2 | 4 | AC-7 manual |
| Unfiltered / `FreshSectionPager` rows get different requests | 1 | 3 | 3 | AC-3 existing tests |
| Failed later page publishes a partial row | 1 | 2 | 2 | AC-6 existing test |

### Finding: AC-4 and the cursor bound

Resolved by spec Q3: a request counter (at most `MAX_RESTORED_PAGES - 1` calls) beside the `maxOf` cursor. The cursor alone bounded requests by `restoreTo - firstPage.pagination.current`, 10 when the first page reports `current = 0` (unfiltered path returns server pagination as is, `ContentListInteractor.kt:132-136`). With two bounds, each AC-4 row asserts the exact pages requested, so a mutation the counter would hide still shows.

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds` | `loadPage(page = 4)` never called and ids `[1,2,5,6,7,8]`; fails if the cursor still steps by 1 from `firstPage.current` |
| AC-1 | unit | P1 red | `refresh_afterAPageThatReadAhead_walksOnFromTheLastServerPageRead` | after the refresh, `LoadMore` requests page 5 once and page 6 never; fails if `deepest.pagination` is not the published one or the extra read stays |
| AC-2 | unit | P0 red | `refresh_withOverlappingPages_publishesEachIdOnceInFirstOccurrenceOrder` | ids `[1,2,3,4,5]`; fails if de-duplication is dropped as a plain list append (`[1,2,3,4,2,5]` on base) |
| AC-2 | unit (`ShowAllVMTest`) | P1 red | `retry_ofAGridWithOverlappingPages_publishesEachIdOnce` | `ShowAllViewState.Content` ids each once after a retry at depth 2; fails if ShowAll does not go through the fixed restore |
| AC-3 | unit | P0 guard | `refresh_reloadsEveryPageTheRowHadAlreadyLoaded`, `refresh_neverPublishesLessThanTheRowAlreadyShowed`, `refresh_interruptedByAnotherRefresh_stillComesBackAtFullDepth` (existing, unchanged) | exact request counts and row length for `current == requested` pages; fails if the new cursor skips or repeats a page |
| AC-4 | unit | P0 guard | `refresh_ofARowDeeperThanTheRestoreCeiling_makesAtMostNineRestoreRequests` | walked to depth 12, the refresh requests pages 1..10 each exactly once and pages 11, 12 never; fails if counter and coercion are both lost (pages 11, 12 requested) or the counter bound stops below page 10 (page 10 never requested) |
| AC-4 | unit | P0 guard | `refresh_whosePagesReportALowerPageThanRequested_makesAtMostNineRestoreRequests` | pages >= 2 answer `current = 1`; after the walk, pages 2..10 are each requested exactly once and no other restore page; fails if `maxOf` becomes `page = next.pagination.current` (page 2 requested nine times), or if that and the counter are both lost (stub throws after 20 calls instead of hanging); deleting the `maxOf` line is caught by the AC-1 read-ahead tests |
| AC-4 | unit | P0 guard | `refresh_ofADeepRowWhosePagesReadAhead_stopsAtTheRestoreCeiling` | at depth 12, pages requested after the walk are exactly 2, 4, 6, 8, 10, page 12 never; fails if the `MAX_RESTORED_PAGES` coercion alone is lost (page 12 requested) |
| AC-4 | unit | P1 red | `refresh_whoseFirstPageReportsPageZero_makesAtMostNineRestoreRequests` | at most 9 restore calls; fails if the request counter is removed (cursor from 0 makes 10 calls, as on base) |
| AC-5 | unit | P0 guard | `refresh_throughEmptyPagesThatReadAhead_stopsAfterTheEmptyPageBudget` | exactly `maxEmptyPageChainUnderTest + 1` restore calls, row ids `[1]` published; fails if the empty-chain break is removed or counts something other than `next.items` |
| AC-6 | unit | P0 guard | `refresh_whoseLaterPageFails_leavesTheRowAsItWas` (existing, unchanged) | fails if a failed page publishes a partial row |
| AC-7 | device | P0 red | Manual 1 | duplicate titles in the Series Popular row after a return; focus not on the opened card |
| AC-8 | device | P0 red | Manual 2 | duplicate titles in the Popular "show all" grid |

### Fixtures for the new unit tests

Config `config("popular")`, `mapperFor` every id used. Request counts come from a per-page counter in the stubbed `answers` reset after the walk, so the walk's own calls are not counted.

| Test | Stubs | Walk |
|---|---|---|
| `..._doesNotReRequestAServerPageItAlreadyHolds`, `..._walksOnFromTheLastServerPageRead` | p1 `(1,2)` cur 2; p3 `(5,6,7,8)` cur 4; p4 `(7,8,9,10)` cur 5; p5 `(11,12)` cur 6; total 6 | one `LoadMore` (requests p3, `currentPage = 4`) |
| `..._publishesEachIdOnceInFirstOccurrenceOrder` | p1 `(1,2,3)` cur 1; p2 `(4,2,5)` cur 2; total 2 | one `LoadMore` |
| `retry_ofAGridWithOverlappingPages_publishesEachIdOnce` | same pages as above, mapper keeping ids | one `LoadMore`, then `RetryClicked`; wait as the file's other tests do (`withTimeout` loop) |
| `..._ofARowDeeperThanTheRestoreCeiling_...` | any page n: `(n)` cur n, total 20 | 11 `LoadMore` |
| `..._whosePagesReportALowerPageThanRequested_...` | walk: page n `(n)` cur n, total 50; after walk, n >= 2 answers `(n)` cur 1 | 9 `LoadMore` (depth 10) |
| `..._ofADeepRowWhosePagesReadAhead_...` | walk: page n `(n)` cur n, total 50; after walk, n >= 2 answers `(n)` cur n + 1 | 11 `LoadMore` (depth 12) |
| `..._whoseFirstPageReportsPageZero_...` | walk as above, total 50; after walk, p1 answers `(1)` cur 0 | 11 `LoadMore` (depth 12) |
| `..._stopsAfterTheEmptyPageBudget` | walk: page n `(n)` cur n, total 50; after walk, n >= 2 answers empty, cur n + 1 | 9 `LoadMore` (depth 10) |

### Bug: failing-first test

`refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds`. On base (`git show 901d820d:.../ContentListPagingVM.kt`, lines 204-215) the cursor starts at 2, requests 3 (cur 4), then `3 < 4` and `4 < 6` request 4, and appends `(7,8)` twice. Written and run red before the change.

### Manual checks (qa-device, leased emulator, worktree dev APK)

A refresh runs only on a non-empty `ContentChangeSet` (`ContentListVM.kt:147-159`, `SectionVM.kt:53-61`): the save, unsave or watched step is an account mutation and needs the owner's explicit authorisation; without it, checks 1-3 do not reach the restore and the refresh half of AC-7/AC-8 stays NOT CHECKED.

Setup: logged in, Settings "show anime" off, "hide watched" off. Keys: `adb -s <serial> shell input keyevent` with `DPAD_RIGHT`, `DPAD_DOWN`, `DPAD_CENTER`, `BACK`. Record titles from screenshots every 5 cards; evidence holds the title list, not raw UI dumps.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Series tab, focus the Popular row; `DPAD_RIGHT` until past 75 cards (3 logical pages); note the focused title; `DPAD_CENTER`, save or unsave it, `BACK`; then `DPAD_RIGHT` to the end of the loaded cards | Focus returns to the noted card; every title in the row appears once | A title twice in the row; focus on another card or out of the row; crash; logcat line `was already used` for the package |
| 2 | Series tab Popular "show all"; `DPAD_DOWN`/`DPAD_RIGHT` past 3 pages; open a title, save or unsave, `BACK`; scroll to the end of the loaded cards | Every title appears once in the grid; focus on the opened card | Duplicates; crash; empty grid or shimmer replacing the old list during the reload |
| 3 | Repeat 1 with "show anime" on (default) | Same as 1 | Same as 1 |

### Perf

N/A: track `perf` is off.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Fix only `restoreLoadedDepth`, or also the shared paginator? | yes | answered | Only `restoreLoadedDepth`; paginator dedupe stays with `paginator-hardening`. |
| Q2 | Is "top series" the Series tab Popular row? | no | answered | Yes. |
| Q3 | Test design finding: the cursor alone does not bound AC-4 when the first page reports `current = 0`. Start the cursor at `maxOf(current, 1)` or keep a separate request counter? | yes | answered | Keep a separate request counter bounded by `MAX_RESTORED_PAGES - 1` beside the `maxOf` cursor (the brief's original scope); it does not depend on how the server numbers its first page. |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-11 | Q1: fix the `restoreLoadedDepth` path only | owner (standing instruction: recommended) |
| 2026-11-11 | Q2: "top series" = Series tab Popular row | owner (standing instruction: recommended) |
| 2026-11-11 | Q3: separate request counter (at most `MAX_RESTORED_PAGES - 1` calls) beside the `maxOf` cursor; overrides the architect's "no second counter" decision | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1: approved as M, tracks none, AC-1..AC-8 | owner (standing instruction: recommended) |
