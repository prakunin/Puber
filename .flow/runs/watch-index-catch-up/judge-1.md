# Gate - watch-index-catch-up - round 1

**Decision:** PASS
**Head:** 6d49f6e5   **Base:** e4c3b5aa

## Done as asked

The diff (`git diff e4c3b5aa...HEAD`) matches spec Design. `WatchStateSyncInteractor.catchUp`/`requestCatchUp` use their own `catchUpMutex`, read from page 1 down to the mark at `perpage=50` and write the entries and the mark in one `recordCatchUpPage` transaction. `refreshSeriesThatLeftTheList` makes at most 10 Details refreshes. Windows are owned by kind (`SyncWindowKind`). Migration 4 -> 5 is `HistoryPageSizeMigration`. The triggers are MainVM start/resume/Movies-Series tab entry and `PlayerVM.exitPlayer` -> `PlayerInteractor.requestWatchStateCatchUp`. Out of scope is untouched: `WatchCompletionPolicy`, HistoryVM and `collection-watched-mark-lag`.

Differences from the spec, each traced to a triage decision:

| Change | Source |
|---|---|
| Page cap `MAX_CATCH_UP_PAGES = 1000`; the mark stays put at the cap (AC-3 needs 30+ pages, tested at 310) | triage-1 #5 |
| Stop rule is `any { <= mark }` | triage-1 #4 |
| 1 s monotonic catch-up emission deadline | triage-4, triage-5, triage-6 |

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | SI `catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour` (`WatchStateSyncInteractorTest.kt:928`) and siblings | qa-report round 1; test results at 15:44 after 210f055e, 0 `<failure` | PASS |
| AC-2 | SI `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks` (`:1004`, virtual-time assertion from triage-1 #7) and a box observation | evidence Round 2: p15, p1, p16 | PASS |
| AC-3 | SI `catchUp_readsPastTheWalksPageBudget` (`:1111`), `..WhenAPageFails`; WD `raiseHistoryNewestSeenNeverLowersTheMark` on the box | qa-report round 1 | PASS |
| AC-4 | SI `catchUp_asksDetailsForASeriesThatLeftTheWatchingList` (`:1133`) and its P1 rows; ID `refreshRecordsTheFreshVerdictInTheWatchStateIndex` | unit only, which is what the AC specifies | PASS |
| AC-5 | MV `tabSelected_requestsAFlooredCatchUpForMoviesAndSeries` (`MainVMWatchStateSyncTest.kt:210`), onStart/resumed rows; SI `catchUp_withoutTheFloorRunsInsideTheMinute` (`:1257`); PV `backPressed_requestsAWatchStateCatchUpAfterTheFlushBeforeBack` (`PlayerVMInvalidationTest.kt:114`) | qa-report round 1; floor seen on the box (evidence Round 2, AC-7) | PASS |
| AC-6 | WR `recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune` (`WatchStateRepositoryTest.kt:399`); SI `catchUp_throwsAwayWhatItWroteWhenTheSessionEndedMidRun` (`:1348`) | qa-report round 1 | PASS |
| AC-7 | SI `catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark` (`:1376`); WR `syncWindow_announcesNothingForACatchUpThatChangedNothing` (`:367`); box observation | evidence Round 2 | PASS |
| AC-8 | Manual 1 (`history order` debug line) | evidence: 157 lines with ordered=true, none with ordered=false, perpage=50 | PASS |
| AC-9 | Manual 2 | needs an account mutation on another client | NOT CHECKED |
| AC-10 | check.full | review-tests-7: green at 210f055e (2442 tests). Only an evidence commit follows; qa-report is not older than HEAD, so not re-run. Removed test lines are only the `getHistoryData(page)` -> `(page, 50)` signature change | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Nested catch-up emission held until the walk ends (major) | edge-1 F1, state-path-1 F1, codex-1 | fixed | 1bafea56; follow-up regressions fixed in c55c5d3c, 9910c26e, c4519fa4, aa19fb51 |
| AC-2 test passes with the walk's mutex (major) | tests-1 F1 | fixed | a02e3e38 |
| P0 red rows green on base (major) | tests-1 F2 | fixed | 559eae4f (relabelled guard, mutation runs recorded) |
| Default `SystemClock` kills the DI-built collector in JVM tests (codex major) | codex-6, edge-6, state-path-6 | fixed | 210f055e; guard test `repositoryFromTheModuleAdvancesVersionWhenTheSnapshotChanges` |
| Catch-up across logout + re-login wipes the new session (minor) | codex-2 | deferred | run `.flow/runs/catchup-session-wipe-race` (stage idea) |
| Cold start fetches lists and page 1 twice (minor) | blind-1 F4 | rejected, accepted | triage-1 #6: about 3 requests once per stale cold start, inside the startup slot |
| Intermediate snapshot consumes the pending flag (codex) | codex-3 | rejected, accepted | superseded by the deadline (c4519fa4); worst case is a delay, not a loss |
| Remaining minors (detailsCheckedIds, stop rule, page cap, kind tests, enum KDoc, wall clock) | triage-1..5 | fixed | a02e3e38, c55c5d3c, 9910c26e, c4519fa4, aa19fb51 (commits exist) |

Round 7 ran without codex (usage limit; owner decision 2026-11-23). Edge, tests and state-path reported no findings.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Floored triggers cost 2 list requests, history page 1 and at most 10 Details refreshes, at most once a minute; player exit is unfloored but goes through `catchUpMutex` with one rerun. `logHistoryOrder` allocates one list per page and only logs through the debug Timber tree (`PuberApp.kt:105`). |
| Reliability | A failed page writes nothing and leaves the mark unchanged; cancellation closes windows in `finally`; `invalidate()` clears `lastCatchUpAt` and `detailsCheckedIds`; the walk cursor is rescaled by migration 4 -> 5 (instrumented test on the box). Unproven: an entry with null `updated` reads as 0 and would stop the read early (edge-2). No such entry appeared in the 157 ordered pages. |
| Maintainability | Follows AGENTS.md conventions; the old `openSyncWindows`/`syncChangeHeld` are gone with no dead code left; `@Suppress("ReturnCount")`/`("TooManyFunctions")` are local; detekt and lint pass. |
| Security / privacy | The debug log line carries no ids, titles or timestamps; `tools/mobile-evidence-audit.sh` on `docs/superpowers/evidence` reports `passed` (re-run here). |
| TV | No UI, focus or layout change. |

## Manual checks for the owner

1. AC-9 (box `192.168.1.110:5555`, `com.kino.puber.stage`, worktree build at 210f055e or later):
   1. Settings: turn "Hide watched" on. On the Series tab, note a series X that still has unwatched episodes.
   2. Press `KEYCODE_HOME`.
   3. On another client, watch or mark X's remaining episodes.
   4. Resume the app, open Movies, then Series, and wait up to 60 s.
   5. Expected: X is gone without opening its Details.
   6. Repeat with a cold start (`am force-stop`, then launch) on a second series Y.

## Must fix

none

## Concerns for the owner

none
