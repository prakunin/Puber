# Telemetry core: opt-in session log sink, Settings toggle and first events

Status: approved
Date: 2026-10-15
Slug: telemetry-core
Kind: feature
Size: M
Tracks: ui, perf

## Problem / Why

Slice S1 of `usage-telemetry-log` (series brief `.flow/runs/usage-telemetry-log/brief.md`). The owner wants an
opt-in, temporary, bounded on-device log of real usage to find speed, memory, smoothness and responsiveness
problems. This slice delivers the sink, the toggle and the first event families.

### Findings in code (verified at base)

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

### Scope

In: `core/telemetry` (typed event classes, allow-list encoder, bounded rotating JSONL writer on one writer
thread, per-type rate caps with `meta` lines, 2 MB per file / 20 MB total, flush every few seconds and on
pause, volatile enabled flag, 7-day auto-off with injectable clock, API that later slices extend by adding an
event class and an encoder branch), Settings > Developer rows ("Session log (for diagnostics)" toggle,
"recording, N d H h left" indicator, "Share log"), FileProvider share of one zip of closed files, events
`session`, `startup`, `nav`, `ready`, `act`, `err`, explicit stable names, `startup` marks.

## Acceptance criteria

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

## Out of scope

Out: `key`, `key_lat`, `frames`, `mem`, `net`, `net_media`, `play`, `ApplicationExitInfo` exit reason, summary
tool, gzip. For `startup`, marks available without new dependencies only: process start, `Application.onCreate`,
`App` composed, Home first content (S1 may skip a "splash frame" mark if it needs JankStats or
`ReportDrawnWhen` wiring that is not trivial; say so in the plan).

Sink API requirement for later slices: a single `Telemetry` entry (`isEnabled` volatile read, `record(event)`),
event as a sealed type with a per-event encoder in one file, per-type caps declared next to the event, no free
`Map<String, Any>` entry point. S2-S5 can then add an event and a probe without touching the writer.

## Design

Read against worktree `/home/user1/orca/workspaces/Puber/telemetry-core` at `39903d31`. Paths are relative to
`app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/` or `config/`. Every `path:line`
below was opened and checked at base. The visual delta for Settings is the ux-designer's
(`design-ux-designer.md`). This design follows it and sets no sizes.

### Findings that change the brief's plan (verified at base)

- **F7. The base `onAction` sees almost nothing.** 18 of the 19 overrides call `super.onAction(action)` only
  in their `else ->` branch (for example `ui/feature/home/vm/HomeVM.kt:197`,
  `ui/feature/player/vm/PlayerVM.kt:678`, `ui/feature/device/settings/vm/DeviceSettingsVM.kt:175`).
  `docs/architecture/viewmodel.md:45` makes that the convention. A probe in `core/ui/PuberVM.kt:135-140`
  would therefore record only the actions no screen handles, and AC-9 (`act` lines) would fail. The hook
  has to become a template method (D3).
- **F8. In minified `release`, R8 removes every `log(...)` call.** `app/proguard-rules.pro:130-132` declares
  `-assumenosideeffects` for `LoggerKt.log(...)`, which covers both overloads (`core/logger/Logger.kt:7`,
  `:11`). `DefaultExceptionHandler` reaches the logger only through `t.log(t)`
  (`core/coroutine/CoroutinesErrorHandler.kt:13`). So a Timber `err` tree in `release` would get nothing,
  and AC-10 (all builds, owner Q3) would not hold there. See D7.
- **F9. Token refresh failure never logs a throwable.** `data/api/KinoPubApiClient.kt:200` and `:218` log
  only a message (debug priority), and `sessionExpiredFailure` (`:224-227`) throws
  `ApiError.Unauthorized`. A WARN-and-above tree does not see the failure at its source, so it needs its
  own probe (D7).
- **F10. `PuberScreen` names already survive R8.** `app/proguard-rules.pro:90` keeps the names of
  `PuberScreen` implementers. AC-8 still needs names for view models, actions and view states, and only
  `UIAction` has a marker interface (`core/ui/uikit/model/UIAction`, used for example by
  `ui/feature/home/model/HomeAction.kt:5`). View state types share no supertype: `PuberVM<ViewState>` takes
  any type (`core/ui/PuberVM.kt:30`). See D6.
- **F11. `ready` cannot fire for 4 view models.** `MainViewState`, `ContentListViewState`,
  `NetworkDiagnosticsViewState` and `DeviceSettingsViewState`
  (`ui/feature/device/settings/model/DeviceSettingsViewState.kt:10`) are single data classes, so their
  class never changes. The other 15 are sealed. See Open question 2.
- **F12. `SanitizedLogThrowable` is private** (`core/logger/SensitiveRequestLogPolicy.kt:257-272`) and
  carries the original type only inside its message (`:264-265`). The `err` tree needs the type as a field.
- **F13. The screen key is not a safe name.** Parameterized screens build `key` from ids (for example
  `DetailsScreen`, `docs/architecture/screens-and-di.md:52-55`), and `DIScope` scope names derive from it
  (`core/di/DIScope.kt:38-43`). Telemetry must never log `key` or a scope id. Use class names only.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Where the sink lives and how probes reach it | A process-wide `object Telemetry` in `core/telemetry/` holds an installed `TelemetrySink` (a disabled no-op until `PuberApp` installs the real one). Koin exposes only the controller for Settings: `single<SessionLogController> { Telemetry.controller }` in a new `telemetryModule`, added to `PuberApp.kt:77-83`. | The flag has to work before Koin (`PuberApp.onCreate` marks, crashes in `initDi`), in `PuberVM`, whose constructor takes only `router` (`core/ui/PuberVM.kt:30`) so injecting would touch 20 view models, and in non-DI code (Timber tree, uncaught handler). `AppLocale` (`core/system/AppLocale.kt:307`) is the precedent for state that sits outside the graph on purpose. |
