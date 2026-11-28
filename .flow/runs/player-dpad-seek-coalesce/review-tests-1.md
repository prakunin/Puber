LENS: tests  ROUND: 1  BASE: 8c3117ea
### F1 [minor] The two stream-failover commit sites have no test
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:479 (`switchToNextStreamCandidate`), :532 (`refreshStreamLinksOnce`)
- Problem: Only the `switchStreamUrl` commit is tested, through `aQualitySwitchCommitsThePendingTargetFirst`. If either of the other two `commitPendingSeek()` calls is deleted, every test still passes. The mutation check in the Implementation record also covered only the commit before `switchStreamUrl`.
- Failure scenario: A stream error during a held Right triggers failover to the next candidate or a link refresh. Without the commit, `PlaybackController.switchStream` reopens from the leading target, not the pending one, and the new stream plays from an earlier time until the trailing seek lands. No test notices.
- Fix: Add a case in `PlayerVMSeekCoalesceTest` that makes two presses, then raises a playback error through `callbackSlot.captured` so the VM moves to the next candidate. Assert `verifyOrder { seekTo(P + 20_000); switchStream(any(), any()) }` and `assertNoSeekAfter(P + 20_000)`. Do the same for the refresh path if the fixture can drive it.

### F2 [minor] Manual check 3 (seek prompt on a series intro) was never run on the device
- Where: /home/dev/Puber/.flow/runs/player-dpad-seek-coalesce/qa-report.md (AC-7 row, "device NOT CHECKED")
- Problem: The Test design's Manual 3 is the only check that a skip prompt stays hidden during a real hold and appears about 1.5 s after release. The qa run opened only a film, so on the device the prompt timing (risk R9, which the design names "Prompt flicker") is backed only by unit tests. The report says this openly. AC-7 itself is marked unit and is covered, so this is a gap in evidence, not a missing test.
- Failure scenario: On real hardware, the trailing seek's `onSeekPerformed` and the 1 500 ms burst interact through ExoPlayer's real seek callbacks rather than the mock. A flicker there would show only on a series episode with an intro, and nothing has observed that case.
- Fix: Have qa-device run Manual 3 on the box (a series episode with an intro segment) before gate 2, or have the owner accept AC-7 as unit-only.

Suite state: I ran `check.full` in the worktree at HEAD 50809306 (clean tree) and it exited 0. Unit results: `PlayerVMSeekCoalesceTest` 15/15, `PlayerVMSeekBurstTest` 7/7, `SeekHandlerTest` green; both tooling scripts passed. The Gradle tasks were mostly up-to-date, meaning results from an earlier run on the same inputs were reused.

What I checked and found sound:
- **Red runs:** every P0 `red` row (`theStepLadderRestartsAfterTheBurstEnds`, both AC-2 cases, `thePositionTickKeepsThePendingTarget`) has a recorded red run.
- **Guard rows:** every P0 `guard` row is marked so in the test design, and I reasoned each one fails when its guarded line is removed. That includes `resumeDropsThePendingSeek`, which the record says was not mutated.
- **Test behaviour:** the tests assert VM state and the recorded `seekTo` calls, not private calls. They run on virtual time only; the busy-wait on the wall clock in `SeekHandlerTest` is gone. They use `@RegisterExtension` with `MainDispatcherExtension`.
- **Weakened tests:** the only assertion removed is `callAfterTimeout_resetsToFirstStep`, which the spec planned because the reset moved into the VM.

### Unproven
- PlayerVM.kt:1014-1036 (`selectBufferPreset` / `toggleFastDns`, code this diff did not touch): these read `playbackController.currentPosition` with a pending target and no commit, and the live throttle job then seeks the re-created player. A test would need to show the trailing seek arriving before `initializePlayer` restores the position to make this real; the readiness note left the check to reviewers.
- PlayerVM.kt:2097 (`dropPendingSeek` in `onCleared`): no test covers it, but cancelling `viewModelScope` already stops the throttle job. It only matters if the job ever runs outside the VM scope.
