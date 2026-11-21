LENS: edge  ROUND: 1  BASE: 7bcbfd83

### F1 [minor] The update prompt can now take focus while the user's first keys are going to Home, so a DPAD_CENTER meant for a card can start the update download
- Where: app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:52 (`awaitSlot(StartupSlot.UpdateCheck)`), app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:167 (`screenFocusActive = !updatePromptOpen`), app/src/main/java/com/kino/puber/ui/feature/update/component/UpdatePromptOverlay.kt:60-66 (focus request with no input guard)
- Problem: at base the auto check ran under the splash, and `CoveredKeyFilter` swallows keys while the splash covers, so the prompt opened before any key could reach the app. Now it opens about 1 s after settle (splash gone plus 1000 ms). That is inside the window the run itself measures for first input (keys in the first 3 s after `revealed`). Once focus moves to the primary Update button, nothing ignores a key that was already on its way. The spec's Risk 1 covers focus moving to the prompt and Back. It does not cover an accidental activation.
- Failure scenario: a newer release exists. The user cold-starts and, as Home reveals, presses DPAD_RIGHT twice, then DPAD_CENTER on a card. The prompt opens between those presses and takes focus. DPAD_CENTER triggers `UpdateClicked`, and the APK download and installer flow start instead of Details opening. AC-12 was not checked on a device (qa-report: "Manual 2 not run"), so nothing has looked at this timing.
- Fix: in `UpdatePromptOverlay`, ignore confirm keys for a short guard (for example about 300 ms) after the prompt first takes focus. Or, if the owner agrees, skip the auto prompt while a key arrived less than N ms ago and retry later.

No other boundaries are left without handling and with a reachable scenario. These ones are handled:
- Latch inputs: restored, fresh, a second fresh activity, a settle arriving twice, an activity destroyed mid-fade (the fallback settles it), and process restart after Back.
- Each slot waits inside `viewModelScope`, so the work is cancelled on logout or domain switch.
- Login on Auth (AC-8): the slots count from Main start.
- The watch-state slot is paid once.
- A held prefetch warm takes no permit and is cancelled by a focus change.
- HomeVM coalescing:
  - Every caller writes its state before it takes its number (`collectSection`, `collectCollections`, `markHeroSettled`, `refreshHomePresentation`).
  - The epoch-skip records the captured `upTo`, not the live counter.
  - A cancelled in-flight publish records nothing, so the next queued request maps.
  - `applySavedOverrides` and `currentDialogState` are read live at publish time, so the covering publish is never staler than the requests it skips.

### Unproven
- app/src/main/java/com/kino/puber/core/startup/StartupSettled.kt:38-42 Fallback fires while the splash still covers: the 5 s timer runs on `Dispatchers.Default` from `onCreate`, so a main thread jammed for more than 5 s (slow real TV, JIT) would settle before the fade. Real evidence would be a `SettledMeasure settled fallback` line on `startup-first-input-on-tv` hardware.
- app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:51-58 The delayed auto-check result sets `Available` whatever the current state is. It could overwrite a manual check's `Downloading` or error state if the user starts a manual check within about 1 s of the auto slot. That needs a user who reaches Settings and checks within about 1.25 s of the reveal; nothing shows that happens.
- app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:557 (`awaitPublishesDrained`) This test-only hook hangs if the last request counted is cancelled while it waits for `publishMutex` and no later request comes. It would matter only if a test cancelled the load job as its last step and then drained.
