# Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 1741 unit tests, 0 failed, 0 skipped; detekt and lint clean)
DEVICE: emulator-5554 (puber_tv_36, API 36 TV); leased and released. The emulator process died mid-run (host load above 80) and I restarted the same AVD with its original flags; app data and login survived. Left on the worktree's devDebug (sha256 e44a16a4...a5c3 matches on device), toggle off.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Device: Developer section with 7 non-scrolling rows in debug, deploy and minified benchmarkRelease; Session log off by default with off text; on shows "Recording · 6 d 23 h left", row keeps focus and height; Share focusable, DPAD_LEFT returns to the section row; overlay row only in debug (absent in deploy and release). Unit T01-T04. Instrumented T05 exists and passes (corrected in Round 3). |
| AC-1a | PASS | Device: zip `puber-telemetry-<sid>-<date>.zip` holds only closed files (open file excluded), second press replaces it (one zip), chooser launched with the zip (`ACTION_CHOOSER`, only handler Bluetooth, system toast "This action isn't supported"), no crash, focus back on Share. "Nothing to share yet" disabled and focusable on a fresh install. `update_file_paths.xml` has only `updates` and `telemetry` entries (T07). Update-install URI on a device NOT CHECKED (no update offered). Accepted per spec Open question 3. |
| AC-2 | PASS | Device: no `telemetry/` dir on a fresh install; no new file or growth with the toggle off in devDebug and minified release; release logcat has no app log lines; `am crash` off leaves no err line. Unit T15-T17. T18 (P2 allocation) not written. |
| AC-3 | PASS | `jq` key sets of 356 pulled lines equal envelope plus catalogue per type, nothing extra (debug and release). Null-directory and IO-error cases are unit-only (T21, T22). |
| AC-4 | PASS | Unit only (T25-T28, injected clock); the device does not change wall clock (as designed). |
| AC-5 | PASS | Unit only (T29-T34 caps, meta, rotation at 2 MiB, 20 MiB oldest-first). Device `act` folding seen (`ItemFocused n` up to 6, `TextChanged n=8`). |
| AC-6 | PASS | Unit T35/T36 (real writer thread, blocked store). Indirectly: no jank regression. |
| AC-7 | PASS | `grep -i` of typed searches (`ZQXPROBE`, `qwerty`), opened titles, `token`, `http` over all 29 pulled files: no hit; `mobile-evidence-audit.sh` passed on the pulled directory and on the evidence directory. Unit T38-T41. |
| AC-8 | FAIL | Minified `devBenchmarkRelease`: screens, VMs and states are readable and `PuberScreen.key` is unchanged, but (1) `act.a` is `CommonAction$ItemSelected`, `MainAction$Resumed`, `UpdatePromptAction$OnResume`, `DeviceSettingsActions$ToggleSessionLog` where debug and spec D5 give the bare `ItemSelected`, `Resumed`, `OnResume`; (2) `nav.from` is `pm2` (obfuscated private `LoadingTab` object, not covered by any keepnames rule) vs `LoadingTab` in debug. Names are therefore not stable across build types and one is obfuscated. |
| AC-9 | PASS | Device (debug and release): one `startup` line per process, marks ascending (release 0/46/365/1487 ms, debug 0/749/1500/3210); `nav` for new_root, tab, push_result, back; `ready` once per class change with `ms`; `act` folded with `n`. Unit T47-T53. |
| AC-10 | PASS | Release: `err src=log et=IOException` with sanitized msg and frames at each cold start (shows the narrowed strip rule keeps `log(Throwable,...)`); `am crash` with logging on writes `err src=uncaught` and the process dies in under 0.25 s with `FATAL EXCEPTION`, identical to logging off (no err line, same crash buffer). Off: no tree or handler effect observed. Token-refresh failure unit only (T62). A forced network failure could not be reproduced (DNS break/airplane mode did not stop the app's requests). In release `err.frames` are obfuscated (`of.b:280`), as the implementer noted. |
| AC-10a | PASS | Device: five force-stops, `enabled_at_wall_ms` unchanged, indicator continues; toggle off at runtime stops writes (`meta stop`), on again without restart writes `session`+`meta start`. Unit T65-T67. |
| Perf | PASS | Off, A/B on the same emulator and build type (base rebuilt, 2 sets each, 14 runs): base median 2429 ms, new 2503 ms, +74 ms (+3.0 percent), under the max(5 percent = 121, 2 x 150 = 300) threshold. Baseline file said 2804 ms but the host load changed (5-80), so the A/B is the valid comparison. Jank: off mean 99.38 percent, on 99.03, baseline 99.55 (software GPU, all frames janky; no gate). Logging on cold start median 2297 ms (informational). |

TESTS ADDED: none. Existing tests cover every unit row of the Test design except T18 (P2, not present).
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-core/docs/superpowers/evidence/2026-10-15-telemetry-core-device.md (committed in the worktree as eea7207d; audit passed)

DEFECTS:
1. AC-8, `act.a` is not the bare class name in minified release. Reproduce: install `app-dev-benchmarkRelease.apk`, toggle Session log on, open Home then History, pull `files/telemetry/*.jsonl`, `jq -r 'select(.type=="act").a'` shows `CommonAction$ItemSelected`, `MainAction$Resumed`, `LauncherAction$SplashShown`, `UpdatePromptAction$OnResume`; the same steps in devDebug show `ItemSelected`, `Resumed`, `SplashShown`, `OnResume`. Cause is likely that `TelemetryNames` takes `simpleName`, which in R8 output falls back to the `Outer$Inner` binary name when the InnerClasses entry is not kept for those classes. Failing check: spec D5 device scenario ("`act.a` reads like `ItemFocused`"). The unit test cannot see it.
2. AC-8, obfuscated `nav.from` in release. Reproduce: same release build, cold start with logging on; first `nav` after `new_root` is `{"cmd":"tab","from":"pm2","to":"Home"}` (debug: `from":"LoadingTab"`). `LoadingTab` (`TabComponent.kt:133`, a private `object ... : Tab`) is neither a `PuberScreen` nor under the `-keepnames` rules, and `TelemetryNames` does not unwrap it. Low severity (one internal class) but it is the "a0/pm2-style name" AC-8 says must not appear.
3. Note, not a defect: `err.frames` and any app frame in release are R8-renamed (no retrace); line order in a file is not strictly by `t` because folded `act` lines are written when the fold window closes.

UNAVAILABLE: none. Emulator restart was needed once (see DEVICE).

MANUAL:
- Wording of the Settings strings and the Share "Preparing archive" state are the owner's to judge on a real TV (indicator text, section name "For developers").
- On a real TV with a share target, press OK on Share log and confirm the zip arrives; the emulator only offers Bluetooth.
- Optional: a real "Check for updates" with an update available to confirm the update FileProvider path still resolves.
- Account state: during testing a mis-aimed key press played Psyren S1E1 for about 6 seconds, so History now lists it (details in the evidence file).
- Left on the emulator: 29 session files plus one zip under `files/telemetry/` (device-local, not in git).

# Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL)
DEVICE: emulator-5554, leased and released. Installed devBenchmarkRelease (sha 1fab1515...) for the check, then left on the worktree devDebug (sha 9197bfca... equal on device), logging off, file count constant. No playback.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-8 | PASS | Minified release: `act.a` = OnResume, SplashShown, Resumed, ItemSelected, ItemFocused, LoadMore, ToggleSessionLog (bare names); `nav.from` = `LoadingTab`; scr/VM/state names readable; navigation works, key unchanged. Defects 1 and 2 fixed. |
| AC-9 | PASS (light) | One startup line (0/82/512/1688 ms), nav, ready, folded act. |
| AC-10 | PASS (light) | err src=log IOException at cold start; am crash with logging on wrote err src=uncaught, process died. |
| AC-10a | PASS (light) | toggle off wrote ToggleSessionLog + meta stop; on again new -02 file with session + meta start. |
Other ACs: unchanged from round 1 (not re-run).
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-core/docs/superpowers/evidence/2026-10-15-telemetry-core-device.md (Round 2 section, commit fc5738b1, audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: unchanged from round 1.

# Round 3

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (unit tests re-run with --rerun: 1757 tests, 0 failed, 0 skipped; detekt and lint clean)
DEVICE: emulator-5554, leased and released. devDebug of the worktree installed via install-preserve (`installed`, compatible_replace, sha 914f1a6b... equal on device), left installed, logging off. No playback.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| T05 (triage row 14) | PASS | `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=...DeviceSettingsContentFocusTest`: 21 tests, 0 failed, includes `developerSectionRowsAreFocusableAndLeftReturnsToSectionList`. Round-1 "does not exist" line was wrong. |
| AC-1a | PASS | Share about 2 s after a toggle: zip holds `act ToggleDebugOverlay n=2` and `act ShareSessionLog`; one zip after each press (replaced), no `*.tmp`. |
| AC-9 | PASS | Cold start: one `startup` line with `first_content` 5632 and marks 0/1242/2659/5632 (ascending), written with Home's first `ready`; second start 0/1065/2488/4927. Non-Home landing screen not provokable on device, unit tests only. |
| AC-10 | PASS | `am crash` logging on: `err src=uncaught et=android.app.RemoteServiceException$CrashedByAdbException`, process dies, FATAL EXCEPTION as before. `frames` is `[]` (framework-only trace, app frames only are kept). Full binary names seen in `src=log` err: `java.io.IOException`, `com.kino.puber.core.tvhome.TvHomeSyncCoordinator$refreshNow$1.invokeSuspend:14`. Release retrace NOT CHECKED. |
| AC-10a | PASS | Toggle off/on without restart: new sid `6885f0ee`, `t` 2 and 17 for session and `meta start`; old file ends with `meta stop`. |
| AC-8 release, AC-10 release retrace | NOT CHECKED | Optional devBenchmarkRelease pass skipped; round 2 covers names in release, the full binary `et` was only observed on debug. |
Other ACs: unchanged, not re-run (unit tests green).
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-core/docs/superpowers/evidence/2026-10-15-telemetry-core-device.md (Round 3 section; round-1 T05 line corrected; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: unchanged. Note: `make itest` needed `GRADLE=./tools/agentw` in a worktree (the default `./gradlew` is refused by the guard).

# Round 4

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass on the final forced run (1766 tests, 0 failed, 0 skipped; detekt, lint clean). NOTE: the first forced run under host load failed once in `HomeVMIncrementalPublishTest.aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped` (line 562, Home, not telemetry); the class passed alone twice and the full gate passed twice after it. Flake, flagged.
DEVICE: emulator-5554, leased and released. devDebug installed (install-preserve `installed`), left on devDebug with logging off. devBenchmarkRelease used for the optional pass. No playback.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1a (rows 1) | PASS | Share twice: one zip each time (old replaced), no `*.tmp`; second zip holds the latest events (`ToggleDebugOverlay` x2, `ShareSessionLog`); after more events, force-stop and a next cold start the newest zip is unchanged and still the only one. Size rotation (2 MiB) unit only. |
| AC-9 (row 2) | PASS | Debug 0/755/1589/3567, release 0/44/534/1722, written with Home `ready Content`. Non-Home landing, error-state exclusion: unit only. |
| AC-4 / AC-10a (rows 3-4, light) | PASS | Off then on without restart: old file ends `meta stop`, new sid starts `t:0`/`t:5`, nothing leaked. Wrong future `enabled_at` is unit only (device clock not changed). |
| AC-10 release (optional) | PASS | `et` full binary name `java.io.IOException`; frames `of.b:280` etc. retraced with SDK `retrace` and `mapping.txt` to `AndroidTvHomePublisher.ensureChannel(AndroidTvHomePublisher.kt:52)`, `TvHomeSyncCoordinator.refreshNow`. Needs the standard `at of.b(SourceFile:280)` form. |
| Rows 5-6 (tests) | PASS | in the green unit run. |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-core/docs/superpowers/evidence/2026-10-15-telemetry-core-device.md (Round 4 section, committed in the worktree, audit passed)
DEFECTS: none (one load-dependent flake noted above)
UNAVAILABLE: none
MANUAL: unchanged.