| D2 | Flag storage | A separate SharedPreferences file `telemetry_preferences` with the key `enabled_at_wall_ms` (absent means off). It is read once in `PuberApp.onCreate`, right after `super.onCreate()` (`PuberApp.kt:68`), before `initDi()` (`:70`). Writes use `apply()`. | Survives process death (AC-10a). Readable before Koin, as `AppLocale.stored` does (`core/system/AppLocale.kt:321-324`). Wall time is needed because the 7-day limit has to survive reboots, and `elapsedRealtime` resets on boot. |
| D3 | `act` hook | Template method in `PuberVM`: `onAction(action)` becomes final, records `act` and calls `protected open fun handleAction(action)`. The base `handleAction` keeps today's snackbar body (`core/ui/PuberVM.kt:136-139`). All 19 overrides are renamed mechanically: `override fun onAction` becomes `override fun handleAction`, and `super.onAction(action)` becomes `super.handleAction(action)`. `docs/architecture/viewmodel.md:18,22,45` are updated to match. | F7. One choke point that the compiler enforces: a future view model cannot bypass it. Callers (`vm::onAction` at about 31 screen call sites, and the tests) do not change, because `onAction` stays public with the same signature. |
| D4 | `ready` hook | In `PuberVM.updateViewState(viewState)` (`core/ui/PuberVM.kt:82-84`): read the previous value, set the new one, and if logging is on and `prev?.javaClass !== new?.javaClass`, record `ready(vm class, new state class, ms since open)`. The open time is `private val openedAtMs = Telemetry.nowMs()`, taken in the `PuberVM` constructor. | Only one setter exists (the reified overload at `:74-80` delegates to it, and no subclass overrides it). Class identity costs nothing and carries no payload. Measuring from VM construction is the closest observable point to "screen opened": `puberViewModel` creates the VM on the screen's first composition (`core/di/DIScope.kt:19-28`). |
| D5 | Names (`scr`, `vm`, state, action) | `TelemetryNames.of(Class<*>)`, resolved and cached on the writer thread: `simpleName` with one trailing `Screen` or `VM` removed, so `HomeScreen` and `HomeVM` both become `Home` and nav lines join ready/act lines by string. A `PuberTab` is replaced by the screen it wraps through a new non-parceled getter `internal val contentScreenClass: Class<*> get() = screen.javaClass` on `core/ui/navigation/PuberTab.kt:19`. State and action names are the bare `simpleName` (`Content`, `ItemFocused`). `PuberScreen.key` (`core/ui/navigation/PuberScreen.kt:7-10`) is unchanged. | Owner Q2. F13. Probes enqueue `Class` references, so they allocate no strings. |
| D6 | Names under R8 (AC-8) | Add to `app/proguard-rules.pro` after `:94`: `-keepnames class * extends com.kino.puber.core.ui.PuberVM`, `-keepnames class * implements com.kino.puber.core.ui.uikit.model.UIAction`, and, because view states have no marker (F10), the name patterns `-keepnames class com.kino.puber.ui.**State` and `-keepnames class com.kino.puber.ui.**State$*`. `InnerClasses` is already kept (`:15`), so `simpleName` of nested sealed members stays short. | Owner Q2. These are names only, so shrinking still applies. The only proof is checking on the device in a minified `release` (brief AC-8). |
| D7 | `err` sources (AC-10) | (a) `TelemetryErrorTree : Timber.Tree`, loggable at `priority >= WARN` with a non-null throwable only. It is planted when logging turns on and uprooted when it turns off. (b) Narrow `app/proguard-rules.pro:130-132` so that only the message overload is stripped: `public static void log(java.lang.Object, java.lang.String, java.lang.String);`. `log(Throwable, …)` then survives in `release` and reaches the tree, which covers `DefaultExceptionHandler` (`core/coroutine/CoroutinesErrorHandler.kt:13`) and about 40 other `log(e, …)` sites. (c) A one-line probe in `KinoPubApiClient.sessionExpiredFailure` (`data/api/KinoPubApiClient.kt:224-227`): build the `ApiError.Unauthorized`, record it with `src=auth`, then throw it. (d) `TelemetryUncaughtHandler`, installed when logging turns on: it records `src=uncaught`, flushes with a bound of 1.5 s (skipped when the crash is on the writer thread), then always calls the previous handler in `finally`. On disable it restores the previous handler only if the current default is still ours; otherwise it stays installed but inert. (e) `SanitizedLogThrowable` becomes `internal` and exposes `sourceType` (F12). | F8 and F9. Logging stays uniform across build types. With the toggle off, output is unchanged: R8 still strips debug-message calls, and no tree is planted outside `debug`. See Open question 1 for the CPU-only difference. |
| D8 | Threading (AC-6) | Producers check the flag, build an immutable event (a primitive `t` plus enums and `Class` or `Throwable` references) and call `queue.offer`. The queue is a `ConcurrentLinkedQueue` with an `AtomicInteger` bound of 4096. All work happens on one daemon thread named `puber-telemetry`, a single-thread `ScheduledExecutorService` created lazily on the first enable: caps, folding, name resolution, JSON encoding, file IO, rotation, zip and auto-off checks. Drain runs every 5 s, on `onActivityPaused` (an `ActivityLifecycleCallbacks` registered in `PuberApp.onCreate`), on export, and on a crash. | Copies the single-writer model of `ui/feature/player/component/TimedActionDebugTrace.kt:15-17` and adds bounds. One owner for the directory means rotation, deletion and zip need no locks. |
| D9 | Off means free (AC-2) | Every probe is written `if (Telemetry.isEnabled) Telemetry.record(...)`, or uses an `inline` helper with the same check, so nothing is allocated before the check. `isEnabled` reads a `@Volatile` boolean. The encoder and writer are reachable only through `record`, which also checks the flag. | AC-2 unit test: with the flag off, a fake sink, encoder and store see zero calls. |
| D10 | Encoding | `TelemetryEncoder.encode(event): JsonObject` uses `kotlinx.serialization.json.buildJsonObject` (already a dependency, `app/build.gradle.kts:386`), with one exhaustive `when` over the sealed `TelemetryEvent` in one file. There is no `Map<String, Any>` entry point. | Exhaustive `when` makes a new event fail to compile until it has an encoder branch. That is the allow-list. kotlinx works in JVM unit tests, which `org.json` (used by the trace) does not. |
| D11 | Caps and folding (AC-5, AC-9, AC-10) | `enum class TelemetryEventType(val wire: String, val capPerMinute: Int)` declares the caps next to the events: `session` 10, `startup` 2, `nav` 120, `ready` 240, `act` 240 (after folding), `err` 20, `meta` uncapped. The writer counts per type per wall minute and drops above the cap. Queue overflow drops are counted by producers in an `AtomicIntegerArray` indexed by ordinal. Both counts go out in one `meta` line (`ev=dropped`, `counts={type:n}`) on the next drain. There are two `EventFolder` stages on the writer: `act` folds consecutive identical (vm, action) within 1000 ms into one line with `n`; `err` folds identical (type, first frame) within 60 s into the first line's follow-up `n`. | The series' rate and bound principle. Folders are a list, so later slices can add one without editing the writer. |
| D12 | Files (AC-3, AC-5) | Directory: `getExternalFilesDir("telemetry")`, resolved on the writer thread only, on the first enable or export. Files are `s-<yyyyMMdd-HHmmss UTC>-<sid>-<nn>.jsonl`. At 2 MiB the current file is closed and the next opened. When a file opens and `*.jsonl` plus the archive exceed 20 MiB, the oldest `*.jsonl` by name is deleted, never the open file. `sid` is 8 random hex characters per process. `t` is ms since the sink was first enabled in this process. If the directory is null, recording stays off and the status is `Unavailable`. | Sorting by name keeps tests deterministic with a fake store. Not touching `getExternalFilesDir` while off keeps a fresh install free of any telemetry directory (AC-1). |
| D13 | Auto-off (AC-4) | `TelemetryRuntime.install` checks `wallNow - enabledAt >= 7 d` before anything is recorded. The writer checks the same on every drain. On expiry: `meta ev=auto_off`, close the file, clear the preference, set the flag false, uproot the tree, restore the handler, set the status to `Off`. A clock that runs backwards (`enabledAt > now`) clamps to `now`. | Both paths that AC-4 names ("starts", "next event recorded") are covered by the injected clock. A drain follows the next record within 5 s. |
| D14 | `startup` marks (AC-9) | `proc_start` comes from `Process.getStartElapsedRealtime()` (API 24, which is minSdk). `app_create` is taken at the entry of `PuberApp.onCreate` (`PuberApp.kt:67`). `app_composed` is a `LaunchedEffect` placed after `App()` in `MainActivity.kt:54-56`. `first_content` fires on the first `ready` transition into the class a view model declares through a new `protected open val telemetryStartupContentClass: Class<*>? = null` on `PuberVM`; only `HomeVM` overrides it, with `HomeViewState.Content::class.java`. One `startup` line is written on `first_content`, or after 60 s with the marks collected so far. No splash-frame mark: it needs `ReportDrawn` or JankStats wiring, which the brief allows S1 to skip. | Explicit, one line in `HomeVM`, and no name heuristics. The 60-s fallback covers the Auth landing page and a non-Home startup tab. |
| D15 | Settings (AC-1) | `DeviceSettingsContent.kt:185-187` drops the `Developer` filter. `developerItems` (`DeviceSettingsInformationItems.kt:147-158`) emits three rows: a `SettingsToggleItem` with key `session-log` and the indicator in `description`; a `SettingsListItem(role = Role.Button)` with key `share-log`; and the existing `debug-overlay` row, last, wrapped in `if (BuildConfig.DEBUG)`. The state is `sessionLog: SessionLogUi` inside `DeviceSettingsState.Success`, kept current in the same way as `latestWatchIndex` (`DeviceSettingsVM.kt:53, 71-88, 124`). | Follows the ux-designer delta exactly. The `Success` pattern avoids threading a new parameter through `DeviceSettingsContent` → `DeviceSettingsPane` → `SettingsSectionContent` → `developerItems` (`:176, :403, :465`). |
| D16 | Share (AC-1a, owner Q3) | `SessionLogController.share()` (suspend) submits one task to the writer thread: rotate the current file, delete any previous `*.zip`, zip all closed `*.jsonl` into `puber-telemetry-<sid>-<yyyyMMdd-HHmm>.zip` in the same directory (write to `.tmp`, then rename), and return the `File` or null. On the caller side: `FileProvider.getUriForFile(context, "${packageName}.update_files", file)` with the same expression as `data/repository/AppUpdateInstaller.kt:38-41`, then a new `ContentSharer.shareFile(uri, "application/zip", title)` with `EXTRA_STREAM`, `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION` (`core/system/ContentSharer.kt:6-23`). Result: `Shared`, `NothingToShare` or `Failed`, which the view model maps to a snackbar. `res/xml/update_file_paths.xml` gains `<external-files-path name="telemetry" path="telemetry/"/>`. The manifest is unchanged (`AndroidManifest.xml:66-74`). | Owner Q3. Least change: the existing provider gets one path entry, and the `updates` entry is untouched. Zipping on the writer thread cannot race rotation or deletion. |

### Sink API (the extension point for S2-S5)

```text
core/telemetry/
  Telemetry.kt              object Telemetry { val isEnabled: Boolean; fun nowMs(): Long;
                              fun record(event: TelemetryEvent); val controller: SessionLogController;
                              internal fun install(sink: TelemetrySink) }      // tests: install(fake)
  TelemetryEvent.kt         sealed interface TelemetryEvent { val t: Long; val type: TelemetryEventType }
                            enum class TelemetryEventType(wire, capPerMinute)   // caps live here
                            Session, Startup, Nav, Ready, Act, Err, Meta        // S1 members
  TelemetryEncoder.kt       the one exhaustive `when` -> JsonObject (the allow-list)
  TelemetryNames.kt         Class -> stable name, cached; suffix rule; PuberTab unwrap
  TelemetrySink.kt          queue, writer thread, pipeline (caps -> folders -> encoder -> store)
  EventFolder.kt            interface + ActFolder, ErrFolder
  TelemetryStore.kt         interface (list, append, delete, size, zip) + FileTelemetryStore
  TelemetryClock.kt         interface { elapsedMs(); wallMs() } + SystemTelemetryClock
  TelemetryRuntime.kt       install(app): prefs, auto-off at start, tree/handler, lifecycle flush
  SessionLogController.kt   interface { state: StateFlow<SessionLogState>; setEnabled(Boolean);
                              refresh(); suspend share(): ShareResult }
  StartupMarks.kt           primitive marks, emits one Startup event
  TelemetryErrorTree.kt, TelemetryUncaughtHandler.kt, ErrorScrubber.kt
  di/TelemetryModule.kt     single<SessionLogController> { Telemetry.controller }
```

A later slice adds a sealed member, an enum entry with its cap, an encoder branch and its probe, plus a
folder or aggregator if it needs one. It does not touch the writer, the store or the controller.
`SessionLogState` is `Off | Recording(remainingMs, hasFiles) | Unavailable`, plus `hasFiles` when off.

#### Line schema (S1)

