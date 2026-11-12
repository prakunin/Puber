LENS: tests  ROUND: 3  BASE: ce2444ea

No findings.

I ran `PlayerVMPromptDismissalTest` at babd220b with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '...PlayerVMPromptDismissalTest'` and it passed. I did not re-run the full suite.

Both new tests would fail if their fix broke:
- **Triage-2 #1, `aPanelOpenedInsideTheDeferralOfAnEndedDismissedEpisodeStartsTheNextOneOnClose`:** the panel opens after `onPlaybackEnded()` and before the 1 ms delay runs out, so it goes through the new `activePanel != None` branch. If that branch only returned, `onPanelClosed()` would find `advanceWhenPanelCloses` false and `verifySwitchedTo1x2()` would fail. That matches the red run in the plan's "Fix round 2" record.
- **Triage-2 #2, `leavingInsideTheDeferralOfAnEndedDismissedEpisodeDoesNotSwitch`:** `verify { router.back(...) }` runs before the time advances, so `closing` is already set. Without `closing` in the re-check, the deferred block would reach `playNextEpisodeOrShowEndedControls()` and `release()` would be called, and the test would fail. That also matches the recorded red run.
- **Older tests:** the existing panel-at-end test (`closingAPanelOverAnEndedDismissedEpisodeStartsTheNextOne`) and the plain-deferral test (`aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut`) still cover the paths this diff did not change. No assertions were removed.

### Unproven
- /home/user1/orca/workspaces/Puber/next-episode-end-stall/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1426 `controlsStateMachine.markPlaybackEnded()` in the new branch is not asserted by any test. It only matters when a panel opens inside the 1 ms window and `playNextEpisode()` then returns false. It would be real only if there is evidence the next episode can fail to resolve after `hasNextEpisode` was true.
