LENS: tests  ROUND: 1  BASE: 0bc3ab0e

Suite state: I did not rerun check.full. The brief says it was green at HEAD 5994eedd. The worktree is clean at that HEAD and the code has not changed since. I reasoned about the instrumented tests without running them.

### F1 [major] AC-4 and AC-6 in the AC-2 order (prompt over content that already has focus, then Back) have no test and no device observation
- Where: docs/superpowers/evidence/2026-11-20-update-dialog-focus-trap-device.md (Round 2); app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:104-110, 116-119, 191-199
- Problem: AC-4 requires Back to dismiss the prompt "in either order of AC-1 and AC-2". In round 2, Back was only pressed in the launch-race order (cold-start runs 1 and 2). The prompt-over-focused-content path (Settings, "Check for updates now") was dismissed only with Not now. The evidence says the order "Home holds focus before the prompt" was never seen on a cold start. No instrumented test covers it either:
  - `dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail` copies Main's `LaunchedEffect(outerActive) { beginInitialHandoff() }` into the test, so it never runs Main's code.
  - Nothing exercises the D7 `outerActive &&` in `RailClosedBackHandler` / `RailOpenBackHandler`.
- Failure scenario: the user opens Device settings, runs the manual check and gets `Available`, then presses Back. Back goes through the gated handlers and the handoff started by `outerActive -> false`. Round 1 showed that this dismissal can leave focus on the rail. A regression of the `outerActive` wiring, or a Back that lands on the rail, would pass every test and every recorded device step.
- Fix: in M3/M2, add a Back dismissal on the Settings path (prompt over focused content): rail closed, focus on a visible settings item, next D-pad moves within settings. Record it in the evidence file.

### F2 [minor] `launchRequestReArmsWhenGateOpens` cannot catch the mutation the test design says it catches
- Where: app/src/androidTest/kotlin/com/kino/puber/core/ui/uikit/component/modifier/ContentFocusGateTest.kt:75-98; app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:74-85
- Problem: the gate is closed when the composition starts. So `if (!isFocusRequested && !isDrawerOpen && autoFocusEnabled)` is false and no coroutine is ever scheduled while the gate is closed. The D4 branch (scheduled, gate closes, flag must stay unset) never runs in this test. `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` does reach that branch, but never reopens the gate.
- Failure scenario: someone moves `isFocusRequested = true` above the `latestAutoFocusEnabled` check (the mutation the spec row names). Both tests stay green. In the real launch race (request scheduled, prompt opens within 100 ms), the launch target is never requested again after dismissal.
- Fix: at the end of `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus`, set `autoFocusEnabled = true`, call `advanceTimeBy(500)`, then `assertIsFocused()`.

### F3 [minor] `GateFocusRestoreEffect` (D8) has no test, and the device run says it does nothing
- Where: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:172-175, 659-680
- Problem: no test drives `screenFocusActive` through a `FlowComponent`. The fix-round record says that on the device "its restore returns false and the handoff does the work". Deleting or breaking the effect would leave every test and every device step unchanged.
- Failure scenario: a later change makes `restoreFocusedChild()` succeed at the wrong time, for example before the handoff settles, and it races the handoff. Nothing notices this until a device run.
- Fix: either add an instrumented test through `FlowComponent(screenFocusActive = ...)` that asserts the saved child is restored, or remove D8 and keep the handoff as the single owner of focus on dismiss.

### F4 [minor] `updatePromptDpadInAllDirectionsStaysInsidePrompt` does not check that the "from Later" half ever runs
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/update/component/UpdatePromptOverlayFocusTest.kt:131-152
- Problem: the test only checks that no sentinel is focused. It never checks that Right (press #3) moved focus to Later. If Right stayed on Update, the four "from Later" presses would repeat the Update checks and still pass. AC-3 says "on every button".
- Failure scenario: a layout or trap change stops Right from reaching Later, or the trap cancels the move inside the prompt. The test stays green, and the AC-3 coverage for Later is gone.
- Fix: after index 3, call `assertFocusOn(laterText)` (from `R.string.update_prompt_not_now`). After the last Left, call `assertFocusOn(updateText)`.

### F5 [minor] No test fails if the AC-2 retry loop is removed
- Where: app/src/main/java/com/kino/puber/ui/feature/update/component/UpdatePromptOverlay.kt:58-68; app/src/androidTest/kotlin/com/kino/puber/ui/feature/update/component/UpdatePromptOverlayFocusTest.kt:97-116
- Problem: `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` is marked guard, which is allowed. The implementer recorded that it passes with the old single attempt too. The retry loop (D6), the actual fix for "the initial claim misses an unattached button", has no test that fails on the old code. The device evidence gives "under 2.7 s including the dump time", not the 1 s that AC-2 requires.
- Failure scenario: the loop is reverted to one `runCatching { requestFocus() }`. The suite stays green, and the launch-race claim on an unattached button is lost again.
- Fix: make the button attach late in the test, for example by composing the overlay's content after a gated delay, so that a single attempt at 100 ms fails and the retry succeeds before 1000 ms.

### Unproven
- app/src/main/java/com/kino/puber/core/ui/uikit/component/TvDialogOverlay.kt:22-30 `FocusDirection.Next`/`Previous` are trapped but untested. This would matter only with evidence of a Tab key path on a TV remote or keyboard.
- app/src/androidTest/kotlin/com/kino/puber/ui/feature/update/component/UpdatePromptOverlayFocusTest.kt:66-93 the hero stand-in's `contentActive` condition is the test's own code (it matches `HomeScreenContent.kt:193-197`). If Home's real claim stopped reading the local, this test would not notice. That needs a Home-level regression to matter.