Envelope on every line: `t` (ms since session start), `sid`, `type`, `scr`. `scr` is the event's own screen
for `nav` (equal to `to`), `ready` and `act` (the view model's name, D5). On other lines it is the last
`nav.to` the writer saw, or `""` before the first one.

| type | fields | notes |
|---|---|---|
| `session` | `build`, `ver`, `api`, `model`, `ram_class`, `low_ram`, `hz`, `wall_ms` | once per enable per process; gathered on the writer thread |
| `startup` | `proc_start`(0), `app_create`, `app_composed`, `first_content` (ms from process start; absent if not reached) | ascending by construction; a unit test asserts it |
| `nav` | `cmd` (`push`, `push_result`, `replace`, `new_root`, `back`, `back_to`, `finish_flow`, `tab`, `activity`), `from`, `to` | `from`/`to` = `navigator.lastItemOrNull` before and after the `when` |
| `ready` | `st` (state class), `ms` (since VM open) | only when the state class changes |
| `act` | `a` (action class), `n` | folded within 1 s |
| `err` | `src` (`log`, `auth`, `uncaught`), `et` (exception type), `msg`, `frames` (at most 5 strings), `n` | `msg` passes through `sanitizeText` (`core/logger/SensitiveRequestLogPolicy.kt:106`), then `ErrorScrubber` replaces every URL with `<url>`, cuts at `JSON input`, collapses whitespace and caps at 160 chars. `frames` drops `java.`, `javax.`, `kotlin.`, `kotlinx.`, `android.`, `androidx.`, `dalvik.`, `com.android.`, `okhttp3.`, `io.ktor.` and `timber.` frames, which still works when R8 has renamed packages. Frames are `Class.method:line`, no messages. |
| `meta` | `ev` (`start`, `stop`, `rotate`, `auto_off`, `dropped`, `unavailable`), `counts` (dropped only) | uncapped |

### Changes by layer

**API / repository**
- `data/api/KinoPubApiClient.kt:224-227`: `sessionExpiredFailure` records `err src=auth` before it throws (D7c).
- `core/system/ContentSharer.kt:6-23`: add `shareFile(uri, mimeType, chooserTitle): Boolean`. `share` stays as it is.
- `core/logger/SensitiveRequestLogPolicy.kt:257-272`: `SanitizedLogThrowable` becomes `internal` with `val sourceType` (D7e).

**Core (new package `core/telemetry/`)**, as listed above. Also:
- `PuberApp.kt:67-72`: capture `app_create` at entry, call `TelemetryRuntime.install(this)` after `super.onCreate()` and before `initDi()`, and register the lifecycle callbacks for flush. `PuberApp.kt:77-83`: add `telemetryModule`. `initLogger` (`:88-92`) is unchanged.
- `MainActivity.kt:54-56`: `app_composed` mark after `App()`.

**ViewModel state and actions**
- `core/ui/PuberVM.kt`: `openedAtMs` field, the probe in `updateViewState` (`:82-84`), `onAction` made final with `handleAction` (`:135-140`), and `telemetryStartupContentClass`.
- The 19 overrides are renamed (D3). The files are listed in F7's grep: `UpdatePromptVM`, `ContentListVM`, `SectionVM`, `HistoryVM`, `ShowAllVM`, `EpisodeScheduleVM`, `NetworkDiagnosticsVM`, `CollectionDetailVM`, `MainVM`, `DeviceSettingsVM`, `SearchVM`, `FavoriteVM`, `HomeVM`, `CollectionsVM` (super call first, `:38`), `AuthVM`, `DetailsVM`, `LauncherVM`, `PlayerVM`, `BookmarksVM`.
- `ui/feature/home/vm/HomeVM.kt`: override `telemetryStartupContentClass` (the initial state is `:71`).
- `ui/feature/device/settings/model/DeviceSettingsActions.kt:8-39`: add `ToggleSessionLog` and `ShareSessionLog`. `DeviceSettingsViewState.kt:22-47`: `Success.sessionLog: SessionLogUi` (`@Immutable`: `enabled`, `description: String`, `shareEnabled`, `sharing`) and a top-level `restoreShareFocus: Boolean`, the same pattern as `restoreNetworkDiagnosticsFocus` (`:14`).
- `DeviceSettingsVM.kt`: a constructor parameter `sessionLog: SessionLogController` (`viewModelOf` at `DeviceSettingsScreen.kt:36` resolves it from global Koin); in `onStart` (`:66-69`) call `refresh()` and collect `state` into `latestSessionLog`; map the state to strings through `ResourceProvider` (`:49`); handle the two actions (`:135-176`), with `share` run in `launch` and its result mapped to `showMessage`. The four existing tests that construct this view model (`DeviceSettingsVM{AutoTrailer,Language,MirrorNavigation,MenuSections}Test`) pass a relaxed mock.

**Screen and DI module**
- `core/ui/navigation/component/FlowComponent.kt:225-268` (`FlowCommandRunner`) and `:708-746` (`TabFlowCommandRunner`): capture `from` before the `when` and record `nav` after it, inside the existing collector. The `router.log` lines (`:226`, `:709`) stay.
- `core/ui/navigation/component/TabComponent.kt:107`: record `nav cmd=tab` with the previous and new tab.
- `core/ui/navigation/PuberTab.kt:19`: the `contentScreenClass` getter (D5).

**Compose content**
- `ui/feature/device/settings/DeviceSettingsContent.kt:185-187`: show every section.
- `ui/feature/device/settings/DeviceSettingsInformationItems.kt:147-158`: three rows per D15 and the ux delta; Share is `enabled = shareEnabled && !sharing` with `focusableWhenDisabled = true`; a focus requester on Share, plus a request when `restoreShareFocus` is set after resume, as at `DeviceSettingsContent.kt:198-204`.

**Resources and build**
- `res/values/device_settings_strings.xml` (ru, the default) and `res/values-en/strings.xml`: session log label, three description variants (off, recording "N д H ч" / "N d H h", unavailable), share headline and subtitle, "nothing to share yet", "preparing archive", chooser title, share-failed message. The owner judges the wording.
- `res/xml/update_file_paths.xml`: the `telemetry/` entry (D16).
- `app/proguard-rules.pro`: the `-keepnames` rules (D6) and the narrowed `assumenosideeffects` (D7b).
- `docs/architecture/viewmodel.md:18,22,45`: the `handleAction` convention.

### Alternatives rejected

- Probe in the base `onAction` as it stands: misses every handled action (F7).
- Wrapping `vm::onAction` at about 31 screen call sites: misses calls a view model makes to itself and direct `vm.onAction(...)` lambdas, and the compiler does not enforce it.
- Injecting a sink into `PuberVM` through Koin: changes 20 constructors and does not exist before Koin.
- Removing the `LoggerKt` strip rule entirely: debug-message string building would come back to `release`.
- Explicit `err` probes only, with no Timber tree: AC-10 and AC-10a name the tree.
- A second `FileProvider` subclass and manifest entry: more surface for the same restriction. One path entry is enough.
- Free `Map` events or reflection-based serialization: there would be no allow-list to enforce.
- Hand-written JSON: an escaping risk with no gain on the writer thread.
- `ProcessLifecycleOwner` for flush on pause: a new dependency. Activity callbacks are enough.
- A separate indicator row: rejected by the ux delta. It would be a dead focus stop.

### Risks (highest first)

1. **Privacy: `err.msg` is the only free-text field.** Exception messages can carry titles or JSON
   excerpts, and `sanitizeText` (`core/logger/SensitiveRequestLogPolicy.kt:106-114`) redacts query values
   and item ids but keeps URLs and other text. Mitigation: `ErrorScrubber` (removes every URL, cuts at
   `JSON input`, 160-char cap). Unit test with titles, URLs, tokens and a serialization message. The device
   log is scanned with `tools/mobile-evidence-audit.sh`. A title inside a custom exception message is a
   residual risk, and only the audit can catch it.
2. **Crash path: the uncaught handler can swallow or delay crashes** (`TelemetryUncaughtHandler`, a new
   file). Mitigation: the previous handler is called in `finally`, the flush waits at most 1.5 s and is
   skipped on the writer thread, and the handler restores itself only if it is still the default. Unit
   test: the previous handler is called once and with the same throwable, including when the sink throws.
3. **R8 semantics (`app/proguard-rules.pro:130-132`, `:94+`).** A wrong signature in the narrowed rule
   either brings back stripped debug messages in `release` or leaves `err` empty. A wrong `-keepnames`
   pattern leaves names obfuscated. Mitigation: check on the device in minified `release` (AC-8, AC-10):
   force a coroutine failure and read the `err` line and the names.
4. **Merge friction and a mechanical rename across 19 view models (D3).** `PlayerVM.kt` and `FlowComponent.kt`
   are in `player-overlays-port` and `upstream-sync-triage` (both `impl`). A missed rename fails to compile,
   so it is safe but noisy. Keep each edit to the two lines.
5. **Main-thread work at startup** (`PuberApp.kt:67-72`). The preference read happens before Koin, and
   executor creation happens on the first enable. Mitigation: one small preferences file, as `AppLocale`
   does. `getExternalFilesDir`, `session` fields, encoding and IO run only on the writer thread. AC-6 test:
   the encoder runs only on `puber-telemetry`.
6. **Focus and D-pad: the Developer section ships to prod** (`DeviceSettingsContent.kt:185-187`). The
   overlay row must stay `DEBUG`-only, Share must stay focusable while disabled, and focus must return to
   Share after the chooser closes. Review on the device, per the ux delta.
7. **Concurrency in `updateViewState`** (`core/ui/PuberVM.kt:82-84`). `PagingVM` updates from
   `Dispatchers.Default` (`core/paginator/PagingVM.kt:25`). Reading the previous value and comparing it is
   not atomic, so a `ready` line can rarely be duplicated or missed. Accepted: it is a diagnostic. The
   global `Telemetry` object can leak state between unit tests, so tests that install a sink reset it in
   `@AfterEach`.
8. **Memory.** The queue is bounded at 4096 events, and `err` events hold `Throwable` references for up to
   5 s. The name cache is bounded by the number of classes.
9. **Data shape.** No `ready` for the 4 data-class states (F11). The `nav.to` attribution after `Back`
   through a parent navigator (`FlowComponent.kt:303-317`) can name the stale top. `startup.first_content`
   appears only on a Home landing.
10. **Storage and lifecycle.** External storage can be unmounted mid-session: on an IO error the writer
    turns recording off (`meta ev=unavailable`, status `Unavailable`) and never throws. Process death loses
    at most the 5 s since the last drain or pause flush.
11. **Share on TV.** Many TV images have no `ACTION_SEND` target for zip. The chooser may be empty, or
    `startActivity` may throw: `shareFile` returns false and a snackbar says so. AC-1a evidence depends on
    what the emulator offers (Open question 3).
12. **Baseline profile.** `PuberVM.updateViewState` and `onAction` are hot and profiled. With logging off
    the added cost is one volatile read each. Regenerating the profile is not needed for S1.

### Open questions

1. **AC-10, "existing `log(...)` call sites behave as before when off".** Narrowing the R8 rule (D7b)
   leaves output unchanged, but in `release` each `log(throwable, …)` now runs the sanitizer and a Timber
   dispatch to zero trees. That is CPU on error paths only. Recommended: accept, since it is the only way
   `err` is uniform across builds. Alternative: an explicit probe in `DefaultExceptionHandler`, so `release`
   catches coroutine failures, refresh failures and crashes only. Blocks: no.
2. **`ready` for data-class states (F11).** Recommended: accept the gap in S1 and add an optional
   `PuberVM` hook (`telemetryStateClass(state)`) in a later slice if the summary tool needs Settings,
   Main, ContentList or Diagnostics. Blocks: no.
3. **AC-1a on a TV emulator with no share target.** Recommended: count "the chooser opens with the zip
   offered (or the system says no app can handle it), and the URI grant covers only `telemetry/`" as met,
   and keep `adb pull` as the primary export. Blocks: no.
4. **Field names beyond the catalogue wording** (`err.src`, `err.n`, `session.wall_ms`, the `meta.ev`
   values). Recommended: accept the schema above as the AC-3 "catalogue fields". None of them carries a
   payload. Blocks: no.
5. **The toggle is reachable only when device settings have loaded** (`developerItems` takes
   `DeviceSettingsState.Success`). Recommended: accept for S1. Blocks: no.

### Checks for the plan

- fast: `./tools/agentw :app:compileDevDebugKotlin`
- full: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`

Both run in the worktree, never `./gradlew` there.

### UX delta (ux-designer)

Author: ux-designer, design mode. Read against `telemetry-core` worktree at base `39903d31`.

#### Governing stand

**No `docs/design/<screen>/` stand covers Settings.** The stands that exist are `catalogue`, `content` and
`player-controls`, and none of their numbers applies to the Settings panel. The usual route would be
`designing-a-screen` with the owner before planning. Owner decision Q1 (state.md, 2026-10-15) waives it on
one condition: the change only reuses existing Settings row styles. This delta holds to that condition. Any
step past it (a new colour, a dot or badge, a new row component, new sizes) brings the stand route back.

So the reference here is the Settings code as it is, not a stand. The sizes below are quoted from the source
and still need checking on the device in review:

| What | Value | Source |
|------|-------|--------|
| Screen padding | 48 dp horizontal, 28 dp vertical | `DeviceSettingsContent.kt:73-74` |
| Section list width | 264 dp; rows 16/14 dp padding, `titleMedium`, 6 dp gap, 12 dp corner | `DeviceSettingsContent.kt:75, 290, 308, 330` |
| Panel | `surfaceVariant` @ 0.2, 20 dp corner, top pad 8 dp; list pad 12/8 dp, 4 dp row gap | `DeviceSettingsContent.kt:367-371, 437-438` |
| Row | min height 48 dp, padding 16 h / 8 v dp, 12 dp corner, focused scale 1.0 | `DeviceSettingsItems.kt:71, 121-122, 165-166` |
| Row headline | `titleMedium` (16 sp / 18 sp line) Medium, max 2 lines | `DeviceSettingsItems.kt:172-174`, `Type.kt` |
| Row supporting text | `bodySmall` 12 sp, content colour @ 0.7, top pad 3 dp, max 2 lines | `DeviceSettingsItems.kt:177-183` |
| Trailing text | `bodyMedium`, content colour @ 0.76, 20 dp gap, 1 line | `DeviceSettingsItems.kt:187-195` |
| Switch | Material switch scaled 0.75 (39 x 24 dp), 16 dp gap, not focusable | `DeviceSettingsItems.kt:77, 198, 239-264` |
| Focus | fill `onSurface`, content `surface`; pressed `onSurface` @ 0.82 | `DeviceSettingsItems.kt:130-133` |
| Disabled | whole row alpha 0.46 (`dimWhenDisabled`) | `DeviceSettingsItems.kt:103` |

Text width available in a toggle row on 1920 x 1080 (960 x 540 dp): 960 - 2*48 - 264 - 28 = 572 dp of panel,
minus 2*12 list padding, 2*16 row padding, 16 + 39 switch slot = **about 460 dp**. That is roughly 65-70
characters of 12 sp `bodySmall` in Cyrillic before the supporting text wraps to a second line.

#### The delta

##### Section list

- `DeviceSettingsContent.kt:185-187`: the section list stops filtering `Developer` out. All seven sections
  show in every build. The order stays as it is (`SettingsSection` entries), with Developer last. The list
  has no new rows or sizes. Seven rows at about 46 dp plus 6 dp gaps come to about 364 dp, inside the roughly
  424 dp under the title, so the list must not scroll. Check this on the device.
- The section title "Для разработчиков" / "For developers" and its description "Диагностическая информация
  для отладки" now ship to prod. They still describe the section, so they stay. A rename (for example to
  "Диагностика") is a wording question for the owner, not a visual one.

##### Rows in the Developer panel, top to bottom

1. **Session log toggle**: `SettingsToggleItem`, key `"session-log"`.
   - Label: "Журнал сеанса (для диагностики)" / "Session log (for diagnostics)".
   - `description` always set, so the row keeps one height in both states and does not jump when toggled:
     - off: one line explaining what it does and that it turns off by itself, for example "Записывает
       действия в приложении в локальный файл. Выключится сам через 7 дней."
     - on and recording: **the indicator**, for example "Идёт запись · осталось 6 д 23 ч" / "Recording ·
       6 d 23 h left".
     - on but storage unavailable (AC-3, `getExternalFilesDir` null): the line must not say recording, for
       example "Запись недоступна: нет хранилища". Whether the switch shows off or on in this case is the
       ViewModel's call, not a visual one.
   - Keep each variant to one line, at most about 60 characters in Russian (see the width above), so the
     row is the same height in all three states.
2. **Share log**: `SettingsListItem` with `role = Role.Button`, key `"share-log"`. This is the same pattern
   as "Проверить обновления сейчас" (`DeviceSettingsContent.kt:499-506`) and "Проверка скорости"
   (`:762-770`), the existing row-shaped actions in Settings.
   - Headline: "Поделиться журналом" / "Share log".
   - Supporting text: "Упаковать закрытые файлы в архив и отправить" when there is something to share.
     Optionally the archive size goes in `trailingText` (for example "1,2 МБ"), the same slot the API
     domain row uses for its value (`:755`).
   - **Nothing to share**: `enabled = false`, `focusableWhenDisabled = true`, default `dimWhenDisabled`
     (row alpha 0.46), supporting text "Пока нечего отправлять" / "Nothing to share yet". It stays
     focusable so the D-pad does not skip it and the reason can still be read. This matches the disabled
     "Rebuild the index" button in Data (`DeviceSettingsInformationItems.kt:70-76`, `focusableWhenDisabled =
     true`). Do not hide the row.
   - **Zip in progress**: the same disabled, focusable look, with the supporting text "Готовлю архив…". If
     the work takes long enough to need a progress bar, reuse the `SettingSwitchItem` pattern: a `Box` with
     a `LinearProgressIndicator` along the bottom edge (`DeviceSettingsItems.kt:282-295`). No spinner and
     no new component.
3. **Debug overlay** ("Показывать информацию о потоке"): unchanged, `item(key = "debug-overlay")`, emitted
   only under `BuildConfig.DEBUG` and moved to the end of the list. No heading separates it.

There is no separate indicator row. A non-clickable `SettingsListItem` between the toggle and Share would be
a line of text with no focus stop. It reads as a broken row, and the D-pad passes over it. Putting the
indicator in the toggle's own supporting line keeps it next to the switch that controls it. It is visible
whether or not the row has focus, and it inverts correctly with the focused fill because it uses
`LocalContentColor`.

##### Not to add

- No coloured recording dot, red text or `primary`/`error` tint on the indicator. A fixed colour does not
  invert with the focused row (white fill, dark content) and would turn into a hole on focus. It would also
  be a new visual element, which Q1 excludes.
- No `TvSafeButton` for Share. In this panel the pill button only appears under an information block in
  Data. In the Developer section it would set a second action style beside the list rows.
- No `SectionHeading` above the three rows. The panel title and description already say what the section
  is, and one more heading would only push the rows down.

#### Focus order and visibility

- Right from the "Для разработчиков" section row goes to the panel's top item, the **Session log toggle**.
  This is the existing `panelFocusRequester` and `onExit` route (`DeviceSettingsContent.kt:282-286`), and
  the list scrolls to item 0 on the way out (`:427-429`).
- Down goes to **Share log** (a focus stop in all states, including disabled), then in debug builds Down
  goes to **Debug overlay**. Up reverses this. Down from the last row stays put.
- Left from any of these rows goes to the "Для разработчиков" row in the section list. This comes for free
  through `LocalSettingsLeftFocusRequester` and `returnFocusLeft` in `SettingsListItem`, as long as all
  three rows are built on `SettingsListItem` or `SettingsToggleItem`.
- Visibility: the focused row has the standard inverted fill (`onSurface` with `surface` content, 12 dp
  corner, no scale). On a disabled, focusable Share row the whole row, fill included, is at alpha 0.46. That
  is the existing disabled-focused look and must still read as a grey slab on the panel. Review checks this
  on the device.
- Toggling keeps focus on the toggle row. The item key must not change with the state, and the description
  swap must not change the row height.
- **Return from the share sheet**: when the system chooser closes and Settings resumes, focus must be back on
  the Share row, not on the navigation rail or the section list. The precedent is the network-diagnostics
  return (`restoreNetworkDiagnosticsFocus`, `DeviceSettingsContent.kt:198-204`): a requester on the row
  and a request after resume. Review checks it visually with OK on Share, then Back out of the chooser,
  then a screenshot.

#### Shared constants: reuse, do not retune

These are shared by every Settings row. The change must not touch them, and must not copy them into new
values: `ItemMinHeight` 48 dp, `DenseItemMinHeight`, `TrailingSlotSize`, `SwitchScale` 0.75, the 0.46
disabled alpha, the 0.7 and 0.76 supporting and trailing alphas, the 12 dp row corner, and the 16/8 dp row
padding in `SettingsListItemBody`. The same applies to the panel and navigation constants in
`DeviceSettingsContent.kt:73-75`. If a new row seems to need different spacing, raise that as a stand
question. Do not pass a one-off modifier.

The debug-only rule moves from the section filter to the single row. It belongs inside `developerItems`, next
to the overlay item, and the list filter is not to be reworded into a different condition.

#### Open items for the owner (wording, not geometry)

- The indicator and description text (both locales), including the format of the time left. Recommendation:
  always "N д H ч", and "меньше часа" in the last hour.
- Whether to rename the section for a prod audience ("Для разработчиков" or "Диагностика").
- Whether Share shows the archive size in the trailing slot.

#### What review will measure on device (1080p, dp = px / 2)

The section list with seven rows and no scroll. Toggle row height equal in the off and on states (48 dp
minimum, so 96 px or more, and the same in both). Supporting text on one line in both states. The Share row
disabled at alpha 0.46 and still visibly focused. Left edges of all three rows aligned with the other
sections' rows. No overlay row in deploy and release builds. Focus on Share after the share sheet is
dismissed.

## Test design

Inputs: `brief.md`, `design-architect.md` (D1-D16, F7-F13), `design-ux-designer.md`, `.flow/roles/test-designer.md`.
Base `39903d31`. Kind is feature, so there is no failing-first bug test (see "Bugs"). Architect questions are
taken as resolved on recommendation (sanitizer runs on release error paths; no `ready` for data-class states;
AC-1a met when the chooser opens or the system reports no handler, grant limited to `telemetry/`; the
architect's field schema is the AC-3 catalogue; toggle reachable only after device settings load).

### Levels and style

- `unit`: JUnit 5 under `app/src/test/kotlin/com/kino/puber/...`, `@RegisterExtension MainDispatcherExtension`
  (`util/MainDispatcherExtension.kt`), `FakeResourceProvider` (`util/FakeResourceProvider.kt`) for strings,
  mockk for collaborators only (as `DeviceSettingsVMAutoTrailerTest` does), never for the class under test.
- `instrumented`: only `make DEVICE=<serial> itest TESTS=<fqcn>`; extends the existing
  `app/src/androidTest/.../device/settings/DeviceSettingsContentFocusTest.kt`.
- `device`: manual on a leased emulator through `qa-device`, `adb -s <serial>`, D-pad via `input keyevent`.
- `static`: grep / XML / text checks and detekt+lint (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`).

