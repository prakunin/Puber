# Coalesce D-pad hold seeks in the player - plan

**Spec:** `docs/superpowers/specs/2026-11-28-player-dpad-seek-coalesce.md`
**Goal:** A held Left/Right makes at most one `seekTo` per 250 ms window plus a trailing seek to the final target, while the indicator, thumb, saves and stream switches follow the pending target.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh && bash tools/tests/test-itest-tooling.sh`.
- All commands run in `/home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce` through `./tools/agentw`, never `./gradlew`; prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched tests: `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.*'` (written `player-vm tests` below).
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: `app/src/main/java/com/kino/puber/ui/feature/player/vm/` is written `vm/`, `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/` is written `test/vm/`. Line numbers are at base `8c3117ea` and shift as tasks land; find symbols by name.
- Out of scope (spec Scope): `SeekParameters`, scrubbing mode, `PlaybackController`, Compose `component/`, DI, resources.
- detekt: `LoopWithTooManyJumpStatements` allows one jump (`config/detekt/detekt.yml:677-679`), so the throttle loop has a single `break`.

### Fixture for `test/vm/PlayerVMSeekCoalesceTest.kt` (test-design.md, "Fixture for the new suite")

Shape of `test/vm/PlayerVMSeekBurstTest.kt:25-31`: extends `PlayerVMTestFixture`, companion `MainDispatcherExtension` with `@RegisterExtension`, each case `runTest(mainDispatcher.dispatcher.scheduler)` and `vm.testCancelScope()` at the end. `P = 100_000` ms; ladder 10, 10, 20, 30, 60, 60 s; duration is the fixture's `2_400_000` (`test/vm/PlayerVMTestFixture.kt:93`).

| Helper | Behaviour |
|--------|-----------|
| `seeks: MutableList<Pair<Long, Long>>` | `every { playbackController.seekTo(any()) }` answers: record `(testScheduler.currentTime - t0, target)`, set `position = target`, call `callbackSlot.captured.onSeekPerformed()` (`callbackSlot` at `test/vm/PlayerVMTestFixture.kt:77`). `t0` is the scheduler time of the first press, so "t = 250" in the rows is 250 ms after the first press |
| `playingAt(from)` | `position = from`; `every { playbackController.currentPosition } answers { position }` |
| `press(action, gapMs = 50)` | `advanceTimeBy(gapMs)` then `vm.onAction(action)`, no `runCurrent` in between |
| `mapper.formatTime` | `every { mapper.formatTime(any()) } answers { "t${firstArg<Long>()}" }` (overrides `test/vm/PlayerVMTestFixture.kt:123`) |

---

## Tasks

### Task 1: Move the step-ladder reset from the wall clock to the seek burst

**Covers:** R7 (supports AC-1, AC-7)
**Files:**
- Modify: `vm/SeekHandler.kt` - drop `resetTimeoutMs`, `lastSeekTime` and `System.currentTimeMillis()` (`SeekHandler.kt:17-38`); keep `DEFAULT_SEEK_RESET_TIMEOUT_MS` (`SeekHandler.kt:7`) and the `steps` parameter.
- Modify: `vm/PlayerVM.kt` - in `seekForward` and `seekBackward` (`PlayerVM.kt:733-749`), before `startSeekBurst()`: `if (seekBurstJob?.isActive != true) seekHandler.reset()` (`seekBurstJob` at `PlayerVM.kt:154`, `startSeekBurst` at `PlayerVM.kt:728-731`, `seekHandler` at `PlayerVM.kt:200`).
- Test: `test/vm/SeekHandlerTest.kt`, new `test/vm/PlayerVMSeekCoalesceTest.kt` (fixture above).

**Consumes:** `seekBurstJob`, `DEFAULT_SEEK_RESET_TIMEOUT_MS`.
**Produces:** `SeekHandler(steps: IntArray = DEFAULT_SEEK_STEPS_SECONDS)`; `nextStep(): Int` returns `steps[stepIndex]`, then advances the index while it is below `steps.size - 1`; `reset()` sets the index to 0. The fixture file later tasks extend.

