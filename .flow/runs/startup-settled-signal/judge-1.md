# Gate - startup-settled-signal - round 1

**Decision:** PASS
**Head:** 904573a3   **Base:** 7bcbfd83

## Done as asked

The diff adds `core/startup/StartupSettled.kt` (process-wide latch, Koin single in `domain/di/modules.kt:83`), settles it from `MainActivity.kt:78-93` (restored or splash `onGone`, 5 s fallback), puts the four consumers behind `awaitSlot` in their own `launch` (MainVM TV home / device info / watch-state, UpdatePromptVM auto check) and the prefetcher behind `await` before its permit, removes `StartupSyncDelay`, and makes `HomeVM.publishSections` leading-edge conflated under `publishMutex` with the epoch check kept. `TvHomeSyncCoordinator` is unchanged (Out of scope held). Beyond the spec: the auto result applies only while `Hidden` and a 500 ms Update-press guard (`UpdatePromptVM.kt:27`, `:175-187`); both fix regressions this change opened (triage-1 T2, T3), recorded in the plan's Implementation record.

## Traceability

Every test named below exists in `app/src/test` or `app/src/androidTest` (grep at head, 0 missing).

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `StartupSettledTest` (`isNotSettledBeforeAnyActivityIsCreated`, `splashGoneSettlesTheLatch`, `aFreshActivityAfterTheSplashIsGoneStartsNoFallback`); `StartupSplashHostTest.splashReportsGoneOnceAfterTheFade` | qa-report round 1; device evidence: settled by splash 10 of 10 | PASS |
| AC-2 | `restoredActivitySettlesAtOnce`; manual 3 | red line in plan Implementation record (T1); evidence manual 3, revealed 278 ms | PASS |
| AC-3 | `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone`, `aSecondFreshActivityDoesNotPostponeTheFallback` | qa-report round 1 | PASS |
| AC-4 | three `...BeforeStartupSettles` tests, `MainVMStartupSlotsTest.onStart_runsDeferredWorkInSlotOrderAfterStartupSettles`, guards | qa-report round 1; `git grep StartupSyncDelay -- app` empty at head | PASS |
| AC-5 | `UpdatePromptVMTest` slot, manual-path guard, T2/T3 tests | qa-report round 2 (2237 tests green) | PASS |
| AC-6 | `DetailsPrefetcherTest.noWarmStartsBeforeStartupSettles`, `aWarmHeldForStartupProceedsOnceItSettles`, wiring guard | qa-report round 1 | PASS |
| AC-7 | `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared`, `onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot` | qa-report round 1 | PASS |
| AC-8 | `onStartAfterStartupSettled_countsSlotsFromMainStart` | qa-report round 1 | PASS |
| AC-9 | `HomeVMIncrementalPublishTest` fold, leading-edge and serialization rows | qa-report round 1; publishes median 8 -> 5 on device | PASS |
| AC-10 | the three epoch tests | qa-report round 1 | PASS |
| AC-11 | Perf track, 9 vs 8 cold starts | evidence Summary: publishes 5 vs 8, revealed median 2718 vs 2857.5 (gate 3241.5), cap at most 7 of 9 vs 8 of 8 (inferred, spec Q4), key_lat p95 1088 vs 1060 (reported only) | PASS |
| AC-12 | `UpdatePromptOverlayFocusTest` 8 of 8 on emulator-5554 at head; slot unit test | evidence Round 2; the AC's no-newer-release branch | PASS (device flow NOT CHECKED, section Manual) |
| AC-13 | check.full | qa-report round 2, BUILD SUCCESSFUL at b846ea7d; 904573a3 changes only the evidence file, qa-report is newer, so not re-run | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| T1 restored P0 red row never seen red | review-tests-1 F1 (major) | fixed | b846ea7d, red line in plan Implementation record |
| T2 auto result overwrites a manual flow | review-codex-1 | fixed | 17f8710a, `automaticResult_doesNotReplaceAManualCheckInProgress` |
| T3 prompt takes a confirm press meant for Home | review-edge-1 F1 | fixed | 17f8710a, `updateClicked_isIgnored_whileTheAutomaticPromptIsInItsInputGuard` |
| T4 `delay(200)` orders a race | review-tests-1 F3, review-blind-1 F2 | fixed | 059cbe5e (`awaitPublishRequestsBeyond`) |
| T5 prefetcher order test cannot fail | review-tests-1 F2 | fixed (dropped, recorded) | b846ea7d; state.md Decision |
| T6 settle-twice test cannot fail | review-blind-1 F3 | fixed | 0d18f039 |
| T7 warm relaunch skips the settle wait | review-blind-1 F1 | rejected, accepted | Q1 answer "once per process"; KDoc `StartupSettled.kt:21-25` says first start |
| T8-T10 | blind, edge unproven | rejected, accepted | unproven; T8 covered by the fallback (AC-3); T10 test-only hook |
| U1 manual-path guard not pinned by a test | review-tests-2 F1 | accepted minor | guard set only in `showAutomaticPrompt` (`UpdatePromptVM.kt:175`) |
| U2-U4, U6 guard anchor, Back, dropped auto result, guard after dismiss | review-edge-2, review-tests-2 unproven | accepted | unproven; Concerns |
| U5 guard not gesture-level | review-codex-2 | rejected, accepted | `TvSafeButton.kt:120` `onTvSelectClick` clicks on KeyUp only after its own KeyDown |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Fewer Home publishes (8 -> 5 median); background work after the splash, 500 ms apart; no new main-thread work beyond a counter per publish request |
| Reliability | Deferred work stays in `viewModelScope` (AC-7); fallback bounds a missing `onGone`; a cancelled publish records nothing (`aCancelledFollowUpLeavesTheNextQueuedRequestToPublish`); restored start settles at once |
| Maintainability | Koin DSL, JUnit 5, real latch in tests per conventions; `StartupSyncDelay` removed with its KDoc; three `@VisibleForTesting` hooks in `HomeVM.kt:550-564`, each used by a test; `docs/architecture/` has no reference to the changed symbols |
| Security / privacy | `tools/mobile-evidence-audit.sh` on `docs/superpowers/evidence` at head: passed (59 files); no account data in the evidence; `SettledMeasure` absent from `app` |
| TV | Focus path changes only through the update prompt timing (AC-12); key delivery after reveal observed on device, focus position not observed |

## Manual checks for the owner

| # | Item | Steps |
|---|------|-------|
| 1 | AC-12 device flow (needs a newer release) | On a build older than the latest release: force-stop, cold start; the prompt opens about 1 s after the splash fades, focus on Update; a press in the first 500 ms does nothing, a later one starts the download; D-pad stays inside; Back closes it and focus returns to Home |
| 2 | Home focus right after the reveal (test-design manual 1) | Cold start on the TV; as the splash fades press DPAD_RIGHT x3, DPAD_DOWN x2; focus moves on each press, no stall |

## Must fix

none

## Concerns for the owner

none
