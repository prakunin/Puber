LENS: tests  ROUND: 1  BASE: 4db9c0d4

### F1 [minor] The P1 AC-7 "missing next episode" test takes the panel path, not the R7 path the spec names, so it cannot catch what it says it catches
- Where: /home/user1/orca/workspaces/Puber/next-episode-end-stall/app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerPositionPublishingTest.kt:113-129 (spec Test design row "AC-7 P1 `theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition`")
- Problem: The spec row asks for "the R7 setup": no panel, `HideControls`, dismissed, then `onPlaybackEnded()`, which goes through `playNextEpisodeOrShowEndedControls()` and then `showControlsForEndedPlayback()`. The test instead opens Settings, ends the episode, cancels and closes the panel. That is the same path as the P0 test just above it; the only change is the `findNextEpisode` stub, and the panel path never reads it. Both AC-7 tests therefore cover only the "ended under a panel, then closed" path. No test covers the two no-panel paths that show the ended controls: the R7 fallback, and the `nextEpisodeCountdown != null` branch.
- Failure scenario: Move the `updateContent { copy(currentPosition = …) }` block from the top of `onPlaybackEnded()` into the `content.activePanel != ActivePanel.None ->` branch. Both AC-7 tests stay green. With no panel open, the ended controls (fallback, or countdown already running when the media ends) then show the stale seek bar position from Root cause 2. AC-7 says "any path".
- Fix: Rewrite the P1 test to the spec's R7 setup: `findNextEpisode` null, `HideControls`, `CancelNextEpisodeCountdown`, controller at 2_410_000, `onPlaybackEnded()` with no panel. Then assert `controlsVisible` plus `currentPosition`/`duration` equal 2_410_000.

### F2 [minor] The reordered `nextEpisodeCountdown != null` branch has no test
- Where: /home/user1/orca/workspaces/Puber/next-episode-end-stall/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1405 (`content.nextEpisodeCountdown != null -> showControlsForEndedPlayback()`)
- Problem: The diff rewrote the `when` and gave this condition a branch of its own. No test, new or existing, ends the media while the countdown is running with no panel open. The test files calling `onPlaybackEnded()` (PromptDismissal, PlaybackFlow, CountdownClock, EpisodeFlow, OverlayAutoHide, PositionPublishing) either end it with no prompt up, cancel first, or open a panel. The countdown lasts about 5-9 s and the prompt appears in the credits, so this case comes up in ordinary use. On the device it showed up as M4.
- Failure scenario: Delete that branch, or swap it below `countdownDismissed`/`else`. An end that arrives during the countdown then falls to `startNextEpisodeCountdown()`, which restarts the countdown or does nothing, and the ended controls never appear. The whole suite stays green.
- Fix: Add a guard test. Raise the prompt with the tick (2_380_000, `advanceTimeBy(600)`), call `onPlaybackEnded()` without cancelling, then assert `controlsVisible` true and that the countdown value has not been reset. Optionally also check that `release()` happens after the original wait, not a fresh one.

Suite state (round 1): I re-ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug --rerun-tasks` at HEAD 3bf1209d. Result: 2068 tests, 0 failures, 0 errors, 0 skipped. Lint reports were written. Nothing is skipped, and the only removed test is the planned rewrite of `aCancelledNextEpisodeIsNotOfferedAgainWhenTheEpisodeRunsOut`.

Traceability:
- **P0 rows:** every one has a test under its spec name.
- **Red runs:** the Implementation record in /home/user1/orca/workspaces/Puber/next-episode-end-stall/docs/superpowers/plans/2026-11-12-next-episode-end-stall.md shows a red run for every P0 `red` row (AC-1, AC-2, AC-6, AC-7). Every P0 test that was green on base is a `guard` row.
- **Guards:** `pickingAnEpisodeOverAnEndedDismissedEpisodePlaysThePickedOne` and `aRestartUnderThePanelCancelsTheAdvanceOnClose` can actually fail. The main dispatcher is `UnconfinedTestDispatcher`, so the switch runs synchronously and `episodeSwitchInProgress` would drop the picked episode.
- **AC-8:** checked on the device through M2 (Back cancel).
- **Not checked on the device:** M1 (the X button) and M3 (AC-6). AC-6 rests on unit tests only, as the qa report says.

### Unproven
- PlayerVMPromptDismissalTest.kt:113-122 (AC-1) does not assert "the ended controls are not shown" from AC-1's Then clause. Only the switch is verified. It would only matter if a regression switched episodes and also raised the controls on the old Content, and there is no sign of that.
