# Design (architect): player-dpad-seek-coalesce

Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/vm/` unless stated. Line numbers are at base `8c3117ea`.

## Decisions

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

## Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: no API or repository code changes. |
| ViewModel state and actions | `PlayerVM.kt`: the fields and constant above. `seekBy(isForward)`: if the burst is inactive, `seekHandler.reset()`; then `startSeekBurst()`, `nextStep()`, the target from the pending base clamped to `[0, duration]`, `requestSeek(target)` and `showSeekIndicator(..., targetPosition)`. `requestSeek`: with the window open, set the pending target; otherwise `seekTo` and launch `seekThrottleJob` (loop: `delay(SEEK_THROTTLE_WINDOW_MS)`; take the pending target or break; `seekTo`). `commitPendingSeek()`: read the target, drop it, `seekTo` if non-null. `dropPendingSeek()`: cancel the job and clear the target. Call sites as in Decisions. Routing at `:659-660` points to `seekBy`. |
| ViewModel helper | `SeekHandler.kt`: `nextStep()` advances up to the last step, and `reset()` returns to the first step. The constructor keeps `steps`. `DEFAULT_SEEK_RESET_TIMEOUT_MS` (`SeekHandler.kt:7`) stays as the burst length used at `PlayerVM.kt:730`. |
| Screen and DI module | N/A: `PlayerVM` keeps its constructor (`PlayerVM.kt:66-77`). |
| Compose content | N/A: `PlayerVideoSurface`, `PlayerProgressBar` and `PlayerTimedActionOverlay` keep emitting `SeekForward` and `SeekBackward` per key-down. |
| Resources | N/A: no strings or resources change. |
| Tests touched by the call pattern | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SeekHandlerTest.kt` (the timeout cases go, because the reset moves to the VM). `PlayerVMSeekBurstTest.kt`: its `seek()` helper calls `onSeekPerformed` by hand on every press (`:48-52`), which is now correct only for presses that make a real seek. `PlayerVMPlaybackFlowTest.kt:403-416` still holds. The instrumented `PlayerPromptFocusAndKeysTest` asserts actions only, so it is unaffected. The test-designer owns the rows. |

## Alternatives rejected

- `CLOSEST_SYNC` for D-pad seeks: the brief's Q1, and the seek count stays the same.
- Scrubbing mode during the burst: the brief's Q1, `@UnstableApi`, and the fixes in 1.11.1 are not in this project yet.
- A pure trailing debounce, with no leading seek: it breaks AC-1, and a single press would wait 250 ms.
- Committing the seek inside `saveCurrentPosition`: it adds a player side effect to a save, and a save followed by `release()` would seek for nothing.
- Injecting a clock into `PlayerVM` for `SeekHandler`: the burst job already measures the same 1 500 ms on the dispatcher.
- An extracted `SeekCoalescer` class: it would need the VM scope and the commit and drop hooks wired back in.

## Risks

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

## Open questions

N/A: the brief's Q1 to Q3 are taken as recommended, and no AC needs a change.
