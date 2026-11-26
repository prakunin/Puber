# Device evidence: diagnostics-transport-label (round 1)

Date: 2026-11-26. Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only.
Build: worktree dev debug APK, sha256 e46d4586...ed734c after the final reinstall (install-preserve outcome installed, compatible_replace, no uninstall, signer unchanged). Session saved with auth-save before the first install.

## Instrumented: NetworkDiagnosticsContentFocusTest (make DEVICE=192.168.1.110:5555 itest)

Run twice on the worktree head: 8 tests, 6 pass, 2 fail, same result both times.

| Test | Result |
|---|---|
| initialStateStartsAmsterdamFromTheFocusedCard, launchFocusRecovers_whenTheRailTakesFocusDuringTheSettleDelay, focusedServerCardKeepsFocus_whenTheRunStarts, serverCardKeepsFocusWhenResultsChange, currentServerCardRegainsFocusAfterStateRestoration | PASS (first focus guards for AC-7) |
| transportLine_isAbsent_whenTransportNull (AC-6) | PASS |
| transportLine_isShownAndNotFocused_whenEthernet (AC-7) | FAIL at assertIsNotFocused: the node exists with Text = [Подключение: Ethernet] but has no Focused semantics key, and assertIsNotFocused requires Focused = false |
| serverCard_startsThatServerOnPress | FAIL, also on base 4c398e07 (6 tests, same failure with app/src at base): pre-existing, not caused by this change |

## Manual M1 / M2

Not run. The account on the box answers every API request with Unauthorized: user_banned (seen in the app's own failed Settings load). Network diagnostics opens only from the device settings screen (DeviceSettingsVM.kt:245), which needs that API call, so the screen cannot be reached. No workaround attempted; language was not switched.


## Round 2 (after fix 03265fbc, test-only)

- Device: 192.168.1.110:5555, com.kino.puber.stage. App APK unchanged (test-only fix), so no install-preserve; `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.device.diagnostics.NetworkDiagnosticsContentFocusTest` ran with its usual install, nothing uninstalled or cleared.
- Result: 8 tests, 7 passed, 1 failed.
- transportLine_isShownAndNotFocused_whenEthernet: PASS (was FAIL in round 1; AC-7 line not focused).
- transportLine_isAbsent_whenTransportNull: PASS (AC-6).
- First-focus guards (initialStateStartsAmsterdamFromTheFocusedCard, serverCardKeepsFocusWhenResultsChange, focusedServerCardKeepsFocus_whenTheRunStarts, currentServerCardRegainsFocusAfterStateRestoration, launchFocusRecovers_...): PASS.
- serverCard_startsThatServerOnPress: FAIL (expected Start(server=Moscow) but was empty); same failure on base 4c398e07 in round 1, not caused by this change.
- Settings was not retried (account ban); M1/M2/M3 remain manual.