- [x] Write `PlayerVMSeekCoalesceTest` with the fixture and `theStepLadderRestartsAfterTheBurstEnds` (three presses, `advanceTimeBy(1_600)`, one press: `currentPosition` = previous + 10 s); run it on the unchanged code and record the red run (it steps 30 s).
- [x] Add `theStepLadderKeepsClimbingInsideTheBurst` (presses 1 400 ms apart climb 10, 10, 20 s).
- [x] Change `SeekHandler` and the two VM call sites as above.
- [x] `SeekHandlerTest`: delete `callAfterTimeout_resetsToFirstStep` (`SeekHandlerTest.kt:42-53`); `maxStep_clampsAtLastValue` and `reset_resetsIndexAndTime` construct without `resetTimeoutMs` (`SeekHandlerTest.kt:34, 57`); `firstCall_returnsFirstStep` and `rapidConsecutiveCalls_incrementsStep` stay, with the timeout comments removed.
- [x] Run the fast gate and the player-vm tests; commit "Reset the seek step ladder when the seek burst ends instead of on the wall clock".

**Done when:** player-vm tests green, including both new cases; `rg -n currentTimeMillis app/src/main/java/com/kino/puber/ui/feature/player/vm/SeekHandler.kt` finds nothing.

### Task 2: Throttle D-pad seeks to one per window with a trailing seek

**Covers:** AC-1, AC-2, AC-3, AC-6
**Files:**
- Modify: `vm/PlayerVM.kt`
  - Fields next to `seekBurstJob` (`PlayerVM.kt:154`): `private var pendingSeekTarget: Long? = null`, `private var seekThrottleJob: Job? = null`. Companion (`PlayerVM.kt:2066-2078`): `const val SEEK_THROTTLE_WINDOW_MS = 250L`.
  - Replace `seekForward` / `seekBackward` (`PlayerVM.kt:733-749`) with `seekBy(isForward: Boolean)`: the Task 1 reset, `startSeekBurst()`, `val step = seekHandler.nextStep()`, `val base = pendingSeekTarget ?: playbackController.currentPosition`, target `base ± step * 1000L` clamped by `coerceAtMost(playbackController.duration)` forward and `coerceAtLeast(0)` backward, `requestSeek(target)`, `showSeekIndicator(isForward, step, target)`. No separate `updateContent`.
  - Routing `PlayerVM.kt:659-660`: `SeekForward -> seekBy(isForward = true)`, `SeekBackward -> seekBy(isForward = false)`.
  - `requestSeek(target: Long)`: if `seekThrottleJob?.isActive == true`, set `pendingSeekTarget = target` and return. Otherwise `playbackController.seekTo(target)` and `seekThrottleJob = launch { while (isActive) { delay(SEEK_THROTTLE_WINDOW_MS); val next = pendingSeekTarget ?: break; pendingSeekTarget = null; playbackController.seekTo(next) } }`.
  - `showSeekIndicator` (`PlayerVM.kt:751-767`): the same `updateContent` also sets `currentPosition = targetPosition`.
  - Position tick (`PlayerVM.kt:1587`): `currentPosition = pendingSeekTarget ?: playbackController.currentPosition`; `duration`, `bufferedPosition`, `debugInfo` unchanged.
- Test: `test/vm/PlayerVMSeekCoalesceTest.kt`.

**Consumes:** Task 1 `SeekHandler`; `PlaybackControl.seekTo` (interface in `vm/PlaybackController.kt:146`).
**Produces:** `pendingSeekTarget`, `seekThrottleJob`, `SEEK_THROTTLE_WINDOW_MS`, `seekBy`, `requestSeek` for Task 3.

`thePositionTickKeepsThePendingTarget` timing. The tick loop starts in `startProgressSync` (`PlayerVM.kt:386`, loop at `PlayerVM.kt:1572-1576`) and fires every `POSITION_UPDATE_INTERVAL_MS` = 500 ms (`PlayerVM.kt:2071`) after that.