#### Test seams the implementation must offer (so the tests below are possible)

These follow brief F6 and D8-D13; the plan should treat them as requirements, not optional.
- `TelemetrySink` takes `TelemetryClock` (elapsed + wall), `TelemetryStore` and a `ScheduledExecutorService`
  (or a small `Executor` plus manual "drain now" entry). Tests use a `FakeTelemetryClock`, an in-memory
  `FakeTelemetryStore` (records every call with `Thread.currentThread().name`, can throw, can block on a latch,
  can return a null directory) and a manual executor that runs queued tasks on demand. Exactly two tests
  (T35, T36) use the real daemon thread `puber-telemetry`, with a latch and a timeout, never `sleep`.
- `Telemetry.install(sink)` is `internal`; every test that installs one resets it in `@AfterEach`
  (architect risk 7, global state).
- `TelemetryRuntime.install` takes the `SharedPreferences` (in-memory fake that implements the interface, or
  Robolectric-free hand fake) and a `Timber`-forest probe through `Timber.forest()`; the uncaught handler is
  read and written through `Thread.getDefaultUncaughtExceptionHandler()`, saved and restored in the test.
- `PuberVM` test subclass lives in the test source set (a trivial concrete VM; the class under test is the
  real `PuberVM`, only the sink is faked).

