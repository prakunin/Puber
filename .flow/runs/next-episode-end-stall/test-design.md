# Test design: next-episode-end-stall

Base 4db9c0d4. ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Tests live in `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/` and extend `PlayerVMTestFixture`; level names follow `.flow/roles/test-designer.md`.

## Risk profile

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

## Traceability

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

## Failing-first test

`aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut`. On base the dismissed branch calls `showControlsForEndedPlayback()` (`git show 4db9c0d4:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt`, lines 1371-1381), so `release()` is never called and the test fails; after the change the ended branch calls `playNextEpisode()` and it passes.

## Manual checks

qa-device, emulator under lease, the worktree's dev APK installed with `tools/android-apk-install-preserve`. Series with at least two episodes, not on its last one. Playback evidence stays out of Git.

| # | Steps (D-pad) | Expected | Must not happen |
|---|---|---|---|
| M1 (AC-8, AC-1) | 1. Start an episode. 2. Seek with `DPAD_RIGHT` to about 1 min before the end, then wait without touching seek. 3. When the next-episode prompt appears, focus Cancel and press `DPAD_CENTER`. 4. Press nothing until the episode ends. | Within a few seconds of the end the next episode loads and plays from 0:00; title shows the next episode; the finished episode is marked watched in the episode list. | Seek bar at 0 on the finished episode, a spinner on it, the ended controls, a second countdown, a crash. |
| M2 (AC-8, AC-2) | As M1, but step 3 presses `BACK` on the prompt. | Same as M1; the player does not exit. | Same as M1; leaving the player. |
| M3 (AC-6) | As M1 to step 3, then open the Episodes row before the end and let the episode end under it; then press `BACK` to close the row. | The next episode starts when the row closes. | Ended controls left on screen; the player exiting. |
| M4 (AC-3) | Start an episode, seek near the end, do not cancel the prompt. | Countdown runs out, next episode plays (as base). | Switch before the countdown ends. |

Any seek after step 3 resets the dismissal (AC-4) and invalidates M1-M3; seeking is done before the prompt.

## Perf

N/A: no perf track.

## UNTESTABLE

none