| Step | Scheduler time (tS = `currentTime` right after `startedVM()`; `runCurrent()`) | Action |
|------|------|--------|
| 1 | tS | `playingAt(P)`, controls visible |
| 2 | tS + 400 | `advanceTimeBy(400)`, `vm.onAction(SeekForward)`: `seekTo(P + 10_000)`, window open to tS + 650 |
| 3 | tS + 450 | `press(SeekForward)`: target `P + 20_000`, pending on Task 2 code, sought on Task 1 code |
| 4 | tS + 450 | `position = P + 10_000` (the player has not reached the target) |
| 5 | tS + 550 | `advanceTimeBy(100)`; `runCurrent()`: the tick at tS + 500 runs inside the window |
| 6 | tS + 550 | assert `currentPosition == P + 20_000`. Task 1 code: the tick writes `P + 10_000`, red |

- [x] Write `aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget` and `aLongHoldKeepsAtLeastOneWindowBetweenSeeks` (test-design.md, AC-2 rows) and `thePositionTickKeepsThePendingTarget` (timing table above); run the three on the Task 1 code and record the red run (six and twenty `seekTo` calls; the tick writes `P + 10_000`).
- [x] Implement the changes above.
- [x] Add `everyPressInAHoldShowsTheCumulativeTarget`, `aHoldNearTheEndClampsTheTargetToTheDuration`, `aSingleBackwardPressSeeksOnceToZero` (test-design.md, AC-1, AC-3 rows).
- [x] `PlayerVMPlaybackFlowTest.seekForward_updatesCurrentPosition` and `seekBackward_updatesCurrentPosition` (`test/vm/PlayerVMPlaybackFlowTest.kt:403-416`) stay unchanged and green.
- [x] Run the fast gate and the player-vm tests; commit "Coalesce held D-pad seeks into one seek per 250 ms window plus a trailing seek".

**Done when:** player-vm tests green; the two AC-2 cases and `thePositionTickKeepsThePendingTarget` were red on the Task 1 commit.

### Task 3: Commit or drop the pending target at saves, stream switches, jumps and teardown

**Covers:** AC-4, AC-5, R4
**Files:**
- Modify: `vm/PlayerVM.kt`
  - `commitPendingSeek()`: `val target = pendingSeekTarget ?: return; pendingSeekTarget = null; playbackController.seekTo(target)`. `dropPendingSeek()`: `seekThrottleJob?.cancel(); seekThrottleJob = null; pendingSeekTarget = null`.
  - `commitPendingSeek()` right before each `playbackController.switchStream`: `PlayerVM.kt:456` (`switchStreamUrl`), `:476` (`switchToNextStreamCandidate`), `:528` (`refreshStreamLinksOnce`). `PlaybackController.switchStream` reads `player.currentPosition` (`vm/PlaybackController.kt:532`).
  - `saveCurrentPosition` (`PlayerVM.kt:1898-1901`): `timeSeconds = ((pendingSeekTarget ?: playbackController.currentPosition) / 1000).toInt()`; no seek.
  - `dropPendingSeek()` after the early-return guard and before the seek in `resumeFromSavedPosition` (`PlayerVM.kt:1465-1476`), at the start of `startFromBeginning` (`PlayerVM.kt:1484`), after the guards of `performSkipSegment` (`PlayerVM.kt:1860-1862`).
  - `dropPendingSeek()` in `resetMediaSession` (`PlayerVM.kt:1030`; reached from `switchEpisode` `:1058` after its save `:1056`, and `retryPlayback` `:1516`), in `exitPlayer` right after `saveCurrentPosition()` (`PlayerVM.kt:2040`), and in `onCleared` (`PlayerVM.kt:2054-2064`).
- Test: `test/vm/PlayerVMSeekCoalesceTest.kt`.

**Consumes:** Task 2 fields; `interactor.saveWatchingTime` (called at `PlayerVM.kt:1921`).
**Produces:** `commitPendingSeek()`, `dropPendingSeek()`.

