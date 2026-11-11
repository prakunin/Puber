# Same title repeats within one carousel row after a refresh - plan

**Spec:** `docs/superpowers/specs/2026-11-11-carousel-row-duplicate-cards.md`
**Goal:** A refresh that restores a row's depth requests each server page once, publishes each id once, and makes at most `MAX_RESTORED_PAGES - 1` restore requests.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run both from the worktree root `/home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards`; never `./gradlew`.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message per commit style (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Production change is `restoreLoadedDepth` only. Untouched: `Paginator` (`Replace`/`NextPage`, run `paginator-hardening`), `ContentListInteractor`, `FreshSectionPager`, Home rows, Compose code.
- Spec Decision Q3 overrides the Design table row "How is the request count bounded?": keep a separate request counter beside the `maxOf` cursor.
- Paths below are relative to the worktree root. `VM` = `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt`; `RDT` = `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMRefreshDepthTest.kt`; `FIX` = `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMTest.kt`; `SAT` = `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt`.

---

## Tasks

### Task 1: Restore each server page once, de-duplicated, under a request counter

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6; AC-7 is device-only (qa-device, Manual 1)
**Files:**
- Modify: `VM` - `restoreLoadedDepth` (`VM:190-224`): cursor, counter, id map; KDoc (`VM:173-189`) and the comment at `VM:202-203`. Relies on `MAX_RESTORED_PAGES` (`VM:399`), `MAX_EMPTY_PAGE_CHAIN` (`VM:379`), `interactor.loadPage` call (`VM:208`), `publish` setting `currentPage` from the returned pagination (`VM:269`).
- Test: `RDT` - seven new cases; the four existing (`RDT:32,66,116,154`) unchanged.

**Consumes:** fixture `SectionVMTestFixture` (`FIX:57`): `createVM` (`FIX:98`), `paginator` (`FIX:119`), `config` (`FIX:124`), `servesFirstPageFromTheNetwork` (`FIX:76`, calls `loadPage(config, page = 1)` on every first-page load), `mapperFor` (`FIX:132`), `maxEmptyPageChainUnderTest` (`FIX:141`); `pageOf(vararg ids, current, total)` (`RDT:189`). `Item.id: Int` (`app/src/main/java/com/kino/puber/data/api/models/Models.kt:47`). `SectionState.Content.items` (`app/src/main/java/com/kino/puber/ui/feature/contentlist/model/SectionState.kt:11-14`).
**Produces:** no new signature; `restoreLoadedDepth(firstPage, depth): PaginatedResponse<Item>?` keeps its signature.

Target loop (replaces `VM:199-223`):

```kotlin
val items = LinkedHashMap<Int, Item>()
firstPage.items.forEach { items.putIfAbsent(it.id, it) }
var deepest = firstPage
var emptyChain = 0
var requests = 0
var page = firstPage.pagination.current
while (requests < MAX_RESTORED_PAGES - 1 && page < restoreTo &&
    deepest.pagination.current < deepest.pagination.total
) {
    page += 1
    requests += 1
    val next = /* unchanged try/catch around interactor.loadPage(config, page), null on failure */
    next.items.forEach { items.putIfAbsent(it.id, it) }
    deepest = next
    page = maxOf(page, next.pagination.current)
    emptyChain = /* unchanged, counts raw next.items */
    /* unchanged break when emptyChain > MAX_EMPTY_PAGE_CHAIN */
}
return PaginatedResponse(items = items.values.toList(), pagination = deepest.pagination)
```

Comment at `VM:202-203` becomes: the reply may move the cursor forward (a filtered answer that read ahead), never back and never decide how many requests are made; `requests` is that ceiling. KDoc gains one sentence: pages are merged by id, first occurrence kept.

