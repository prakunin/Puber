# Coalesce D-pad hold seeks in the player instead of an exact seek per key repeat

Status: approved
Date: 2026-11-28
Slug: player-dpad-seek-coalesce
Kind: improvement
Size: M
Tracks: none

## Problem / Why

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (D-pad input and playback seeks on the TV box 192.168.1.110:5555).
Research: `research.md` (code map, Media3 seek findings, options A-C; not repeated here).

### Scope
Option A (research.md: 6. Options): coalesce `SeekForward` / `SeekBackward` in `PlayerVM`. `SeekParameters` and scrubbing mode stay untouched (Q1).

Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/vm/`.

Why it wastes work, confirmed by reading the code at the base commit: `seekForward` / `seekBackward` (`PlayerVM.kt:733-749`) call `playbackController.seekTo` on every key-down, auto-repeats included. `PlaybackController.seekTo` (`PlaybackController.kt:568-578`) then calls `ExoPlayer.seekTo` with exact seek parameters, `onSeekPerformed` (`PlayerVM.kt:1757-1764`, one `updateContent`) and `seekStallRecovery.start`. With the two `updateContent` calls in the VM, each repeat writes the state three times. The next step comes from `playbackController.currentPosition`, which right now only works because ExoPlayer masks the position to the last seek target.

Size M because D-pad input and playback are risk areas (`.flow/roles/analyst.md`). The change also reaches every place that reads or moves the position:

| Site | Needed behaviour with a pending target |
|------|----------------------------------------|
| `PlayerVM.kt:733-749` | Step from the pending target if there is one, else from the player position. Write position and indicator in one `updateContent` |
| `PlayerVM.kt:1584-1594` position tick | Do not overwrite `currentPosition` with the player position while a target is pending |
| `saveCurrentPosition` `PlayerVM.kt:1898` (callers `:701, :1056, :1499, :1567, :2040`) | Commit the pending seek first, or save the pending target |
| `switchStreamUrl` / `switchStream` callers `PlayerVM.kt:450-528` | Commit before the switch, because `PlaybackController.kt:550-558` re-seeks from the player position |
| Resume `:1476`, start-over `:1485`, skip-segment `:1868` | Drop the pending target, so it cannot land after the jump |
| `onCleared` `:2054`, episode switch `:1053` | Cancel the trailing job |


### Risks
| Risk | Where |
|------|-------|
| `SeekHandler` uses `System.currentTimeMillis()` (`SeekHandler.kt:25`) while the coalescer uses coroutine `delay`, so tests run the ladder and the throttle on different clocks; so the tests need an injectable clock | `SeekHandler.kt:17-38` |
| The video frame lags the indicator by up to one window during a hold | `PlayerVM.kt:733-749` |
| `onSeekPerformed` now fires per real seek, not per press; the 1 500 ms burst (`PlayerVM.kt:728-731`) must still start on every press so a countdown cannot start between presses | `PlayerVM.kt:1597-1600, 1757-1764` |
| Stall recovery re-arms only on real seeks; with a 250 ms window it is still re-armed far inside its 8 s check | `SeekStallRecovery.kt:18-22` |
| The gain is not measured as a number (Q3) | `research.md: 7. Risks` |

## Acceptance criteria

- **AC-1** Given playback at position P, When six Seek-forward actions arrive 50 ms apart in virtual time, Then the indicator and `currentPosition` after each action show the cumulative target (P+10, +20, +40, +70, +130, +190 s, clamped to duration), and `playbackController.seekTo` gets the first target immediately (leading seek). (unit)
- **AC-2** Given the burst from AC-1, When 250 ms pass after the last action, Then `seekTo` has been called no more than once per 250 ms window plus one trailing call, and the last call carries exactly the final target. (unit)
- **AC-3** Given a single Seek-backward action, When it is handled, Then `seekTo` is called once with the target clamped at 0, and no second call follows. (unit)
- **AC-4** Given a pending target, When resume, start-over or skip-segment seeks, Then no D-pad target reaches `seekTo` after that seek. (unit)
- **AC-5** Given a pending target, When a progress save (pause, background, sync, close) or a quality switch happens, Then the saved time and the position the switch keeps equal the pending target. (unit)
- **AC-6** Given a pending target with controls shown and playback running, When the position tick fires, Then `currentPosition` stays at the pending target. (unit)
- **AC-7** Given a skip or next-episode prompt that would apply, When the viewer holds Left/Right through it, Then the prompt stays hidden during the hold and its behaviour after the hold matches `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md` (Q2/Q3). Existing `PlayerVMSeekBurstTest` / `PlayerVMSkipCountdownFitTest` cases are rewritten for the coalesced call pattern, not dropped. (unit)
- **AC-8** Given an HLS title playing on the box, When Right is held for about 5 s and released, Then the seek bar and indicator follow every repeat, playback resumes at the released target time ±1 s, and no stall failover or prompt flicker shows. Repeated for Left and for the focused seek bar. (device observation)

## Out of scope

See Problem / Why (Scope).

## Design

Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/vm/` unless stated. Line numbers are at base `8c3117ea`.

### Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Throttle shape (Q2) | Leading-edge throttle with a trailing seek at the end of the window. A press with no open window calls `seekTo` at once and opens a 250 ms window. Presses inside the window only overwrite `pendingSeekTarget`. When the window ends, a pending target is sought and the window reopens; with nothing pending the window closes. | One job covers AC-1, AC-2 and AC-3. The final seek lands no more than 250 ms after the last press, and holding a key gives at most one seek per window. No key-up plumbing is needed. |
| Where the state lives | Two private fields in `PlayerVM`: `pendingSeekTarget: Long?` and `seekThrottleJob: Job?`, next to `seekBurstJob` (`PlayerVM.kt:154`), plus `SEEK_THROTTLE_WINDOW_MS = 250L` in the companion (`PlayerVM.kt:2066`). | Every site that has to commit or drop the target is a private VM function. A separate class would need the VM scope and its callbacks passed in. |
| Base of the next step | `pendingSeekTarget ?: playbackController.currentPosition` | After a real seek, ExoPlayer masks its position to that seek's target, so the steps add up with or without a pending target. |
| Ladder clock | `SeekHandler` drops its wall clock (`lastSeekTime`, `resetTimeoutMs`, `System.currentTimeMillis()` at `SeekHandler.kt:17-33`). `PlayerVM` calls `seekHandler.reset()` when `seekBurstJob` is not active, before it restarts the burst. | The burst window (`DEFAULT_SEEK_RESET_TIMEOUT_MS`, `PlayerVM.kt:728-731`) is already the 1 500 ms reset. With one dispatcher clock, the ladder and the throttle follow virtual time in tests (brief risk 1). |
| Progress save (AC-5) | `saveCurrentPosition` (`PlayerVM.kt:1898-1901`) stores `pendingSeekTarget ?: playbackController.currentPosition`. It does not seek. | A single expression covers all five callers (`:701, :1056, :1499, :1567, :2040`). The save keeps no side effect on the player. |
| Stream switch (AC-5) | `commitPendingSeek()` runs right before each `playbackController.switchStream` (`PlayerVM.kt:456, 476, 528`). | `PlaybackController.switchStream` reads `player.currentPosition` (`PlaybackController.kt:532`). After the commit, that value is the masked target. |
| Jumps and teardown (AC-4) | `dropPendingSeek()` runs at the start of `resumeFromSavedPosition` (`:1465`), `startFromBeginning` (`:1484`) and `performSkipSegment` (`:1860`). It also runs in `resetMediaSession` (`:1030`, which covers the episode switch `:1058` and retry `:1516`), in `exitPlayer` after its `saveCurrentPosition` (`:2040`) and in `onCleared` (`:2054`). | A pending target never lands after a jump, on a released player, or on the next episode. |
| Position tick (AC-6) | The tick writes `currentPosition = pendingSeekTarget ?: playbackController.currentPosition` (`PlayerVM.kt:1587`). Duration, buffer and debug info stay as they are. | The thumb does not jump back to the last real seek while a target is pending. |
| State writes per press | `seekForward` and `seekBackward` (`:733-749`) become one `seekBy(isForward)`. `showSeekIndicator` (`:751-767`) gains the `currentPosition` write, so a press makes one `updateContent`. | A press inside the window writes the state once instead of three times. |
| Prompt hold and reset | `startSeekBurst` still runs on every press. `onSeekPerformed` (`:1757-1764`) and `SeekStallRecovery.start` stay in `PlaybackController.seekTo` (`PlaybackController.kt:568-578`) and run once per real seek. | The burst (1 500 ms) gates the tick checks (`:1597`, `:1714`) well past the 250 ms trailing seek, so a countdown cannot start in between (AC-7). |
| Mechanism (Q1) | No `SeekParameters` and no scrubbing mode. | Brief Q1. |

### Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: no API or repository code changes. |
| ViewModel state and actions | `PlayerVM.kt`: the fields and constant above. `seekBy(isForward)`: if the burst is inactive, `seekHandler.reset()`; then `startSeekBurst()`, `nextStep()`, the target from the pending base clamped to `[0, duration]`, `requestSeek(target)` and `showSeekIndicator(..., targetPosition)`. `requestSeek`: with the window open, set the pending target; otherwise `seekTo` and launch `seekThrottleJob` (loop: `delay(SEEK_THROTTLE_WINDOW_MS)`; take the pending target or break; `seekTo`). `commitPendingSeek()`: read the target, drop it, `seekTo` if non-null. `dropPendingSeek()`: cancel the job and clear the target. Call sites as in Decisions. Routing at `:659-660` points to `seekBy`. |
| ViewModel helper | `SeekHandler.kt`: `nextStep()` advances up to the last step, and `reset()` returns to the first step. The constructor keeps `steps`. `DEFAULT_SEEK_RESET_TIMEOUT_MS` (`SeekHandler.kt:7`) stays as the burst length used at `PlayerVM.kt:730`. |
| Screen and DI module | N/A: `PlayerVM` keeps its constructor (`PlayerVM.kt:66-77`). |
| Compose content | N/A: `PlayerVideoSurface`, `PlayerProgressBar` and `PlayerTimedActionOverlay` keep emitting `SeekForward` and `SeekBackward` per key-down. |
| Resources | N/A: no strings or resources change. |
| Tests touched by the call pattern | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SeekHandlerTest.kt` (the timeout cases go, because the reset moves to the VM). `PlayerVMSeekBurstTest.kt`: its `seek()` helper calls `onSeekPerformed` by hand on every press (`:48-52`), which is now correct only for presses that make a real seek. `PlayerVMPlaybackFlowTest.kt:403-416` still holds. The instrumented `PlayerPromptFocusAndKeysTest` asserts actions only, so it is unaffected. The test-designer owns the rows. |

### Alternatives rejected

- `CLOSEST_SYNC` for D-pad seeks: the brief's Q1, and the seek count stays the same.
- Scrubbing mode during the burst: the brief's Q1, `@UnstableApi`, and the fixes in 1.11.1 are not in this project yet.
- A pure trailing debounce, with no leading seek: it breaks AC-1, and a single press would wait 250 ms.
- Committing the seek inside `saveCurrentPosition`: it adds a player side effect to a save, and a save followed by `release()` would seek for nothing.
- Injecting a clock into `PlayerVM` for `SeekHandler`: the burst job already measures the same 1 500 ms on the dispatcher.
- An extracted `SeekCoalescer` class: it would need the VM scope and the commit and drop hooks wired back in.

### Risks

| Risk | Where |
|------|-------|
| D-pad path: a drop or commit placed wrong loses the final target, so the indicator shows T while playback stays at an earlier seek. AC-2 and AC-8 cover it. | `PlayerVM.kt:733-767`, the new `requestSeek` and `seekThrottleJob` |
| Lifecycle: a throttle loop that outlives the media fires `seekTo`, and with it `onSeekPerformed`, on the next episode, wiping its prompts. The drop in `resetMediaSession` has to run before `startPreparingPlayback`. | `PlayerVM.kt:1030, 1053-1065, 1513-1517` |
| Ordering in tests: when a press and the window end fall on the same virtual instant, the outcome depends on scheduler order. The final call is the final target in both orders, but the test-designer must pin call counts with `advanceTimeBy` semantics in mind. | the `PlayerVMSeekBurstTest` rewrite |
| Prompt timing: `onSeekPerformed` now runs only on a real seek. A prompt that is up when the hold starts is cleared by the leading seek, and the burst blocks new prompts until 1 500 ms after the last press. | `PlayerVM.kt:1597-1600, 1714, 1757-1764` |
| Teardown inside `PlaybackController` that the VM cannot commit before: `disableAc3AndRetry` re-seeks from `player.currentPosition`. A pending target then lands after it, on the right time. | `PlaybackController.kt:641-652` |
| Ended: a trailing seek that arrives after `onPlaybackEnded` clears the next-episode countdown through `onSeekPerformed`, the same as a manual seek after the end today. | `PlayerVM.kt:1389-1419, 1757` |
| Stall recovery re-arms once per real seek, under 250 ms apart during a hold, against its 8 s check. | `SeekStallRecovery.kt:18-22, 36-43` |
| Performance: the gain is not measured (brief Q3). The main thread loses work and gains none. | `PlayerVM.kt:733-767` |

### Open questions

N/A: the brief's Q1 to Q3 are taken as recommended, and no AC needs a change.

## Test design

Inputs: [brief.md](brief.md) (AC-1 to AC-8), [design-architect.md](design-architect.md). Base `8c3117ea`.
Unit tests live in `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/` and extend `PlayerVMTestFixture`.

### Fixture for the new suite

New file `PlayerVMSeekCoalesceTest.kt`, same shape as `PlayerVMSeekBurstTest`: `MainDispatcherExtension`, `runTest(mainDispatcher.dispatcher.scheduler)`, `vm.testCancelScope()` at the end.

| Helper | Behaviour |
|--------|-----------|
| `seeks: MutableList<Pair<Long, Long>>` | `playbackController.seekTo(any())` answers: record `(testScheduler.currentTime, target)`, set `position = target` (ExoPlayer masking), call `callbackSlot.captured.onSeekPerformed()` (the controller does this per real seek, `PlaybackController.kt:568-578`) |
| `playingAt(from)` | `currentPosition` answers `position`; duration stays the fixture's 2 400 000 ms |
| `press(action, gapMs = 50)` | `advanceTimeBy(gapMs)` then `vm.onAction(action)`, no `runCurrent` in between, so a press always runs before a window end due at the same instant (design risk "Ordering in tests") |
| `mapper.formatTime` | answers `"t${firstArg<Long>()}"` so the indicator text carries the target |

Expected values below use P = 100 000 ms and the ladder 10, 10, 20, 30, 60, 60 s.

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|------|---|---|-------|------------|
| R1 | Final target lost: indicator shows T, playback stays at an earlier seek | 2 | 3 | 6 | AC-2 rows |
| R2 | Pending target lands after resume, start-over or skip-segment | 2 | 3 | 6 | AC-4 rows |
| R3 | Position tick drags the thumb back to the last real seek | 3 | 2 | 6 | AC-6 row |
| R4 | Throttle loop outlives the media and seeks (and clears prompts) on the next episode | 2 | 3 | 6 | `aPendingSeekDoesNotLandOnTheNextEpisode` |
| R5 | Progress save or exit stores the pre-hold time | 2 | 2 | 4 | AC-5 save rows |
| R6 | Quality switch re-seeks from the leading target | 2 | 2 | 4 | AC-5 switch row |
| R7 | Ladder reset moves from wall clock to the burst job and stops resetting | 2 | 2 | 4 | `theStepLadderRestartsAfterTheBurstEnds` |
| R8 | Press and window end on one virtual instant make call counts scheduler-dependent | 2 | 2 | 4 | `press` helper above |
| R9 | A countdown starts between a press and its trailing seek | 1 | 3 | 3 | AC-7 rows |
| R10 | Stall recovery fails over during a long hold | 1 | 3 | 3 | Manual 1 |
| R11 | Video frame lags the indicator by up to one window | 3 | 1 | 3 | Manual 1, observed only |
| R12 | Gain not measured as a number (brief Q3) | 3 | 1 | 3 | Accepted: AC-2 counts seeks |
| R13 | `disableAc3AndRetry` re-seeks before the pending target lands (`PlaybackController.kt:641-652`) | 1 | 2 | 2 | Accepted: the trailing seek still lands on the target |
| R14 | Trailing seek after `onPlaybackEnded` clears the next-episode countdown | 1 | 2 | 2 | Accepted: same as a manual seek after the end today |

### Traceability

| AC | Level | Priority | Test | Proves |
|----|-------|----------|------|--------|
| AC-1 | unit | P0 `guard` | `PlayerVMSeekCoalesceTest.everyPressInAHoldShowsTheCumulativeTarget` | After each of six `press(SeekForward)`: `currentPosition` and `seekIndicator.targetTimeText` are P+10/20/40/70/130/190 s, and `seeks.first()` is `(0, P+10 s)` recorded before the second press. Fails if the step base becomes the player position instead of the pending target, or if the leading seek is dropped |
| AC-1 | unit | P1 | `PlayerVMSeekCoalesceTest.aHoldNearTheEndClampsTheTargetToTheDuration` | From P = duration - 15 s, two presses: state and last seek equal `duration`. Fails if the clamp is lost in `seekBy` |
| AC-2 | unit | P0 `red` | `PlayerVMSeekCoalesceTest.aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget` | Six presses 50 ms apart, then `advanceTimeBy(1_000)`: `seeks.map { it.second } == listOf(P+10 s, P+190 s)` and the second is at t = 250. Base calls `seekTo` six times; fails if the trailing seek is missing or carries a stale target |
| AC-2 | unit | P0 `red` | `PlayerVMSeekCoalesceTest.aLongHoldKeepsAtLeastOneWindowBetweenSeeks` | Twenty presses 50 ms apart: consecutive `seeks` times differ by at least 250 ms, the last target equals the state's `currentPosition`, and no seek is recorded later than 250 ms after the last press. Fails if the window does not reopen after a trailing seek |
| AC-3 | unit | P0 `guard` | `PlayerVMSeekCoalesceTest.aSingleBackwardPressSeeksOnceToZero` | From P = 5 000, one `SeekBackward`, `advanceTimeBy(1_000)`: `seeks == [(0, 0)]`. Fails if an empty window still seeks or the lower clamp is lost |
| AC-4 | unit | P0 `guard` | `PlayerVMSeekCoalesceTest.startOverDropsThePendingSeek` | Resume dialog in the content state (as `PlayerVMOverlayAutoHideTest:198`), two presses, `StartFromBeginning`, `advanceTimeBy(1_000)`: last seek target is 0 and no seek follows it. Fails if `dropPendingSeek` is missing in `startFromBeginning` |
| AC-4 | unit | P0 `guard` | `PlayerVMSeekCoalesceTest.resumeDropsThePendingSeek` | Same with `ResumeFromPosition` and saved 60 000: last seek is 60 000, none after. Fails if the drop is missing in `resumeFromSavedPosition` |
| AC-4 | unit | P1 | `PlayerVMSeekCoalesceTest.skipSegmentDropsThePendingSeek` | Skip prompt in state, `seekTo` answer here not calling `onSeekPerformed` so the prompt survives the leading seek, two presses, `SkipSegmentClicked`: last seek is the segment end, none after. Fails if the drop is missing in `performSkipSegment` |
| AC-5 | unit | P0 `guard` | `PlayerVMSeekCoalesceTest.backgroundSavesThePendingTarget` | Two presses, then `OnBackground` before the window ends: `interactor.saveWatchingTime(42, 1, (P+20 s)/1000, 1)`. Fails if `saveCurrentPosition` reads only the player position |
| AC-5 | unit | P1 | `PlayerVMSeekCoalesceTest.backPressedSavesThePendingTarget` | Two presses, `HideControls`, `OnBackPressed`: same saved time, and no seek after the exit. Fails if `exitPlayer` saves before reading the pending target or seeks a released player |
| AC-5 | unit | P0 `guard` | `PlayerVMSeekCoalesceTest.aQualitySwitchCommitsThePendingTargetFirst` | Qualities in content state, two presses, `SelectQuality(1)`: `verifyOrder { seekTo(P+20 s); switchStream(any(), any()) }` and no seek after. Fails if `commitPendingSeek` is missing before `switchStream` |
| AC-6 | unit | P0 `red` | `PlayerVMSeekCoalesceTest.thePositionTickKeepsThePendingTarget` | Controls visible, `playingAt(P)` at tS (after `startedVM()`; `runCurrent()`); `advanceTimeBy(400)` then `SeekForward` at tS+400 and again at tS+450 (target P+20 s); `position` set to P+10 s; `advanceTimeBy(100)`, `runCurrent()` to tS+550 so the tick at tS+500 runs inside the window: `currentPosition == P+20 s`. Red on the Task 1 commit (tick writes P+10 s). Fails if the tick writes `playbackController.currentPosition` unconditionally |
| AC-7 | unit | P0 `guard` | `PlayerVMSeekBurstTest` (all six cases, `seek()` helper rewritten) | The hand call to `onSeekPerformed` per press goes; the `seekTo` answer calls it per real seek. Prompts stay down during the burst and appear one tick after it. Fails if the burst stops restarting on every press |
| AC-7 | unit | P0 `guard` | `PlayerVMSeekBurstTest.aHeldKeyKeepsThePromptDownAndTheTrailingSeekStartsNoCountdown` | Intro segment ahead, twenty presses 50 ms apart into it: `activeSkipSegment` null at every press and through 1 500 ms after the last, then non-null one tick later and unchanged for two more ticks. Fails if the burst gate or the trailing `onSeekPerformed` timing lets a prompt start mid-hold |
| AC-7 | unit | P0 `guard` | `PlayerVMSkipCountdownFitTest` (unchanged) and `PlayerVMPromptDismissalTest.seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (existing) | Countdown fit and the seek-after-cancel answer (spec 2026-11-10 Q2) stay green with the new call pattern |
| AC-7 | instrumented | P2 | `PlayerPromptFocusAndKeysTest` (existing) | Prompt focus and key pass-through (spec 2026-11-10 Q3) unaffected; run only if `component/` changes |
| AC-8 | device | P0 | Manual 1 and 2 | See below |
| R4 | unit | P1 | `PlayerVMSeekCoalesceTest.aPendingSeekDoesNotLandOnTheNextEpisode` | Two presses, `SelectEpisode(1, 2)`, `advanceTimeBy(1_000)`: no seek to P+20 s after the switch. Fails if `resetMediaSession` does not drop the target |
| R7 | unit | P0 `red` | `PlayerVMSeekCoalesceTest.theStepLadderRestartsAfterTheBurstEnds` | Three presses, `advanceTimeBy(1_600)`, one press: the step is 10 s again (`currentPosition` = previous + 10 s). Base resets on wall clock, which has not moved 1.5 s in virtual time, so it steps 30 s |
| R7 | unit | P1 | `PlayerVMSeekCoalesceTest.theStepLadderKeepsClimbingInsideTheBurst` | Presses 1 400 ms apart climb 10, 10, 20. Fails if `reset()` runs on every press |
| R7 | unit | P2 | `SeekHandlerTest.maxStep_clampsAtLastValue`, `SeekHandlerTest.reset_resetsIndexAndTime` (constructors without `resetTimeoutMs`) | Ladder and reset; `callAfterTimeout_resetsToFirstStep` is removed with the wall clock |
| AC-1 | unit | P2 | `PlayerVMPlaybackFlowTest.seekForward_updatesCurrentPosition`, `seekBackward_updatesCurrentPosition` (existing) | Stay green unchanged |

