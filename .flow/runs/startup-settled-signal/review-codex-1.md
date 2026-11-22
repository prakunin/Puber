CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/update/vm/UpdatePromptVM.kt:51-56 - The deferred automatic update check can overwrite a manual flow already under way. Its only guard is `dismissedTagName`, and it is not tied to `manualCheckJob` or `manualCheckRequestId`. Scenario: auto check is enabled, startup has settled but the `UpdateCheck` slot has not yet elapsed, and the user opens Settings, runs a manual check, finds an update and presses Update. The state is now `Downloading`. The auto job then wakes, `checkForUpdate` returns, and `updateViewState(Available)` replaces `Downloading`. `isActiveDownload` then returns false, so progress and completion are dropped and the installer never launches. The same overwrite hits `Checking`, `PermissionRequired` and `Error`.
  - The race exists on base, but the window was one network call. Now it is the whole wait for the slot, so it is wider.
  - The reachable window is narrow: the user needs a manual check plus a download start in the few seconds after reveal.
  - Suggested fix: apply the auto result only when `stateValue == Hidden`, or cancel the auto job when a manual check or download starts.
  - The new test stops before the latch releases, so it misses this interleaving.
REJECTED:
- Plain `review` found no regressions in the latch, deferred scheduling or Home publish coalescing - nothing to adjudicate. I did not independently trace the latch or the HomeVM coalescing.
- Adversarial pass: the latch and publish-conflation changes show no supported blocking defect - the same, nothing to adjudicate.
VERDICT: changes needed (one minor finding; the implementer may choose to accept it)