Test fixtures (each test: `config("popular")`, `mapperFor` every id used, `StandardTestDispatcher(testScheduler)`, walk by `vm.onAction(CommonAction.LoadMore)` + `testScheduler.advanceUntilIdle()` per step, refresh by `coordinator.requestRefresh()` + `advanceUntilIdle()`, ids from `(vm.testStateValue as SectionState.Content).items.map { it.id }`). Count requests with a `var callsAfterWalk` incremented in a `coEvery { interactor.loadPage(sectionConfig, any()) } answers { ... secondArg<Int>() ... }` stub and set to 0 after the walk; it includes the first-page call, so restore requests = `callsAfterWalk - 1`. The same stub appends `secondArg<Int>()` to a `pagesAfterWalk` list cleared with the counter; "requested once each after the walk" means `pagesAfterWalk.groupingBy { it }.eachCount()` gives count 1 for each of those pages (`coVerify` would also count the walk's calls). Add `private val maxRestoredPagesUnderTest = 10` to `RDT` with KDoc "Mirrors ContentListPagingVM.MAX_RESTORED_PAGES."

| Test | Stubs | Walk | Assert |
|---|---|---|---|
| `refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds` | p1 `(1,2)` cur 2; p3 `(5,6,7,8)` cur 4; p4 `(7,8,9,10)` cur 5; p5 `(11,12)` cur 6; total 6 | 1 `LoadMore` | `coVerify(exactly = 0) { loadPage(sectionConfig, page = 4) }`; ids `[1,2,5,6,7,8]` |
| `refresh_afterAPageThatReadAhead_walksOnFromTheLastServerPageRead` | as above | 1 `LoadMore`, refresh, 1 more `LoadMore` | `loadPage(page = 5)` exactly 1, `loadPage(page = 6)` exactly 0 |
| `refresh_withOverlappingPages_publishesEachIdOnceInFirstOccurrenceOrder` | p1 `(1,2,3)` cur 1; p2 `(4,2,5)` cur 2; total 2 | 1 `LoadMore` | ids `[1,2,3,4,5]` |
| `refresh_ofARowDeeperThanTheRestoreCeiling_makesAtMostNineRestoreRequests` | page n: `(n)` cur n, total 20 | 11 `LoadMore` | `callsAfterWalk == 1 + (maxRestoredPagesUnderTest - 1)`; pages 1..10 requested once each after the walk; fails if both the counter and the `MAX_RESTORED_PAGES` coercion are lost, or if the counter bound stops below page 10 |
| `refresh_whosePagesReportALowerPageThanRequested_makesAtMostNineRestoreRequests` | walk: page n `(n)` cur n, total 50; after walk n >= 2 answers `(n)` cur 1; stub throws `IllegalStateException` once `callsAfterWalk > 20` | 9 `LoadMore` | `callsAfterWalk <= 1 + (maxRestoredPagesUnderTest - 1)`; pages 2..10 requested exactly once each after the walk; fails if the `maxOf` in the cursor is dropped (page 2 is then requested nine times) |
| `refresh_whoseFirstPageReportsPageZero_makesAtMostNineRestoreRequests` | walk as above, total 50; after walk p1 answers `(1)` cur 0, n >= 2 `(n)` cur n | 11 `LoadMore` | `callsAfterWalk <= 1 + (maxRestoredPagesUnderTest - 1)` |
| `refresh_throughEmptyPagesThatReadAhead_stopsAfterTheEmptyPageBudget` | walk: page n `(n)` cur n, total 50; after walk n >= 2 answers empty (`pageOf(current = n + 1, total = 50)`) | 9 `LoadMore` | `callsAfterWalk - 1 == maxEmptyPageChainUnderTest + 1`; ids `[1]` |

- [x] Write the seven tests in `RDT`.
- [x] Run `RDT` on the unchanged `VM`; record in the Implementation record which fail. Expected red: `..._doesNotReRequestAServerPageItAlreadyHolds`, `..._walksOnFromTheLastServerPageRead`, `..._publishesEachIdOnceInFirstOccurrenceOrder`, `..._whoseFirstPageReportsPageZero_...`. A P0 red test passing on base is a blocker: stop and report.
- [x] Change `restoreLoadedDepth` as above.
- [x] Run the fast gate and `RDT` (all 11 green) and `com.kino.puber.ui.feature.contentlist.vm.SectionVMTest`; commit, e.g. `Restore each server page of a refreshed row once and publish every title once`.

**Done when:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.contentlist.vm.*'` is green and `git diff HEAD~1 --stat` lists only `VM` and `RDT`.

---

### Task 2: Guard the "show all" grid through the same restore

**Covers:** AC-2 (ShowAll); AC-8 is device-only (qa-device, Manual 2)
**Files:**
- Test: `SAT` - one new case. `ShowAllVM` extends `ContentListPagingVM` (`app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:32`); `RetryClicked` calls `refreshFirstPage()` (`ShowAllVM.kt:77`); `LoadMore` calls `notifyLoadNextPage()` (`ShowAllVM.kt:76`); `ShowAllViewState.Content.items` maps the paginator list through `mapItems` (`ShowAllVM.kt:123-125`, `VM:340-346`, mapper called again for each new list instance).

**Consumes:** Task 1's `restoreLoadedDepth`; `SAT` helpers `servesFirstPageFromTheNetwork` (`SAT:267`), `createVM(config, paginator, mapper)` (`SAT:274`), `videoItem` (`SAT:289`), `item` (`SAT:291`). `ShowAllVM` runs on `Dispatchers.Default` (`VM:38`), so the test uses `runBlocking` + `withTimeout` polling as `SAT:191-195` does.
**Produces:** nothing.

`retry_ofAGridWithOverlappingPages_publishesEachIdOnce`:
- Stubs: `loadPage(any(), page = 1)` answers `(1,2,3)` cur 1 total 2; `loadPage(any(), page = 2)` answers `(4,2,5)` cur 2 total 2 and increments an `AtomicInteger page2Calls`. Build pages with `PaginatedResponse(items = ids.map(::item), pagination = Pagination(current, perpage = ids.size, total))` in a private `pageOf` helper in `SAT`.
- Mapper: `mockk<VideoItemUIMapper>()` whose `mapShortItemList(any())` answers `firstArg<List<Item>>().map { videoItem(it.id) }` and appends `page2Calls.get() to ids` to a `Collections.synchronizedList`.
- Steps: `testOnStart()`; poll until `Content`; `onAction(LoadMore)`; poll until `page2Calls == 1` and `Content` holds id 5; `onAction(RetryClicked)`; poll (`withTimeout(2_000)`) until the recorded list has an entry with `page2Calls == 2`.
- Assert: that entry's ids have no duplicate (`ids.size == ids.toSet().size`) and equal `[1,2,3,4,5]`; `testStateValue` ids likewise. On base the entry is `[1,2,3,4,2,5]`.

- [x] Write the test; run it once against the base `VM` (`git show 901d820d:<VM path> > <VM path>`, run `SAT`, then `git checkout -- <VM path>`); record the red run.
- [x] Run the fast gate and `com.kino.puber.ui.feature.showall.vm.ShowAllVMTest`; then the full gate; commit, e.g. `Check that a retried show-all grid publishes every title once`.

**Done when:** the full gate passes and `git diff HEAD~1 --stat` lists only `SAT`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** f309a928 Restore each server page of a refreshed row once and publish every title once; Task 2 commit "Check that a retried show-all grid publishes every title once".

**Red run** (P0 `red` tests on the unchanged code):
- red: `refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds` - `loadPage(page = 4)` verification failed (called once)
- red: `refresh_afterAPageThatReadAhead_walksOnFromTheLastServerPageRead` - `loadPage(page = 5)` / `page = 6` verification failed
- red: `refresh_withOverlappingPages_publishesEachIdOnceInFirstOccurrenceOrder` - expected [1, 2, 3, 4, 5] but was [1, 2, 3, 4, 2, 5]
- red: `refresh_whoseFirstPageReportsPageZero_makesAtMostNineRestoreRequests` - requests 11
- red: `retry_ofAGridWithOverlappingPages_publishesEachIdOnce` (against base VM) - expected [1, 2, 3, 4, 5] but was [1, 2, 3, 4, 2, 5]
- guard, green on base as designed: `..._ofARowDeeperThanTheRestoreCeiling_...`, `..._whosePagesReportALowerPageThanRequested_...`, `..._throughEmptyPagesThatReadAhead_...`

**Deviations from the plan:** none.

**Files touched outside the plan:** none.

**For the reviewers:** the `RefreshRow` helper in `SectionVMRefreshDepthTest` holds the call counters (`callsAfterWalk`, `pagesAfterWalk`); the "too many calls" `check` in its stub is the plan's throw-after-20-calls guard. Guard rows were not mutation-tested against their stated mutations.

**Blocked:** none.

**Fix round 1** (triage items 2 and 3, tests only):
- red: `refresh_ofADeepRowWhosePagesReadAhead_stopsAtTheRestoreCeiling` with `coerceAtMost(MAX_RESTORED_PAGES)` removed from `restoreLoadedDepth` (restored afterwards) - fails, page 12 requested after 2,4,6,8,10; it is the only failure of the 12 in `SectionVMRefreshDepthTest`. Green with the coercion.
- `ShowAllVMTest.retry_ofAGridWithOverlappingPages_publishesEachIdOnce`: `mapped` is a `CopyOnWriteArrayList`, so polling it while the mapper appends on `Dispatchers.Default` cannot throw `ConcurrentModificationException`.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

## Readiness 2: carousel-row-duplicate-cards

Verdict: **READY**

Worktree HEAD ca58fa12 (base 901d820d plus the spec commit; spec edited and plan untracked in the working tree). Plan and test design checked against the code at that HEAD.

### Round 1 fixes

| # | Status | Where |
|---|---|---|
| 1 | Fixed | Plan Task 1 table row `..._whosePagesReportALowerPageThanRequested_...`: pages 2..10 once each after the walk, counted through `pagesAfterWalk`; test-design row says the same. Dropping `maxOf` gives page 2 nine times, caught. |
| 2 | Fixed | Test-design and plan row `..._ofARowDeeperThanTheRestoreCeiling_...` name mutations the asserts catch: counter and coercion lost (`callsAfterWalk` 12), counter bound below page 10 (page 10 missing). |
| 3 | Fixed | Plan Task 1 **Covers** names AC-7 as device-only (Manual 1). |

### Checks

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

### Citation sample

| Citation | Resolves to |
|---|---|
| `VM:191,199,202-203,208,215,223` | `restoreLoadedDepth`; `toMutableList`; ceiling comment; `interactor.loadPage`; `items += next.items`; `return PaginatedResponse(...)` |
| `VM:173-189`, `VM:269`, `VM:340-346`, `VM:379,399`, `VM:38` | KDoc; `currentPage = response.pagination.current`; `mapItems`; `MAX_EMPTY_PAGE_CHAIN = 3`, `MAX_RESTORED_PAGES = 10`; `Dispatchers.Default` |
| `RDT:32,66,116,154,189` | four existing tests; `pageOf(vararg ids, current, total)` |
| `FIX:57,76,98,119,124,132,141` | `SectionVMTestFixture`, `servesFirstPageFromTheNetwork`, `createVM`, `paginator`, `config`, `mapperFor`, `maxEmptyPageChainUnderTest = 3` |
| `SAT:191-195,267,274,289,291` | `withTimeout` polling; `servesFirstPageFromTheNetwork`, `createVM`, `videoItem`, `item` |
| `ShowAllVM.kt:32,76,77,123-125` | `: ContentListPagingVM<ShowAllViewState>(`; `LoadMore`, `RetryClicked`; `Content(mapItems(...))` |
| `Models.kt:47`, `SectionState.kt:11-14`, `ContentListInteractor.kt:153` | `val id: Int`; `Content(items, ...)`; `putIfAbsent(item.id, item)` |

### Non-blocking notes for the implementer

- Test-design Finding says every AC-4 row asserts exact pages; the page-zero row asserts only `callsAfterWalk <= 10`. Its mutation (counter removed) is still caught.