### Risk profile

P = probability 1-3, I = impact 1-3. Score 6 or more needs a test at the level where it fails.

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Privacy: `err.msg` free text carries a title, URL, token or JSON excerpt; a new field slips past the allow-list | 2 | 3 | 6 | T19, T38, T39, T40, T41, D3 audit |
| R2 | Uncaught handler swallows or delays a crash, or chain breaks | 2 | 3 | 6 | T56-T59, D7 (`am crash`) |
| R3 | R8: wrong `-keepnames` leaves obfuscated names; wrong narrowed `assumenosideeffects` signature empties `err` in release or restores stripped debug strings | 3 | 2 | 6 | T45 (static), D5, D7 on minified release |
| R4 | Logging off is not free or changes behaviour (allocation, file created, tree planted outside debug, handler added) | 2 | 3 | 6 | T04, T15, T16, T17, D1, perf P1-P2 |
| R5 | Startup cost / main-thread work (flag read before Koin, IO or JSON on main) | 2 | 3 | 6 | T35, T36, perf P1 |
| R6 | Developer section ships to prod and the debug-overlay row leaks with it | 2 | 3 | 6 | T05, D2 on `deploy` and minified `release` |
| R7 | FileProvider widening exposes `Download/updates/` or more of external files, or breaks the update install path | 2 | 3 | 6 | T07, D4 |
| R8 | Share on a TV image with no `ACTION_SEND` target throws or hangs | 3 | 2 | 6 | T12, D4 |
| R9 | Bounds fail: file over 2 MiB, 20 MiB exceeded, open file deleted, cap not applied, queue unbounded | 2 | 3 | 6 | T29-T34 |
| R10 | `act` misses handled actions (F7), or a VM bypasses the template method | 2 | 2 | 4 | T52 and S1 (compiler: `onAction` final) |
| R11 | Auto-off wrong across reboot or clock going backwards (wall clock) | 2 | 2 | 4 | T25-T28 |
| R12 | Share races rotation/writes, zips the open file | 2 | 2 | 4 | T08, T35 |
| R13 | Storage unmounted or IO error mid-session throws into the app | 2 | 2 | 4 | T21, T22 |
| R14 | Mechanical rename in 19 VMs leaves one test or screen calling the wrong method; merge friction in `PlayerVM`/`FlowComponent` | 2 | 2 | 4 | S1, S4, full suite, compile |
| R15 | Global `Telemetry` object leaks state between tests, flaky suite | 2 | 2 | 4 | `@AfterEach` reset rule above |
| R16 | `ready` duplicate or missed under concurrent `updateViewState` (`PagingVM`) | 2 | 1 | 2 | accepted, noted; no test |
| R17 | Memory: queue holds `Throwable` references | 1 | 2 | 2 | T31 asserts the 4096 bound; otherwise accepted |
| R18 | No `ready` for 4 data-class VM states (F11) | 3 | 1 | 3 | accepted by owner decision; T49 documents with a negative case |
| R19 | Real bytes per line differ from the 20 MB arithmetic | 2 | 1 | 2 | measured in D3, correct the budget in the evidence; no test |

### Traceability

Priority: P0 must exist before review, P1 should, P2 nice to have. Test names are the Kotlin test method names;
classes are under `app/src/test/kotlin/com/kino/puber/` unless marked instrumented/device/static.

