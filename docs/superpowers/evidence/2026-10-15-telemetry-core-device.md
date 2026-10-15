# Device evidence: telemetry-core (AC-1, AC-1a, AC-2..AC-10, AC-10a, perf)

Date: 2026-10-15. Round 1. Worktree HEAD `c66264d4` (base `39903d31`). Package `com.kino.puber.stage`.

## Build and device

- Device: `emulator-5554` (AVD `puber_tv_36`, API 36 TV, software GPU), leased and released. The emulator process died
  mid-run (host load average above 80 from other work); it was restarted with the same AVD and flags
  (`-no-window -no-audio -no-boot-anim -no-snapshot-save`). App data and login survived the reboot (app still
  logged in, `telemetry_preferences.xml` intact).
- Builds (all from this worktree, all installed with `tools/android-apk-install-preserve`, outcome `installed`,
  classification `compatible_replace`, `destructive_action: false`, same signer `6b29181257cb...`):
  - devDebug `e44a16a47561...a5c3`, on-device `base.apk` sha256 equal. Left installed at the end.
  - devBenchmarkRelease (minified, R8, debug-signed) `050b36f165a0...fd81`, on-device sha256 equal.
  - devDeploy (unminified, non-debug) installed once for the Developer-section check.
  - Base build (`39903d31`, `git archive` into a scratch dir, built with `tools/agentw`) `9de4ee4af257...f9f4`
    installed for the A/B cold-start measurement only.
- `make DEVICE=emulator-5554 auth-save` was run once before the first install (snapshot under `.auth/`, gitignored).
- Nothing was uninstalled, cleared or downgraded. Input: D-pad key events (text typed with `input text` in Search).
- Global settings touched and restored: `private_dns_mode`/`private_dns_specifier` (attempt to force network
  failure; deleted afterwards, ping works again); airplane mode enable/disable.

## Account-state changes (recorded, unintended)

During the first D6 pass a mis-aimed D-pad sequence (focus was on a Home item, not the rail) pressed OK on
"Psyren" S1E1 and played it for about 6 seconds. History now lists Psyren Season 1 Episode 1 with a progress bar
(it was absent before). No other playback. Searches typed: `ZQXPROBE`, `qwerty` (no results; read-only).

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> BUILD SUCCESSFUL,
1741 unit tests, 0 failures, 0 skipped.

## Steps and results

### D1 / AC-2: toggle off is empty (devDebug and minified release)

- Steps: fresh pref (no `telemetry_preferences.xml` key), cold start, Home, History, Settings, 10+ DOWN/RIGHT.
- Observed: `files/` held only the pre-existing `timed_action_debug.jsonl`; no `telemetry/` directory existed
  until the toggle was switched on. After toggling off later, file count and sizes stayed constant (checked 12 s and
  with D-pad movement). In `devBenchmarkRelease` with the toggle off the file count stayed at 21 across a cold start
  and Settings navigation, and logcat for the app pid held no Timber/app lines (only ART, HWUI, SLF4J notices).
  `am crash` with the toggle off: process died within 0.25 s, `FATAL EXCEPTION` in the crash buffer, no `err` line
  written. PASS.

### D2 / AC-1: Developer section and rows

- debug: section list shows 7 rows without scrolling; "For developers" panel: Session log (off, "Writes app activity
  to a local file, turns off after 7 days"), Share log (disabled look, "Nothing to share yet", focusable: DOWN lands on
  it with the grey focused slab), "Show stream information" (debug overlay) last. DPAD_LEFT from the toggle and from
  the overlay row returns to the "For developers" section row. PASS.
- minified release and deploy: 7 sections, panel shows Session log and Share log only; no "Show stream
  information" row. PASS.
- Toggle on: "Recording · 6 d 23 h left", switch on, focus stays on the row, row height unchanged. PASS.

### D3 / AC-3, AC-7: files and schema (debug and release)

