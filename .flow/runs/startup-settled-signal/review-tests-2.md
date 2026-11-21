LENS: tests  ROUND: 2  BASE: 7bcbfd83

### F1 [minor] No test checks that the Update-press guard is limited to the automatic prompt
- Where: app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:175-195; app/src/test/kotlin/com/kino/puber/ui/feature/update/vm/UpdatePromptVMTest.kt:111, :267-361
- Problem: `inputGuardActive` is set only in `showAutomaticPrompt`. Every existing test that presses Update reaches `Available` through the automatic path and calls `passInputGuard()` before the press. No test reaches `Available` through a manual check, or `Error`, and presses Update straight away. Suppose a later edit moves the guard into `updateViewState(Available)` or into `checkForUpdateNow`. Every test stays green, yet the manual "Check for updates" flow then drops its first press.
- Failure scenario: someone sets the guard for every `Available` state. The user runs a manual check, sees "Available" and presses Update at once, within 500 ms. Nothing happens, and no test fails.
- Fix: add a test like `manualCheck_updateClicked_startsDownloadAtOnce`: `requestManualCheck()` returns an update, `UpdateClicked` follows with no `passInputGuard()`, then assert `downloadUpdate` was called once.

Suite state: I ran only the tests the incremental diff touches (StartupSettledTest, DetailsPrefetcherTest, HomeVMIncrementalPublishTest, UpdatePromptVMTest). BUILD SUCCESSFUL, all green.

How the round 1 fixes hold up:
- **T1:** the red line for `restoredActivitySettlesAtOnce` is now in the plan's Implementation record, from a stub where `restored = true` only arms the fallback. Closed.
- **T2:** the new test covers `Checking`. The same `stateValue == Hidden` condition covers the other manual states too. A red run is recorded.
- **T3:** the new test fails without the guard (a download starts) and also fails if the guard never clears (the second press is checked). A red run is recorded.
- **T4:** `awaitPublishRequestsBeyond(requestedBeforeReset)` waits for the right event. The stale publish was counted before the reset. `resetApiDomain` → `clearRowsFromPreviousCatalogue` → `loadHome` add no publish request. Every source except `newWatching` is a flow that never emits, and Hot never completes, so `markHeroSettled` cannot add one either. That leaves the new Watching arrival as the first increment.
- **T5:** the test was dropped, and the spec and plan rows explain why.
- **T6:** the new test fails if the `!settled.isCompleted` guard is removed (one child coroutine is left in the scope). The plan cites line 58 for that failure, which is the test's declaration. The assertion that would fail is on line 67.

### Unproven
- UpdatePromptVM.kt:175-181 The guard flag outlives a dismiss. If the auto prompt is dismissed and a manual check returns `Available` within 500 ms, that first press is ignored. It becomes real only if a manual check can finish in under 500 ms after a dismiss; it is a network call.