#### AC-1 Developer section, toggle off by default, indicator

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | T01 `ui/feature/device/settings/vm/DeviceSettingsVMSessionLogTest.offStateShowsToggleOffAndOffDescription` | with controller state `Off`, `Success.sessionLog.enabled == false`, description is the off string (via `FakeResourceProvider`) |
| AC-1 | unit | P0 | T02 same class `recordingStateShowsIndicatorWithDaysAndHoursLeft` | `Recording(remainingMs)` maps to "recording, N d H h left" text with correct floor/rounding (6 d 23 h, under 1 h, exactly 7 d) |
| AC-1 | unit | P0 | T03 same class `toggleActionCallsSetEnabledAndRefreshesState` | `ToggleSessionLog` calls `setEnabled(!current)` and the new state shows without reopening |
| AC-1 / AC-2 | unit | P0 | T04 `core/telemetry/TelemetryRuntimeTest.freshInstallDoesNotResolveDirectoryOrCreateWriterThread` | default prefs (no key) leave the store untouched: `getExternalFilesDir` seam never called, no executor created; fresh install has no telemetry directory |
| AC-1 | instrumented | P1 | T05 `ui/feature/device/settings/DeviceSettingsContentFocusTest.developerSectionRowsAreFocusableAndLeftReturnsToSectionList` | Developer section listed, `session-log` and `share-log` rows take focus (also disabled Share), DPAD_LEFT returns to the section list, seven sections do not scroll |
| AC-1 | device | P0 | D2 | Developer section on `deploy` and minified `release`; overlay row absent there, present in `debug` |

#### AC-1a Share log

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1a | static/unit | P0 | T07 `core/telemetry/UpdateFilePathsXmlTest.pathsCoverTelemetryDirOnlyAndKeepUpdates` (parses `app/src/main/res/xml/update_file_paths.xml`) | has `external-files-path path="telemetry/"`, still has the `Download/updates/` entry unchanged, no `root-path`, no `external-path` with `.` or empty path, exactly the two entries |
| AC-1a | unit | P0 | T08 `core/telemetry/SessionLogControllerTest.shareRotatesAndZipsOnlyClosedFiles` | share rotates the open file first, zip contains every closed `*.jsonl` and no `.tmp`/old zip; archive name `puber-telemetry-<sid>-<yyyyMMdd-HHmm>.zip` |
| AC-1a | unit | P1 | T09 same class `shareReplacesPreviousArchive` | exactly one `*.zip` remains (stays in the 20 MiB bound) |
| AC-1a | unit | P0 | T10 same class `shareWithNoClosedFilesReturnsNothingToShare` | returns `NothingToShare`, no zip created, no throw |
| AC-1a | unit | P1 | T11 same class `shareIoFailureReturnsFailedAndLeavesNoPartialZip` | `.tmp` then rename: failure leaves no `.zip`, result `Failed`, no exception |
| AC-1a | unit | P0 | T13 `DeviceSettingsVMSessionLogTest.shareRowDisabledWhenNoFiles` | `shareEnabled == false` when no files; `ShareSessionLog` then shows the "nothing to share" message and never calls the file sharer |
| AC-1a | unit | P1 | T12 same class `shareResultsMapToMessagesAndSharingFlag` | `Shared` -> no error message; `NothingToShare`/`Failed` -> snackbar through `FakeResourceProvider` strings; `sharing` true while in flight, false after |
| AC-1a | device | P0 | D4 | chooser opens with the zip (or the system says no app can handle it); no crash; update-install FileProvider path still resolves |

#### AC-2 Off is free

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-2 | unit | P0 | T15 `core/telemetry/TelemetrySinkTest.disabledRecordNeverReachesEncoderOrStore` | flag false: `record(any event)` leaves encoder, store, clock and executor with zero calls (spy encoder, fake store) |
| AC-2 | unit | P0 | T16 `core/ui/PuberVMTelemetryTest.stateAndActionProbesAreSilentWhenOff` | with a counting fake sink installed and off, `updateViewState` and `onAction` produce zero `record` calls and VM behaviour (state, snackbar for unhandled action) is the same as before |
| AC-2 / AC-10 | unit | P0 | T17 `core/telemetry/TelemetryRuntimeTest.offStartPlantsNoTreeAndNoHandler` | `Timber.forest()` empty (or unchanged), default uncaught handler identical to the saved one after `install` with flag off |
| AC-2 | unit | P2 | T18 `core/telemetry/TelemetryAllocationTest.disabledProbeAllocatesNothing` | `ThreadMXBean.getThreadAllocatedBytes` delta over 100k off-path `record` calls under a small bound; tagged flaky-tolerant, skip on JVMs without the bean |
| AC-2 | device | P0 | D1 | empty/missing `telemetry/` directory after a full session with toggle off |

#### AC-3 Files and schema

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-3 | unit | P0 | T19 `core/telemetry/TelemetryEncoderTest.everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` | for `session`, `startup`, `nav`, `ready`, `act`, `err`, `meta`: key set equals `{t,sid,type,scr}` plus the architect schema fields, no more. Parameterised over `TelemetryEventType.entries` so a new type without a schema row fails |
| AC-3 | unit | P1 | T20 same class `startupOmitsMarksNotReached` | `first_content` absent (not null/0) when not reached |
| AC-3 | unit | P0 | T21 `core/telemetry/TelemetrySinkTest.nullDirectoryKeepsRecordingOffAndStatusUnavailable` | store returns null dir: no file, no throw, `SessionLogState.Unavailable`, indicator is not "recording" (T02 string absent) |
| AC-3 | unit | P1 | T22 same class `ioErrorTurnsRecordingOffWithMetaUnavailableAndNeverThrows` | store append throws: sink goes off, status `Unavailable`, producers unaffected |
| AC-3 | unit | P1 | T23 `core/telemetry/FileTelemetryStoreTest.fileNamingAndAppendOnTempDir` (`@TempDir`) | name `s-<yyyyMMdd-HHmmss>-<sid>-<nn>.jsonl`, lines are `\n`-terminated valid JSON |
| AC-3 | device | P0 | D3 | files under `getExternalFilesDir("telemetry")`, pulled by `adb pull`, each line has the right keys |

#### AC-4 Seven-day auto-off

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-4 | unit | P0 | T25 `TelemetryRuntimeTest.autoOffAtStartAfterSevenDays` | prefs `enabled_at_wall_ms = now - 7d` (injected clock): install leaves flag false, pref cleared; `now - 7d + 1ms` stays on (boundary) |
| AC-4 | unit | P0 | T26 same class `autoOffOnNextDrainWhenExpiredWhileRunning` | clock advanced past 7 d, drain runs: recording stops and no further line is written |
| AC-4 | unit | P1 | T27 same class `clockGoingBackwardsClampsToNow` | `enabledAt > now` does not yield a negative or >7 d remaining |
| AC-4 | unit | P0 | T28 same class `autoOffEmitsMetaClosesFileClearsPrefUprootsAndStatusOff` | `meta ev=auto_off` written, file closed, tree uprooted, handler restored, `SessionLogState.Off` so the Settings toggle shows off (T01 path) |

#### AC-5 Caps and bounds

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-5 | unit | P0 | T29 `core/telemetry/TelemetryCapsTest.excessAboveCapDropsAndMetaReportsCountsPerType` | cap+N events of `nav` in one wall minute: exactly cap lines, one `meta ev=dropped counts={nav:N}`; `meta` itself uncapped |
| AC-5 | unit | P1 | T30 same class `capWindowResetsEachWallMinute` | next minute accepts again |
| AC-5 | unit | P1 | T31 same class `queueOverflowDropsAreCountedAndQueueStaysAtFourThousandNinetySix` | 4097th offer is dropped, counted per ordinal, reported in `meta` on next drain |
| AC-5 | unit | P0 | T32 `core/telemetry/TelemetryStoreRotationTest.rotatesAtTwoMiB` | fake store: file closed at 2 MiB, next opened, `meta ev=rotate` |
| AC-5 | unit | P0 | T33 same class `oldestDeletedFirstAtTwentyMiBAndNeverTheOpenFile` | name-sorted oldest `*.jsonl` removed when a file opens over 20 MiB; open file survives |
| AC-5 | unit | P1 | T34 same class `archiveCountsTowardTheBudget` | an existing zip's size is included in the 20 MiB sum |

#### AC-6 No main-thread work

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-6 | unit | P0 | T35 `TelemetrySinkTest.encodingAndFileIoRunOnlyOnWriterThread` (real thread) | encoder spy, store fake and name resolver record `Thread.currentThread().name == "puber-telemetry"`; never the calling thread, never `main` |
| AC-6 | unit | P0 | T36 same class `recordReturnsWhileStoreIsBlocked` (real thread, latch) | store blocked in `append`: `record` still returns immediately; the event enqueued is immutable (a `Class`/primitive/enum/`Throwable`, no String built) |

#### AC-7 No payloads

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-7 | unit | P0 | T38 `core/telemetry/TelemetryPrivacyTest.noSensitiveValueAppearsInAnyEncodedLine` | events built from a known title, search text, URL with token and `access_token=...` query, and a JSON excerpt; every encoded line searched for each literal: none found |
| AC-7 | unit | P0 | T39 `core/telemetry/ErrorScrubberTest` cases `replacesEveryUrl`, `cutsAtJsonInput`, `collapsesWhitespace`, `capsAt160`, `keepsSanitizedQueryRedaction` | `msg` rules from architect schema |
| AC-7 | unit | P0 | T40 `core/telemetry/TelemetryErrorPipelineTest.errLineUsesSourceTypeSanitizedMessageAndAppFramesOnly` | `SanitizedLogThrowable.sourceType` (F12) is the `et` field; message goes through `sanitizeText` then scrubber; max 5 frames, none from `java.`/`kotlin.`/`android.`/`okhttp3.`/`io.ktor.`/`timber.`, no frame text beyond `Class.method:line` |
| AC-7 | unit | P0 | T41 `core/telemetry/TelemetryNamesTest.parameterizedScreenNameNeverContainsKeyOrIds` | a `PuberScreen` whose `key` embeds an id logs only the class name; `nav` and `act` carry no payload fields (no id in S1) |
| AC-7 | device | P0 | D3 | `tools/mobile-evidence-audit.sh <evidence-dir> com.kino.puber` clean; grep for the titles/queries typed during the session finds nothing |

