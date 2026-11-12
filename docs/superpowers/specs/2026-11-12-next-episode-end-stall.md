# After a cancelled next-episode countdown, the end of the episode stalls

Status: approved
Date: 2026-11-12
Slug: next-episode-end-stall
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

After the owner cancels the next-episode countdown and watches to the end, the player stays on the finished episode: seek bar at the start, buffering, nothing plays. The owner expects the next episode to start at the end even after a cancel (state.md Log). That answers research.md: Open questions Q1 as "hide the prompt, still advance at the end" (Option 1).

### Root cause

| # | Symptom | Cause | Status |
|---|---------|-------|--------|
| 1 | No move to the next episode | `cancelCountdown()` sets `countdownDismissed = true` (`PlayerVM.kt:1398-1404`; Back on the prompt goes there too, `:1474-1476`). `onPlaybackEnded()` then skips the countdown and calls `showControlsForEndedPlayback()` (`PlayerVM.kt:1374-1381`). Intended on base and pinned by `PlayerVMPromptDismissalTest.kt:69-78`. | confirmed (code trace) |
| 2 | Seek bar at the start | The position tick publishes only while `isPlaying` or `isBuffering` (`PlayerVM.kt:1513-1523`); at `STATE_ENDED` `isPlaying` is false (`PlaybackController.kt:907-914`) and the ended path publishes no final position, so the bar draws a stale value, possibly 0 (research.md: Current state in code). | plausible |
| 3 | Buffering, nothing plays | Any Play intent at the end runs `play()`, which calls `seekToDefaultPosition()` (`PlaybackController.kt:523-532`) and restarts from 0 with no `SeekStallRecovery` armed (compare `seekTo()`, `:535-544`). Why the reload stalls is unknown. | plausible, not reproduced on a device |

With cause 1 removed, the series-with-next-episode path no longer reaches the ended controls outside the panel case, so causes 2 and 3 no longer apply to the reported scenario.

### Scope

In: `onPlaybackEnded()` (`PlayerVM.kt:1359-1382`) switches to the next episode at once when the countdown was dismissed and no panel is open; a panel that closes on an ended, dismissed episode does the same (`onPanelClosed()`, `PlayerVM.kt:848-861`; see Q2); the ended path publishes the final `currentPosition`/`duration` once. Rewrite `PlayerVMPromptDismissalTest.kt:69-78` and the comment at `PlayerVM.kt:1375-1376`.

## Acceptance criteria

- AC-1. Given a series episode with a next episode, the countdown cancelled, no panel open, When playback reaches `STATE_ENDED`, Then `switchEpisode` runs for the next episode without a second countdown and the ended controls are not shown. (unit, red on base)
- AC-2. Given the same, but cancelled with Back on the prompt, When playback ends, Then the next episode starts as in AC-1. (unit)
- AC-3. Given a series episode with no countdown cancelled, When playback ends, Then the countdown starts and plays the next episode when it runs out, as on base. (unit, existing tests green)
- AC-4. Given a cancel and then a seek, When the tick reaches the prompt position, Then the prompt shows again (`PlayerVMPromptDismissalTest.kt:81-95` green unchanged).
- AC-5. Given a film or the last episode, When playback ends, Then the player exits, or exits when the open panel closes, as on base. (unit, existing tests green)
- AC-6. Given a dismissed countdown and a panel open when playback ends, When the panel closes, Then the next episode starts. (unit; depends on Q2)
- AC-7. Given playback ends on any path that shows the ended controls, When they appear, Then `currentPosition` and `duration` equal the player's final values. (unit, red on base)
- AC-8. Given a series episode on the TV, When the user cancels the countdown with the D-pad and watches to the end, Then the next episode starts playing from its start with no spinner left on the finished episode. (device)

### Risks (analyst)

- Risk areas "playback" and "ViewModel state", hence M rather than the research's S; the diff itself is `PlayerVM.kt` plus tests.
- Removing the stop-at-end behaviour leaves Back on the prompt as the only way to stay at the end; the decisions in `seek-blocked-by-skip-prompt` and `player-overlay-autohide` (research.md: Related work) are superseded for this case.
- `onPlaybackEnded()` also runs `autoMarkCurrentAsWatched()` first (`PlayerVM.kt:1360`); the switch must keep that order so the finished episode is still marked.
- AC-8 needs an owner-account episode played to its end on the emulator; playback evidence stays out of Git (AGENTS.md).

