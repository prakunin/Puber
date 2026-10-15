# Design (architect): telemetry-core

Read against worktree `/home/user1/orca/workspaces/Puber/telemetry-core` at `39903d31`. Paths are relative to
`app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/` or `config/`. Every `path:line`
below was opened and checked at base. The visual delta for Settings is the ux-designer's
(`design-ux-designer.md`). This design follows it and sets no sizes.

## Findings that change the brief's plan (verified at base)

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

## Decisions

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

## Sink API (the extension point for S2-S5)

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

### Line schema (S1)

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

## Changes by layer

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

## Alternatives rejected

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

## Risks (highest first)

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

## Open questions

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

## Checks for the plan

- fast: `./tools/agentw :app:compileDevDebugKotlin`
- full: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`

Both run in the worktree, never `./gradlew` there.