#### AC-8 Stable names

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-8 | unit | P0 | T43 `core/telemetry/TelemetryNamesTest.suffixRuleTabUnwrapAndCache` | `HomeScreen`/`HomeVM` -> `Home`; only one trailing `Screen`/`VM` stripped; nested sealed member -> bare `simpleName` (`Content`); `PuberTab` -> wrapped screen via `contentScreenClass`; second call hits the cache |
| AC-8 | unit | P1 | T44 `core/ui/navigation/PuberScreenKeyTest.keyIsStillSimpleNameForNonParameterizedScreens` | `PuberScreen.key` value unchanged (`javaClass.simpleName`) |
| AC-8 | static | P1 | T45 `core/telemetry/ProguardRulesTest.keepNamesRulesPresentAndLogStripRuleIsNarrowed` (reads `app/proguard-rules.pro`) | the three `-keepnames` rules and the single-overload `assumenosideeffects` signature exist; the broad `LoggerKt.log(...)` rule is gone. Text only: a fast tripwire, not proof |
| AC-8 | device | P0 | D5 | names readable in the minified `release` log; this is the only real proof |

#### AC-9 Cold start, nav, ready, act

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-9 | unit | P0 | T47 `core/telemetry/StartupMarksTest.oneLineWithAscendingMarksOnFirstContent` | marks `proc_start`(0) <= `app_create` <= `app_composed` <= `first_content` (fake clock); exactly one `startup` line |
| AC-9 | unit | P1 | T48 same class `fallbackLineAfterSixtySecondsWithMarksSoFar` | 60 s without `first_content`: one line, `first_content` absent; later `first_content` does not emit a second line |
| AC-9 | unit | P0 | T49 `core/ui/PuberVMTelemetryTest.readyOnlyOnStateClassChangeWithMsSinceOpen` | with sink on: Loading -> Content records `ready(st=Content, ms)`, `ms` from the fake clock since VM construction; Content -> Content (new instance, same class) records nothing; data-class VM state records no second `ready` (F11 documented) |
| AC-9 | unit | P1 | T50 same class `startupContentClassTransitionTriggersFirstContent` | a VM overriding `telemetryStartupContentClass` fires the mark once on the first matching transition; `HomeVM` declares `HomeViewState.Content` (existing HomeVM test setup, add one case) |
| AC-9 | unit | P0 | T51 `core/telemetry/ActFolderTest.identicalActionsWithinOneSecondFoldIntoOneLineWithCount` | N identical (vm, action) within 1000 ms -> one line `n=N`; different action or gap >= 1000 ms starts a new line; boundary 999 vs 1000 ms |
| AC-9 | unit | P0 | T52 `core/ui/PuberVMTelemetryTest.actRecordedForHandledAndUnhandledActions` | `onAction` is final and routes to `handleAction`: a VM that handles the action and one that falls to `super.handleAction` both produce one `act`; base snackbar behaviour intact; this is the F7 regression |
| AC-9 | unit | P1 | T53 `core/ui/navigation/NavTelemetryTest.commandsMapToCmdWithFromAndTo` | `push`, `replace`, `back`, `new_root`, `tab`, ... give the right `cmd` and class names. If `FlowCommandRunner` cannot be built in a unit test without a Voyager navigator, extract the cmd mapping into a small pure function and test that; else rely on D6 |
| AC-9 | device | P0 | D6 | real cold start: `startup` line, `nav`, `ready`, `act` for Home, History, Settings, Back |

#### AC-10 Errors

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-10 | unit | P0 | T55 `core/telemetry/TelemetryErrorTreeTest.onlyWarnAndAboveWithThrowableIsRecorded` | DEBUG/INFO and null-throwable calls produce nothing; WARN/ERROR with throwable produces `err src=log` |
| AC-10 | unit | P0 | T56 `core/telemetry/TelemetryUncaughtHandlerTest.previousHandlerCalledOnceWithSameThreadAndThrowable` | chain preserved; `src=uncaught` recorded |
| AC-10 | unit | P0 | T57 same class `previousHandlerStillCalledWhenSinkThrows` | `finally` delegation |
| AC-10 | unit | P1 | T58 same class `flushIsBoundedAndSkippedWhenCrashIsOnWriterThread` | blocked store: handler returns within the bound (fake clock/latch timeout); no self-deadlock |
| AC-10 | unit | P1 | T59 same class `disableRestoresPreviousHandlerOnlyIfStillDefault` | someone else installed after ours: ours stays but is inert, no double chain |
| AC-10 | unit | P0 | T60 `core/telemetry/ErrFolderAndCapTest.identicalErrorsFoldAndTwentyPerMinuteCap` | identical (type, first frame) within 60 s fold into `n`; 21st distinct error in a minute dropped and counted in `meta` |
| AC-10 | unit | P0 | T61 `core/ui/PuberVMTelemetryTest.coroutineFailureInLaunchReachesErrThroughLoggerWithThrowable` | with the Timber tree planted and sink on, a failing `launch` in a VM yields an `err` line (the `DefaultExceptionHandler` -> `t.log(t)` path) |
| AC-10 | unit | P0 | T62 `data/api/KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` | `err src=auth` recorded with sink on; the same `ApiError.Unauthorized` still thrown; nothing recorded when off. Extend the existing `data/api` test setup |
| AC-10 | device | P0 | D7 | minified `release`: network failure yields `err src=log`; `am crash` yields `err src=uncaught` and the process dies as it did before |

#### AC-10a Runtime toggle and persistence

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-10a | unit | P0 | T65 `TelemetryRuntimeTest.enablingAtRuntimePlantsTreeAndHandlerWithoutRestart` | `controller.setEnabled(true)`: `Timber.forest()` gains `TelemetryErrorTree`, default handler is ours, flag true, `session` + `meta start` recorded |
| AC-10a | unit | P0 | T66 same class `disablingUprootsTreeAndRestoresHandler` | forest and handler as before; flag false |
| AC-10a | unit | P0 | T67 same class `enabledStateSurvivesNewRuntimeInstance` | second `TelemetryRuntime` on the same fake prefs (process-death simulation) is on with the same `enabledAt`; remaining time continues, not reset |
| AC-10a | device | P0 | D8 | `am force-stop` then launch: toggle still on, indicator shows time left |

#### Static and regression

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-9 / R10 | static | P1 | S1 `grep -rn "override fun onAction" app/src/main` returns nothing under `ui/`; `PuberVM.onAction` is `final` | all 19 renamed to `handleAction`; compiler also enforces |
| regression | unit | P0 | S4 the four existing `DeviceSettingsVM{AutoTrailer,Language,MirrorNavigation,MenuSections}Test` plus full `testDevDebugUnitTest` pass with a relaxed `SessionLogController` mock | rename and constructor change do not break existing behaviour |
| regression | static | P1 | S2 `:app:detektAll :app:lintDevDebug` clean | no new findings (new package, `internal` visibility) |
| docs | static | P2 | S3 `docs/architecture/viewmodel.md` lines 18, 22, 45 say `handleAction` | docs match the convention |

### Bugs

None: feature work. There is no failing-first test. The nearest thing is the F7 regression: on the base commit a probe
in `PuberVM.onAction` would see only unhandled actions. T52 encodes the target behaviour; it cannot run on base
(no sink exists), so it is written first only in the sense that the template-method refactor lands with it.

### Manual / device scenario