- [x] Add, per test-design.md: `startOverDropsThePendingSeek`, `resumeDropsThePendingSeek` (resume dialog set up as `test/vm/PlayerVMOverlayAutoHideTest.kt:194-199`), `skipSegmentDropsThePendingSeek` (its own `seekTo` answer without `onSeekPerformed`), `backgroundSavesThePendingTarget` (`saveWatchingTime(42, 1, 120, 1)`), `backPressedSavesThePendingTarget`, `aQualitySwitchCommitsThePendingTargetFirst` (`SelectQuality(1)` as `test/vm/PlayerVMPlaybackFlowTest.kt:349`, qualities in the content state, `verifyOrder { seekTo(P + 20_000); switchStream(any(), any()) }`), `aPendingSeekDoesNotLandOnTheNextEpisode`.
- [x] Implement the changes above.
- [x] Run the fast gate and the player-vm tests; commit "Save, switch streams and jump from the pending seek target instead of letting it land late".

**Done when:** player-vm tests green, including the seven new cases.

### Task 4: Rewrite the seek-burst prompt tests for the coalesced call pattern

**Covers:** AC-7
**Files:**
- Test: `test/vm/PlayerVMSeekBurstTest.kt` - `playingAt` (`:36-46`): the `seekTo` answer sets `position` and calls `callbackSlot.captured.onSeekPerformed()`; the cases that override the answer (`:84, :103, :120`) call it too. `seek()` (`:48-52`) drops its hand `onSeekPerformed()`; `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` drops its hand call (`:181`). All six cases stay.
- Test: new case `aHeldKeyKeepsThePromptDownAndTheTrailingSeekStartsNoCountdown` in the same file: intro segment ahead and long enough to contain the hold's landing (for example `from = 20_000`, intro `30_000..2_000_000`), twenty presses 50 ms apart; `activeSkipSegment` null at every press and through 1 500 ms after the last, non-null one tick (`PlayerCountdowns.TICK_MS`) later, unchanged for two more ticks.
- Unchanged and run: `test/vm/PlayerVMSkipCountdownFitTest.kt`, `test/vm/PlayerVMPromptDismissalTest.kt:288` (`seekingAfterCancellingPutsTheNextEpisodeQuestionBack`).

**Consumes:** Tasks 1-3.
**Produces:** N/A: test-only.

- [x] Rewrite the helpers and add the case.
- [x] Run the full gate; commit "Drive the seek-burst prompt tests through real seeks and cover a held key".

**Done when:** the full gate exits 0. `PlayerPromptFocusAndKeysTest` (instrumented) is not run: `component/` is untouched.

### Task 5: Device verification (qa-device)

**Covers:** AC-8
**Files:** N/A: evidence only, written by `qa-device`.

**Consumes:** the worktree dev APK built after Task 4.

- [ ] `qa-device` runs Manual checks 1-4 of test-design.md on `192.168.1.110:5555` (`com.kino.puber.stage`, lease, `tools/android-apk-install-preserve`, `adb -s`).

**Done when:** evidence for Manual 1-4 is recorded with no "Must not happen" item observed.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- dd0d149b Reset the seek step ladder when the seek burst ends instead of on the wall clock
- 52d4c18e Coalesce held D-pad seeks into one seek per 250 ms window plus a trailing seek
- 63995b1f Save, switch streams and jump from the pending seek target instead of letting it land late
- (tip) Drive the seek-burst prompt tests through real seeks and cover a held key
- (review round 1) Land a pending seek target instead of exiting when playback ends

**Red run** (P0 `red` tests on the unchanged code):

- red: theStepLadderRestartsAfterTheBurstEnds - expected 150000 but was 170000 (30 s step on the wall clock)
- red: aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget - expected [110000, 290000] but was six seeks [110000, 120000, 140000, 170000, 230000, 290000]
- red: aLongHoldKeepsAtLeastOneWindowBetweenSeeks - seeks at 0 and 50 ms differ by less than 250 ms
- red: thePositionTickKeepsThePendingTarget - expected 120000 but was 110000
- guards (Task 3 and the rest) were written after the code; checked by mutation instead: removing the drop in `startFromBeginning`, `resetMediaSession` and `performSkipSegment`, the commit before `switchStreamUrl` and the pending read in `saveCurrentPosition` fails six of them (start over, skip segment, quality switch, next episode, background save, back save). `resumeDropsThePendingSeek` was not mutated.