- Files `s-<yyyyMMdd-HHmmss>-<sid>-<nn>.jsonl` under `files/telemetry/`, pulled with `adb pull`.
- `jq` key sets per type across 356 lines (debug + release sessions): act `a,n,scr,sid,t,type`; err
  `et,frames,msg,n,scr,sid,src,t,type`; meta `ev,scr,sid,t,type`; nav `cmd,from,scr,sid,t,to,type`; ready
  `ms,scr,sid,st,t,type`; session `api,build,hz,low_ram,model,ram_class,scr,sid,t,type,ver,wall_ms`; startup
  `app_composed,app_create,first_content,proc_start,scr,sid,t,type`. Exactly the spec schema, nothing else. PASS.
- Search text `ZQXPROBE`/`qwerty`, titles opened (Westside/Outlander/Psyren/Compostelle, Cyrillic and Latin), `token`,
  `http`: `grep -il` over every pulled file found no hit. PASS (AC-7 device part).
- Budget: about 110 bytes per line; a 45-second session writes about 45-75 lines.
- `tools/mobile-evidence-audit.sh` on the pulled `telemetry/` directory (29 files) and on the evidence directory: `evidence_audit_status=passed`.

### D4 / AC-1a: Share log

- With files present, OK on Share log: archive `puber-telemetry-<sid>-<yyyyMMdd-HHmm>.zip` created, the previous
  open file rotated (`-02`), the new open file (`-03`) not in the zip (`unzip -l` lists only closed `.jsonl`).
  A second press replaced the zip (exactly one `*.zip` in the directory).
- logcat: `ActivityTaskManager START act=android.intent.action.CHOOSER ... clip text/uri-list`, then
  `ChooserActivity` auto-launched the only handler (`com.google.android.bluetooth` OPP); the TV settings device
  picker then showed the system toast "This action isn't supported". No crash. Back at Settings focus was on
  "Share log" (screenshot checked). Accepted per spec Open question 3. PASS.
- Disabled "Nothing to share yet" with focus: seen on the first fresh-state visit (D2). PASS.
- `update_file_paths.xml` has exactly the `updates` and `telemetry` `external-files-path` entries (T07 unit test
  and file read). The update-install URI on a device was not exercised (no update offered): NOT CHECKED.

### D5 / AC-8: names in minified release

- `PuberScreen`/VM/state names are readable (`Home`, `Main`, `Details`, `History`, `Search`, `Player`,
  `DeviceSettings`, `Launcher`, `UpdatePrompt`; `ready.st` = `Content`, `Loading`, `Empty`).
- FINDING 1: `act.a` for actions nested in sealed interfaces is the prefixed form in release (`CommonAction$ItemSelected`,
  `MainAction$Resumed`, `UpdatePromptAction$OnResume`, `LauncherAction$SplashShown`,
  `DeviceSettingsActions$ToggleSessionLog`); debug logs the bare form (`ItemSelected`, `Resumed`, `OnResume`).
  Spec D5/D5-scenario expects the bare name in every build.
- FINDING 2: `nav.from` is `pm2` (obfuscated) for the private `LoadingTab` object in release
  (`{"cmd":"tab","from":"pm2","to":"Home"}`), `LoadingTab` in debug. `LoadingTab` is a `Tab`, not a `PuberScreen`, so
  none of the `-keepnames` rules cover it.
- `err.frames` in release are obfuscated (`of.b:280`, `lz4.b:125`); the implementer's record already says this.
- `PuberScreen.key`: Details opened and Back restored Home with focus in release; `PuberScreenKeyTest` passes.

### D6 / AC-9: cold-start story (debug and release)

- One `startup` line per process with ascending marks: debug `proc_start 0, app_create 749, app_composed 1500,
  first_content 3210`; release `0, 46, 365, 1487`. `ready` once per state-class change with `ms` (Home Content 574
  to 1131 ms in release). `nav` lines for new_root, tab, push_result, back. `act` folded per second:
  `ItemFocused n` 1 to 6 while holding DOWN in History, `TextChanged n 8` for the typed query.
- Observation: line order inside a file is not strictly by `t` (an `act` is written when its fold window closes,
  after later `nav` lines). Not a spec violation; the summary tool must sort.

### D7 / AC-10: errors in minified release

- `err src=log` with `et=IOException`, sanitized `msg` (no URL), frames present, in release at every cold start
  (`Failed to add logo, so channel (ID=n) was not created`, from `TvHomeSyncCoordinator`). This proves
  the narrowed `assumenosideeffects` rule keeps `log(Throwable, ...)` in release. A forced network failure was not
  reproducible (see Not checked).