Conductor runs the lease and install through `qa-device` (`tools/emulator-resource-lock.sh`, `adb -s <serial>`,
the worktree's own APK installed with `tools/android-apk-install-preserve`, never uninstall or clear). Evidence
goes under `docs/superpowers/` and is scanned with `tools/mobile-evidence-audit.sh` before it is shared. Build
types: `devDebug` for D1, D3, D6 to D8 first; then one minified `release` (and `deploy` for D2) for D2, D5, D7.
Release is built through the worktree (`./tools/agentw`); a worktree build has no credentials, so the app is
reached with preserved data only.

D1. Toggle off is empty. Steps: fresh state (or toggle off after clearing nothing), cold start, Home, History,
Settings, Back, 2 minutes of D-pad use. `adb -s <serial> shell ls /sdcard/Android/data/com.kino.puber.dev/files/`.
Expected: no `telemetry` directory, or an empty one if it pre-existed. Must not happen: any `*.jsonl`; a
Timber tree planted outside debug (nothing from `log` in `release` logcat).

D2. Developer section in prod. Steps: on `deploy` and on minified `release`, open Settings, DPAD_DOWN to the
last section row "For developers"/"Для разработчиков", DPAD_RIGHT. Expected: panel shows "Session log" (off,
off-description), "Share log" (disabled look, "Nothing to share yet", focusable), and no "Show stream info"
(debug overlay) row; section list shows seven rows without scrolling. Repeat on `devDebug`: overlay row is last.
Must not happen: overlay row in `deploy`/`release`; focus lost; Share skipped by D-pad; DPAD_LEFT not returning
to the section list.

D3. Recording and files (AC-3, AC-7). Steps: toggle on (DPAD_CENTER). Expected: description becomes "Recording
· N d H h left" with about 6 d 23 h. Open Home, History, search for a distinctive text (for example
"ZQXPROBE"), open Settings, Back. `adb -s <serial> pull /sdcard/Android/data/<pkg>/files/telemetry/ <out>`.
Check with `jq -c 'keys'` that every line carries `t,sid,type,scr` plus the type's schema keys only; `grep -ri
ZQXPROBE` and any visible title shows no hit; `tools/mobile-evidence-audit.sh <out> <pkg>` clean. Record the
bytes per line and lines per minute for the budget (risk R19). Must not happen: a title, URL, token or
search text; a field outside the schema.

D4. Share log (AC-1a). Steps: with files present, DPAD to "Share log", DPAD_CENTER. Expected: the "Preparing
archive" state, then the Android chooser with `puber-telemetry-<sid>-<date>.zip`, or the system message that no
app can handle it; no crash. Back out: focus returns to the Share row. `adb shell run-as` is not available for
dev builds, so check the `telemetry/` directory for exactly one `*.zip`. Empty-file case: delete files via
toggle off and a fresh install state is not possible, so cover "nothing to share" with T10/T13 and with D2's
disabled Share on a never-recorded device. Check the update flow path is unchanged: `update_file_paths.xml`
retains its entry (T07), and one "Check for updates" run does not fail on URI resolution when an update is
available (if no update is available, record "not exercisable"). Must not happen: a crash on a TV with no
share target; the zip containing the open `.jsonl`.

D5. Names in minified `release` (AC-8). Steps: build `release` (minified), install preserve, toggle on, repeat
D3's navigation. Expected: `nav.to`, `ready.st`, `act.a` read like `Home`, `Content`, `ItemFocused`, none like
`a0`, `b1`, `a.b.c`; `scr` joins `nav` and `act` lines. `nonMinifiedRelease` does not count. Must not happen:
single-letter names; `PuberScreen.key` changed (check a Details screen still restores focus/state after Back).

D6. Cold-start story (AC-9). Steps: toggle on, `am force-stop`, launch, wait for Home content, open History, Back,
Settings. Expected: one `startup` line with ascending marks and `first_content` present; `nav` lines for each
move; `ready` once per state-class change with `ms`; rapid DPAD movement in a list produces `act` lines with
`n > 1` folded per second rather than one per key. Must not happen: more than one `startup`; `ready` repeating
for same-class updates.

D7. Errors in minified `release` (AC-10). Steps: toggle on, disable network (`adb -s <serial> shell svc wifi
disable` / `svc data disable`, or the emulator's network off), open Home or Search, restore network. Expected:
at least one `err src=log` with `et`, sanitized `msg` (no URL), at most 5 app frames. Then `adb -s <serial> shell
am crash <pkg>` with logging on: an `err src=uncaught` line is written (it needs the 1.5 s flush) and the app dies
exactly as it does with logging off (compare with a `am crash` run with logging off: same dialog / restart
behaviour). Token-refresh failure is not forced on a device (needs an expired token); it is unit-only (T62).
Must not happen: a swallowed crash, a hang above about 2 s, `err` empty in `release` (D7b regression).

D8. Persistence and auto-off display (AC-10a, AC-4). Steps: toggle on, `am force-stop`, launch, open Settings >
Developer. Expected: toggle still on, indicator still shows time left (no reset to 7 d). Then toggle off:
indicator disappears, log writing stops (the file does not grow after 10 s). Toggle on again without restart: a
`session` + `meta start` line appears. The 7-day expiry is not exercised on a device (wall-clock change would
need `date` rights and affects other agents); T25-T28 cover it.

D9. Perf re-measure (see Perf track).

### Perf track

Metric 1, cold start: `adb -s <serial> shell am start -W -n <pkg>/<MainActivity>` `TotalTime` and
`WaitTime`, after `am force-stop`, N = 10 runs, drop the first (cold disk cache), median and range of the
rest. Metric 2, Home time-to-content: the `startup.first_content` field with logging on (informational only, it
is the first real number). Metric 3, scroll jank: `dumpsys gfxinfo <pkg> reset` before and `framestats`/
"Janky frames" percentage after a scripted 20 DPAD_DOWN/RIGHT sequence on Home (same device, same sequence).
Metric 4, off-path cost: T18 (P2) and the volatile read in `updateViewState`/`onAction` is not separately
measured on device.

Baseline: `{run}/baseline.md` does not exist yet. Before implementation, on the base commit `39903d31`,
capture metrics 1 and 3 on the same leased emulator, same build type, same API image, same animation scale,
with the same preserved login state (one `devDebug` set; one minified `release` set if D5/D7 build one, so the
shipped path is also covered). Record serial, image, build type, date and the raw numbers.

Pass thresholds, same device and build type:
- Logging off: median cold-start `TotalTime` within noise: delta <= max(5% of baseline median, 2 x the baseline
  run-to-run standard deviation). Janky-frame percentage not worse by more than 1 percentage point.
- Logging on (informational, not a gate): cold-start delta reported; jank delta reported; no more than 2
  percentage points worse; `session` and writer-thread creation must not appear in main-thread traces during
  startup (spot check with a `Trace`/`StrictMode` run on `devDebug`).
- If the off-path delta exceeds the threshold, the finding is a defect and goes back to implementation; repeat
  twice before concluding it is more than noise.

### UNTESTABLE / limited

- AC-4 seven days on a real device: unit with injected clock only (T25-T28); the device does not change wall clock.
- AC-1a on a TV image with no share target: only "chooser opens or system says no handler" is observable
  (owner-accepted); the URI grant breadth is proven by T07 (static) rather than by device.
- AC-10 token-refresh failure on a device: unit only (T62).
- AC-8 R8 behaviour in unit tests: not possible; T45 is a tripwire, D5 is the proof.
- AC-6 "no main-thread IO" on a device: unit proves threading of the sink (T35/T36); device main-thread
  timing is covered only indirectly by the jank metric.
- `ready` for the four data-class view states: by owner decision not produced in S1 (T49 documents the negative).

Nothing is silently skipped.

### Baseline (qa-device, mode baseline)

Device: emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU, logged in). Package com.kino.puber.stage.
Build: worktree telemetry-core own devDebug APK, versionName 1.10.0-dev, sha256 9de4ee4a...f9f4,
installed via install-preserve (outcome installed, compatible_replace, no destructive action).
Lease taken and released.

#### Cold start (am start -W, force-stop + 2 s, 7 runs, 6 s gap)
TotalTime ms: 2799, 2856, 2658, 3044, 2684, 3038, 2804
Sorted: 2658 2684 2799 2804 2856 3038 3044 ; median 2804 ms ; min 2658 ; max 3044 (spread ~14%).
WaitTime: 2834 2869 2668 3071 2705 3062 2823.
No reportFullyDrawn in the code at base, so this is time to first activity frame (Home content
arrives later over the network); not time-to-Home-content. Debug build (debuggable), so absolute numbers are
high; compare only against devDebug on the same emulator.
AC-9 time-to-content must come from the new `ready` event, not from this number.

#### APK size
- devDebug: 36,236,318 bytes (app-dev-debug.apk)
- minified: assembleDevRelease FAILS in a worktree (SigningConfig "release" missing storePassword; no
  keystore.properties). Substitute: assembleDevDeploy (initWith release, R8 + resource shrinking, debug signing):
  25,155,146 bytes (app-dev-deploy.apk). Prod flavor not built. Deploy APK was not installed.

#### Frame stats (dumpsys gfxinfo reset; Home: 6 Down, 8 Right, 8 Left, 6 Up with 0.3-0.4 s gaps; 3 runs)
Total frames / janky: 161/161 (100%), 148/146 (98.65%), 154/154 (100%).
Frame time p50 150 ms, p90 250-300, p95 300, p99 350-800 ms.
Not usable as a discriminating metric: the emulator renders in software (every frame janky, gpu percentile
4950 ms). Use only as a gross regression check on the same emulator; the series' own `frames` event (S3) is the
real instrument.

#### Method for the later comparison
Same emulator, devDebug, install-preserve, 7 force-stop cold starts with identical gaps, compare median TotalTime
with the log OFF (expect within the 2658-3044 spread) and ON; compare devDeploy APK size.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Design stand for the Settings change (no docs/design stand covers Settings; Developer section becomes visible in prod)? | yes | answered | No stand: reuse existing toggle/row styles, ux-designer reviews visually; indicator wording judged by the owner at release. |
| Q2 | Stable names under R8 (AC-8)? | no | answered | -keepnames for PuberScreen, UIAction, ViewState implementers plus a telemetry name accessor falling back to simpleName; PuberScreen.key unchanged. |
| Q3 | What does Share log include? | no | answered | Rotate current file, zip only closed files, replace previous archive, archive kept inside the telemetry directory. |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-15 | AC-8 minified check runs on the `deploy` variant (R8 + resource shrinking, debug-signed): `assembleDevRelease` cannot sign in a worktree. SUPERSEDED below | owner (standing instruction: recommended) |
| 2026-10-15 | Minified device checks (AC-8, AC-10 release path) use `devBenchmarkRelease` (minified, shrunk, debug-signed, app/build.gradle.kts:212-215); `deploy` is unminified (:202) and is used only for the Developer-section-in-non-debug check. Baseline's 25.2 MB deploy APK is not an R8 build | owner (standing instruction: recommended; architect plan Q1) |
| 2026-10-15 | Gate 1: spec approved as assembled (M, ui+perf, AC-1, AC-1a, AC-2..AC-10, AC-10a) | owner (standing instruction: recommended) |
| 2026-10-15 | Q1-Q3 take the analyst's recommendations; Q1 overrides the escalate_also design-stand route because only existing row styles are reused | owner (standing instruction: recommended) |
