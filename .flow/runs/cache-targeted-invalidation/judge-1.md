# Gate - cache-targeted-invalidation - round 1

**Decision:** CONCERNS
**Head:** 8329d402   **Base:** 40762767

## Done as asked

`git diff 40762767...HEAD -- app/src/main` touches only the three files in spec Scope:

| Spec item | Diff |
|---|---|
| M1 save | `PlayerInteractor.saveWatchingTime` writes the API only; new `markItemDetailsStale(id)` delegates to `ItemDetailsRepository.markStale` |
| M1 mark points, T1 | `PlayerVM`: `detailsStaleOwed` set after a successful drain save, `homeRefreshOwed` in `markContentChanged`; `flushOwedInvalidation` clears both before suspending; exit flushes in `closeJob` after `awaitPendingMutations` and `backgroundFlushJob?.join()`, before `router.back`; background flush in `withContext(NonCancellable)` (triage-1 #1) |
| E1 | `CachedFeed`: four maps become one `ConcurrentHashMap<String, KeyRecord>` with `holders`; `holding` wraps `load`, `markStale`, `invalidate`, `peek`, `peekAll`, `putAll`; release removes at zero in `finally`; `supersedeNamespace` keeps only `namespaceEpoch` |

Coordinator, DI, `PlayerVM` constructor and UI unchanged, as Out of scope requires. Worktree clean; `grep -rn PUBERBASE app/src` empty; no `Co-Authored-By` in the 11 commits.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PlayerInteractorTest.saveWatchingTime_success_doesNotMarkItemDetailsStale`, `markItemDetailsStale_marksTheRepositoryEntryStale` | diff read; check.full green (below) | PASS |
| AC-2 | `PlayerVMInvalidationTest.playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing`; device | qa-report R1 and R2; verify-round2-device.md AC-2 (shim build) | PASS |
| AC-3 | `backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack`, `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing`, `backPressed_markFailure_stillReturnsResult`; device | verify-round2-device.md AC-3: mark 12:16:07.748 before routerBack .759 | PASS |
| AC-4 | `onBackground_marksOnlyAfterTheHeldSaveDrains`, `backPressed_afterBackgroundFlush_withNoNewSuccessfulSave_marksAndRefreshesNothingMore`, `clearedDuringBackgroundFlush_stillMarksAndRefreshes`; device | verify-round2-device.md AC-4 and triage #1 runs 1-4 | PASS |
| AC-5 | `watchedInFront_requestsNoRefreshUntilExit`, `watchedInFront_backgroundRequestsOneRefresh`; device | qa-report R1 AC-5 | PASS |
| AC-6 | Emulator observation (M1/M2); guard `DetailsVMCachedLoadTest.afterMarkItemDetailsStale_detailsOpenRevalidatesAndShowsNewProgress` | qa-report R1: outcome not observable, account payload carries no per-episode progress (marktime `ApiError.Serialization`, on base too); mechanism (exit mark, revalidation) seen | NOT CHECKED |
| AC-7 | `CachedFeedTest` `afterEveryOperationSettles_*`, `whileAnOperationIsInFlight_*`, `aFailedOrCancelledLoad_*` | qa-report R1; check.full green | PASS |
| AC-8 | `CachedFeedTest` removal/recreation cases | CachedFeedTest diff has 0 removed lines; check.full green | PASS |
| AC-9 | Named suites unchanged except PlayerInteractorTest (AC-1 contract) | diff of HistoryVM/PlayerVM flow tests empty; check.full green | PASS |
| AC-10 | Emulator before/after, 29 min, shim on both builds | verify-device.md: in front base 58/59/3, head 0/0/2; exit 1 mark, 1 refresh; routerBack median 905 vs 898 ms | PASS |

check.full, run by the judge at 8329d402: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL, 2307 tests, 0 failures, 0 errors.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Owed mark and refresh lost when the VM is cleared during the background flush | blind F1 (triage-1 #1) | fixed | 2eb4cab3, a0fa2468; `clearedDuringBackgroundFlush_stillMarksAndRefreshes`; verify-round2-device.md runs 1-4 |
| AC-6 outcome has no test and no device check | tests-1 F1, codex-1 (triage-1 #2) | fixed in part | 2eb4cab3 guard test; device check stays NOT CHECKED (Concerns 1) |
| P0 row labelled red, never-started half cannot fail | tests-1 F2 (triage-1 #3) | fixed | 2eb4cab3 drops the half and asserts `router.back`; spec relabelled 525d68cd |
| HistoryVMTest wall-clock flake | tests-1 F3 (triage-1 #4) | rejected, accepted | branch does not touch `ui/feature/history` (diff empty); logged in `.flow/runs/unit-suite-flaky/state.md:35`; judge run green |
| AC-6 Details test labelled red, is a guard | tests-2 F1 major (triage-2 #1) | fixed | a5f79b4f |
| AC-6 row describes a different test | tests-2 F2 (triage-2 #2) | fixed | a5f79b4f; test at `DetailsVMCachedLoadTest.kt:231` calls `repository.markStale(42)`, matches row |
| Cleared-VM row says `onCleared` | tests-2 F3 nit (triage-2 #3) | fixed | a5f79b4f |
| `PUBERBASE` logs in main sources | codex-3 P1 | rejected, accepted | worktree clean, grep empty at 8329d402 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | In front 0 marks / 0 refreshes against 58 / 59 on base (AC-10); exit latency unchanged; key records bounded to keys in flight (AC-7) |
| Reliability | Failed mark swallowed by `runCatchingCancellable`, exit not blocked; failed saves owe nothing; process kill in front falls back to the 10 min TTL (owner Q1). Background flush outlives a cleared VM for at most one HTTP timeout (review-edge-2 unproven) |
| Maintainability | Follows AGENTS.md conventions; `@VisibleForTesting internal` seam only; no dead code (`advanceKey` callers updated, old maps gone) |
| Security / privacy | `tools/mobile-evidence-audit.sh docs/superpowers/evidence` passed (68 files); evidence holds markers only |

## Manual checks for the owner

AC-6 needs an account whose item payload carries per-episode progress, or the `resume-progress-anomaly` fix first:

1. Install the head dev APK with `tools/android-apk-install-preserve` on a leased emulator.
2. From Home, play a series episode (no Details below); let at least one 30 s save pass; press Back.
3. Within 10 min open the same title's Details with the D-pad.
4. Expected: the resume line and episode bar show the position just left, not the one from before the session.

## Must fix

none

## Concerns for the owner

1. AC-6 is NOT CHECKED: on the test account every `watching/marktime` response fails to decode, so a clean build (base or head) never counts a save as successful and never marks. All device evidence for AC-2..AC-4 and AC-10 comes from a build with the qa shim that ignores `ApiError.Serialization`. The change is unobservable on this account until `resume-progress-anomaly` lands.