- `am crash com.kino.puber.stage` with logging on: `err src=uncaught et=RemoteServiceException$CrashedByAdbException
  msg="shell-induced crash"` written; process gone within 0.25 s, `FATAL EXCEPTION: main` in the crash buffer. With
  logging off: identical process death and crash buffer, no `err` line. Chain preserved. PASS.
- Token-refresh failure: unit only (T62).

### D8 / AC-10a, AC-4: persistence and runtime toggle

- Toggle on, five `am force-stop` plus relaunches: `enabled_at_wall_ms` unchanged (`1792063556889`), indicator
  still "6 d 23 h left", a new session file and `session`+`meta start` on every launch. Toggle off at runtime: pref
  cleared, `meta stop` written, file stopped growing. Toggle on again without restart: new file with `session` and
  `meta start`. PASS. The 7-day expiry is unit-only (T25-T28).

## Perf (D9)

Baseline (`{run}/baseline.md`, emulator-5554 devDebug, loaded host): cold start median 2804 ms, 7 runs
2658-3044 ms; jank 98.65-100 percent (software GPU, every frame janky).

The host load varied from 5 to 80 during this session, so the baseline number cannot be compared directly. A/B
re-measurement on the same emulator, same build type, alternating base (`39903d31`, rebuilt) and the new build with
logging off, 8 force-stop cold starts per set, first run of each set dropped (`am start -W` TotalTime, ms):

| Set | Runs 2-8 | Median |
|---|---|---|
| BASE A | 1411 2678 2416 2514 2388 2626 2384 | 2416 |
| NEW off A | 2514 2626 2311 2492 2681 2610 2396 | 2514 |
| BASE B | 2466 2415 2402 2369 2494 2442 2533 | 2442 |
| NEW off B | 2634 1630 2352 2462 2662 2555 2398 | 2462 |
| pooled BASE | 14 runs | 2429 |
| pooled NEW off | 14 runs | 2503 |

Delta +74 ms (+3.0 percent), threshold max(5 percent = 121 ms, 2 x SD of baseline runs 2 x 150 = 300 ms) -> within
noise. Earlier quiet-host runs: base 1712 median, new off about 1660 (runs of 7). Logging on (informational,
load 17-20): median 2297 ms. Home time-to-content from `startup.first_content`: 3210/3371/3368 ms debug, 1472-2738
ms release (informational).

Jank (`dumpsys gfxinfo`, Home 6 DOWN, 8 RIGHT, 8 LEFT, 6 UP, 3 runs each): off 100.00 / 98.76 / 99.39 percent
(mean 99.38), on 97.70 / 100.00 / 99.38 (mean 99.03); baseline mean 99.55. All frames janky on the software GPU, as
in the baseline; frame p50 150 ms, p90 300 ms in every run. Not worse by more than 1 point.

## Not checked

- Forced network failure `err` on a device (DNS break and airplane mode did not stop the app's requests; the
  emulator uses Ethernet and `svc` is limited). The `err src=log` path is shown with a real IOException instead.
- Update-install FileProvider URI on a device (no update available).
- T05 instrumented focus test: correction in Round 3. The test exists (`DeviceSettingsContentFocusTest.developerSectionRowsAreFocusableAndLeftReturnsToSectionList`); round 1 searched for the wrong strings and missed it. It was run in Round 3 and passes.
- Time-to-content with a splash-frame mark (out of scope by spec).

## Round 2 (fix commit `4d786240`): AC-8 re-check

- Device: `emulator-5554`, leased and released. Static gate re-run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw
  testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> BUILD SUCCESSFUL.
- Builds from this worktree, installed with `tools/android-apk-install-preserve` (outcome `installed`,
  `compatible_replace`, `destructive_action: false`): devBenchmarkRelease (minified) `1fab15134a4e...`, then devDebug
  `9197bfca16cb...` (on-device `base.apk` sha256 equal; left installed, session log off).
- No playback, no Details opened. Steps: rail to Settings, "For developers", Session log on; force-stop and cold start;
  Home, rail to History, three DOWN, BACK; `am crash`; relaunch, Settings toggle off, on, off. Pulled the three newest
  session files (adb pull of `files/telemetry`) and grepped the key lines only.