## Out of scope

Out: `PlaybackController.play()` from ENDED and stall recovery for it; Next-button focus on the ended controls (Q3).

## Design

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where does "cancel still advances at the end" live? | Only in `onPlaybackEnded()` (`PlayerVM.kt:1360-1382`) and `onPanelClosed()` (`PlayerVM.kt:848-861`). `cancelCountdown()` (`PlayerVM.kt:1398-1404`) and `countdownDismissed` keep their meaning: "do not show the prompt again before the end". | `countdownDismissed` also gates the early prompt (`PlayerVM.kt:1572`) and the credits check (`PlayerVM.kt:1644`); AC-4 needs that unchanged. |
| What does the ended branch do after a cancel with no panel open? | Calls `playNextEpisode()` (`PlayerVM.kt:1046-1054`) right after `autoMarkCurrentAsWatched()` (`PlayerVM.kt:1361`). It does not start a countdown (Q1). | `playNextEpisode()` is the single path the countdown, the Next button and the credits skip (`PlayerVM.kt:1394`, `:668`, `:1796`) already use; `switchEpisode()` (`PlayerVM.kt:1031-1044`) saves the position and resets the session. |
| Shape of the new `when` in `onPlaybackEnded()` | Ordered: panel open → mark only (as base); `nextEpisodeCountdown != null` → `showControlsForEndedPlayback()` (as base); `countdownDismissed` → advance; else → `startNextEpisodeCountdown()`. | Same results as base in every branch except the dismissed one. |
| What if `findNextEpisode` returns null although `hasNextEpisode` is true? | `playNextEpisode()` returns `Boolean` (true when it called `switchEpisode`); the ended branch falls back to `showControlsForEndedPlayback()` on false. The other three callers ignore the result. | Without it the viewer would be left on a still frame with no controls (risk 2). |
| How does a panel closing on an ended, dismissed episode advance (Q2, AC-6)? | New field `advanceWhenPanelCloses`, next to `exitWhenPanelCloses` (`PlayerVM.kt:164`). `onPlaybackEnded()` sets it when a panel is open, the episode has a next one and `countdownDismissed`. `onPanelClosed()` consumes it after the `exitWhenPanelCloses` check: clear, then `playNextEpisode()`; on false it falls through to the existing `checkEarlyNextEpisode()`, because `closePanel()` already shows the controls with focus on the panel opener (`ControlsStateMachine.kt:83-91`). | Reading `countdownDismissed` at close time is not enough: `selectEpisodeById()` closes the panel before its own switch (`PlayerVM.kt:991-993`) and would be overtaken by the next episode, because `switchEpisode()` drops a second call while one is in progress (`PlayerVM.kt:1032`). |
| Where is `advanceWhenPanelCloses` cleared? | In `onPlaybackResumed()` (`PlayerVM.kt:805-808`, which `resetMediaSession()` also calls, `:1028`) and in `selectEpisodeById()` beside `exitWhenPanelCloses = false` (`PlayerVM.kt:991`). It is **not** cleared by the hide timeout (`PlayerVM.kt:818`). | An Episodes row closed by its 6 s timeout on an ended episode is still "the panel closes" in AC-6; only `exitWhenPanelCloses` carries the "a timeout never leaves the player" rule. |
| AC-7: final position on the ended path | `onPlaybackEnded()` publishes `currentPosition`, `duration` and `bufferedPosition` from `playbackController` once, right after the `Content` check (`PlayerVM.kt:1362`), on every path. | The tick stops publishing at ENDED (`PlayerVM.kt:1513-1523`; `isPlaying` false per `PlaybackController.kt:907-914`). One copy at the top covers both "controls now" and "controls when the panel closes". `bufferedPosition` keeps the bar's buffered segment consistent with the position. |
| Comment and pinned test | Replace the comment at `PlayerVM.kt:1375-1376` with the new rule; rewrite `PlayerVMPromptDismissalTest.kt:69-78` (path under `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/`) to assert the switch. | Brief scope. |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change. |
| ViewModel state and actions | `PlayerVM.kt` only: field `advanceWhenPanelCloses` (near `:164`); `onPlaybackEnded()` (`:1360-1382`) publishes the final position, reorders the `when`, advances or arms the flag; `onPanelClosed()` (`:848-861`) consumes the flag; `onPlaybackResumed()` (`:805-808`) and `selectEpisodeById()` (`:991`) clear it; `playNextEpisode()` (`:1046-1054`) returns `Boolean`. `ControlsStateMachine.kt` unchanged. `PlaybackController.kt` unchanged (Q3). |
| Screen and DI module | N/A: no wiring change. |
| Compose content | N/A: `PlayerContentState` fields already exist; the seek bar reads them as before. |
| Resources | N/A. |
| Tests | `PlayerVMPromptDismissalTest.kt`: rewrite `:69-78`; the AC rows themselves come from test-design. Fixture stubs `findNextEpisode` to null (`app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:117`); advancing tests stub it to a pair and verify `playbackController.release()` / Loading state, as `PlayerVMEpisodeFlowTest.kt:129-147` does through `SelectEpisode`. Back on the prompt (AC-2) goes through `vm.onBackPressed()` (`PlayerVM.kt:1450`), which reaches `cancelCountdown()` at `:1474-1476`. |

