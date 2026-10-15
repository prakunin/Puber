# Test design: telemetry-core (S1)

Inputs: `brief.md`, `design-architect.md` (D1-D16, F7-F13), `design-ux-designer.md`, `.flow/roles/test-designer.md`.
Base `39903d31`. Kind is feature, so there is no failing-first bug test (see "Bugs"). Architect questions are
taken as resolved on recommendation (sanitizer runs on release error paths; no `ready` for data-class states;
AC-1a met when the chooser opens or the system reports no handler, grant limited to `telemetry/`; the
architect's field schema is the AC-3 catalogue; toggle reachable only after device settings load).

## Levels and style

- `unit`: JUnit 5 under `app/src/test/kotlin/com/kino/puber/...`, `@RegisterExtension MainDispatcherExtension`
  (`util/MainDispatcherExtension.kt`), `FakeResourceProvider` (`util/FakeResourceProvider.kt`) for strings,
  mockk for collaborators only (as `DeviceSettingsVMAutoTrailerTest` does), never for the class under test.
- `instrumented`: only `make DEVICE=<serial> itest TESTS=<fqcn>`; extends the existing
  `app/src/androidTest/.../device/settings/DeviceSettingsContentFocusTest.kt`.
- `device`: manual on a leased emulator through `qa-device`, `adb -s <serial>`, D-pad via `input keyevent`.
- `static`: grep / XML / text checks and detekt+lint (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`).

### Test seams the implementation must offer (so the tests below are possible)

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

## Risk profile

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

## Traceability

Priority: P0 must exist before review, P1 should, P2 nice to have. Test names are the Kotlin test method names;
classes are under `app/src/test/kotlin/com/kino/puber/` unless marked instrumented/device/static.

### AC-1 Developer section, toggle off by default, indicator

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | T01 `ui/feature/device/settings/vm/DeviceSettingsVMSessionLogTest.offStateShowsToggleOffAndOffDescription` | with controller state `Off`, `Success.sessionLog.enabled == false`, description is the off string (via `FakeResourceProvider`) |
| AC-1 | unit | P0 | T02 same class `recordingStateShowsIndicatorWithDaysAndHoursLeft` | `Recording(remainingMs)` maps to "recording, N d H h left" text with correct floor/rounding (6 d 23 h, under 1 h, exactly 7 d) |
| AC-1 | unit | P0 | T03 same class `toggleActionCallsSetEnabledAndRefreshesState` | `ToggleSessionLog` calls `setEnabled(!current)` and the new state shows without reopening |
| AC-1 / AC-2 | unit | P0 | T04 `core/telemetry/TelemetryRuntimeTest.freshInstallDoesNotResolveDirectoryOrCreateWriterThread` | default prefs (no key) leave the store untouched: `getExternalFilesDir` seam never called, no executor created; fresh install has no telemetry directory |
| AC-1 | instrumented | P1 | T05 `ui/feature/device/settings/DeviceSettingsContentFocusTest.developerSectionRowsAreFocusableAndLeftReturnsToSectionList` | Developer section listed, `session-log` and `share-log` rows take focus (also disabled Share), DPAD_LEFT returns to the section list, seven sections do not scroll |
| AC-1 | device | P0 | D2 | Developer section on `deploy` and minified `release`; overlay row absent there, present in `debug` |

### AC-1a Share log

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

### AC-2 Off is free

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-2 | unit | P0 | T15 `core/telemetry/TelemetrySinkTest.disabledRecordNeverReachesEncoderOrStore` | flag false: `record(any event)` leaves encoder, store, clock and executor with zero calls (spy encoder, fake store) |
| AC-2 | unit | P0 | T16 `core/ui/PuberVMTelemetryTest.stateAndActionProbesAreSilentWhenOff` | with a counting fake sink installed and off, `updateViewState` and `onAction` produce zero `record` calls and VM behaviour (state, snackbar for unhandled action) is the same as before |
| AC-2 / AC-10 | unit | P0 | T17 `core/telemetry/TelemetryRuntimeTest.offStartPlantsNoTreeAndNoHandler` | `Timber.forest()` empty (or unchanged), default uncaught handler identical to the saved one after `install` with flag off |
| AC-2 | unit | P2 | T18 `core/telemetry/TelemetryAllocationTest.disabledProbeAllocatesNothing` | `ThreadMXBean.getThreadAllocatedBytes` delta over 100k off-path `record` calls under a small bound; tagged flaky-tolerant, skip on JVMs without the bean |
| AC-2 | device | P0 | D1 | empty/missing `telemetry/` directory after a full session with toggle off |

### AC-3 Files and schema

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-3 | unit | P0 | T19 `core/telemetry/TelemetryEncoderTest.everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` | for `session`, `startup`, `nav`, `ready`, `act`, `err`, `meta`: key set equals `{t,sid,type,scr}` plus the architect schema fields, no more. Parameterised over `TelemetryEventType.entries` so a new type without a schema row fails |
| AC-3 | unit | P1 | T20 same class `startupOmitsMarksNotReached` | `first_content` absent (not null/0) when not reached |
| AC-3 | unit | P0 | T21 `core/telemetry/TelemetrySinkTest.nullDirectoryKeepsRecordingOffAndStatusUnavailable` | store returns null dir: no file, no throw, `SessionLogState.Unavailable`, indicator is not "recording" (T02 string absent) |
| AC-3 | unit | P1 | T22 same class `ioErrorTurnsRecordingOffWithMetaUnavailableAndNeverThrows` | store append throws: sink goes off, status `Unavailable`, producers unaffected |
| AC-3 | unit | P1 | T23 `core/telemetry/FileTelemetryStoreTest.fileNamingAndAppendOnTempDir` (`@TempDir`) | name `s-<yyyyMMdd-HHmmss>-<sid>-<nn>.jsonl`, lines are `\n`-terminated valid JSON |
| AC-3 | device | P0 | D3 | files under `getExternalFilesDir("telemetry")`, pulled by `adb pull`, each line has the right keys |

### AC-4 Seven-day auto-off

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-4 | unit | P0 | T25 `TelemetryRuntimeTest.autoOffAtStartAfterSevenDays` | prefs `enabled_at_wall_ms = now - 7d` (injected clock): install leaves flag false, pref cleared; `now - 7d + 1ms` stays on (boundary) |
| AC-4 | unit | P0 | T26 same class `autoOffOnNextDrainWhenExpiredWhileRunning` | clock advanced past 7 d, drain runs: recording stops and no further line is written |
| AC-4 | unit | P1 | T27 same class `clockGoingBackwardsClampsToNow` | `enabledAt > now` does not yield a negative or >7 d remaining |
| AC-4 | unit | P0 | T28 same class `autoOffEmitsMetaClosesFileClearsPrefUprootsAndStatusOff` | `meta ev=auto_off` written, file closed, tree uprooted, handler restored, `SessionLogState.Off` so the Settings toggle shows off (T01 path) |

### AC-5 Caps and bounds

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-5 | unit | P0 | T29 `core/telemetry/TelemetryCapsTest.excessAboveCapDropsAndMetaReportsCountsPerType` | cap+N events of `nav` in one wall minute: exactly cap lines, one `meta ev=dropped counts={nav:N}`; `meta` itself uncapped |
| AC-5 | unit | P1 | T30 same class `capWindowResetsEachWallMinute` | next minute accepts again |
| AC-5 | unit | P1 | T31 same class `queueOverflowDropsAreCountedAndQueueStaysAtFourThousandNinetySix` | 4097th offer is dropped, counted per ordinal, reported in `meta` on next drain |
| AC-5 | unit | P0 | T32 `core/telemetry/TelemetryStoreRotationTest.rotatesAtTwoMiB` | fake store: file closed at 2 MiB, next opened, `meta ev=rotate` |
| AC-5 | unit | P0 | T33 same class `oldestDeletedFirstAtTwentyMiBAndNeverTheOpenFile` | name-sorted oldest `*.jsonl` removed when a file opens over 20 MiB; open file survives |
| AC-5 | unit | P1 | T34 same class `archiveCountsTowardTheBudget` | an existing zip's size is included in the 20 MiB sum |

### AC-6 No main-thread work

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-6 | unit | P0 | T35 `TelemetrySinkTest.encodingAndFileIoRunOnlyOnWriterThread` (real thread) | encoder spy, store fake and name resolver record `Thread.currentThread().name == "puber-telemetry"`; never the calling thread, never `main` |
| AC-6 | unit | P0 | T36 same class `recordReturnsWhileStoreIsBlocked` (real thread, latch) | store blocked in `append`: `record` still returns immediately; the event enqueued is immutable (a `Class`/primitive/enum/`Throwable`, no String built) |

### AC-7 No payloads

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-7 | unit | P0 | T38 `core/telemetry/TelemetryPrivacyTest.noSensitiveValueAppearsInAnyEncodedLine` | events built from a known title, search text, URL with token and `access_token=...` query, and a JSON excerpt; every encoded line searched for each literal: none found |
| AC-7 | unit | P0 | T39 `core/telemetry/ErrorScrubberTest` cases `replacesEveryUrl`, `cutsAtJsonInput`, `collapsesWhitespace`, `capsAt160`, `keepsSanitizedQueryRedaction` | `msg` rules from architect schema |
| AC-7 | unit | P0 | T40 `core/telemetry/TelemetryErrorPipelineTest.errLineUsesSourceTypeSanitizedMessageAndAppFramesOnly` | `SanitizedLogThrowable.sourceType` (F12) is the `et` field; message goes through `sanitizeText` then scrubber; max 5 frames, none from `java.`/`kotlin.`/`android.`/`okhttp3.`/`io.ktor.`/`timber.`, no frame text beyond `Class.method:line` |
| AC-7 | unit | P0 | T41 `core/telemetry/TelemetryNamesTest.parameterizedScreenNameNeverContainsKeyOrIds` | a `PuberScreen` whose `key` embeds an id logs only the class name; `nav` and `act` carry no payload fields (no id in S1) |
| AC-7 | device | P0 | D3 | `tools/mobile-evidence-audit.sh <evidence-dir> com.kino.puber` clean; grep for the titles/queries typed during the session finds nothing |

### AC-8 Stable names

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-8 | unit | P0 | T43 `core/telemetry/TelemetryNamesTest.suffixRuleTabUnwrapAndCache` | `HomeScreen`/`HomeVM` -> `Home`; only one trailing `Screen`/`VM` stripped; nested sealed member -> bare `simpleName` (`Content`); `PuberTab` -> wrapped screen via `contentScreenClass`; second call hits the cache |
| AC-8 | unit | P1 | T44 `core/ui/navigation/PuberScreenKeyTest.keyIsStillSimpleNameForNonParameterizedScreens` | `PuberScreen.key` value unchanged (`javaClass.simpleName`) |
| AC-8 | static | P1 | T45 `core/telemetry/ProguardRulesTest.keepNamesRulesPresentAndLogStripRuleIsNarrowed` (reads `app/proguard-rules.pro`) | the three `-keepnames` rules and the single-overload `assumenosideeffects` signature exist; the broad `LoggerKt.log(...)` rule is gone. Text only: a fast tripwire, not proof |
| AC-8 | device | P0 | D5 | names readable in the minified `release` log; this is the only real proof |

### AC-9 Cold start, nav, ready, act

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

### AC-10 Errors

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

### AC-10a Runtime toggle and persistence

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-10a | unit | P0 | T65 `TelemetryRuntimeTest.enablingAtRuntimePlantsTreeAndHandlerWithoutRestart` | `controller.setEnabled(true)`: `Timber.forest()` gains `TelemetryErrorTree`, default handler is ours, flag true, `session` + `meta start` recorded |
| AC-10a | unit | P0 | T66 same class `disablingUprootsTreeAndRestoresHandler` | forest and handler as before; flag false |
| AC-10a | unit | P0 | T67 same class `enabledStateSurvivesNewRuntimeInstance` | second `TelemetryRuntime` on the same fake prefs (process-death simulation) is on with the same `enabledAt`; remaining time continues, not reset |
| AC-10a | device | P0 | D8 | `am force-stop` then launch: toggle still on, indicator shows time left |

### Static and regression

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-9 / R10 | static | P1 | S1 `grep -rn "override fun onAction" app/src/main` returns nothing under `ui/`; `PuberVM.onAction` is `final` | all 19 renamed to `handleAction`; compiler also enforces |
| regression | unit | P0 | S4 the four existing `DeviceSettingsVM{AutoTrailer,Language,MirrorNavigation,MenuSections}Test` plus full `testDevDebugUnitTest` pass with a relaxed `SessionLogController` mock | rename and constructor change do not break existing behaviour |
| regression | static | P1 | S2 `:app:detektAll :app:lintDevDebug` clean | no new findings (new package, `internal` visibility) |
| docs | static | P2 | S3 `docs/architecture/viewmodel.md` lines 18, 22, 45 say `handleAction` | docs match the convention |

## Bugs

None: feature work. There is no failing-first test. The nearest thing is the F7 regression: on the base commit a probe
in `PuberVM.onAction` would see only unhandled actions. T52 encodes the target behaviour; it cannot run on base
(no sink exists), so it is written first only in the sense that the template-method refactor lands with it.

## Manual / device scenario

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

## Perf track

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

## UNTESTABLE / limited

- AC-4 seven days on a real device: unit with injected clock only (T25-T28); the device does not change wall clock.
- AC-1a on a TV image with no share target: only "chooser opens or system says no handler" is observable
  (owner-accepted); the URI grant breadth is proven by T07 (static) rather than by device.
- AC-10 token-refresh failure on a device: unit only (T62).
- AC-8 R8 behaviour in unit tests: not possible; T45 is a tripwire, D5 is the proof.
- AC-6 "no main-thread IO" on a device: unit proves threading of the sink (T35/T36); device main-thread
  timing is covered only indirectly by the jank metric.
- `ready` for the four data-class view states: by owner decision not produced in S1 (T49 documents the negative).

Nothing is silently skipped.