- AC-8 observed in release: `act.a` = `OnResume`, `SplashShown`, `Resumed`, `ItemSelected`, `ItemFocused`, `LoadMore`,
  `ToggleSessionLog` (bare names, same as debug). `nav` = `{"cmd":"tab","from":"LoadingTab","to":"Home"}`,
  `new_root Launcher -> Main`, `tab Home -> History`; `scr` values `UpdatePrompt`, `Launcher`, `Main`, `Home`,
  `History`, `DeviceSettings`. `ready.st` = `Content`. Navigation worked (Home, History, Settings) so `PuberScreen.key`
  is unaffected. AC-8: PASS.
- AC-9 (light): one `startup` line (marks 0/82/512/1688 ms), `nav` and `ready` lines present, `act` folded with `n`.
- AC-10 (light): `err src=log et=IOException` at cold start (frames obfuscated, as before); `am crash` with logging on
  wrote `err src=uncaught et=RemoteServiceException$CrashedByAdbException` and the process died.
- AC-10a (light): toggle off writes `act ToggleSessionLog` and `meta stop`; toggle on again opened a new `-02` file with
  `session` and `meta start`; final off left file count constant (33) across 5 s.

## Round 3 (head `0f20e63a`): review-fix verification

- Static gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` ->
  BUILD SUCCESSFUL; unit tests forced to re-run (`--rerun`): 1757 tests, 0 failed, 0 skipped; detekt and lint clean.
- Device: `emulator-5554` (puber_tv_36), leased and released. Telemetry files live in the app's external files dir
  (`/sdcard/Android/data/com.kino.puber.stage/files/telemetry`), not in internal `files/`.
- Instrumented (triage row 14): `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest
  TESTS=com.kino.puber.ui.feature.device.settings.DeviceSettingsContentFocusTest` -> 21 tests, 0 failed, 0 skipped,
  includes `developerSectionRowsAreFocusableAndLeftReturnsToSectionList` (T05). Gradle was routed through `agentw`
  (`GRADLE=`) because the guard refuses `./gradlew` in a worktree.
- Install: `tools/android-apk-install-preserve` outcome `installed`, `compatible_replace`, `destructive_action: false`,
  devDebug `914f1a6b181d...` (on-device `base.apk` sha256 equal). No uninstall, clear or downgrade. No playback, no
  Details opened. Login survived (Home loaded).
- AC-1a: Settings, For developers, Session log on. Press Share: zip `puber-telemetry-ff0f416b-...-1255.zip` holds the
  closed file with `act ShareSessionLog` (the action that triggered the export, so the drain works) and its open
  file was rotated. Second run: toggled "Show stream information" on and off, then Share about 2 s later: the new zip
  holds `act ToggleDebugOverlay n=2` and `act ShareSessionLog` from the just-written tail. Exactly one zip in the
  directory after each press (the older one replaced, name carries the new minute), 0 `*.tmp`. The stream-information
  toggle was left off.
- AC-9: force-stop and cold start with logging on: one `startup` line `{proc_start:0, app_create:1242,
  app_composed:2659, first_content:5632}` (ascending), written together with `ready scr=Home st=Content ms=1644`
  (Home was the landing screen; `first_content` = the process-clock time of that first ready, t 4375 + 1257 ms process
  offset). Repeated on the next cold start: 0/1065/2488/4927. Landing on a non-Home screen could not be provoked
  on this device; covered by unit tests only.
- AC-10: `am crash` with logging on: new line `err scr=Home src=uncaught et=android.app.RemoteServiceException$CrashedByAdbException`
  (full binary name, `msg` "shell-induced crash"); `frames` is `[]` because that exception's trace holds only framework
  frames and the sink keeps app frames only. The earlier `err src=log` at cold start shows full names:
  `et=java.io.IOException`, frames `com.kino.puber.core.tvhome.AndroidTvHomePublisher.ensureChannel:52`,
  `...TvHomeSyncCoordinator$refreshNow$1.invokeSuspend:14`. Process died (no pid after 3 s), `FATAL EXCEPTION` in the
  crash buffer as before.
- AC-10a: relaunch, toggle off then on without restart: new file with `sid 6885f0ee`, `t` 2 / 17 for `session` and
  `meta start`, previous sid `5b618ff6` (its tail `ToggleSessionLog` and `meta stop`). Toggled off again; pref cleared
  (`telemetry_preferences.xml` empty map), file set constant. Final device state: devDebug, logging off.
- Not done: the optional devBenchmarkRelease pass (AC-8 and AC-10 release path, retrace of `et`): not run in this round;
  Round 2 covers the release names, and the full-binary-name change is only shown on debug here.

## Round 4 (head `bbe17d3e`): review round 2 fixes

- Static gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` with tests
  forced to re-run. First forced run under host load: 1766 tests, 1 failed
  (`HomeVMIncrementalPublishTest.aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped`, line 562, a
  Home test unrelated to telemetry). Re-run of that class alone twice with `--rerun-tasks`: pass both times. Then the full
  gate again (`:app:cleanTestDevDebugUnitTest` first): BUILD SUCCESSFUL, 1766 tests, 0 failed, 0 skipped; detekt and
  lint clean. The one failure is treated as a load-dependent flake and reported as such, not rounded up.
