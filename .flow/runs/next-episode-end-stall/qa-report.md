# QA report: next-episode-end-stall

## Round 1

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` at 1239fd91 -> pass (BUILD SUCCESSFUL; tests were up to date from the implementer's run, result XML read: PromptDismissal 13, PositionPublishing 6, OverlayAutoHide 31, PlaybackFlow 32, CountdownClock 2, EpisodeFlow 15, all 0 failures, 0 skipped; `anEpisodesRowTimingOut...` present)
DEVICE: emulator-5554 (puber_tv_36), leased and released. Own dev APK installed with install-preserve (`installed`, `compatible_replace`, non-destructive); sha256 on device equals the build.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Unit `aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut` and `theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads` green. Device: cancelled prompt (Back), next episode started without ended controls or second countdown (M2). Cancel via the X button not reachable on device (M1, see Manual). |
| AC-2 | PASS | Unit `backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut` green. Device M2: Back on the prompt, player stayed, S2E14 loaded from 0:00 and played. |
| AC-3 | PASS | `playbackEnded_withNextEpisode_countsDownInsteadOfLeaving`, `nextEpisodeCountdown_switchesOnlyWhenTheWholeWaitHasRun`, `closePanel_startsCountdownDeferredWhilePanelWasOpen` green; device M4 twice (countdown ran out, next episode played). |
| AC-4 | PASS | `seekingAfterCancellingPutsTheNextEpisodeQuestionBack` green; seen on device (a 10 s seek after prompt put the prompt back). |
| AC-5 | PASS | `playbackEnded_onMovie/onLastEpisode_returnsToTheCallingScreen`, `playbackEnded_underOpenPanel_waitsForThePanelToClose`, `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays` green (unit only; not run on device). |
| AC-6 | PASS (unit) / NOT CHECKED (device) | All AC-6 rows green: `closingAPanelOver...`, `anEpisodesRowTimingOver...`, `pickingAnEpisodeOver...`, `aRestartUnderThePanel...`, `aCancelAfterTheEndUnderAPanel...`, three overlay-autohide guards. Device M3: the Episodes row closed on its 6 s timeout before the episode ended, so the ended-under-open-panel branch was not exercised. |
| AC-7 | PASS (unit) | `theEndedControlsShowTheFinalPosition`, `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition` green. No device observation (the ended controls no longer appear on this path). |
| AC-8 | PASS | Device M2: Back-cancel then watching to the end: next episode started from 0:00, title advanced, no spinner on the finished episode, no ended controls, no exit. M1 with the X button is NOT CHECKED; same `cancelCountdown()`. |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/next-episode-end-stall/docs/superpowers/evidence/2026-11-12-next-episode-end-stall-device.md (committed as 3bf1209d; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL:
- M1 with the X button: in a series episode that is not the last, seek to about 1 min before the end (before the prompt), when the "Next episode" pill appears move focus to the X button and press OK, then watch to the end. Expected: next episode starts at once. I could not reach the X with the D-pad inside the 5 s window (LEFT seeks when controls are hidden).
- M3 (AC-6): cancel the prompt, open the Episodes row while the episode has under ~6 s left so it ends with the row open, then press Back. Expected: next episode starts. The row's 6 s auto-hide beat my timing.
- Side effect: playback marked watch progress for a few series on the owner's account (episodes seen in M2/M4).

## Round 2

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:assembleDevDebug` at a7027fa1 -> pass (BUILD SUCCESSFUL, re-run for the APK)
DEVICE: emulator-5554 (puber_tv_36), leased and released. Own dev APK (sha256 4d103c20...) installed with install-preserve (`installed`, `compatible_replace`, non-destructive); on-device sha256 equals the build.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Round-1 unit tests; device M2 on afb8b9b3: after the prompt was cancelled the next episode started at the end, no ended controls, no spinner, no crash. M1 (X button) NOT CHECKED. |
| AC-2 | PASS | Device M2: `BACK` on the prompt (player stayed open), next episode (S2E14) started from 0:00 at the end. |
| AC-3 | PASS | Device M4: no cancel, pill counted 6 to 1, next episode played from 0:00 (also seen unattended three more times). |
| AC-8 | PASS | Device M2 as above, no exit and no stall. |
| AC-6 | PASS (unit) / NOT CHECKED (device) | M3 not reachable: the Episodes row cannot be opened inside the ~5 s prompt window and survive its 6 s auto-hide to the end. |
| AC-4, AC-5, AC-7 | PASS (unit) | Unchanged from round 1. |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/next-episode-end-stall/docs/superpowers/evidence/2026-11-12-next-episode-end-stall-device.md (round 2 section, committed; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: M1 (X button) and M3 (end under an open Episodes row, then Back) as in round 1.
Logcat: no FATAL / AndroidRuntime / IllegalStateException / ANR around the M2 switch; one W-level framework `IllegalStateException` log (Choreographer handler on a dead codec thread) seen once during an unattended switch, no crash.
