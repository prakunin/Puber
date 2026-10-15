# Brief: telemetry-core (slice S1 of usage-telemetry-log)

Triage of 2026-10-15 against master `39903d31`. Builds on `research.md` (this folder, inherited) and on the
series brief `.flow/runs/usage-telemetry-log/brief.md` (principles, event catalogue, AC-1..AC-21, binding
"Owner answers (2026-10-15)": Q1 adb pull + Share log (AC-1a), Q2 content ids yes / titles and search text no
(search only as length), Q3 toggle in all builds incl. prod release, default off, Settings > Developer,
7-day auto-off, visible indicator). Series IDs are kept. No `brief.md` template file exists in the checkout;
this follows the shape of the series brief.

## Classification

- **kind:** feature
- **size:** M. One goal (the sink and its first event families) that crosses layers (core, DI/startup,
  ViewModel base, navigation, Settings UI, manifest/FileProvider, R8 rules) and touches risk areas (startup,
  Koin, ViewModel wiring, persistence, manifest, build variants). Not L: it is one shippable "sink plus
  first probes". If the gate proves too big at plan time, the narrower cut is: move AC-1a (Share log) and
  `err` hooks beyond the Timber tree into a follow-up; do not split the sink from its toggle.
- **tracks:** `perf` (the log is the measuring instrument; AC-9 yields the first time-to-content numbers).
  `ui` as well: the Settings change is not just a row. See finding F1.
- **Device verification: yes** (startup, DI, persistence, Settings D-pad UI, FileProvider share, R8 build).

## Findings in code (verified at base)

- **F1. The Developer section is hidden outside `debug`.** `DeviceSettingsContent.kt:185-187` filters
  `SettingsSection.Developer` out unless `BuildConfig.DEBUG`; `DeviceSettingsContent.kt:465` renders
  `developerItems` (`DeviceSettingsInformationItems.kt:147-158`, one row: debug overlay). Q3 needs the section
  in `deploy`/`release`. So the gate moves: show the section always, keep the debug-overlay row itself
  `BuildConfig.DEBUG`-only (today a release user must not gain the overlay toggle). Also
  `settings_developer_description` (`DeviceSettingsContent.kt:398`) and section order change visibly in
  prod. No stand under `docs/design/` governs Settings (only `catalogue`, `content`, `player-controls`),
  so `ux-designer` will report "no stand"; see question 1.
- **F2. Timber is planted only on `DEBUG`.** `PuberApp.kt:88-92` (`initLogger`). `Any.log` reaches Timber via
  `core/logger/Logger.kt:7-20` and already sanitizes through `SensitiveRequestLogPolicy`
  (`core/logger/SensitiveRequestLogPolicy.kt:64-279`). The `err` tree must be planted when the toggle is on,
  in every build type, and unplanted when off (AC-10: no behaviour change when off).
- **F3. Hook points.** `PuberVM.updateViewState` `core/ui/PuberVM.kt:82` (single state setter);
  `PuberVM.onAction` `:135`; `DefaultExceptionHandler` `core/coroutine/CoroutinesErrorHandler.kt:10-14`,
  reached from `PuberVM.launch` `:86-99`; nav collector `FlowComponent.kt:225-226`; tab router
  `TabComponent.kt:104` (`TabCommand.Open` collector); `PuberApp.onCreate` `PuberApp.kt:62-` (Koin starts in
  `initDi`; `attachBaseContext` runs before Koin, so the sink must not depend on Koin for the flag read:
  read `SharedPreferences` directly, as `AppLocale` does). No uncaught-exception handler exists today.
- **F4. R8 names (AC-8).** `PuberScreen.key` is `javaClass.simpleName` (`core/ui/navigation/PuberScreen.kt:7-10`);
  `release` is minified (`app/build.gradle.kts:181-189`); `app/proguard-rules.pro` has no `-keepnames` for
  screens/actions/states. `simpleName` of `ViewState` and `UIAction` classes would be obfuscated in `release`.
  `key` must not change (it is navigation identity); add a separate telemetry name.
- **F5. FileProvider exists but is update-only.** `AndroidManifest.xml:66-74` declares authority
  `${applicationId}.update_files` with `res/xml/update_file_paths.xml` (`external-files-path` `Download/updates/`).
  Sharing the log needs a second path entry restricted to `telemetry/` (or a second provider); the zipped
  archive must live in that directory (or a `cache-path` subfolder named for it). `ContentSharer`
  (`core/system/ContentSharer.kt:9-15`) is text-only and is not reusable as is.
