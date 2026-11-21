# Device evidence: startup-settled-signal (round 1), head aacf6035

Run slug startup-settled-signal, 2026-11-21. Device emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU), leased for the whole run and released at the end. Nothing uninstalled, cleared or downgraded; every install through install-preserve (outcome installed, compatible_replace, destructive_action false, candidate signer = installed signer 6b2918...a83e, versionCode 2090000000).

## Static gate

- `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree at aacf6035: BUILD SUCCESSFUL (every task up to date from the implementer's run); to be sure the tests really ran, `:app:testDevDebugUnitTest --rerun` was run: BUILD SUCCESSFUL, 2236 tests, 0 failures, 0 errors, 0 skipped.

## Instrumented tests (make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=...)

| Class | Result |
|---|---|
| ui.feature.root.component.StartupSplashHostTest | 5 of 5 pass, including splashReportsGoneOnceAfterTheFade (AC-1) |
| ui.feature.update.component.UpdatePromptOverlayFocusTest | 8 of 8 pass, including updatePromptShownOverFocusedContentTakesFocusWithinOneSecond (AC-12) |

## Perf build

- `devBenchmarkRelease` at aacf6035 with temporary, uncommitted instrumentation (sources reverted after the build; saved as 2026-11-21-startup-settled-signal-measure-after.patch): the baseline's one line `Log.i("SettledMeasure", "homePublish")` re-applied before `updateViewState(HomeViewState.Content(...))` at its new place (inside the `withLock` block, after the epoch check; the original patch no longer applies by line, applied by hand), plus `Log.i("SettledMeasure", "settled restored|fallback|splash")` at the three settle sites, logged only by the call that completes the latch.
- APK sha256 37aa2d05...0ffa, installed with install-preserve, on-device base.apk sha256 equal. At the end a clean devDebug head APK (sha256 bce66b33...5838, no instrumentation) was installed the same way and is left installed.
- Telemetry was already enabled on the device.

## Method (same as the baseline)

Cold start = am force-stop, 3 s, am start -W. Before each start wait (up to 2 min) for host 1-minute load <= 9, then record it. 10 starts, the first dropped, 9 reported. Marks from the telemetry startup line (ms since process start). Home publishes = SettledMeasure homePublish lines for the whole start (about 8 s of keys plus 6 s). 14 DPAD keys (22, 21 alternating, 0.4 s sleep, about 0.8 s after launch). key_lat paired to the key event of the same key; a key without key_lat counts as 50 ms; measured set = keys delivered in [revealed, revealed + 3000 ms]. Frames from dumpsys gfxinfo (reset before the first key).

Incident: the emulator was killed during the run (host load 30+ from other agents; adb offline). Restarted with -no-window -no-audio -no-boot-anim -no-snapshot-save -read-only -gpu swiftshader_indirect; the app, login and APK (sha256 re-checked) were intact. The two starts that straddled the kill were discarded and re-run; they are not in the table. A first smoke start before the runs is not counted either.

## Raw runs

| Run | Load | TotalTime | app_composed | first_content | revealed | Home ready | publishes | settled by | keys in window | key_lat (ms) | frames | janky |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 (dropped) | 9.89 | 1216 | 1025 | 1827 | 4410 | 638 | 5 | splash | 5 | 2097, 551, 566, 551, 373 | 109 | 99.08% |
| 2 | 13.85 | 1578 | 1315 | 2690 | 3768 | 1046 | 7 | splash | 1 | 50 | 70 | 100.00% |
| 3 | 11.46 | 1063 | 1012 | 1790 | 2718 | 595 | 10 | splash | 2 | 2381, 533 | 104 | 99.04% |
| 4 | 8.96 | 589 | 459 | 925 | 2367 | 382 | 5 | splash | 6 | 50, 113, 50, 69, 50, 56 | 174 | 77.59% |
| 5 | 8.6 | 735 | 612 | 1131 | 2604 | 388 | 6 | splash | 6 | 313, 67, 85, 67, 50, 92 | 181 | 72.38% |
| 6 | 8.37 | 727 | 579 | 1162 | 2871 | 467 | 5 | splash | 6 | 380, 283, 115, 76, 50, 76 | 184 | 71.74% |
| 7 | 7.44 | 727 | 587 | 1185 | 2986 | 447 | 6 | splash | 6 | 435, 193, 301, 51, 50, 51 | 177 | 70.62% |
| 8 | 8.53 | 791 | 641 | 1285 | 2963 | 448 | 5 | splash | 5 | 822, 924, 1088, 1147, 50 | 165 | 95.15% |
| 9 | 6.9 | 568 | 447 | 891 | 2303 | 356 | 5 | splash | 6 | 216, 111, 50, 67, 50, 79 | 180 | 75.56% |
| 10 | 6.78 | 687 | 546 | 1123 | 2657 | 406 | 5 | splash | 6 | 356, 248, 104, 91, 50, 60 | 166 | 81.33% |

## Summary (runs 2-10, n = 9) against the baseline (runs 2-9, n = 8)

| Metric | Baseline | After | Gate |
|---|---|---|---|
| Home publishes per start, median (range) | 8 (8-9) | 5 (5-10; 7 of 9 starts at 5 or 6) | median below 8: PASS |
| revealed ms, median (range) | 2857.5 (2709-3093) | 2718 (2303-3768) | <= 2857.5 + max(100, 384) = 3241.5: PASS on the median; run 2 (load 13.9) was 3768 |
| Starts that the 2.5 s cap can explain (revealed >= about 2570) | 8 of 8 (inferred) | at most 7 of 9; runs 4 (2367) and 9 (2303) revealed before the cap could fire | after <= before: PASS (still inferred, no cap marker exists in either build) |
| key_lat p95, keys in the first 3 s after revealed, pooled (unlogged = 50) | 1060 ms (39 keys, median 322) | 1088 ms (44 keys, median 82, max 2381) | reported only |
| Settle reason | not measured | splash in 10 of 10 starts (9 of 9 reported) | 5 of 5 by splash: PASS |
| Host load during runs | 5.3-8.1 | 6.8-13.9 | |

Subset at host load <= 9 (runs 4-10, n = 7; the baseline's load band is 5.3-8.1, so this is the closer comparison): publishes 5,6,5,6,5,5,5; revealed median 2657 (2303-2986); key_lat p95 924 ms over 41 keys (median 79).
The runs at load 9.9-13.9 (runs 1-3) are the slow ones (revealed 4410, 3768, 2718; one start with only one key in the window): host load inflates reveal and key_lat, so the numbers are not a clean A/B; the count metrics (publishes, settle reason) are unaffected.

## Manual checks

| # | Steps | Observed | Result |
|---|---|---|---|
| 1 | force-stop, cold start; after the splash settled, DPAD_RIGHT x3 and DPAD_DOWN x2 at 0.5 s | settled by splash; revealed 3078 ms; 3 DPAD_RIGHT key events delivered to Home (two key_lat lines, 105 and 81 ms; the third was at or below the 50 ms logging threshold), no crash in the crash buffer, 0 NoDefinitionFound lines, MainActivity top resumed. DPAD_DOWN is not a logged key in the telemetry, so the vertical move is not observed; per-card focus was not observed (no screenshots/UI dump on an account screen) | PASS for no crash, no DI failure, keys handled; focus position NOT observed |
| 2 | newer release available, cold start | not run: no newer release than the installed build is known to exist and none can be provoked without account or network mutation | NOT CHECKED (instrumented test above covers the focus claim) |
| 3 | HOME, am kill, relaunch | log: `settled restored` at once; startup line revealed 278 ms (no splash wait); 3 keys delivered, key_lat 769 and 367 ms on the first two (Home loading its first publish, not a 5 s stall); 1 Home publish | PASS |
| 4 | `git log -p 7bcbfd83..HEAD -- app | grep -c SettledMeasure`; `git grep -n StartupSyncDelay -- app` | 0; no match | PASS |


## Round 2 (head b846ea7d)

Device: emulator-5554 (puber_tv_36, API 36 TV), leased and released. Install: install-preserve outcome installed, compatible_replace, no destructive action; on-device sha256 4408539b4662 equals the built APK.

- UpdatePromptOverlayFocusTest (com.kino.puber.ui.feature.update.component), make DEVICE=emulator-5554 itest: 8 tests, 0 failures, 0 errors, 0 skipped, incl. updatePromptShownOverFocusedContentTakesFocusWithinOneSecond. AC-12 PASS (instrumented).
- Cold start smoke: force-stop, am start -W Status ok (TotalTime 1828 ms), 12 s wait, six D-pad keys (DOWN, DOWN, RIGHT, RIGHT, LEFT, UP) sent; MainActivity stays top resumed, process alive, 0 crash-buffer lines, 0 FATAL or ANR lines. Focus position not observed (no UI dump taken).
- The automatic prompt itself (guard window) cannot be provoked: no newer release is known. Covered by unit tests only.
