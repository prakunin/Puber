# Test design: carousel-row-duplicate-cards

Base 901d820d. ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Unit tests go in `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMRefreshDepthTest.kt` (fixture `SectionVMTestFixture`, `pageOf`, `mapperFor`, `servesFirstPageFromTheNetwork`) unless the row names `ShowAllVMTest` (`app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt`, its own `servesFirstPageFromTheNetwork`, `createVM`). Row walked with `CommonAction.LoadMore` + `advanceUntilIdle`; refresh through `coordinator.requestRefresh()` (ShowAll: `CommonAction.RetryClicked`). Ids read from `SectionState.Content.items.map { it.id }`.

## Risk profile

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

## Finding: AC-4 and the cursor bound

Resolved by spec Q3: a request counter (at most `MAX_RESTORED_PAGES - 1` calls) beside the `maxOf` cursor. The cursor alone bounded requests by `restoreTo - firstPage.pagination.current`, 10 when the first page reports `current = 0` (unfiltered path returns server pagination as is, `ContentListInteractor.kt:132-136`). With two bounds, each AC-4 row asserts the exact pages requested, so a mutation the counter would hide still shows.

## Traceability

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

## Fixtures for the new unit tests

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

## Bug: failing-first test

`refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds`. On base (`git show 901d820d:.../ContentListPagingVM.kt`, lines 204-215) the cursor starts at 2, requests 3 (cur 4), then `3 < 4` and `4 < 6` request 4, and appends `(7,8)` twice. Written and run red before the change.

## Manual checks (qa-device, leased emulator, worktree dev APK)

A refresh runs only on a non-empty `ContentChangeSet` (`ContentListVM.kt:147-159`, `SectionVM.kt:53-61`): the save, unsave or watched step is an account mutation and needs the owner's explicit authorisation; without it, checks 1-3 do not reach the restore and the refresh half of AC-7/AC-8 stays NOT CHECKED.

Setup: logged in, Settings "show anime" off, "hide watched" off. Keys: `adb -s <serial> shell input keyevent` with `DPAD_RIGHT`, `DPAD_DOWN`, `DPAD_CENTER`, `BACK`. Record titles from screenshots every 5 cards; evidence holds the title list, not raw UI dumps.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Series tab, focus the Popular row; `DPAD_RIGHT` until past 75 cards (3 logical pages); note the focused title; `DPAD_CENTER`, save or unsave it, `BACK`; then `DPAD_RIGHT` to the end of the loaded cards | Focus returns to the noted card; every title in the row appears once | A title twice in the row; focus on another card or out of the row; crash; logcat line `was already used` for the package |
| 2 | Series tab Popular "show all"; `DPAD_DOWN`/`DPAD_RIGHT` past 3 pages; open a title, save or unsave, `BACK`; scroll to the end of the loaded cards | Every title appears once in the grid; focus on the opened card | Duplicates; crash; empty grid or shimmer replacing the old list during the reload |
| 3 | Repeat 1 with "show anime" on (default) | Same as 1 | Same as 1 |

## Perf

N/A: track `perf` is off.
