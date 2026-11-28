LENS: tests  ROUND: 2  BASE: 50809306

### F1 [minor] Manual 3 (AC-7 on device) is still not checked after round 2
- Where: `docs/superpowers/evidence/2026-11-28-player-dpad-seek-coalesce-device.md` ("Round 2: Manual 3")
- Problem: Round 1 triage item #3 was to run Manual 3 on a series episode that has an intro segment. The round 2 attempt opened one series, S1E1. That episode has no intro segment in the data the app received, so the evidence ends with "Manual 3 stays NOT CHECKED". AC-7 still has unit coverage: `PlayerVMSeekBurstTest`, including `aHeldKeyKeepsThePromptDownAndTheTrailingSeekStartsNoCountdown`, and `PlayerVMSkipCountdownFitTest`. So the AC has tests, but the device check that triage asked for has not happened.
- Failure scenario: a skip prompt that comes up on real hardware during a held-key burst, for example because the segment loads late or ExoPlayer reports a seek at a different time, cannot be caught. The fake `onSeekPerformed` timing in the unit tests is then the only guard.
- Fix: run Manual 3 once on a series episode known to have an intro segment, at human pace. Otherwise, at gate 2, record the gap as accepted with the unit tests as the coverage instead of leaving it open.

What I checked, for the conductor:
- I ran the player test package (`:app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.*'`) through `./tools/agentw`, and it is green. `PlayerVMSeekCoalesceTest` ran all 18 cases with 0 skipped and 0 failures, including the three new ones.
- `aFilmEndingWithAPendingTargetSeeksThereInsteadOfExiting` would fail on three wrong versions of the fix:
  - no commit: the last seek would be 2 400 000;
  - drop instead of commit: same result;
  - commit without the early return: `exitPlayer` would call `router.back`, which the test checks never happens.
  - Its red run on the unchanged code is in the plan's Implementation record ("`router.back(1001, ...)` was called").
- `aStreamFailureCommitsThePendingTargetBeforeTheNextCandidate` and `aLinkRefreshCommitsThePendingTargetBeforeTheNewStream` are recorded as green on base and are labelled guards in the plan. That fits: the commit sites at `PlayerVM.kt:479` and `:532` were already there. Removing either commit would make its test fail. With no time advance before `verifyOrder`, the 250 ms trailing loop has not run, so `seekTo(P + 20_000)` would be missing. The checks on `switchStream(fallback|refreshed, any())` show that each test reaches its intended path.
- The tests behind triage items #1 and #2 are both in place. No assertions were removed in this diff.

### Unproven
- `PlayerVM.kt:1430` A held Right key whose pending target equals the duration lands there through the early return, so the end handling only runs on a second `STATE_ENDED`. No test calls `onPlaybackEnded` twice in a row. It would become real if `PlaybackController` did not re-send ENDED after a seek to the end while already ended.
