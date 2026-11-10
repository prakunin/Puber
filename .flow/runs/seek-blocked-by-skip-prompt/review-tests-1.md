LENS: tests  ROUND: 1  BASE: 6b087697

### F1 [major] Two new P0 `red` Compose tests have no valid red run on the unchanged code
- Where: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt:147` (`skipPrompt_appearsWithActionFocused_okSkips`) and `:167` (`promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks`). The P1 `red` test `controlsHidingWhilePromptIsUp_focusesThePromptAction` (`:184`) has the same gap.
- Problem: in `red-run-task3.md`, these tests failed only because the `action(label)` matcher found no node, which was a bug in the test. The matcher fix landed in `b7854fbb`. The plan's Implementation record still says "red pending re-run after `b7854fbb`", and no later run exists: qa-device ran the suite only at `35f9866e`, after the production change. So no run has shown these P0 tests failing for the bug they target.
- Failure scenario: if the corrected matcher picks the wrong node, or the focus assertion is vacuous, the suite still goes green and the AC-5 and AC-6 rows look covered when they are not. Reading the code, both tests should fail on base: the base overlay focuses the X on appear, so `:150` fails, and a Right press on the X moves focus without seeking, so `:180` fails. That conclusion is reasoning only, not a recorded run.
- Fix: production code at `b7854fbb` is still identical to base. Run `make DEVICE=<serial> itest TESTS=...PlayerPromptFocusAndKeysTest` on that commit (qa-device, from a separate Orca worktree). Record the failing line of each of the three tests under "Red run" in the plan and close the "pending re-run" note.

### F2 [major] `everySeekInTheBurstRestartsTheWindow` cannot fail when the window is not restarted
- Where: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMSeekBurstTest.kt:94-114`
- Problem: the test checks the prompt only once, at 3 000 ms, right after the third seek. That seek calls `onSeekPerformed()`, which clears `activeSkipSegment` (`PlayerVM.kt:1687`) and opens a new window. The mutation the test claims to catch therefore survives.
- Failure scenario: change `startSeekBurst()` to `if (seekBurstJob?.isActive != true) seekBurstJob = launch { delay(..) }`, so the window is not restarted per seek. The window then runs 0 to 1 500 ms, and the seek at 1 000 ms does not extend it. The 2 000 ms tick raises the skip prompt in the middle of the burst, which is the original bug. The seek at 2 000 ms then clears the prompt and starts a new window to 3 500 ms. At 3 000 ms the prompt is null, the assertion passes, and the prompt is set again at 4 000 ms. The test stays green.
- Fix: also assert `assertNull(contentState(vm).activeSkipSegment)` after each `wait(1_000)`, before the next `seek(vm)`. The 2 000 ms check is the one that catches the mutation.

### F3 [minor] Starting the burst on `seekBackward` has no test
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:728`
- Problem: every `PlayerVMSeekBurstTest` case seeks with `PlayerAction.SeekForward`. AC-3 covers "a Left/Right burst".
- Failure scenario: delete `startSeekBurst()` from `seekBackward`. Every unit test stays green, yet rewinding with Left back through an intro or the credits lead-in raises the prompt in the middle of the burst. The Compose tests cannot catch this because they stub the VM.
- Fix: add one case that starts after a segment, sends `PlayerAction.SeekBackward` with `seekTo` landing inside it, and asserts the prompt is null at +1 000 ms and set after the window plus one tick.

### F4 [minor] The new focus-on-hide branch and its retry are covered by no test that fails without them
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreenContent.kt:86-90`, `:109-113`
- Problem: `skipPromptHidden_nextRightSeeks` and `nextEpisodePromptHidden_nextRightSeeks` pass on base, so they are recorded as guards. As a result, nothing fails if the `null -> focusRequesters.player` request when a prompt hides is removed, or if `requestFocusOrRetryNextFrame` loses its second attempt. The record is honest about this, but AC-4's new code path is untested, and only the device run M2 backs it.
- Failure scenario: someone simplifies the effect back to "only on controls or panel change". Unit and Compose tests stay green, and only a manual device pass would notice.
- Fix: accept this and say so explicitly in the plan ("AC-4 hide refocus: device only, M2"). Or add a Compose test where the prompt hides while focus would otherwise land on a non-surface node, and assert that the surface (the `player` requester) is focused, not only that Right seeks.

### Unproven
- `PlayerVMSeekBurstTest.kt:41` The segment stub ends a segment with `position < endMs`, but the real `SkipSegmentInteractor.findActiveSegment` uses `<=`. The guard test therefore runs under different boundary rules than production. It would matter only if real segment data had back-to-back segments within the 2 s lead-in, where a skip landing on `endMs` picks the old segment again.
- Suite state: I did not re-run the check. qa reported `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` green at `35f9866e`, and the only commit after it, `b6660c74`, adds evidence files and touches no code.