- **F6. Write-path precedent.** `ui/feature/player/component/TimedActionDebugTrace.kt:10-37`: one daemon
  executor, `getExternalFilesDir` with early return when null; unbounded. Copy the thread model, not the
  absence of bounds. Unit tests live under `app/src/test/` (JUnit 5, `MainDispatcherExtension`); the sink
  must take a clock, a file store and an executor/dispatcher as constructor parameters to be testable.

## Scope of this run

In: `core/telemetry` (typed event classes, allow-list encoder, bounded rotating JSONL writer on one writer
thread, per-type rate caps with `meta` lines, 2 MB per file / 20 MB total, flush every few seconds and on
pause, volatile enabled flag, 7-day auto-off with injectable clock, API that later slices extend by adding an
event class and an encoder branch), Settings > Developer rows ("Session log (for diagnostics)" toggle,
"recording, N d H h left" indicator, "Share log"), FileProvider share of one zip of closed files, events
`session`, `startup`, `nav`, `ready`, `act`, `err`, explicit stable names, `startup` marks.

Out: `key`, `key_lat`, `frames`, `mem`, `net`, `net_media`, `play`, `ApplicationExitInfo` exit reason, summary
tool, gzip. For `startup`, marks available without new dependencies only: process start, `Application.onCreate`,
`App` composed, Home first content (S1 may skip a "splash frame" mark if it needs JankStats or
`ReportDrawnWhen` wiring that is not trivial; say so in the plan).

Sink API requirement for later slices: a single `Telemetry` entry (`isEnabled` volatile read, `record(event)`),
event as a sealed type with a per-event encoder in one file, per-type caps declared next to the event, no free
`Map<String, Any>` entry point. S2-S5 can then add an event and a probe without touching the writer.

## Acceptance criteria (series IDs kept)

- **AC-1** Given a fresh install, when the user opens Settings, Then the Developer section is present in
  `debug`, `deploy` and minified `release`, shows a "Session log" toggle that is off, and no telemetry
  directory contents exist. Switching it on shows a visible "recording" indicator in Settings with the time
  left until auto-off. The debug-overlay row remains `debug`-only. (D-pad reachable: every new row takes
  focus, Left returns to the section list.)
- **AC-1a** Given logging has produced files, when the user picks "Share log" in Settings > Developer, Then
  the Android share sheet opens with one zip of the closed files, and the FileProvider path covers only the
  telemetry directory (check the `paths` XML and that the update-file path still works). Given no files,
  Then the action is disabled or says there is nothing to share, and does not crash.
- **AC-2** Given the toggle is off, when the app runs through startup, navigation and actions, Then no
  telemetry file is created or written and no probe allocates (unit test on the sink flag: encoder and
  writer never invoked; device observation of the empty directory).
- **AC-3** Given the toggle is on, when the app is used, Then JSONL files appear under
  `getExternalFilesDir("telemetry")`, every line has `t`, `sid`, `type` plus the catalogue fields of that type
  and no others, readable after `adb pull`. If `getExternalFilesDir` returns null, recording silently stays
  off and the indicator does not claim "recording" (unit test with a fake store).
- **AC-4** Given the toggle was switched on 7 days ago (injected clock), when the app starts or the next
  event is recorded, Then recording is off and the toggle shows off.
- **AC-5** Given a stream above its cap, Then excess events drop and a `meta` line reports counts per type;
  given 20 MB of files, when a new file opens, Then the oldest file is deleted first (fake clock, fake store).
- **AC-6** Given the sink is writing, when a screen opens and a list is scrolled, Then no write, JSON
  formatting or file IO runs on the main thread (unit test asserts the writer thread; encoding also happens on
  the writer thread, the probe only enqueues an immutable event).
- **AC-7** Given any recorded event, when the log is searched for any title, search text, URL, token or query
  value used in the session, Then none is found. Unit test: events built from sensitive inputs through the
  encoder; `err` messages pass `SensitiveRequestLogPolicy`; device log scanned with
  `tools/mobile-evidence-audit.sh`. Content ids are permitted only as typed fields (Q2); `nav` and `act` carry
  class names and no payloads, so S1 logs no ids.
- **AC-8** Given `release` (minified), `deploy` and `debug`, when screens, states and actions are logged,
  Then the names are stable and readable (verify in the minified `release` build on device; `PuberScreen.key`
  value unchanged).
- **AC-9** Given a cold start with logging on, when Home shows first content, Then the log holds a `startup`
  event with marks in ascending order, and `nav`, `ready` and `act` lines for the screens opened, each `ready`
  carrying ms since its screen opened and only on state-class change; high-frequency actions are coalesced
  per second with a count.
