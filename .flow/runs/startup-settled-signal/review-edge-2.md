LENS: edge  ROUND: 2  BASE: 7bcbfd83

No findings.

### Unproven
- app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:175-181 The input guard is measured from the moment the state becomes `Available`, not from when the Update button takes focus, which is the anchor T3 asked for. `UpdatePromptOverlay.kt:59-67` first asks for focus after 100 ms and can retry for up to about 900 ms when the target is not attached yet. If focus lands after 500 ms, a confirm press meant for Home reaches Update with no guard left. This becomes real if device evidence shows the prompt taking focus more than about 400 ms after the state change. The fix would be to start the guard from the overlay's successful `requestFocus`, or to make the guard longer than the retry window.
- app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:85-91 Back is not guarded. A Back press meant for Home in the first moments after the prompt appears dismisses it and records the version in `dismissedTagName`, so the prompt does not come back for the rest of the session. This becomes real if users actually press Back on Home within about 0.5 s of the reveal.
- app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:62-65 The automatic result is now thrown away in any state other than `Hidden`. When the user has a manual `UpToDate` or `CheckError` dialog open, the update that was found is never shown after they close it, for that session. This becomes real if a manual check can start within roughly 1 s of the reveal, before the deferred automatic check returns.

I checked three things in the test changes and found no problem:
- **`awaitPublishRequestsBeyond` hook** (HomeVM.kt:560-564 and HomeVMIncrementalPublishTest.kt:951-958): `resetApiDomain` → `clearRowsFromPreviousCatalogue` makes no publish request. Every section except the new watching flow never completes, so the only request counted after `requestedBeforeReset` is the new arrival. `publishSections` counts it in `updateAndGet` before it waits on the mutex, so the wait cannot return before the arrival has queued.
- **`aFreshActivityAfterTheSplashIsGoneStartsNoFallback`**: the scope has its own `Job`, so a fallback timer left running would show up as a child and fail the test.
- **Dropping `aWarmHeldForStartupTakesNoSlot`**: the spec and the plan both record it, and no production code changed with it.
