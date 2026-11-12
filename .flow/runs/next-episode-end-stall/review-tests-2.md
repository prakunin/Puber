LENS: tests  ROUND: 2  BASE: 3bf1209d

No findings.

The two test classes this diff touches pass at `a7027fa1` (`PlayerPositionPublishingTest` and `PlayerVMPromptDismissalTest`, run with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests ...`). I did not rerun the full check this round.

All four triage rows are fixed, and the new tests would fail if the behaviour broke:
- **#1:** `theSwitchOfAnEndedDismissedEpisodeLeavesThePlayersEndedCallbackFirst` checks that nothing has switched right after `onPlaybackEnded()`, then checks the switch after 2 ms. On 3bf1209d the switch ran inline, so this test fails there: it is a valid red on the unchanged code, not only against the `yield()` variant. The order test (mark watched, then `release()`) now advances time before it verifies.
- **#2:** the publishing test now uses the R7 setup: no panel, `HideControls`, then cancel. The position update runs before the `when`, so every branch is covered.
- **#3:** the new countdown-running test checks that the controls show, the countdown stays and nothing is released. It guards a branch that already existed, and that is how the plan records it.
- **#4:** `assertNull(nextEpisodeCountdown)` is in place.

Other test classes that end a dismissed episode still pass. `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime` (PlayerVMOverlayAutoHideTest.kt:420) now asserts `controlsVisible` before the delayed step runs. Its later 60 s step runs that step and asserts again, so the ended-controls fallback is still covered. Its tail ends in `router.back`, which would fail if it had wrongly switched.

### Unproven
- /home/user1/orca/workspaces/Puber/next-episode-end-stall/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1420-1422: no test covers the checks inside `advanceAfterDismissedEnd`. If a panel opens, a switch starts or the countdown comes back during the 1 ms window, the block returns without setting `advanceWhenPanelCloses`. This becomes real only if some input or callback can arrive inside that 1 ms on a device.