### Bugs

N/A: improvement. Written first: `PlayerVMSeekCoalesceTest.aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget` (fails on `8c3117ea`, `PlayerVM.kt:733-749` seeks per press).

### Manual checks

Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage`, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held, every adb call with `-s 192.168.1.110:5555`. A burst is one `input keyevent` invocation with repeated codes, for example `adb -s 192.168.1.110:5555 shell input keyevent 22 22 22 22 22 22 22 22` (eight Right presses, ladder sum 310 s). Debug overlay on, to read the stream URL and buffer.

| # | Steps | Expected | Must not happen |
|---|-------|----------|-----------------|
| 1 | Open an HLS title, play from about 5 min. Note the time. Send the eight-press Right burst. Watch the indicator and seek bar; read the time 3 s after the burst | Indicator and thumb advance on every press; playback resumes at start + 310 s, within 1 s; playback continues | Thumb jumping back; playback at an earlier time than the indicator showed; spinner past 8 s or a stream URL change in the debug overlay (stall failover); crash |
| 2 | Repeat 1 with Left (`21`), with media fast-forward (`90`), and with the seek bar focused (`19` to open seek controls, then the Right burst) | Same as 1 for each; the seek bar keeps focus after the burst | Focus leaving the seek bar; a burst ending on a stale target |
| 3 | On a series episode with an intro segment, play from 10 s before its lead-in and send a Right burst ending inside the intro | No skip prompt during the burst; the prompt appears about 1.5 s after the last press and counts down normally | Prompt flicker during the burst; a countdown starting before 1.5 s; the prompt taking focus from the seek bar |
| 4 | Send a Right burst, then press Back twice within 250 ms of the last press; reopen the title | Resume offer shows the burst's final time | Resume at the pre-burst time |

### Perf track

N/A: perf track off (brief Q3).

### UNTESTABLE

None.

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Mechanism? | Option A only; no `SeekParameters` or scrubbing mode until a box measurement shows a need (research.md: 8. Open questions Q1). | no |
| Q2 | Window? | Leading seek on the first press, at most one seek per 250 ms, trailing flush 250 ms after the last press; no key-up plumbing. Seek bar and media FF/RW keys share the path. | no |
| Q3 | Measure a before/after number on the box (track `perf`)? | No: AC-2 counts seeks in a unit test and AC-8 observes the hold; a perf track would need a telemetry baseline the request does not ask for. | no |

All questions answered with the recommended answer (owner standing instruction: recommended).

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-28 | Gate 1: approved on the recommended answers | owner (standing instruction: recommended) |