### Alternatives rejected

- Drop `countdownDismissed` from the ended branch only and let it start a countdown: contradicts Q1 (switch at once).
- Clear `countdownDismissed` in `cancelCountdown()`: brings the early prompt straight back on the next tick, breaking AC-4.
- Decide the panel-close advance from `controlsStateMachine.state.playbackEnded && countdownDismissed`: `selectEpisodeById()` would lose its chosen episode to the next one.
- Fix `PlaybackController.play()` from ENDED: Out of scope (Q3, run `player-ended-play-hardening`).

### Risks

| # | Risk | Where | Mitigation |
|---|---|---|---|
| 1 | Correctness: picking an episode from the Episodes row on an ended, dismissed episode plays the next episode instead of the picked one. | `selectEpisodeById()` `PlayerVM.kt:983-997` → `closePanel()` → `onPanelClosed()`; `switchEpisode()` guard `:1032`. | Clear `advanceWhenPanelCloses` before `closePanel()`; test-design should add a row for it. |
| 2 | Lifecycle: `hasNextEpisode` true but `findNextEpisode` null leaves no controls and no exit. | `onPlaybackEnded()`, `playNextEpisode()` `PlayerVM.kt:1046-1054`. | Boolean result and fallback to `showControlsForEndedPlayback()`. |
| 3 | Lifecycle: the advance on panel close fires during exit. | `scheduleControlsHide()` already returns when `closing` (`PlayerVM.kt:815`); `exitWhenPanelCloses` is checked first in `onPanelClosed()`, and both flags are never set together (film/last episode vs. has-next). | Keep the consume after the exit check. |
| 4 | Data: the finished episode must be marked watched and its position saved before the switch. | `autoMarkCurrentAsWatched()` `PlayerVM.kt:1361` runs first; `switchEpisode()` calls `saveCurrentPosition()` `:1034`. | Keep the order; a unit test verifies the watched request before `release()`. |
| 5 | Focus / D-pad: after an automatic advance focus lands where a normal switch leaves it (Loading → new Content, controls re-initialized). No new focus path. | `ControlsStateMachine.initialize()` `ControlsStateMachine.kt:45-50`. | Covered by AC-8 on the device. |
| 6 | Recomposition: one extra `PlayerContentState` copy per ended event. | `onPlaybackEnded()`. | Negligible; once per media end. |
| 7 | Rollout: no in-player way remains to stay on a finished series episode that has a next one (Back on the prompt cancels too, AC-2); earlier decisions in `seek-blocked-by-skip-prompt` and `player-overlay-autohide` are superseded for this case. | Product behaviour. | Owner decision (state.md Decisions); no spec edit. |

### Open questions (architect)

none

## Test design

### Risk profile

