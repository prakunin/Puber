LENS: tests  ROUND: 2  BASE: 6b087697

No blockers or majors. Each round-1 row assigned to this run now has a test that fails when its behaviour breaks, or an honest note in the plan saying why it has none:

- #6: in `everySeekInTheBurstRestartsTheWindow`, the new null check at `:153` comes after the 2 000 ms tick. If the window does not restart, it fails.
- #7: `aSeekBackwardBurst...` fails without `startSeekBurst()` in `seekBackward` (`PlayerVM.kt:728`). `onSeekPerformed` does not open a window by itself, so the tick at 1 000 ms raises the prompt.
- #3: `creditsLoadedMidBurst...` exercises the new check. `testOnStart` does not advance time, so the `delay(500)` load finishes inside the window, at a position past the credits start.
- #5: the red run is recorded, and the green-on-base test is marked as a guard.
- #8: "device-only (M2)" is recorded.

`PlayerVMSeekBurstTest` run in the worktree with `./tools/agentw`: green. Instrumented tests not run.

### F1 [minor] The fix that makes the cancel X unfocusable has no automated test
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerTimedActionOverlay.kt:379`
- Problem: triage #1 (major) is fixed in production. The plan says "No androidTest asserted focus on the X", and the only check is qa's M4 re-capture on the device.
- Failure scenario: someone drops the `focusProperties { canFocus = false }` line. All tests stay green, and Up from the seek bar lands on the X again (the original device shot m4).
- Fix: add a Compose case to `PlayerPromptFocusAndKeysTest`. Start in `promptAppearingUnderControls...`, press Up, and assert that the X node is not focused. Or, at the least, confirm that the M4 re-capture after Up is in the evidence before gate 2.

### F2 [minor] The red-run record gets its own counts wrong
- Where: `.flow/runs/seek-blocked-by-skip-prompt/red-run-b7854fbb.md` header, copied into the plan's Implementation record ("5 pass / 6 fail")
- Problem: the header says 11 tests and 6 FAIL. The table it heads lists 12 tests, 7 FAIL and 5 PASS. `PlayerPromptFocusAndKeysTest` has 12 `@Test` methods.
- Failure scenario: a reader cannot tell whether one test failed or never ran in that run.
- Fix: correct both lines to "12 tests, 5 pass / 7 fail", or name the test that did not run.

### F3 [nit] The plan's mutation-check line numbers point at the test headers
- Where: `docs/superpowers/plans/2026-11-10-seek-blocked-by-skip-prompt.md`, Fix round 1
- Problem: `:117`, `:141` and `:98` are the `runTest(...)` lines of the three tests. The assertions that fail are at `:132`, `:153` and `:108`.
- Fix: cite the assertion lines.

### Unproven
- `PlayerPromptFocusAndKeysTest.kt:209` `skipPrompt_okUpWithoutDownDoesNotSkip`: the OK-up check itself was never red, and the record says so. It would become a gap only if an AC requires the `ACTION_UP` guard, which Task 4 deliberately left out.
