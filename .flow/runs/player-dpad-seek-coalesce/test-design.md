# Test design: player-dpad-seek-coalesce

Inputs: [brief.md](brief.md) (AC-1 to AC-8), [design-architect.md](design-architect.md). Base `8c3117ea`.
Unit tests live in `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/` and extend `PlayerVMTestFixture`.

## Fixture for the new suite

New file `PlayerVMSeekCoalesceTest.kt`, same shape as `PlayerVMSeekBurstTest`: `MainDispatcherExtension`, `runTest(mainDispatcher.dispatcher.scheduler)`, `vm.testCancelScope()` at the end.

| Helper | Behaviour |
|--------|-----------|
| `seeks: MutableList<Pair<Long, Long>>` | `playbackController.seekTo(any())` answers: record `(testScheduler.currentTime, target)`, set `position = target` (ExoPlayer masking), call `callbackSlot.captured.onSeekPerformed()` (the controller does this per real seek, `PlaybackController.kt:568-578`) |
| `playingAt(from)` | `currentPosition` answers `position`; duration stays the fixture's 2 400 000 ms |
| `press(action, gapMs = 50)` | `advanceTimeBy(gapMs)` then `vm.onAction(action)`, no `runCurrent` in between, so a press always runs before a window end due at the same instant (design risk "Ordering in tests") |
| `mapper.formatTime` | answers `"t${firstArg<Long>()}"` so the indicator text carries the target |

Expected values below use P = 100 000 ms and the ladder 10, 10, 20, 30, 60, 60 s.

## Risk profile

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

## Traceability

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

## Bugs

N/A: improvement. Written first: `PlayerVMSeekCoalesceTest.aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget` (fails on `8c3117ea`, `PlayerVM.kt:733-749` seeks per press).

## Manual checks

Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage`, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held, every adb call with `-s 192.168.1.110:5555`. A burst is one `input keyevent` invocation with repeated codes, for example `adb -s 192.168.1.110:5555 shell input keyevent 22 22 22 22 22 22 22 22` (eight Right presses, ladder sum 310 s). Debug overlay on, to read the stream URL and buffer.

| # | Steps | Expected | Must not happen |
|---|-------|----------|-----------------|
| 1 | Open an HLS title, play from about 5 min. Note the time. Send the eight-press Right burst. Watch the indicator and seek bar; read the time 3 s after the burst | Indicator and thumb advance on every press; playback resumes at start + 310 s, within 1 s; playback continues | Thumb jumping back; playback at an earlier time than the indicator showed; spinner past 8 s or a stream URL change in the debug overlay (stall failover); crash |
| 2 | Repeat 1 with Left (`21`), with media fast-forward (`90`), and with the seek bar focused (`19` to open seek controls, then the Right burst) | Same as 1 for each; the seek bar keeps focus after the burst | Focus leaving the seek bar; a burst ending on a stale target |
| 3 | On a series episode with an intro segment, play from 10 s before its lead-in and send a Right burst ending inside the intro | No skip prompt during the burst; the prompt appears about 1.5 s after the last press and counts down normally | Prompt flicker during the burst; a countdown starting before 1.5 s; the prompt taking focus from the seek bar |
| 4 | Send a Right burst, then press Back twice within 250 ms of the last press; reopen the title | Resume offer shows the burst's final time | Resume at the pre-burst time |

## Perf track

N/A: perf track off (brief Q3).

## UNTESTABLE

None.