| # | Risk | Source | P | I | Score | Answer |
|---|---|---|---|---|---|---|
| R1 | A cancel still leaves the player on the finished episode at `STATE_ENDED` (the reported bug) | brief Root cause 1 | 3 | 3 | 9 | P0 red, AC-1 and AC-2 |
| R2 | Ended seek bar shows a stale position (0 or early) | brief Root cause 2, AC-7 | 3 | 2 | 6 | P0 red, AC-7 |
| R3 | Picking an episode from the Episodes row on an ended, dismissed episode plays the next one instead | design Risk 1 | 2 | 3 | 6 | P0 guard |
| R4 | A dismissed episode ending under a panel never advances when the panel closes | AC-6, design `advanceWhenPanelCloses` | 2 | 3 | 6 | P0 red, AC-6 |
| R5 | Finished episode is not marked watched because the switch runs first | design Risk 4 | 2 | 2 | 4 | P1 |
| R6 | `advanceWhenPanelCloses` survives a restart under the panel and jumps episodes later | design (cleared in `onPlaybackResumed`) | 2 | 2 | 4 | P1 guard |
| R7 | `hasNextEpisode` true but `findNextEpisode` null leaves no controls and no exit | design Risk 2 | 1 | 3 | 3 | P1 guard |
| R8 | The change brings the early prompt back after a cancel, or stops the normal countdown | AC-3, AC-4 | 1 | 3 | 3 | existing guards |
| R9 | Advance fires during exit | design Risk 3 | 1 | 2 | 2 | accepted: flags exclusive by branch; exit tests below stay green |
| R10 | Focus after the automatic advance lands somewhere odd | design Risk 5 | 1 | 2 | 2 | device check M1 |
| R11 | No in-player way left to stay on a finished episode that has a next one | design Risk 7 | 1 | 2 | 2 | accepted: owner decision (state.md Decisions) |

### Traceability