- Device: `emulator-5554`, leased and released. devDebug `20265b402079...` installed with install-preserve
  (`installed`, `compatible_replace`, `destructive_action: false`; on-device `base.apk` sha equal). No playback.
  One mis-aimed OK on a Home card opened a Details screen (no Watch pressed); left with BACK.
- AC-9: logging on, force-stop, cold start: one `startup` line `{proc_start:0, app_create:755, app_composed:1589,
  first_content:3567}` (ascending), written at the same `t` as `ready scr=Home st=Content ms=1049`. Release build
  (second cold start): 0/44/534/1722 with `ready Home Content`. Non-Home landing is not provokable; unit only.
- AC-1a: Share press 1 -> exactly one zip (`puber-telemetry-6e817b91-20261015-1327.zip`, the previous
  `ff0f416b` zip was replaced), 0 `*.tmp`, 44 closed files inside; chooser handed to Bluetooth, back on Settings.
  Stream-information toggled on/off, Share press 2 about 2 s later -> still exactly one zip (`...-1328.zip`),
  new name, it contains the `ToggleDebugOverlay` x2 and the second `ShareSessionLog` act lines of the current
  session (`...-6e817b91-02.jsonl` was closed by the export); 0 `*.tmp`. Then two more toggles, force-stop and a
  new cold start (new session file `-01`): the `...-1328.zip` was still present, size unchanged (26951), the only zip,
  0 `*.tmp`. A 2 MiB size rotation cannot be reached on the device; unit-covered.
- AC-4 / AC-10a (light): in the settings, toggle off then on without restart: old file `c98bd57f` ends with
  `act ToggleSessionLog` and `meta stop`; new file `ef755bda` starts with `session` at `t:0` and `meta start` at `t:5`
  (no negative `t`, no leftover events from the old session).
- Optional release pass: `devBenchmarkRelease` (minified, sha `831255bbb10c...`) installed with install-preserve
  (`installed`, `compatible_replace`, non-destructive). `err src=log et=java.io.IOException` (full binary name), frames
  `["of.b:280","kz4.b:125","mz4.invokeSuspend:12","tu.resumeWith:8","p11.run:114"]` (obfuscated `class.method:line`).
  Retrace with `~/Android/Sdk/cmdline-tools/latest/bin/retrace` and `mapping.txt` of the same build, input as
  `at of.b(SourceFile:280)` lines: resolved to `AndroidTvHomePublisher.ensureChannel(AndroidTvHomePublisher.kt:52)`,
  `AndroidTvHomePublisher.reconcile(...kt:25)`, `TvHomeSyncCoordinator.refreshNow(...kt:35)`,
  `TvHomeSyncCoordinator$refreshNow$1.invokeSuspend` (and framework `PreviewChannelHelper` frames), matching the
  debug frames of the same error. Retrace works when the frame is rewritten to the standard form.
- Final device state: devDebug reinstalled, session log off (screenshot checked), file count constant (49) over 5 s.
