## Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at aacf6035; tasks were up to date, so :app:testDevDebugUnitTest --rerun was run too: 2236 tests, 0 failures, 0 errors, 0 skipped)
DEVICE: emulator-5554 (puber_tv_36, API 36 TV). The emulator was killed once by host load 30+ and restarted with the parent run's flags; app, login and APK intact; the two affected starts were re-run.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | StartupSettledTest (3 rows) green; StartupSplashHostTest 5 of 5 on device incl. splashReportsGoneOnceAfterTheFade; perf starts: settled by splash in 10 of 10 |
| AC-2 | PASS | restoredActivitySettlesAtOnce green; device manual 3: "settled restored", revealed 278 ms, no splash, keys handled (first key_lat 769 ms, no 5 s stall) |
| AC-3 | PASS (unit only) | freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone, aSecondFreshActivityDoesNotPostponeTheFallback green; fallback never fired on device (every start settled by splash; one smoke start on a loaded host settled by fallback at about 5 s) |
| AC-4 | PASS (unit only) | the three BeforeStartupSettles tests, MainVMStartupSlotsTest slot order, 3 guards green; slot timing not observed on device |
| AC-5 | PASS (unit only) | UpdatePromptVMTest 4 rows green incl. manualCheck_runsBeforeStartupSettles |
| AC-6 | PASS (unit only) | DetailsPrefetcherTest 3 rows and DetailsPrefetcherWiringTest green |
| AC-7 | PASS (unit only) | deferredStartupWorkNeverRunsAfterTheViewModelIsCleared, onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot green |
| AC-8 | PASS (unit only) | onStartAfterStartupSettled_countsSlotsFromMainStart green |
| AC-9 | PASS | 5 HomeVMIncrementalPublishTest rows green; device: publishes per start fell from 8 to 5 (median) |
| AC-10 | PASS (unit only) | the 3 epoch tests green |
| AC-11 | PASS with caveats | publishes median 5 (5-10) vs 8 (8-9); revealed median 2718 (2303-3768) vs 2857.5 (2709-3093), gate 3241.5; cap: revealed before 2500 ms in 2 of 9 starts (2367, 2303), impossible by the cap, so at most 7 of 9 can be cap-revealed vs 8 of 8 at base (inferred, no cap marker in either build); key_lat p95 1088 ms pooled over 44 keys vs 1060 (reported only). Host load was 6.8-13.9 against the baseline's 5.3-8.1: at load <= 9 (7 starts) revealed median 2657, key_lat p95 924. Cap clause is still inferred and mostly vacuous (Q4) |
| AC-12 | PASS (instrumented); device NOT CHECKED | UpdatePromptOverlayFocusTest 8 of 8 on device incl. updatePromptShownOverFocusedContentTakesFocusWithinOneSecond; Manual 2 not run: no newer release is known and none can be provoked without account or network mutation |
| AC-13 | PASS | full gate exit 0 |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-settled-signal/docs/superpowers/evidence/2026-11-21-startup-settled-signal-device.md (committed 5686efd0 with both measurement patches; mobile-evidence-audit passed). The instrumentation is not in any task commit: `git log -p 7bcbfd83..HEAD -- app | grep -c SettledMeasure` is 0 and `git grep -n StartupSyncDelay -- app` is empty; the evidence commit adds the two patch files under docs/, so a whole-tree grep for SettledMeasure now also matches them.
DEFECTS: none
UNAVAILABLE: none
MANUAL: (1) Manual 2 on a device or build where a newer release exists: cold start, prompt opens about 1 s after the reveal with focus on Update, D-pad stays inside, Back returns focus to Home. (2) Manual 1 focus position: on the emulator only key delivery and absence of crashes were observed (no screenshots or UI dump of an account screen; DPAD_DOWN is not a logged telemetry key); confirm on the TV that each D-pad press moves Home focus right after the reveal. (3) Real-TV numbers stay with startup-first-input-on-tv.
Notes: all 10 counted starts settled by the splash, none by the fallback; the dev debug head APK (sha256 bce66b33...5838, clean) is left installed via install-preserve; lease released; instrumentation reverted.

## Round 2

CHECK: ./tools/agentw testDevDebugUnitTest --rerun :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at b846ea7d; unit tests re-run: 2237 tests, 0 failures, 0 errors, 0 skipped; detekt and lint up to date from the head). KinoPubApiClientAuthProbeTest did NOT reproduce its UncompletedCoroutinesError in this full run (one run only, so flakiness is not excluded).
DEVICE: emulator-5554 (puber_tv_36), up, no restart needed; leased and released. Head APK installed by install-preserve (installed, compatible_replace, on-device sha256 4408539b4662 matches the build).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-5 | PASS (unit only) | UpdatePromptVMTest incl. new automaticResult_doesNotReplaceAManualCheckInProgress, updateClicked_isIgnored_whileTheAutomaticPromptIsInItsInputGuard and manualCheck_runsBeforeStartupSettles green in the rerun. Guard window and automatic prompt not observable on device: no newer release can be provoked |
| AC-12 | PASS (instrumented); device flow NOT CHECKED | UpdatePromptOverlayFocusTest (com.kino.puber.ui.feature.update.component) 8 of 8 on emulator-5554 at head, incl. updatePromptShownOverFocusedContentTakesFocusWithinOneSecond; Manual 2 still not runnable (no newer release) |
| AC-13 | PASS | full gate exit 0 at b846ea7d, 2237 tests green |
Cold start smoke: am start -W ok (1828 ms), 6 D-pad keys sent after 12 s, MainActivity stays resumed, process alive, 0 crash and 0 FATAL/ANR lines. Focus position not observed.
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-settled-signal/docs/superpowers/evidence/2026-11-21-startup-settled-signal-device.md (Round 2 section, committed 904573a3; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: unchanged from round 1 (Manual 2 on a build with a newer release, including that Update presses in the first 500 ms after the prompt appears are ignored and later ones work).