- **AC-10** Given a ViewModel coroutine failure, a token refresh failure or an uncaught exception, when
  logging is on, Then an `err` line records exception type, sanitized message and top 5 app frames, rate
  capped at 20 per minute with identical errors folded; the previous uncaught handler is still called (chain,
  crash behaviour unchanged). Given logging is off, Then no Timber tree is planted outside `debug`, no handler
  is added and the existing `log(...)` call sites behave as before.
- **AC-10a (S1-only, new)** Given the toggle is switched on at runtime, Then the Timber tree and the uncaught
  handler start without an app restart, and switching off unplants them; the toggle value survives process
  death (SharedPreferences, not only VM state).

## Risks

- **Developer section in prod** (F1): changes a shipped screen; the debug-overlay row must not leak into prod.
- **Privacy**: any free-text field is a leak. Mitigation: typed events, allow-list encoder, no map entry point,
  messages through `SensitiveRequestLogPolicy`, stack frames limited to app package without message text.
- **Self-inflicted jank / startup cost**: the flag read happens before Koin; no file IO or JSON on main; the
  startup path must not block on the sink (lazy writer thread, created on first enabled record).
- **R8** (F4): keep rules too broad inflate the APK; too narrow leave obfuscated names. Verify on device in the
  minified build, not by reading the rules.
- **Uncaught handler**: chaining mistakes can swallow crashes. Must delegate to the previous handler; add a
  test.
- **FileProvider**: widening `update_file_paths.xml` must not expose `Download/updates/` or other external
  files; zip is created off the main thread; the share intent grants read via `FLAG_GRANT_READ_URI_PERMISSION`.
- **Storage / Share while writing**: zip only closed files; the current open file is excluded or the writer is
  flushed and rotated first. Decide in plan.
- **Merge friction**: `FlowComponent.kt` and `PuberVM.kt` are hot (`upstream-sync-triage`, `player-overlays-port`
  in `impl`); keep edits at the existing hook lines, minimal diff.
- **Overlap** with `app-coroutine-scope` (error sink) and `device-verification-debt` (device time scarce):
  batch the device pass with the other telemetry slices if the lease allows.
- **Unverified**: real bytes per line (series arithmetic only; measure in AC-3 device session and correct the
  budget); behaviour of `getExternalFilesDir("telemetry")` on API 24-28 emulators/boxes.

## Test notes

JUnit 5 under `app/src/test/`. Sink core takes `Clock`, a `TelemetryStore` (fake in tests), and a single
writer executor/dispatcher. Tests: flag off writes nothing and does not touch encoder; allow-list encoder
with sensitive inputs; rotation at 2 MB and oldest-first deletion at 20 MB; per-type cap emits `meta` with
counts; auto-off at 7 days via injected clock (start and next-record paths); null store; writer-thread
assertion; `ready` records only on class change; `act` coalescing; uncaught-handler chaining; Settings VM
state for toggle, indicator text and Share action (`FakeResourceProvider` for strings). Device: lease, install
the worktree APK with `tools/android-apk-install-preserve`, toggle on, open Home, History, Settings, press
Back, `adb -s <serial> pull` the log, run `tools/mobile-evidence-audit.sh`; repeat the name check on a
minified `release` (or `nonMinifiedRelease` is not enough for AC-8).
Checks: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (per `.flow/config.yaml`).

## Open questions

1. **Does the Settings change need a design stand before planning?** No stand under `docs/design/` covers
   Settings, and the Developer section becomes visible in prod (F1) with three new rows (toggle, indicator,
   Share). Recommended: no stand; reuse `SettingsToggleItem`/existing row styles, `ux-designer` reviews the
   result visually only, and the owner judges the indicator wording. Blocks: yes (if the owner wants a stand,
   `designing-a-screen` runs with them before planning, per `.flow/config.yaml` escalate_also).
2. **How to make names stable under R8 (AC-8)?** Recommended: `-keepnames` rules for `PuberScreen`,
   `UIAction` and `ViewState` implementers in `app/proguard-rules.pro` (no per-class edits across 19 VMs and
   all screens, keeps `simpleName` readable), plus a telemetry name accessor with the `simpleName` fallback.
   Alternative: explicit name strings per class (more code, more merge friction). Blocks: no.
3. **Which logged-on state should the zipped Share include?** Recommended: rotate the current file on Share,
   zip only closed files, name the archive `puber-telemetry-<sid>-<date>.zip`, delete the previous archive
   before creating a new one (stays inside the 20 MB bound). Blocks: no.