Shared setup for advancing tests: `every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)` (fixture default is null, `PlayerVMTestFixture.kt:117`). "Switches to 1x2" means `verify { playbackController.release() }` and `verify { interactor.resolveMedia(item = any(), seasonNumber = 1, episodeNumber = 2, videoNumber = null) }`. "Prompt raised by the tick" means `currentPosition` 2_380_000 and `advanceTimeBy(600)` under `runTest`, as in `aLateTickDoesNotTakeTheNextEpisodePromptDown`; such tests end with `vm.testCancelScope()`.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut` (rewrites `aCancelledNextEpisodeIsNotOfferedAgainWhenTheEpisodeRunsOut`, `PlayerVMPromptDismissalTest.kt:69`): prompt raised by the tick, `CancelNextEpisodeCountdown`, `onPlaybackEnded()`; with no further virtual time: switches to 1x2, `nextEpisodeCountdown` stays null before the switch | Fails while the dismissed branch shows ended controls; fails if the branch starts a countdown instead (`release()` would need 8 s) |
| AC-2 | unit | P0 red | `backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut` (`PlayerVMPromptDismissalTest`): prompt raised by the tick, `PlayerAction.OnBackPressed`, `onPlaybackEnded()`: switches to 1x2, `router.back` never called | Fails if Back on the prompt keeps stop-at-end semantics or exits the player |
| AC-3 | unit | P0 guard | `playbackEnded_withNextEpisode_countsDownInsteadOfLeaving` (`PlayerVMPlaybackFlowTest`), `nextEpisodeCountdown_switchesOnlyWhenTheWholeWaitHasRun` (`PlayerVMCountdownClockTest`), `closePanel_startsCountdownDeferredWhilePanelWasOpen` (`PlayerVMEpisodeFlowTest`), unchanged | Fails if the reordered `when` advances at once without a cancel, or loses the countdown after a panel |
| AC-4 | unit | P0 guard | `seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (`PlayerVMPromptDismissalTest`), unchanged | Fails if `cancelCountdown()` or the ended path clears `countdownDismissed` in a way that changes the seek rule |
| AC-5 | unit | P0 guard | `playbackEnded_onMovie_returnsToTheCallingScreen`, `playbackEnded_onLastEpisode_returnsToTheCallingScreen`, `playbackEnded_underOpenPanel_waitsForThePanelToClose` (`PlayerVMPlaybackFlowTest`), `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays` (`PlayerVMOverlayAutoHideTest`), unchanged | Fails if the film / last-episode branch moves behind the new advance branch |
| AC-6 | unit | P0 red | `closingAPanelOverAnEndedDismissedEpisodeStartsTheNextOne` (`PlayerVMPromptDismissalTest`): prompt raised by the tick, cancel, `OpenSettingsPanel`, `onPlaybackEnded()`; no `release()` yet; `ClosePanel`: switches to 1x2 | Fails if `onPanelClosed()` does not consume `advanceWhenPanelCloses`, or if the ended branch switches from under the open panel |
| AC-6 | unit | P1 red | `anEpisodesRowTimingOutOverAnEndedDismissedEpisodeStartsTheNextOne` (`PlayerVMOverlayAutoHideTest`, `vmTest`): as above with `OpenEpisodesPanel`, then `advanceTimeBy(6_100)`: switches to 1x2, `router.back` never called | Fails if the hide timeout clears `advanceWhenPanelCloses` together with `exitWhenPanelCloses` (`PlayerVM.kt:818`) |
| AC-6 (R3) | unit | P0 guard | `pickingAnEpisodeOverAnEndedDismissedEpisodePlaysThePickedOne` (`PlayerVMPromptDismissalTest`): `getItemDetails` and `markCurrentAsWatched` return an item whose season 1 holds episodes 101, 102, 103; dismissed, `OpenEpisodesPanel`, `onPlaybackEnded()`, `SelectEpisodeById(103)`: `resolveMedia(..., 1, 3, null)` called, `resolveMedia(..., 1, 2, null)` never called | Fails if `selectEpisodeById()` closes the panel before clearing `advanceWhenPanelCloses` (`switchEpisode` guard `PlayerVM.kt:1032` drops the pick) |
| AC-6 (R6) | unit | P1 guard | `aRestartUnderThePanelCancelsTheAdvanceOnClose` (`PlayerVMPromptDismissalTest`): dismissed, `OpenSettingsPanel`, `onPlaybackEnded()`, `onPlaybackStateChanged(isPlaying = true, ...)`, `ClosePanel`: `release()` never called | Fails if `onPlaybackResumed()` does not clear `advanceWhenPanelCloses` |
| AC-6 | unit | P0 guard | `aCancelAfterTheEndUnderAPanelDoesNotStartTheNextOneOnClose` (`PlayerVMPromptDismissalTest`, no `runTest`): next stub, `OpenSettingsPanel`, `onPlaybackEnded()`, `CancelNextEpisodeCountdown`, `ClosePanel`: `release()` never called | Fails if the close path reads `countdownDismissed` at close time instead of the `advanceWhenPanelCloses` armed in `onPlaybackEnded()` |
| AC-6 | unit | P0 guard | `nonLastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`, `settingsClosedAfterEnd_keepsControlsUp_andBackExitsOnce`, `aboutClosedAfterEnd_keepsControlsUp_andBackExitsOnce` (`PlayerVMOverlayAutoHideTest`, fixture `findNextEpisode` null), unchanged | Fails if a panel closing over an ended episode with no next to find loses the ended controls, their focus on the opener, or the single Back exit |
| AC-1 (R5) | unit | P1 red | `theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads` (`PlayerVMPromptDismissalTest`, no `runTest`): `CancelNextEpisodeCountdown` with no prompt up (sets the flag, as `cancelCountdown_setsNull` does), `onPlaybackEnded()`: `coVerifyOrder { interactor.markCurrentAsWatched(42, 1, 1); playbackController.release() }` | Fails if the advance runs before `autoMarkCurrentAsWatched()` |
| AC-1 (R7) | unit | P1 guard | `anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls` (`PlayerVMPromptDismissalTest`): `findNextEpisode` null, `HideControls` and assert hidden, dismissed, `onPlaybackEnded()`: `controlsVisible` true, `release()` and `router.back` never called | Fails if the `playNextEpisode()` false result has no fallback to `showControlsForEndedPlayback()` |
| AC-7 | unit | P0 red | `theEndedControlsShowTheFinalPosition` (`PlayerPositionPublishingTest`, no tick advanced): `OpenSettingsPanel`, then `isPlaying` false, `currentPosition`/`duration`/`bufferedPosition` 2_410_000; `onPlaybackEnded()`, `CancelNextEpisodeCountdown`, `ClosePanel`: `controlsVisible` true, `currentPosition`, `duration` and `bufferedPosition` equal 2_410_000 | Fails while the ended path publishes nothing (base leaves 0 / 2_400_000 from `testContentState`) |
| AC-7 | unit | P1 red | `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition` (`PlayerPositionPublishingTest`): the R7 setup with the controller at 2_410_000: `currentPosition` and `duration` equal 2_410_000 | Fails if the publish is placed inside one branch instead of at the top of `onPlaybackEnded()` |
| AC-8 | device | P0 | M1, M2 below | Fails on any remaining stall after a D-pad cancel |

`endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime` (`PlayerVMOverlayAutoHideTest:398`) now goes through the R7 fallback (fixture `findNextEpisode` null) and must stay green unchanged.

### Failing-first test

`aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut`. On base the dismissed branch calls `showControlsForEndedPlayback()` (`git show 4db9c0d4:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`, lines 1371-1381), so `release()` is never called and the test fails; after the change the ended branch calls `playNextEpisode()` and it passes.

### Manual checks

qa-device, emulator under lease, the worktree's dev APK installed with `tools/android-apk-install-preserve`. Series with at least two episodes, not on its last one. Playback evidence stays out of Git.

| # | Steps (D-pad) | Expected | Must not happen |
|---|---|---|---|
| M1 (AC-8, AC-1) | 1. Start an episode. 2. Seek with `DPAD_RIGHT` to about 1 min before the end, then wait without touching seek. 3. When the next-episode prompt appears, focus Cancel and press `DPAD_CENTER`. 4. Press nothing until the episode ends. | Within a few seconds of the end the next episode loads and plays from 0:00; title shows the next episode; the finished episode is marked watched in the episode list. | Seek bar at 0 on the finished episode, a spinner on it, the ended controls, a second countdown, a crash. |
| M2 (AC-8, AC-2) | As M1, but step 3 presses `BACK` on the prompt. | Same as M1; the player does not exit. | Same as M1; leaving the player. |
| M3 (AC-6) | As M1 to step 3, then open the Episodes row before the end and let the episode end under it; then press `BACK` to close the row. | The next episode starts when the row closes. | Ended controls left on screen; the player exiting. |
| M4 (AC-3) | Start an episode, seek near the end, do not cancel the prompt. | Countdown runs out, next episode plays (as base). | Switch before the countdown ends. |

Any seek after step 3 resets the dismissal (AC-4) and invalidates M1-M3; seeking is done before the prompt.

### Perf

N/A: no perf track.

### UNTESTABLE

none

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | At the end after a cancel: switch at once or show the countdown again? | yes | answered | Switch at once |
| Q2 | If a panel is open when the dismissed episode ends, does closing the panel start the next episode? | yes | answered | Yes, same rule as with no panel (AC-6) |
| Q3 | Include hardening of play() from ENDED (stall recovery, Next focus on ended controls)? | no | answered | No; follow-up run player-ended-play-hardening |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-12 | Research Q1: Cancel on the next-episode prompt only hides it; the next episode still starts at the end (owner's report states this expectation) | owner |
| 2026-11-12 | Q1: switch at once at the end after a cancel | owner (standing instruction: recommended) |
| 2026-11-12 | Q2: closing a panel on an ended, dismissed episode starts the next episode (AC-6) | owner (standing instruction: recommended) |
| 2026-11-12 | Q3: play()-from-ENDED hardening is a follow-up run, not this one | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1: approved (size M, no tracks, 8 ACs, P0 10 / P1 5 / manual 4; design risk 7 accepted: no in-player way remains to stay on a finished series episode with a next one) | owner (standing instruction: recommended) |
| 2026-11-12 | Plan Q1: when the panel closes and no next episode can be played, onPanelClosed() falls through to checkEarlyNextEpisode() instead of showControlsForEndedPlayback(), keeping focus on the panel opener (ControlsStateMachine.kt:83-91 vs :113-116) | owner (standing instruction: recommended) |
