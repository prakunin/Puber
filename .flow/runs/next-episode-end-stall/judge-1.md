# Gate - next-episode-end-stall - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** PASS
**Head:** babd220b   **Base:** 4db9c0d4

## Done as asked

- Production diff is `PlayerVM.kt` only (`git diff 4db9c0d4...HEAD -- app/src/main`). `onPlaybackEnded()` publishes the final position at the top. Its reordered `when` changes only the `countdownDismissed` branch. That branch goes to `advanceAfterDismissedEnd()`, which is deferred out of the ExoPlayer callback by `delay(1)` and re-checks `closing`, an in-progress switch, the countdown and the panel. `advanceWhenPanelCloses` is set on the panel path, used in `onPanelClosed()`, and cleared in `onPlaybackResumed()` and `selectEpisodeById()`. `playNextEpisode()` returns `Boolean` and falls back to the ended controls when it finds no next episode. All of this matches spec Design.
- Out of scope respected: `PlaybackController.kt` and `ControlsStateMachine.kt` are not changed. The comment at old `:1375-1376` and the pinned test at `PlayerVMPromptDismissalTest.kt:69-78` were rewritten as the spec asked.
- Merge with master (e2bccaf5, c486384a): `git merge-tree` is clean. Master changes no player source or test. Of the libraries this diff relies on, Kotlin moved 2.4.10 -> 2.4.21 and mockk 1.14.11 -> 1.14.12; coroutines and Media3 did not change. The merge risk is low, and the ship step's check covers what remains.

## Traceability

check.full was re-run by the judge at babd220b (qa-report 21:04 is older than babd220b at 21:12): exit 0. Result XML: 2072 tests, 0 failures, 0 errors, 0 skipped, written 21:15.

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut`, `theSwitchOfAnEndedDismissedEpisodeLeavesThePlayersEndedCallbackFirst`, `theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads`, `anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls`; device M2 | judge run (green); evidence round 2, M2 | PASS |
| AC-2 | `backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut`; device M2 (`BACK`) | judge run; evidence round 2, M2 | PASS |
| AC-3 | `playbackEnded_withNextEpisode_countsDownInsteadOfLeaving`, `nextEpisodeCountdown_switchesOnlyWhenTheWholeWaitHasRun`, `closePanel_startsCountdownDeferredWhilePanelWasOpen`; device M4 | judge run; evidence rounds 1-2, M4 | PASS |
| AC-4 | `seekingAfterCancellingPutsTheNextEpisodeQuestionBack` | judge run | PASS |
| AC-5 | `playbackEnded_onMovie/onLastEpisode_returnsToTheCallingScreen`, `playbackEnded_underOpenPanel_waitsForThePanelToClose`, `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays` | judge run | PASS |
| AC-6 | unit: `closingAPanelOverAnEndedDismissedEpisodeStartsTheNextOne`, `anEpisodesRowTimingOut...`, `pickingAnEpisode...PlaysThePickedOne`, `aRestartUnderThePanel...`, `aCancelAfterTheEndUnderAPanel...`, `aPanelOpenedInsideTheDeferral...`, `leavingInsideTheDeferral...` | judge run | PASS |
| AC-6 device (M3) | manual M3 | evidence rounds 1-2: the Episodes row timed out before the end | NOT CHECKED (manual, section below) |
| AC-7 | `theEndedControlsShowTheFinalPosition`, `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition`, `theEndedControlsOverARunningCountdownShowTheFinalPositionAndKeepTheCountdown` | judge run | PASS |
| AC-8 | device M2 (cancel with `BACK`) | evidence round 2, M2, at a7027fa1; APK sha256 matched on the device | PASS |
| AC-8 (M1, the X button) | manual M1 | evidence: the X could not be reached in the 5 s window | NOT CHECKED (manual, section below) |

babd220b has not been run on a device. It only adds guard conditions inside the 1 ms deferral, which a device cannot time, and the owner decided this (state.md Decisions, review cap). The guards are carried by two tests, each with a red run.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Release ran inside the ExoPlayer ended callback | edge-1 F1 (triage-1 #1, major) | fixed | afb8b9b3; `theSwitchOfAnEndedDismissedEpisodeLeavesThePlayersEndedCallbackFirst` |
| AC-7 P1 test took the panel path | tests-1 F1 (triage-1 #2) | fixed | a7027fa1 |
| `nextEpisodeCountdown != null` branch untested | tests-1 F2 (triage-1 #3) | fixed | a7027fa1; `theEndedControlsOverARunningCountdown...` |
| R7 guard lacked `assertNull(nextEpisodeCountdown)` | blind-1 F1 (triage-1 #4) | fixed | `PlayerVMPromptDismissalTest`, `anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls` |
| A panel opened inside the deferral dropped the advance | edge-2 F1, codex-2 #2 (triage-2 #1) | fixed | babd220b; `aPanelOpenedInsideTheDeferral...` |
| No `closing` check in the deferral | codex-2 #1 (triage-2 #2) | fixed | babd220b; `leavingInsideTheDeferral...` |
| Device coverage incomplete | codex-1 (rejected) | rejection accepted: a process note, carried as NOT CHECKED (M1, M3) | triage-1 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One `PlayerContentState` copy and one short coroutine per media end; no hot-path work. |
| Reliability | Deferral re-checks `closing`, `episodeSwitchInProgress`, `countdownDismissed`, the countdown and the panel. `viewModelScope` cancellation covers onCleared. A missing next episode falls back to the ended controls. The order (mark watched, then release) is tested. |
| Maintainability | Matches AGENTS.md conventions; `advanceWhenPanelCloses` sits next to `exitWhenPanelCloses`; no dead code (`showControlsForEndedPlayback` is still used); `PlayerVM.kt` keeps its single responsibility. |
| Security / privacy | No secrets in the diff (grep). The evidence file passes `tools/mobile-evidence-audit.sh` (judge re-run: passed) and leaves series titles out. |
| TV | Focus stays on the panel opener when no advance follows (`onPanelClosed()` falls through to `checkEarlyNextEpisode()`). After the advance, focus is the normal switch focus (device M2). |

## Manual checks for the owner

1. M1, the X button (AC-1, AC-8). Start a series episode that is not the last. Seek to about 1 min before the end, then wait. When the "Next episode" pill appears, show the controls, move focus to the X next to the pill, press OK, and touch nothing until the end. Expected: the next episode starts from 0:00, with no ended controls and no spinner.
2. M3, the end under an open panel (AC-6). Cancel the pill with Back. Open the Episodes row with under about 6 s left, so the episode ends while the row is still open. Press Back to close the row. Expected: the next episode starts and the player does not exit.

## Must fix

none

## Concerns for the owner

none