**Review round 1** (triage-1.md #1, #2):

- red: aFilmEndingWithAPendingTargetSeeksThereInsteadOfExiting - `router.back(1001, ...)` was called, expected not to be
- green on base: aStreamFailureCommitsThePendingTargetBeforeTheNextCandidate, aLinkRefreshCommitsThePendingTargetBeforeTheNewStream - the commit sites already exist; the rows are guards
- Fix: `onPlaybackEnded` commits a pending target and returns before the end handling.

**Deviations from the plan:**

- `PlayerVMSeekCoalesceTest` runs each case through a `holdTest` helper that cancels the VM scope in a `finally`: a failing assertion otherwise leaves the position loop running and `runTest` never ends (out of memory in the test worker).
- `aQualitySwitchCommitsThePendingTargetFirst` does not put qualities into the content state: `selectQuality` only compares the selected index and the fixture's stream candidates answer.

**Files touched outside the plan:** none.

**For the reviewers:**

- `requestSeek` / throttle loop in `PlayerVM.kt`: the loop ends on the first empty window; `dropPendingSeek` cancels it, so a leading seek after a drop opens a fresh window.
- `SeekHandler.reset()` now runs from `seekBy` when no burst job is active; the burst job is the only timer.
- Periodic progress sync (`progressTracker` -> `saveCurrentPosition`) also reads the pending target.

**Blocked:** none.

## Readiness

Verdict: **READY**

Inputs: spec (worktree, uncommitted edit on `2e92c054`), plan (worktree, untracked), [test-design.md](test-design.md); code at base `8c3117ea`. Round 1: [readiness-1.md](readiness-1.md).

### Round 1 fix list

| # | Fix | Result |
|---|-----|--------|
| 1 | Plan Fixture table: `PlayerVMTestFixture.kt` citations | Fixed: `:77` `callbackSlot`, `:93` duration `2_400_000L`, `:123` `mapper.formatTime` all resolve |
| 2 | `thePositionTickKeepsThePendingTarget` timing and red check | Fixed: plan Task 2 timing table and test-design.md:53 put the presses at tS+400 and tS+450, the tick at tS+500 inside the window (closes tS+650); marked P0 `red` on the Task 1 commit, and Task 2 "Done when" requires it |

### Citations checked this round

| Citation | Result |
|----------|--------|
| `PlayerVM.kt:386` `startProgressSync()` in prepare | resolves |
| `PlayerVM.kt:1572-1576` `startPositionUpdates` loop, `delay` first | resolves; first tick 500 ms after start, as the table assumes |
| `PlayerVM.kt:1587` tick `currentPosition` write | resolves |
| `PlayerVM.kt:2071` `POSITION_UPDATE_INTERVAL_MS = 500L` | resolves |
| `PlayerVM.kt:1623-1627` `isAnythingShowingPosition` gates the write on `controlsVisible` | resolves; fixture content has `controlsVisible = true` (`PlayerVMTestFixture.kt:293`), so the tick writes and the red check is real |
| `PlayerVMTestFixture.kt:77`, `:93`, `:123` | resolve |

The round 1 citation sample (readiness-1.md, Citations checked) still holds: no code changed since `8c3117ea`.

### Coverage, build order, scope

Unchanged from readiness-1.md: every AC maps to a task and a Test design row, every task leaves the build green, P0 unit rows name a backticked test, `red` or `guard`, and the mutation caught; scope stays inside Out of scope; no open question blocks.

### Notes (non-blocking, carried)

- The spec Problem table still cites `PlaybackController.kt:550-558` for the switch re-seek; the read is at `:532`, which the Design section cites.
- `selectBufferPreset` / `toggleFastDns` (`PlayerVM.kt:984, :996`): reviewer to confirm the pending seek lands on the re-created player.
