# Telemetry core: opt-in session log sink, Settings toggle and first events - plan

**Spec:** `docs/superpowers/specs/2026-10-15-telemetry-core.md`
**Goal:** Add an opt-in, bounded, on-device JSONL session log (`core/telemetry`) with a Settings > Developer
toggle, recording indicator, auto-off after 7 days and zip share, and record `session`, `startup`, `nav`,
`ready`, `act` and `err` events without any main-thread IO and at zero cost when off.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/telemetry-core`. Never `./gradlew` there; every build goes
  through `./tools/agentw`. Prefix with `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate:
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests per task: `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn or package.*>'`.
- One task = green fast gate + green tests of the touched code + one commit. Commit message: one imperative
  English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing
  else. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/`,
  `config/` or `gradle/`. Every `path:line` below was opened at `29b795a9` (base `39903d31` + spec).
- Unit tests: JUnit 5 under `app/src/test/kotlin/com/kino/puber/...`, `@RegisterExtension
  MainDispatcherExtension` (`app/src/test/kotlin/com/kino/puber/util/MainDispatcherExtension.kt`) for view
  models, `FakeResourceProvider` (`.../util/FakeResourceProvider.kt:8-16`, renders `string_<id>` and
  `string_<id>_<args joined by _>`) for strings, mockk only for collaborators. No `Thread.sleep`; real-thread
  tests use latches with timeouts.
- **Unit tests run without Android stubs** (`app/build.gradle.kts` sets no `unitTests.isReturnDefaultValues`).
  Nothing `PuberVM`, `Telemetry` or the probes touch on the off path, or at VM construction, may call an
  `android.*` method (`SystemClock`, `Log`, `Process`). Hence the JVM default clock in Task 1.
- Global state: every test that calls `Telemetry.install(...)` calls `Telemetry.resetForTests()` in
  `@AfterEach`; every test that plants a Timber tree or replaces the default uncaught handler restores both
  in `@AfterEach` (`Timber.uprootAll()` is not allowed if the test did not start from an empty forest; save
  `Timber.forest()` and uproot only what the test planted).
- Off means free (AC-2): every probe is `if (Telemetry.isEnabled) Telemetry.record(...)`; nothing is allocated
  before the check. Probes enqueue only primitives, enums, `Class` and `Throwable` references; names, JSON
  and IO happen on the writer thread `puber-telemetry`.
- Never log `PuberScreen.key`, a DI scope id, a title, a query or a URL. Class names only (spec F13).
- Hot files (`core/ui/PuberVM.kt`, `core/ui/navigation/component/FlowComponent.kt`, `ui/feature/player/vm/PlayerVM.kt`)
  are also edited by `player-overlays-port` and `upstream-sync-triage`. Keep edits at the cited lines and as
  few lines as possible; in the 19 view models change only the two lines named in Task 6.
- Minified device checks (AC-8, AC-10 on `release`; spec D2/D5/D7). Correction found while planning:
  `deploy` is **not** minified (`app/build.gradle.kts:198-206`, `isMinifyEnabled = false` at `:202`), so the
  run decision "AC-8 on the `deploy` variant" rests on a false premise. The minified, debug-signed variant is
  `benchmarkRelease` (`app/build.gradle.kts:212-215`, `initWith(release)` inherits `isMinifyEnabled = true`
  and `isShrinkResources = true` from `:183-184`, `signingConfig = debug` at `:214`, the same signer as
  `devDebug` at `:192`): build it with `./tools/agentw :app:assembleDevBenchmarkRelease`. `deploy` stays the
  variant for the "Developer section in a non-debug build" check (D2). See question 1 in the return block.
- Spec citation corrections (for the reader of the spec): `AppLocale` is `core/system/AppLocale.kt:21`
  (`object AppLocale`), prefs read `:35-38`, `getSharedPreferences` `:72-73` (the spec's `:307`/`:321-324` do
  not exist; the file has 74 lines). `SensitiveRequestLogPolicy` is `internal class` at
  `core/logger/SensitiveRequestLogPolicy.kt:40`.

### Wire schema (S1), for every task that encodes

Envelope on every line, in this order: `t` (ms since the sink first started in this process), `sid`
(8 hex chars per process), `type`, `scr`. `scr` is the event's own owner for `nav` (= `to`), `ready` and
`act` (the view model's name); on other lines the last `nav.to` the writer saw, or `""`.

| type | fields after the envelope |
|---|---|
| `session` | `build`, `ver`, `api`, `model`, `ram_class`, `low_ram`, `hz`, `wall_ms` |
| `startup` | `proc_start` (0), `app_create`, `app_composed`, `first_content` (ms from process start; a key is absent, not null, when the mark was not reached) |
| `nav` | `cmd` (`push`, `push_result`, `replace`, `new_root`, `back`, `back_to`, `finish_flow`, `tab`, `activity`), `from`, `to` |
| `ready` | `st` (state class), `ms` (since VM construction) |
| `act` | `a` (action class), `n` |
| `err` | `src` (`log`, `auth`, `uncaught`), `et`, `msg`, `frames` (array, at most 5), `n` |
| `meta` | `ev` (`start`, `stop`, `rotate`, `auto_off`, `dropped`, `unavailable`); `counts` (object `{type: n}`, only on `dropped`) |

Names (spec D5): owner names (`scr`, `from`, `to`, `vm`) are `simpleName` with one trailing `Screen` or `VM`
removed (`HomeScreen`, `HomeVM` -> `Home`); state and action names are the bare `simpleName` (`Content`,
`ItemFocused`); a `PuberTab` is replaced by the class of the screen it wraps; an empty `simpleName`
(anonymous class) falls back to `name.substringAfterLast('.')`.

---

## Tasks

### Task 1: Telemetry model, names, scrubber and encoder

**Covers:** AC-3, AC-7, AC-8 (unit part)
**Files:**
- Create `core/telemetry/TelemetryClock.kt`: `internal interface TelemetryClock { fun elapsedMs(): Long; fun wallMs(): Long }`;
  `internal object JvmTelemetryClock` (`System.nanoTime() / 1_000_000`, `System.currentTimeMillis()`; the
  default, safe in JVM tests); `internal object SystemTelemetryClock` (`SystemClock.elapsedRealtime()`,
  `System.currentTimeMillis()`; installed only by `TelemetryRuntime` in Task 5).
- Create `core/telemetry/TelemetryEvent.kt`: `internal enum class TelemetryEventType(val wire: String, val capPerMinute: Int)`
  with `SESSION("session",10)`, `STARTUP("startup",2)`, `NAV("nav",120)`, `READY("ready",240)`,
  `ACT("act",240)`, `ERR("err",20)`, `META("meta",UNCAPPED)` (`UNCAPPED = Int.MAX_VALUE`); enums `NavCmd`,
  `ErrSource`, `MetaEv` each with a `wire` string as in the schema; `internal sealed interface TelemetryEvent
  { val t: Long; val type: TelemetryEventType }` (`t` = `clock.elapsedMs()` at capture) with members
  `Session(t)`, `Startup(t, procStartMs, appCreateMs, appComposedMs: Long?, firstContentMs: Long?)`,
  `Nav(t, cmd, from: Class<*>?, to: Class<*>?)`, `Ready(t, vm: Class<*>, state: Class<*>, sinceOpenMs)`,
  `Act(t, vm: Class<*>, action: Class<*>, n: Int = 1)`, `Err(t, src, error: Throwable, n: Int = 1)`,
  `Meta(t, ev, counts: Map<TelemetryEventType, Int> = emptyMap())`. All `data class` / immutable.
- Create `core/telemetry/TelemetryNames.kt`: `internal class TelemetryNames` (writer-thread only, plain
  `HashMap` caches) with `owner(cls: Class<*>?): String` and `plain(cls: Class<*>?): String` (rules above;
  null -> `""`); top-level `internal fun telemetryClassOf(screen: Any?): Class<*>?` = `null` -> null,
  `is PuberTab` -> `screen.contentScreenClass`, else `screen.javaClass` (no strings allocated).
- Modify `core/ui/navigation/PuberTab.kt:19-22`: add inside the class
  `internal val contentScreenClass: Class<*> get() = screen.javaClass` (a getter with no backing field, so
  Parcelize ignores it; `screen` is the private constructor property at `:19`). `key` (`:37`) unchanged.
- Modify `core/logger/SensitiveRequestLogPolicy.kt:257-272`: `private class SanitizedLogThrowable` becomes
  `internal class`, with `val sourceType: String` and `val sourceMessage: String?` constructor properties
  (the message built at `:264-265` unchanged). `sanitizeThrowable` (`:116-120`) unchanged.
- Create `core/telemetry/ErrorScrubber.kt`: `internal object ErrorScrubber`:
  `scrub(message: String?): String` (null -> `""`; replace every `(?i)\b[a-z][a-z0-9+.-]*://\S+` with `<url>`;
  cut at the first `JSON input` (keep the text before it); collapse `\s+` to one space; trim; `take(160)`);
  `frames(t: Throwable): List<String>`: at most 5 of `t.stackTrace` whose `className` starts with none of
  `java.`, `javax.`, `kotlin.`, `kotlinx.`, `android.`, `androidx.`, `dalvik.`, `com.android.`, `okhttp3.`,
  `io.ktor.`, `timber.`, formatted `"${className.substringAfterLast('.')}.${methodName}:${lineNumber}"`;
  `type(t: Throwable): String` = `(t as? SanitizedLogThrowable)?.sourceType ?: t.javaClass.name`, then
  `substringAfterLast('.')`; `message(t: Throwable): String` = if `SanitizedLogThrowable` ->
  `scrub(sourceMessage)` (already sanitized), else `scrub(policy.sanitizeText(t.message.orEmpty()))` with one
  private `SensitiveRequestLogPolicy()` (`core/logger/SensitiveRequestLogPolicy.kt:106` `sanitizeText`).
- Create `core/telemetry/TelemetryEncoder.kt`: `internal class TelemetryEncoder(sid: String, sessionStartElapsedMs: Long, names: TelemetryNames, sessionInfo: () -> SessionInfo, wallMs: () -> Long)`
  with `fun encode(event: TelemetryEvent, scr: String): JsonObject` built with
  `kotlinx.serialization.json.buildJsonObject` (dependency at `app/build.gradle.kts:386`) and **one
  exhaustive `when` over `TelemetryEvent`** (no `else`). Envelope first, then the schema fields, nothing else.
  `t` on the wire = `event.t - sessionStartElapsedMs`. `startup` fields are relative to `procStartMs`; null
  marks are omitted. `err`: `et = ErrorScrubber.type`, `msg = ErrorScrubber.message`, `frames =
  ErrorScrubber.frames`. `meta.counts` uses `type.wire` keys. `internal data class SessionInfo(build: String,
  ver: String, api: Int, model: String, ramClass: Int, lowRam: Boolean, hz: Float)` lives in this file;
  `fun line(event, scr): String = encode(event, scr).toString() + "\n"`.

**Tests (write first):**
- `app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryEncoderTest.kt`: T19
  `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` (parameterised over `TelemetryEventType.entries`
  with a map type -> sample event -> expected key set; a type missing from the map fails), T20
  `startupOmitsMarksNotReached`.
- `.../core/telemetry/ErrorScrubberTest.kt`: T39 `replacesEveryUrl`, `cutsAtJsonInput`,
  `collapsesWhitespace`, `capsAt160`, `keepsSanitizedQueryRedaction` (an `access_token=x` outside a URL,
  after `sanitizeText`, stays redacted).
- `.../core/telemetry/TelemetryPrivacyTest.kt`: T38 `noSensitiveValueAppearsInAnyEncodedLine` (a title, a
  search text, a URL with a token, `access_token=...`, a JSON excerpt fed through `Err` messages and a test
  `PuberScreen` whose `key` embeds an id; every encoded line searched for every literal).
- `.../core/telemetry/TelemetryNamesTest.kt`: T43 `suffixRuleTabUnwrapAndCache` (`HomeScreen`/`HomeVM` ->
  `Home`; `ScreenScreen` -> `Screen` (one suffix only); nested sealed member -> `Content`; a `PuberTab`
  wrapping a test screen -> that screen's name; second call returns the cached instance), T41
  `parameterizedScreenNameNeverContainsKeyOrIds`.
- `.../core/ui/navigation/PuberScreenKeyTest.kt`: T44 `keyIsStillSimpleNameForNonParameterizedScreens`
  (`core/ui/navigation/PuberScreen.kt:7-10`).
- Test classes for screens/tabs are defined in the test files (a `@Parcelize`-free `PuberScreen` test double
  is fine: implement `Content()` as an empty `@Composable` and `describeContents/writeToParcel` stubs).

**Consumes:** `core/logger/SensitiveRequestLogPolicy.kt:40,106,116-120,257-272`; `core/ui/navigation/PuberTab.kt:16-37`.
**Produces:** `TelemetryClock`, `JvmTelemetryClock`, `SystemTelemetryClock`, `TelemetryEvent` + members,
`TelemetryEventType`, `NavCmd`, `ErrSource`, `MetaEv`, `TelemetryNames`, `telemetryClassOf`, `ErrorScrubber`,
`TelemetryEncoder`, `SessionInfo`, `PuberTab.contentScreenClass`, `SanitizedLogThrowable.sourceType/sourceMessage`.

- [x] Write the tests above; they fail to compile.
- [x] Add the files and the two modifications.
- [x] `./tools/agentw :app:compileDevDebugKotlin` and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*' --tests 'com.kino.puber.core.ui.navigation.PuberScreenKeyTest'`; commit
  ("Add the telemetry event model, stable names, error scrubber and JSON encoder").

**Done when:** both commands are green and `grep -n "else ->" app/src/main/java/com/kino/puber/core/telemetry/TelemetryEncoder.kt` finds no branch in the event `when`.

---

### Task 2: Telemetry store and event folders

**Covers:** AC-3, AC-5, AC-9 (folding), AC-10 (folding)
**Files:**
- Create `core/telemetry/TelemetryStore.kt`:
  ```kotlin
  internal data class StoredFile(val name: String, val sizeBytes: Long)
  internal interface TelemetryStore {
      fun isAvailable(): Boolean            // false when the external dir is null; never creates the dir
      fun list(): List<StoredFile>          // *.jsonl and *.zip, sorted by name; empty when the dir is absent
      fun append(name: String, line: String) // UTF-8; creates dir and file on demand; keeps one buffered writer
      fun flush()
      fun closeCurrent()                    // closes the buffered writer, next append reopens
      fun delete(name: String)
      fun zip(sources: List<String>, archiveName: String): File  // writes "$archiveName.tmp", then renames; throws IOException
  }
  ```
  and `internal class FileTelemetryStore(private val baseDir: () -> File?) : TelemetryStore`, whose directory
  is `File(baseDir(), "telemetry")` (`baseDir` is `{ app.getExternalFilesDir(null) }` in Task 5, so the
  FileProvider entry `external-files-path path="telemetry/"` in Task 8 covers exactly this directory and the
  directory is created only on the first `append`/`zip`, never on `isAvailable`/`list`). IO errors propagate
  as `IOException`; the caller (writer) handles them. `zip` uses `java.util.zip.ZipOutputStream`, one entry per
  source with its file name, deletes the `.tmp` on failure.
- Create `core/telemetry/EventFolder.kt`:
  ```kotlin
  internal interface EventFolder {
      fun accepts(event: TelemetryEvent): Boolean
      fun offer(event: TelemetryEvent, emit: (TelemetryEvent) -> Unit)
      fun flush(nowElapsedMs: Long, force: Boolean, emit: (TelemetryEvent) -> Unit)
  }
  ```
  `ActFolder` (window 1000 ms): holds one pending `Act`; an `Act` with the same `(vm, action)` and
  `event.t - pending.t < 1000` increments `n`; any other `Act` emits the pending one and becomes pending;
  `flush` emits the pending one when `force` or `now - pending.t >= 1000`. `ErrFolder` (window 60 000 ms):
  key = `(ErrorScrubber.type(e), ErrorScrubber.frames(e).firstOrNull())`; the first occurrence is emitted at
  once; repeats inside the window only count; when the window closes (`flush`, or a new occurrence after the
  window) and the count of repeats is > 0, one follow-up `Err` with `n = repeats` is emitted. Keep at most 32
  keys (drop the oldest) so memory is bounded.
- Test util `app/src/test/kotlin/com/kino/puber/util/FakeTelemetryStore.kt`: in-memory map name -> lines;
  records `(call, Thread.currentThread().name)` for every call; switches `available`, `throwOnAppend`,
  `blockAppend: CountDownLatch?`; `zip` writes a real file under a JUnit `@TempDir` passed in, or records
  the source list.

**Tests (write first):**
- `.../core/telemetry/FileTelemetryStoreTest.kt` (`@TempDir`): append/flush/close roundtrip, `list` sorted,
  `isAvailable()` and `list()` on a missing directory create nothing, `zip` leaves no `.tmp` and contains the
  sources; failure in `zip` leaves no `.zip` (part of T11).
- `.../core/telemetry/ActFolderTest.kt`: T51 `identicalActionsWithinOneSecondFoldIntoOneLineWithCount`
  (boundary 999 vs 1000 ms; a different action starts a new line).
- `.../core/telemetry/ErrFolderAndCapTest.kt`: the fold half of T60
  (`identicalErrorsFoldWithinSixtySeconds`); the cap half is added in Task 3.

**Consumes:** Task 1 (`TelemetryEvent`, `ErrorScrubber`).
**Produces:** `TelemetryStore`, `StoredFile`, `FileTelemetryStore`, `EventFolder`, `ActFolder`, `ErrFolder`, `FakeTelemetryStore`.

- [x] Write the tests; add the files.
- [x] Fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*'`; commit
  ("Add the bounded telemetry file store and the act and error folders").

**Done when:** both commands are green.

---

### Task 3: Writer pipeline, sink, executor and the `Telemetry` entry point

**Covers:** AC-2, AC-3, AC-5, AC-6, AC-10 (cap)
**Files:**
- Create `core/telemetry/TelemetryExecutor.kt`:
  ```kotlin
  internal interface TelemetryExecutor {
      fun execute(task: () -> Unit)
      fun schedule(delayMs: Long, task: () -> Unit)
      fun scheduleRepeating(periodMs: Long, task: () -> Unit): AutoCloseable
      fun isWriterThread(thread: Thread = Thread.currentThread()): Boolean
  }
  ```
  `ThreadTelemetryExecutor`: a `ScheduledThreadPoolExecutor(1)` created **lazily on first use** with one daemon
  thread named `puber-telemetry` (precedent `ui/feature/player/component/TimedActionDebugTrace.kt:15-17`);
  every task is wrapped in `runCatching` so a failure never kills the thread. Test util
  `app/src/test/kotlin/com/kino/puber/util/ManualTelemetryExecutor.kt`: queues tasks; `runAll()`,
  `runScheduled()`; `isWriterThread` true while it runs a task.
- Create `core/telemetry/TelemetryWriter.kt` (writer-thread state only, no locks; plain class so tests drive
  it synchronously): constructor `(clock, store, encoder factory/sid, folders: List<EventFolder> =
  listOf(ActFolder(), ErrFolder()), statusSink: (SessionLogState) -> Unit)`. Pipeline per event:
  folders -> per-type cap -> encode -> rotate/budget -> `store.append`. Rules:
  - Caps: counts per `TelemetryEventType` per wall minute (`clock.wallMs() / 60_000`); above `capPerMinute`
    the event is dropped and counted. `META` is uncapped.
  - Dropped counts (caps + queue overflow passed in by the sink) produce one `Meta(ev=dropped, counts)` at the
    end of the drain in which any drop happened.
  - Files: `s-<yyyyMMdd-HHmmss>-<sid>-<nn>.jsonl`, time = UTC wall time when the file opens, `nn` two digits
    from `01` per process. The current file is closed when `bytes + line.size > 2 MiB` (2 * 1024 * 1024) and
    the next opens with `Meta(ev=rotate)` as its first line. Before a file opens: while the sum of
    `store.list()` sizes (`*.jsonl` and `*.zip`) > 20 MiB - 2 MiB and a closed `*.jsonl` exists, delete the
    name-sorted oldest closed `*.jsonl`; never the open one.
  - `scr` tracking: the last `Nav.to` owner name.
  - Unavailable: `store.isAvailable()` false at start, or any `IOException` from the store -> stop
    recording (callback to the sink), best-effort `Meta(ev=unavailable)`, status `Unavailable`, never throw.
  - `export(): ExportResult` (`Archive(File)`, `NothingToShare`, `Failed`): flush folders (force), close the
    current file, delete every `*.zip`, zip all `*.jsonl` into `puber-telemetry-<sid>-<yyyyMMdd-HHmm>.zip`
    (UTC), `NothingToShare` when there is no `*.jsonl` (and the directory is not created), `Failed` on
    `IOException`. The next append opens a new file.
  - `hasFiles()` = any `*.jsonl` in `store.list()`.
- Create `core/telemetry/TelemetrySink.kt`: `internal interface TelemetryRecorder { val isEnabled: Boolean;
  fun record(event: TelemetryEvent) }` and `internal class TelemetrySink(clock, store, executor:
  TelemetryExecutor, sessionInfo: () -> SessionInfo, sid: String = randomSid()) : TelemetryRecorder`:
  - `@Volatile override var isEnabled = false` (private set).
  - `record(event)`: `if (!isEnabled) return`; bounded `ConcurrentLinkedQueue` with an `AtomicInteger` size,
    bound 4096; on overflow increment an `AtomicIntegerArray` by `type.ordinal` and drop. No other work.
  - `start(enabledAtWallMs: Long, onExpired: () -> Unit, onStopped: () -> Unit)`: sets `isEnabled = true`, then
    on the writer: `isAvailable` check, `Session` + `Meta(start)`, repeating drain every 5000 ms. The
    session start for `t` is fixed on the first start in the process.
  - Each drain: drain the queue through the writer, flush folders that are due, `store.flush()`, auto-off
    check (`wallNow - enabledAt >= 7 d` -> `Meta(auto_off)`, close the file, `isEnabled = false`, status
    `Off`, `onExpired()`; a backwards clock, `enabledAt > now`, is treated as `enabledAt = now`), then publish
    status.
  - `stop()`: `isEnabled = false`; on the writer: final drain, `Meta(stop)`, force-flush folders, close,
    cancel the repeating drain, status `Off(hasFiles)`.
  - `requestDrain()` (pause hook), `flushBlocking(timeoutMs: Long): Boolean` (no-op returning false on the
    writer thread; otherwise a `CountDownLatch` around a drain + flush, waits at most `timeoutMs`),
    `export(onResult: (ExportResult) -> Unit)` (runs on the writer even when recording is off),
    `refreshStatus()`.
  - `val status: StateFlow<SessionLogState>`; `Recording.remainingMs` is floored to whole hours so the flow
    does not emit every 5 s.
- Create `core/telemetry/SessionLogController.kt`:
  ```kotlin
  internal sealed interface SessionLogState {
      data class Off(val hasFiles: Boolean) : SessionLogState
      data class Recording(val remainingMs: Long, val hasFiles: Boolean) : SessionLogState
      data object Unavailable : SessionLogState
  }
  internal sealed interface ShareResult {
      data class Ready(val archive: File) : ShareResult
      data object NothingToShare : ShareResult
      data object Failed : ShareResult
  }
  internal interface SessionLogController {
      val state: StateFlow<SessionLogState>
      fun setEnabled(enabled: Boolean)
      fun refresh()
      suspend fun share(): ShareResult
  }
  ```
  (`ExportResult` from the writer maps 1:1 to `ShareResult`; one type is fine if you prefer.)
- Create `core/telemetry/Telemetry.kt`: `internal object Telemetry`:
  `val isEnabled: Boolean get() = recorder.isEnabled` (`@Volatile private var recorder: TelemetryRecorder =
  DisabledRecorder`), `fun record(event)` (`if (recorder.isEnabled) recorder.record(event)`),
  `fun nowMs(): Long = clock.elapsedMs()` (`@Volatile internal var clock: TelemetryClock = JvmTelemetryClock`),
  `val controller: SessionLogController` (`@Volatile`, default an `Off(false)` controller whose
  `share()` returns `NothingToShare` and whose `setEnabled` does nothing), `internal fun install(recorder,
  clock)`, `internal fun installController(controller)`, `@VisibleForTesting internal fun resetForTests()`.

**Tests (write first):**
- `.../core/telemetry/TelemetrySinkTest.kt`: T15 `disabledRecordNeverReachesEncoderOrStore`, T21
  `nullDirectoryKeepsRecordingOffAndStatusUnavailable`, T22
  `ioErrorTurnsRecordingOffWithMetaUnavailableAndNeverThrows`, T23
  `fileNamingAndAppendOnTempDir` (real `FileTelemetryStore` on `@TempDir`, `ManualTelemetryExecutor`; lines
  are `\n`-terminated and parse with `Json.parseToJsonElement`), T35
  `encodingAndFileIoRunOnlyOnWriterThread` (real `ThreadTelemetryExecutor`; store and a recording name
  resolver see only `puber-telemetry`), T36 `recordReturnsWhileStoreIsBlocked` (real thread, store blocked on
  a latch, `record` returns; assert the enqueued event types hold no `String` field built by the probe).
- `.../core/telemetry/TelemetryCapsTest.kt`: T29
  `excessAboveCapDropsAndMetaReportsCountsPerType`, T30 `capWindowResetsEachWallMinute`, T31
  `queueOverflowDropsAreCountedAndQueueStaysAtFourThousandNinetySix`.
- `.../core/telemetry/TelemetryStoreRotationTest.kt`: T32 `rotatesAtTwoMiB`, T33
  `oldestDeletedFirstAtTwentyMiBAndNeverTheOpenFile`, T34 `archiveCountsTowardTheBudget`.
- Extend `ErrFolderAndCapTest`: T60 cap half `twentyFirstDistinctErrorInAMinuteIsDroppedAndCounted`.
- Optional (P2) `.../core/telemetry/TelemetryAllocationTest.kt` T18 `disabledProbeAllocatesNothing`
  (`com.sun.management.ThreadMXBean.getThreadAllocatedBytes`; `Assumptions.assumeTrue` when unavailable).
- Test util `.../util/FakeTelemetryClock.kt` (settable `elapsed`, `wall`).

**Consumes:** Tasks 1-2.
**Produces:** `TelemetryExecutor`, `ThreadTelemetryExecutor`, `TelemetryWriter`, `TelemetryRecorder`,
`TelemetrySink` (`start`, `stop`, `requestDrain`, `flushBlocking`, `export`, `refreshStatus`, `status`),
`SessionLogState`, `ShareResult`, `SessionLogController`, `Telemetry`, `ManualTelemetryExecutor`, `FakeTelemetryClock`.

- [x] Write the tests; add the files.
- [x] Fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*'`; commit
  ("Add the telemetry sink with its writer thread, caps, rotation and size budget").

**Done when:** both commands are green, and `TelemetrySinkTest` (T35/T36 on the real thread) passes three
consecutive runs.

---

### Task 4: Error capture: Timber tree and uncaught handler

**Covers:** AC-7 (err), AC-10
**Files:**
- Create `core/telemetry/TelemetryErrorTree.kt`: `internal class TelemetryErrorTree : Timber.Tree()`. It must
  not format anything on the caller's thread: Timber's `prepareLog` builds the stack-trace string before
  `log(...)` is called. So override the throwable overloads directly and record from them:
  `w(t: Throwable?, message: String?, vararg args: Any?)`, `w(t: Throwable?)`, `e(t, message, args)`, `e(t)`,
  `wtf(t, message, args)`, `wtf(t)`, `log(priority: Int, t: Throwable?, message: String?, vararg args: Any?)`
  (only `priority >= Log.WARN`), `log(priority, t)`; each does `if (t != null && Telemetry.isEnabled)
  Telemetry.record(TelemetryEvent.Err(Telemetry.nowMs(), ErrSource.LOG, t))`. The message-only overloads and
  the abstract `log(priority, tag, message, t)` do nothing; `isLoggable` returns `priority >= Log.WARN`.
  (`Log.WARN` is a compile-time constant; it does not call into `android.util.Log`.) The app reaches it through
  `core/logger/Logger.kt:11-19` (`Timber.tag(...).w(sanitizedThrowable, sanitizedMessage)`), which covers
  `DefaultExceptionHandler` (`core/coroutine/CoroutinesErrorHandler.kt:10-14`).
- Create `core/telemetry/TelemetryUncaughtHandler.kt`: `internal class TelemetryUncaughtHandler(private val
  previous: Thread.UncaughtExceptionHandler?, private val sink: TelemetrySink) : Thread.UncaughtExceptionHandler`
  with `@Volatile var active = true`; `uncaughtException(thread, error)`: `try { if (active &&
  Telemetry.isEnabled) { Telemetry.record(Err(..., ErrSource.UNCAUGHT, error)); if
  (!sink.executorIsWriter(thread)) sink.flushBlocking(1500) } } catch (_: Throwable) {} finally {
  previous?.uncaughtException(thread, error) }` (detekt: annotate the catch with
  `@Suppress("TooGenericExceptionCaught", "SwallowedException")` and a one-line reason). Companion helpers
  `install(sink): TelemetryUncaughtHandler` (wraps `Thread.getDefaultUncaughtExceptionHandler()` and sets
  itself) and `uninstall(handler)`: if `Thread.getDefaultUncaughtExceptionHandler() === handler` restore
  `previous`, else set `active = false` (stays in the chain, inert).

**Tests (write first):**
- `.../core/telemetry/TelemetryErrorTreeTest.kt`: T55 `onlyWarnAndAboveWithThrowableIsRecorded` (plant the
  tree, call `Timber.d/i(t, ...)`, `Timber.w("msg")`, `Timber.w(t, "msg")`, `Timber.e(t)`; a recording
  `TelemetryRecorder` installed with `isEnabled = true`; uproot in `@AfterEach`).
- `.../core/telemetry/TelemetryErrorPipelineTest.kt`: T40
  `errLineUsesSourceTypeSanitizedMessageAndAppFramesOnly` (go through `Any.log(throwable)` from
  `core/logger/Logger.kt:11`, then encode the recorded `Err` with `TelemetryEncoder`: `et` is the original
  type, `msg` has no URL, at most 5 frames, none from the excluded prefixes).
- `.../core/telemetry/TelemetryUncaughtHandlerTest.kt`: T56
  `previousHandlerCalledOnceWithSameThreadAndThrowable`, T57 `previousHandlerStillCalledWhenSinkThrows`, T58
  `flushIsBoundedAndSkippedWhenCrashIsOnWriterThread` (blocked `FakeTelemetryStore`; assert return within
  the bound using a latch timeout, no sleep), T59 `disableRestoresPreviousHandlerOnlyIfStillDefault`. Save
  and restore `Thread.getDefaultUncaughtExceptionHandler()` in `@BeforeEach`/`@AfterEach`.

**Consumes:** Tasks 1-3; `core/logger/Logger.kt:7-20`.
**Produces:** `TelemetryErrorTree`, `TelemetryUncaughtHandler` (+ `install`/`uninstall`), and on `TelemetrySink` a
`fun executorIsWriter(thread: Thread): Boolean` delegating to `TelemetryExecutor.isWriterThread`.

- [x] Write the tests; add the files.
- [x] Fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*'`; commit
  ("Capture warnings, coroutine failures and uncaught exceptions as telemetry errors").

**Done when:** both commands are green.

---

### Task 5: Runtime, persistence, auto-off, startup marks and app wiring

**Covers:** AC-1 (fresh install), AC-2, AC-4, AC-9 (startup), AC-10a, AC-1a (export via controller)
**Files:**
- Create `core/telemetry/StartupMarks.kt`: `internal class StartupMarks(private val procStartMs: Long, private
  val appCreateMs: Long, private val record: (TelemetryEvent.Startup) -> Unit, private val clock:
  TelemetryClock)` with `markAppComposed()`, `markFirstContent()`, `onTimeout()`; emits exactly one
  `Startup` (an `AtomicBoolean`): on `markFirstContent`, or on `onTimeout` with the marks so far. Marks are
  primitives; `markAppComposed` keeps the first value only. Add `@Volatile internal var startupMarks:
  StartupMarks? = null` to `Telemetry`.
- Create `core/telemetry/TelemetryRuntime.kt`: `internal class TelemetryRuntime(private val prefs:
  SharedPreferences, private val clock: TelemetryClock, private val sink: TelemetrySink) :
  SessionLogController`. Preference file `telemetry_preferences`, key `enabled_at_wall_ms` (Long; absent =
  off), writes with `apply()`.
  - `start(startup: (() -> StartupMarks)?)`: read the key; absent -> nothing else happens (no executor, no
    directory, no tree, no handler: AC-1, AC-2). `enabledAt > now` -> rewrite it as `now`. `now - enabledAt >=
    7 d` -> remove the key, stay off (no line is written: no file is open). Otherwise `enable(enabledAt)` and,
    only in this cold-start case, `Telemetry.startupMarks = startup?.invoke()` and
    `executor.schedule(60_000) { startupMarks.onTimeout() }` (through a sink method).
  - `enable(enabledAt)`: `sink.start(enabledAt, onExpired = ::onExpired, onStopped = ::detachProbes)`,
    `Timber.plant(TelemetryErrorTree())`, `TelemetryUncaughtHandler.install(sink)`.
  - `setEnabled(true)`: write `now`, `enable(now)`. `setEnabled(false)`: remove the key, `sink.stop()`,
    `detachProbes()` (uproot the tree, uninstall the handler). Both are safe to call twice.
  - `onExpired()`: remove the key, `detachProbes()`.
  - `state` = `sink.status`; `refresh()` re-checks expiry and calls `sink.refreshStatus()` (this may create
    the executor; it is called when Settings opens, not at startup).
  - `share()` = `suspendCancellableCoroutine { sink.export { cont.resume(it.toShareResult()) } }`.
  - `companion object { fun install(app: Application, appCreateMs: Long) }`: builds
    `FileTelemetryStore { app.getExternalFilesDir(null) }`, `ThreadTelemetryExecutor()`, `SystemTelemetryClock`,
    a `SessionInfo` provider (`BuildConfig.BUILD_TYPE`, `BuildConfig.VERSION_NAME`, `Build.VERSION.SDK_INT`,
    `Build.MODEL`, `ActivityManager.memoryClass`, `isLowRamDevice`, default display `refreshRate` via
    `DisplayManager`; evaluated on the writer thread only), the sink, the runtime; calls
    `Telemetry.install(sink, SystemTelemetryClock)`, `Telemetry.installController(runtime)`,
    `runtime.start { StartupMarks(Process.getStartElapsedRealtime(), appCreateMs, ...) }` (API 24 = minSdk,
    `buildSrc/src/main/kotlin/Versions.kt:6`), and registers an `Application.ActivityLifecycleCallbacks` whose
    `onActivityPaused` calls `if (Telemetry.isEnabled) sink.requestDrain()` (all other callbacks empty). The
    splash-frame mark is skipped in S1 (spec Out of scope allows it: it needs `ReportDrawn`/JankStats wiring).
- Create `core/telemetry/di/TelemetryModule.kt`: `val telemetryModule = module { single<SessionLogController> { Telemetry.controller } }`.
- Modify `PuberApp.kt:67-72`: first statement of `onCreate` `val appCreateMs = SystemClock.elapsedRealtime()`;
  after `super.onCreate()` (`:68`) and before `initDi()` (`:70`) call
  `TelemetryRuntime.install(this, appCreateMs)`. Add `telemetryModule` to the list at `:77-83`. `initLogger`
  (`:88-92`) unchanged.
- Modify `MainActivity.kt:54-56`: inside `if (appComposed) { App() ... }` add after `App()`
  `LaunchedEffect(Unit) { Telemetry.startupMarks?.markAppComposed() }`.
- Test util: add `getLong`/`putLong` support to `app/src/test/kotlin/com/kino/puber/util/FakeSharedPreferences.kt`
  (same pattern as `getInt`/`putInt` at its `init` block).

**Tests (write first):**
- `.../core/telemetry/TelemetryRuntimeTest.kt` (fake prefs, `FakeTelemetryClock`, `FakeTelemetryStore`,
  `ManualTelemetryExecutor` wrapped so the test can assert it was never used): T04
  `freshInstallDoesNotResolveDirectoryOrCreateWriterThread`, T17 `offStartPlantsNoTreeAndNoHandler`, T25
  `autoOffAtStartAfterSevenDays` (7 d expires; 7 d - 1 ms stays on), T26
  `autoOffOnNextDrainWhenExpiredWhileRunning`, T27 `clockGoingBackwardsClampsToNow`, T28
  `autoOffEmitsMetaClosesFileClearsPrefUprootsAndStatusOff`, T65
  `enablingAtRuntimePlantsTreeAndHandlerWithoutRestart` (`Timber.forest()` gains a `TelemetryErrorTree`;
  `session` and `meta start` are recorded), T66 `disablingUprootsTreeAndRestoresHandler`, T67
  `enabledStateSurvivesNewRuntimeInstance`.
- `.../core/telemetry/SessionLogControllerTest.kt`: T08 `shareRotatesAndZipsOnlyClosedFiles`, T09
  `shareReplacesPreviousArchive`, T10 `shareWithNoClosedFilesReturnsNothingToShare`, T11
  `shareIoFailureReturnsFailedAndLeavesNoPartialZip`.
- `.../core/telemetry/StartupMarksTest.kt`: T47 `oneLineWithAscendingMarksOnFirstContent`, T48
  `fallbackLineAfterSixtySecondsWithMarksSoFar`.

**Consumes:** Tasks 1-4; `PuberApp.kt:62-92`; `MainActivity.kt:45-58`; precedent for reading prefs before
Koin: `core/system/AppLocale.kt:35-38,72-73`.
**Produces:** `TelemetryRuntime` (`install`, `SessionLogController` impl), `StartupMarks`,
`Telemetry.startupMarks`, `telemetryModule`.

- [x] Write the tests; add the files and wiring.
- [x] Fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*'`; commit
  ("Wire the session log runtime into app startup with persistence and seven-day auto-off").

**Done when:** both commands are green and `PuberApp.onCreate` calls `TelemetryRuntime.install` before `initDi()`.

---

### Task 6: `PuberVM` probes: `act` template method, `ready`, `first_content`

**Covers:** AC-2, AC-9, AC-10 (coroutine path)
**Files:**
- Modify `core/ui/PuberVM.kt`:
  - After `:46` add `private val telemetryOpenedAtMs: Long = Telemetry.nowMs()` (JVM-safe default clock) and
    `internal open val telemetryStartupContentClass: Class<*>? = null` (internal, not protected, so
    `HomeVMTest` can assert it; the spec said protected).
  - `:82-84` `updateViewState(viewState)`: `val previous = if (Telemetry.isEnabled) mutableViewState.value else
    null`; set the value as today; then `if (Telemetry.isEnabled) recordReady(previous, viewState)` where the
    private `recordReady` records `Ready(now, javaClass, newClass, now - telemetryOpenedAtMs)` only when
    `previous?.javaClass !== viewState?.javaClass`, and when `newClass === telemetryStartupContentClass` calls
    `Telemetry.startupMarks?.markFirstContent()`. Keep `open` (no subclass overrides it today).
  - `:135-140`: `fun onAction(action: UIAction)` (no longer `open`) = `if (Telemetry.isEnabled)
    Telemetry.record(Act(Telemetry.nowMs(), javaClass, action.javaClass))` then `handleAction(action)`; the
    old body moves unchanged into `protected open fun handleAction(action: UIAction)`.
- Rename in the 19 overrides, **only these two lines each**: `override fun onAction(action: UIAction)` ->
  `override fun handleAction(action: UIAction)` and `super.onAction(action)` -> `super.handleAction(action)`:
  `ui/feature/auth/vm/AuthVM.kt:148,155`; `ui/feature/bookmarks/vm/BookmarksVM.kt:66,84`;
  `ui/feature/collections/detail/vm/CollectionDetailVM.kt:49,65`; `ui/feature/collections/vm/CollectionsVM.kt:37,38`;
  `ui/feature/contentlist/vm/ContentListVM.kt:48,57`; `ui/feature/contentlist/vm/SectionVM.kt:90,98`;
  `ui/feature/details/vm/DetailsVM.kt:163,193`; `ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt:39,42`;
  `ui/feature/device/settings/vm/DeviceSettingsVM.kt:135,175`; `ui/feature/episodeschedule/vm/EpisodeScheduleVM.kt:30,36`;
  `ui/feature/favorites/vm/FavoriteVM.kt:71,81`; `ui/feature/history/vm/HistoryVM.kt:171,193`;
  `ui/feature/home/vm/HomeVM.kt:176,197`; `ui/feature/main/vm/MainVM.kt:103,108`;
  `ui/feature/player/vm/PlayerVM.kt:632,678`; `ui/feature/root/vm/LauncherVM.kt:24,27`;
  `ui/feature/search/vm/SearchVM.kt:36,47`; `ui/feature/showall/vm/ShowAllVM.kt:73,90`;
  `ui/feature/update/vm/UpdatePromptVM.kt:59,67`. Callers (`viewModel::onAction`, tests calling
  `vm.onAction(...)`) do not change. No test overrides `onAction` (checked).
- Modify `ui/feature/home/vm/HomeVM.kt` next to `:71`: `override val telemetryStartupContentClass: Class<*> = HomeViewState.Content::class.java`
  (`ui/feature/home/model/HomeViewState.kt:19`).
- Modify `docs/architecture/viewmodel.md:18,22,45`: the example uses `override fun handleAction` /
  `super.handleAction(action)`; the table row says `onAction(action)` is final, records `act` and calls
  `protected open handleAction(action)`, which overrides end with `else -> super.handleAction(action)`.

**Tests (write first):**
- `.../core/ui/PuberVMTelemetryTest.kt` (a trivial concrete `PuberVM` with a sealed test state, defined in
  the test file; `MainDispatcherExtension`; recording `TelemetryRecorder` + `FakeTelemetryClock` installed
  before the VM is constructed): T16 `stateAndActionProbesAreSilentWhenOff`, T49
  `readyOnlyOnStateClassChangeWithMsSinceOpen` (includes the data-class negative case, spec F11), T50
  `startupContentClassTransitionTriggersFirstContent`, T52 `actRecordedForHandledAndUnhandledActions`, T61
  `coroutineFailureInLaunchReachesErrThroughLoggerWithThrowable` (plant `TelemetryErrorTree`; a failing
  `launch {}` reaches it through `core/ui/PuberVM.kt:97-99` and `core/coroutine/CoroutinesErrorHandler.kt:13`).
- `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt`: one case asserting
  `telemetryStartupContentClass == HomeViewState.Content::class.java`.

**Consumes:** Tasks 1-5.
**Produces:** final `PuberVM.onAction`, `protected open fun handleAction(action: UIAction)`,
`internal open val telemetryStartupContentClass`.

- [x] Write `PuberVMTelemetryTest` (fails to compile: no `handleAction`).
- [x] Change `PuberVM`, rename the 19 overrides, add the `HomeVM` override, update the doc.
- [x] Fast gate; `./tools/agentw :app:testDevDebugUnitTest` (whole suite: the rename touches every VM test);
  `grep -rn "override fun onAction\|super.onAction" app/src/main` prints nothing (S1); commit
  ("Record view model actions and state changes through a final onAction template method").

**Done when:** the fast gate and the full unit suite are green and the S1 grep is empty.

---

### Task 7: Navigation probes

**Covers:** AC-9 (`nav`)
**Files:**
- Create `core/ui/navigation/component/NavTelemetry.kt`: `internal fun navCmdOf(command: Command): NavCmd`
  (pure: `NavigateTo` -> `PUSH`, `NavigateForResult` -> `PUSH_RESULT`, `Replace` -> `REPLACE`, `NewRoot` ->
  `NEW_ROOT`, `BackTo` -> `BACK_TO`, `FinishFlow` -> `FINISH_FLOW`, `Back` -> `BACK`; `Command` is
  `core/ui/navigation/AppRouter.kt:156-168`) and `internal fun recordNav(cmd: NavCmd, from: Any?, to: Any?)`
  (`if (!Telemetry.isEnabled) return`; `Telemetry.record(Nav(Telemetry.nowMs(), cmd,
  telemetryClassOf(from), telemetryClassOf(to)))`).
- Modify `core/ui/navigation/component/FlowComponent.kt` inside the collector of `FlowCommandRunner`
  (`:225-269`): after `router.log(...)` (`:226`, kept) add `val navFrom = if (Telemetry.isEnabled)
  navigator.items.lastOrNull() else null`; after the `if/else` that ends at `:268` add
  `if (Telemetry.isEnabled) recordNav(if (event.screen is PuberScreenActivity) NavCmd.ACTIVITY else
  navCmdOf(event), navFrom, if (event.screen is PuberScreenActivity) event.screen else navigator.items.lastOrNull())`.
  Same two insertions in `TabFlowCommandRunner` (`:708-747`, after `:709`, after the `if/else` ending at `:746`).
  Never log `key` (`:247`, `:253` use it for anchors; telemetry must not).
- Modify `core/ui/navigation/component/TabComponent.kt:107`: `is TabCommand.Open -> { val from =
  navigator.current; navigator.current = event.tab; recordNav(NavCmd.TAB, from, event.tab) }`
  (`TabCommand.Open(val tab: PuberTab)` is `core/ui/navigation/TabRouter.kt:32`).

**Tests (write first):**
- `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt`: T53
  `commandsMapToCmdWithFromAndTo` (`navCmdOf` for every `Command`; `recordNav` with a `PuberTab` and plain
  screens yields the unwrapped classes; nothing recorded when off).

**Consumes:** Task 1 (`telemetryClassOf`, `NavCmd`), Task 3 (`Telemetry`).
**Produces:** `navCmdOf`, `recordNav`.

- [x] Write the test; add the file; make the three edits.
- [x] Fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.navigation.*'`; commit
  ("Record navigation commands and tab switches as telemetry nav events").

**Done when:** both commands are green; `git diff --stat` shows `FlowComponent.kt` with at most about 12 added lines.

---

### Task 8: Auth probe, R8 rules, FileProvider path

**Covers:** AC-10 (auth), AC-8 (rules), AC-1a (paths)
**Files:**
- Modify `data/api/KinoPubApiClient.kt:224-227` `sessionExpiredFailure`: `onSessionExpired()`; `val error =
  ApiError.Unauthorized(serverMessage = "Failed to refresh token", cause = cause)`; `if
  (Telemetry.isEnabled) Telemetry.record(TelemetryEvent.Err(Telemetry.nowMs(), ErrSource.AUTH, error))`;
  `throw error`.
- Modify `app/proguard-rules.pro`:
  - after `:94` add
    ```
    # Telemetry names (AC-8): view models, actions and view states keep readable simple names.
    -keepnames class * extends com.kino.puber.core.ui.PuberVM
    -keepnames class * implements com.kino.puber.core.ui.uikit.model.UIAction
    -keepnames class com.kino.puber.ui.**State
    -keepnames class com.kino.puber.ui.**State$*
    ```
    (`UIAction` is `core/ui/uikit/model/Actions.kt:5`; `PuberScreen` names are already kept at `:90`;
    `InnerClasses` is kept at `:15`).
  - replace `:130-132` with
    ```
    # Strip only the message overload; log(Throwable, ...) must reach the telemetry error tree in release.
    -assumenosideeffects class com.kino.puber.core.logger.LoggerKt {
        public static void log(java.lang.Object, java.lang.String, java.lang.String);
    }
    ```
    (`Any.log(message, tag)` compiles to `log(Object, String, String)`, `core/logger/Logger.kt:7`; the
    throwable overload `:11` is `log(Object, Throwable, String, String)` and is no longer matched.)
- Modify `app/src/main/res/xml/update_file_paths.xml:3-5`: add after the `updates` entry
  `<external-files-path name="telemetry" path="telemetry/"/>`. Manifest unchanged
  (`app/src/main/AndroidManifest.xml:66-74`).

**Tests (write first):**
- `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientAuthProbeTest.kt`: T62
  `sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` and the off case. Build the client as
  `KinoPubApiClientFailureTest` does (`app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientFailureTest.kt:37-54`
  for the `Log`/`Base64` static mocks, `:112-139` for the client), with tokens present
  (`getAccessToken()`/`getRefreshToken()` return non-empty) and an `OkHttpClient` whose interceptor answers
  HTTP 500 to any request not addressed to the local server, so the refresh at
  `data/api/KinoPubApiClient.kt:194-202` fails without network. The local server answers 401 with
  `WWW-Authenticate: Bearer` on `/v1/items/7`. Assert `getItemDetails(7)` fails with
  `ApiError.Unauthorized` and the recorder holds one `Err(src = AUTH)` of that same instance. Fallback if
  Ktor's bearer plugin does not take the refresh path in this setup: make `sessionExpiredFailure`
  `@VisibleForTesting internal` and call it directly; record the choice under Deviations.
- `.../core/telemetry/UpdateFilePathsXmlTest.kt`: T07 `pathsCoverTelemetryDirOnlyAndKeepUpdates` (parse
  `src/main/res/xml/update_file_paths.xml`, falling back to `app/src/main/...` as
  `app/src/test/kotlin/com/kino/puber/data/repository/EpisodeSchedulePrivacyTest.kt:26-33` does; exactly two
  `external-files-path` entries, `Download/updates/` unchanged, no `root-path`, no `external-path`).
- `.../core/telemetry/ProguardRulesTest.kt`: T45 `keepNamesRulesPresentAndLogStripRuleIsNarrowed` (reads
  `proguard-rules.pro`; the four `-keepnames` lines and the narrowed signature exist; the text `public static
  void log(...);` does not).

**Consumes:** Tasks 1-3.
**Produces:** nothing new for later tasks.

- [x] Write the tests; make the edits.
- [x] Fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.api.*' --tests 'com.kino.puber.core.telemetry.*'`;
  `./tools/agentw :app:assembleDevBenchmarkRelease` builds (R8 accepts the rules); commit
  ("Record token refresh failures, keep telemetry names under R8 and share the telemetry directory").

**Done when:** the commands are green and `assembleDevBenchmarkRelease` produces an APK under
`app/build/outputs/apk/dev/benchmarkRelease/`.

---

### Task 9: Settings > Developer: toggle, indicator, Share log

**Covers:** AC-1, AC-1a
**Files:**
- Modify `core/system/ContentSharer.kt:6-23`: add `fun shareFile(file: File, mimeType: String, chooserTitle:
  String): Boolean = runCatching { ... }.isSuccess` building `FileProvider.getUriForFile(context,
  "${context.packageName}.update_files", file)` (same expression as
  `data/repository/AppUpdateInstaller.kt:38-42`), an `ACTION_SEND` with `type = mimeType`,
  `EXTRA_STREAM = uri`, `clipData = ClipData.newRawUri("", uri)`, `FLAG_GRANT_READ_URI_PERMISSION`, wrapped in
  `Intent.createChooser(...)` with `FLAG_ACTIVITY_NEW_TASK` and the grant flag. `share` (`:9-18`) unchanged.
  `ContentSharer` is already a Koin single (`data/di/modules.kt:81`). Taking a `File` (not a `Uri`, as the
  spec's D16 wrote) keeps `Context` out of the view model.
- Modify `ui/feature/device/settings/model/DeviceSettingsActions.kt:8-39`: add `data object ToggleSessionLog`,
  `data object ShareSessionLog`, `data object SessionLogShareFocusRestored`.
- Modify `ui/feature/device/settings/model/DeviceSettingsViewState.kt`: `:10-15` add
  `val restoreShareFocus: Boolean = false`; `:22-47` add `val sessionLog: SessionLogUi = SessionLogUi()`;
  new `@Immutable internal data class SessionLogUi(val enabled: Boolean = false, val description: String = "",
  val shareEnabled: Boolean = false, val sharing: Boolean = false, val shareSubtitle: String = "")`.
- Create `ui/feature/device/settings/mappers/SessionLogUiMapper.kt`: `internal class SessionLogUiMapper(private
  val resources: ResourceProvider)` with `fun map(state: SessionLogState, sharing: Boolean): SessionLogUi`:
  `enabled` = `Recording` (and `Unavailable` keeps the switch off); description: `Off` -> off text,
  `Recording(remainingMs)` -> `remainingMs < 1 h` ? under-an-hour text : recording text with
  `days = remainingMs / 1 d`, `hours = remainingMs % 1 d / 1 h` (7 d -> "7, 0"), `Unavailable` -> unavailable
  text; `shareEnabled = hasFiles`; `shareSubtitle` = preparing (when `sharing`), else share subtitle when
  `hasFiles`, else nothing-to-share. Keeps `DeviceSettingsVM` (522 lines) clear of detekt `LargeClass`.
- Modify `ui/feature/device/settings/vm/DeviceSettingsVM.kt`:
  - constructor (`:39-51`): add `private val sessionLog: SessionLogController` and `private val contentSharer:
    ContentSharer` (resolved by `viewModelOf(::DeviceSettingsVM)` at `ui/feature/device/settings/DeviceSettingsScreen.kt:36`
    from the global `telemetryModule` and `data/di/modules.kt:81`); a private
    `SessionLogUiMapper(resources)`.
  - field next to `:53`: `private var latestSessionLog = SessionLogUi()`, `private var sharing = false`.
  - `onStart` (`:66-69`): add `observeSessionLog()` = `sessionLog.refresh()` + `launch { sessionLog.state.collect {
    latestSessionLog = mapper.map(it, sharing); push into Success as observeWatchIndex does at :78-86 } }`.
  - `loadDeviceSettings` Success builder (`:99-125`): add `sessionLog = latestSessionLog`.
  - `handleAction` (renamed in Task 6, `:135-176`): `ToggleSessionLog -> sessionLog.setEnabled(!latestSessionLog.enabled)`;
    `ShareSessionLog -> shareSessionLog()`; `SessionLogShareFocusRestored -> updateViewState(stateValue.copy(restoreShareFocus = false))`.
  - `shareSessionLog()`: if `!latestSessionLog.shareEnabled` -> `showMessage(nothing-to-share)` and return;
    if `sharing` return; set `sharing = true` and re-map; `launch { try { when (val r = sessionLog.share()) {
    is Ready -> if (contentSharer.shareFile(r.archive, "application/zip", chooserTitle))
    updateViewState(stateValue.copy(restoreShareFocus = true)) else showMessage(failed); NothingToShare ->
    showMessage(nothing-to-share); Failed -> showMessage(failed) } } finally { sharing = false; re-map } }`.
- Modify `ui/feature/device/settings/DeviceSettingsContent.kt`:
  - `:185-187`: `val sections = remember { SettingsSection.entries.toList() }` (the `BuildConfig.DEBUG`
    filter moves to the overlay row; do not reword it into another condition).
  - `:83-90` `DeviceSettingsContent`: add `restoreShareFocus: Boolean = false`, pass to `DeviceSettingsPane`
    (`:99-106`, `:176-183`).
  - `DeviceSettingsPane`: next to `:197` add `val shareFocusRequester = remember { FocusRequester() }` and a
    `LaunchedEffect(restoreShareFocus)` modelled on `:198-204`: return when false; `selectedSection =
    SettingsSection.Developer`; wait until the lifecycle (`androidx.lifecycle.compose.LocalLifecycleOwner`)
    has left and come back to `RESUMED` (`lifecycle.currentStateFlow.first { !it.isAtLeast(RESUMED) }`, then
    `first { it.isAtLeast(RESUMED) }`), `delay(ReturnFocusDelayMillis)` (`:76`),
    `shareFocusRequester.requestFocus()`, `onAction(DeviceSettingsActions.SessionLogShareFocusRestored)`.
    The chooser does not dispose this composition, so a plain effect would request focus before the chooser
    opens; the lifecycle wait is what makes it "after resume".
  - Thread `shareFocusRequester` like `diagnosticsFocusRequester`: `SettingsSectionPanel` call `:244-255` and
    signature `:338-345`, `SettingsSectionContent` call `:373-384` and signature `:403-411`, and
    `:465` -> `developerItems(state, onAction, shareFocusRequester)`.
- Modify `ui/feature/device/settings/DeviceSettingsInformationItems.kt:147-158` `developerItems(state, onAction,
  shareFocusRequester: FocusRequester)`, rows top to bottom (ux delta; reuse the components, no new sizes):
  1. `item(key = "session-log") { SettingsToggleItem(label = settings_session_log, checked =
     state.sessionLog.enabled, description = state.sessionLog.description, onToggle = {
     onAction(ToggleSessionLog) }) }` (`ui/feature/device/settings/DeviceSettingsItems.kt:207-231`).
  2. `item(key = "share-log") { SettingsListItem(headline = settings_share_log, supportingText =
     state.sessionLog.shareSubtitle, role = Role.Button, enabled = state.sessionLog.shareEnabled &&
     !state.sessionLog.sharing, focusableWhenDisabled = true, modifier =
     Modifier.focusRequester(shareFocusRequester), onClick = { onAction(ShareSessionLog) }) }`
     (`DeviceSettingsItems.kt:80-144`; precedent `DeviceSettingsContent.kt:762-770`).
  3. the existing `debug-overlay` item (`:151-157`) unchanged, last, wrapped in `if (BuildConfig.DEBUG)`.
- Modify `ui/feature/device/settings/DeviceSettingsScreen.kt:48-54`: pass `restoreShareFocus = state.restoreShareFocus`.
- Strings: `app/src/main/res/values/device_settings_strings.xml` (ru default; `settings_developer_description`
  is at `:17`) and `app/src/main/res/values-en/strings.xml` (next to `:99`). Russian variants of the toggle
  description at most about 60 characters (one line, ux delta). Proposed text (the owner judges wording at
  release):

  | name | ru | en |
  |---|---|---|
  | `settings_session_log` | Журнал сеанса (для диагностики) | Session log (for diagnostics) |
  | `settings_session_log_off` | Пишет действия в локальный файл, выключится через 7 дней | Writes app activity to a local file, turns off after 7 days |
  | `settings_session_log_recording` | Идёт запись · осталось %1$d д %2$d ч | Recording · %1$d d %2$d h left |
  | `settings_session_log_recording_under_hour` | Идёт запись · осталось меньше часа | Recording · less than an hour left |
  | `settings_session_log_unavailable` | Запись недоступна: нет хранилища | Recording unavailable: no storage |
  | `settings_share_log` | Поделиться журналом | Share log |
  | `settings_share_log_subtitle` | Упаковать закрытые файлы в архив и отправить | Pack the closed files into an archive and send it |
  | `settings_share_log_empty` | Пока нечего отправлять | Nothing to share yet |
  | `settings_share_log_preparing` | Готовлю архив… | Preparing the archive… |
  | `settings_share_log_chooser` | Отправить журнал сеанса | Send the session log |
  | `settings_share_log_failed` | Не удалось отправить журнал | Could not share the log |
- Update the four existing tests that construct the VM
  (`app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM{AutoTrailer,Language,MenuSections,MirrorNavigation}Test.kt`,
  for example `DeviceSettingsVMAutoTrailerTest.kt:89-103`): pass `sessionLog = FakeSessionLogController()`
  and `contentSharer = mockk(relaxed = true)`. Do not pass a relaxed mock as the controller: collecting a
  mocked `StateFlow` does not behave. New test util `app/src/test/kotlin/com/kino/puber/util/FakeSessionLogController.kt`
  (a `MutableStateFlow<SessionLogState>(Off(false))`, records `setEnabled`/`refresh` calls, scripted
  `share()` result, optional `CompletableDeferred` to hold `share()` in flight).

**Tests (write first):**
- `app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMSessionLogTest.kt`: T01
  `offStateShowsToggleOffAndOffDescription`, T02 `recordingStateShowsIndicatorWithDaysAndHoursLeft` (6 d 23 h,
  under 1 h, exactly 7 d; also the Unavailable text and the switch off), T03
  `toggleActionCallsSetEnabledAndRefreshesState`, T13 `shareRowDisabledWhenNoFiles`, T12
  `shareResultsMapToMessagesAndSharingFlag` (`Ready` + `shareFile` true -> no message and `restoreShareFocus`;
  `shareFile` false and `Failed` -> failed message; `NothingToShare` -> empty message; `sharing` true while
  held, false after).
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/settings/DeviceSettingsContentFocusTest.kt`
  (612 lines; follow its `DeviceSettingsContent(...)` setup at `:239`, `:348`): T05
  `developerSectionRowsAreFocusableAndLeftReturnsToSectionList` (Developer listed; `session-log` and a
  disabled `share-log` take focus; DPAD_LEFT returns to the section list). Compile it here; the qa run
  executes it with `make DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.device.settings.DeviceSettingsContentFocusTest`.

**Consumes:** Tasks 3, 5, 6 (`SessionLogController`, `SessionLogState`, `ShareResult`, `telemetryModule`,
`handleAction`).
**Produces:** the Settings rows, `ContentSharer.shareFile`, `SessionLogUi`, `SessionLogUiMapper`.

- [x] Write `DeviceSettingsVMSessionLogTest` and T05; update the four tests.
- [x] Make the edits.
- [x] Fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.device.settings.*'`;
  `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit
  ("Add the session log toggle, recording indicator and Share log to Settings developer section").

**Done when:** the commands are green and `grep -n "BuildConfig.DEBUG" app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt`
no longer shows the section filter while `DeviceSettingsInformationItems.kt` wraps only `debug-overlay` in it.

---

### Task 10: Full gate

**Covers:** S2, S4 (regression), all ACs at the static level
**Files:** only what the full gate reports (detekt/lint findings in files this plan touched). No baseline
entries added to `config/detekt/detekt-baseline.xml`; fix the finding instead.

- [x] `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- [x] Fix findings; rerun until green; commit only if something changed ("Fix static analysis findings in the telemetry change").

**Done when:** the full gate is green on the branch head.

### Handoff to verification (not implementer work)

qa-device runs the spec's device scenarios D1-D9 and the perf comparison against the baseline
(`.flow/runs/telemetry-core/baseline.md`, cold-start median 2804 ms on emulator-5554, `devDebug`): `devDebug`
for D1, D3, D4, D6, D8, D9; `deploy` (`./tools/agentw :app:assembleDevDeploy`) for D2's non-debug Developer
section; **`devBenchmarkRelease`** (`./tools/agentw :app:assembleDevBenchmarkRelease`, minified, debug-signed,
same signer as `devDebug`, installed with `tools/android-apk-install-preserve`) for D2, D5 and D7. T05 through
`make DEVICE=<serial> itest TESTS=...`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- 01d22f63 Task 1: event model, names, scrubber, encoder
- b911ebdb Task 2: file store, act and error folders
- fdd40ab9 Task 3: sink, writer, executor, `Telemetry` entry point
- cee75b87 Task 4: Timber error tree, uncaught handler
- e5b97b29 Task 5: runtime, persistence, auto-off, startup marks, app wiring
- 9f985934 Task 6: `PuberVM` probes, `onAction` template method, 19 renames
- c2033fc2 Task 7: navigation probes
- 05735e7d Task 8: auth probe, R8 rules, FileProvider path
- eb1c7974 Task 9: Settings > Developer rows
- Task 10: "Fix static analysis findings in the telemetry change" (detekt: magic numbers in the event enum, one complex condition, one loop with two jumps, test line lengths); see `git log` for its sha. Final gate: `BUILD SUCCESSFUL`, 1741 unit tests, 0 failures, `detektAll` and `lintDevDebug` clean. One first attempt died with a JVM crash in the Gradle daemon (G1 SIGSEGV, not the code); the rerun was green.

**Deviations from the plan:**

- `TelemetryWriter` takes `onUnavailable: () -> Unit` instead of `statusSink: (SessionLogState) -> Unit`, and
  also takes the `TelemetryEncoder` and `TelemetryNames` it uses; the sink computes the status. `hasFiles()`
  lives on the sink (it lists the store), not on the writer.
- The writer is built lazily inside `TelemetrySink` (on first `start` or `export`), so `t` is relative to that
  moment. If a share happens before the first enable in a process, `t` of the later session starts a little
  earlier than "first start"; harmless.
- Readiness note 1: `TelemetrySink.schedule(delayMs, task)` was added (Task 3) and is used by Task 5 for the
  60 s startup fallback.
- Readiness note 2: `HomeVM.telemetryStartupContentClass` is a getter. Note 3: `TelemetryRuntime.enable`
  plants the tree and handler idempotently and does it before `sink.start`, so the sink's `onStopped`
  callback (storage unavailable) always finds them; covered by
  `tapWhileStorageIsUnavailablePlantsNoSecondTreeOrHandler` and `enablingTwiceNeverPlantsASecondTree`.
  Note 4: minified frame filtering is not claimed; see For the reviewers. Note 5: no `else ->` exists anywhere
  in `TelemetryEncoder.kt`.
- `TelemetryPrivacyTest` (T38): a title or search text typed as bare prose in a custom exception message cannot
  be recognised by any scrubber (the spec lists it as a residual risk caught only by the device audit). The
  test therefore carries titles and search texts the way real messages carry them, inside URLs (percent
  encoded) and JSON excerpts, plus token queries, and asserts none of them survives.
- T62 ran through the real Ktor refresh path (interceptor answering 500 to anything not addressed to the local
  server); the `@VisibleForTesting` fallback was not needed.
- Added `recordNavCommand(event, from, top)` to `NavTelemetry.kt` so each flow runner needs one inserted line
  after the `when` (activity versus command mapping lives in the helper). `FlowComponent.kt` gained 5 lines.
- `NavTelemetry`'s `from`/`to` read `navigator.lastItemOrNull` (Voyager), which does not copy the stack;
  `navigator.items` would.
- Extra tests beyond the plan list: `SessionLogControllerTest` also covers `export` with the fake store,
  `DeviceSettingsVMSessionLogTest` covers a tap while storage is unavailable and a second share in flight,
  `TelemetryRuntimeTest` covers the cold-start marks wiring.
- Test classes in `core/telemetry` are `internal` (Kotlin refuses a public class exposing internal types).
- Task 8: `assembleDevBenchmarkRelease` builds (R8 accepted the rules); in the produced `mapping.txt`
  `HomeVM`, `HomeViewState$Content` and `CommonAction$ItemSelected` keep their names and `LoggerKt` is
  obfuscated as expected.

**Files touched outside the plan:**

- `app/src/test/kotlin/com/kino/puber/util/FakeSharedPreferences.kt` (getLong/putLong, planned),
  `RecordingTelemetryRecorder.kt`, `TelemetryTestSupport.kt`, `TestScreens.kt` (test doubles only).
- `app/src/main/java/com/kino/puber/MainActivity.kt` and `PuberApp.kt` as planned in Task 5.
- `app/src/test/.../ui/feature/home/vm/HomeVMTest.kt` (one case, planned).

**For the reviewers:**

- Hot files: `PuberVM.kt`, `FlowComponent.kt`, `PlayerVM.kt` (two renamed lines only). Conflicts with
  `player-overlays-port` and `upstream-sync-triage` should be mechanical.
- `ErrorScrubber` keeps the plan's prefix list. In a minified build R8 renames library packages, so the
  prefix filter will not drop renamed library frames and app frames are obfuscated; `devBenchmarkRelease`
  has no retrace for the log. D7 should report what `frames` and `et` look like on that build rather than
  claim "app frames only".
- `DeviceSettingsVM` is now about 595 lines; `detekt` result is in the gate output below.
- The writer swallows `IOException` from the store by design (turns recording off, never throws); the uncaught
  handler swallows everything on purpose and always calls the previous handler in `finally`.
- Share focus return uses a lifecycle wait (RESUMED lost and regained) before the delay and the request; it
  is only set when `ContentSharer.shareFile` returned true. Check with a real chooser on a device.

**Blocked:** none.

### Review round 1 fixes

| Triage row | Fix | Commit |
|---|---|---|
| 1 | `export` drains the queue and force-finishes folds first when running; test records then shares with no periodic drain | d962b687 |
| 2 | `ErrFolder.accepts` refuses `UNCAUGHT`: a crash is written at once as `src=uncaught` | 1b740792 |
| 3 | `et` and frames carry the full binary class name; `SanitizedLogThrowable.sourceType` is now `javaClass.name`, so both paths agree | 1b740792 |
| 4 | `first_content` is the first `ready` of any view model (the landing screen); `telemetryStartupContentClass` and the `HomeVM` override removed | 72af6ac6 |
| 5 | `UNCAUGHT` bypasses the `err` per-minute cap in the writer | 1b740792 |
| 6 | Toggle in `Unavailable` calls `setEnabled(false)` (clears the pref); next tap turns it on | 279b281c |
| 7 | A stored `enabled_at` ahead of the clock is no longer rewritten or clamped | d962b687 |
| 8 | `export` catches `Throwable` and answers `ShareResult.Failed` | d962b687 |
| 9 | The writer and encoder are built per start (new `sid`, new `t` origin); a Share before the first enable builds a throwaway writer only | d962b687 |
| 10 | Budget deletes stale zips and orphan `*.tmp` before any `.jsonl`; `list()` now reports `*.tmp`; `export` removes them too | d962b687 |
| 11 | deferred (run `telemetry-ready-data-states`) | - |
| 12 | Snackbar dismissed before the `Failed` and `NothingToShare` steps | 279b281c |
| 13 | Sink-level tests: act fold, `scr` on nav/act/ready/stop meta, flush on stop, act cap after folding | b07f77d4 |
| 14 | Instrumented T05 also asserts all seven sections are displayed; QA still has to run it on a device | b07f77d4 |
| 15 | Tab keep rule in the tripwire; `plain()` guarded by an anonymous class whose `simpleName` is empty | b07f77d4 |
| 16 | `ThreadTelemetryExecutorTest`: real writer thread returns true, test thread false | b07f77d4 |
| 17 | T52 sets a message before the actions; T47 ascending check replaced by exact value asserts | 72af6ac6 (T47, T52), b07f77d4 |

Spec deltas (ACs untouched):

- D13 "a clock that runs backwards clamps to `now`": instead the stored time is kept and a negative elapsed time
  counts as zero (AC-4's test, "no negative or more than 7 d remaining", still holds). A clamp, even in memory
  only, would let the first drain after a time sync see more than seven days and switch the log off, which is
  the scenario of row 7.
- D14 "only `HomeVM` overrides `telemetryStartupContentClass`": replaced by the first `ready` of any view
  model. Launcher has a `Unit` state and never emits one. A landing screen that never emits `ready` (a data
  class state, row 11) can still let a later screen supply `first_content`; `telemetry-ready-data-states`
  closes that.
- Error line schema: `et` and each `frames` entry are the full binary class name (`pkg.Outer$Inner`), not a
  simple name, so `retrace` with `mapping.txt` resolves them in release.
- `SanitizedLogThrowable.sourceType` is the binary name, so the sanitized message of a nested exception reads
  `Outer$Inner: msg` instead of `Outer.Inner: msg` in the file logger.

### Review round 2 fixes

| Triage row | Fix | Commit |
|---|---|---|
| 1 | `enforceBudget` skips the newest `*yyyyMMdd-HHmm.zip` (it is replaced only by the next export); stale zips and `*.tmp` still go first; near-budget test | 12abafde |
| 2 | `StartupContentState` marker on the content states of Home, Auth, Favorites, History, Search, Bookmarks and Collections (Favorites, History and Search also on `Empty`, a settled answer); `PuberVM.recordReady` marks `first_content` only for a marked state. Tests: Loading -> Error -> Content, an overlay VM ready before the landing content, and the real Home/Auth/UpdatePrompt state classes | 88ae5858 |
| 3 | `expired()` and the remaining hours also use recorded uptime (`elapsedMs - origin`), whichever bound comes first; test with a future `enabled_at` | 369226e1 |
| 4 | `expire()` clears the queue; `startOnWriter` drops queued events older than the new origin; two tests | 369226e1 |
| 5 | Export fold test uses one shared `Throwable` and three errors (`n` 1, 2); new test overflows the queue and asserts the `dropped` meta line in the archive | 27ca119a |
| 6 | New test: an `Act` and a `Ready` from `DialogVM` after a Nav to Home carry `scr=Dialog`, the trailing `meta stop` still says `Home` | 27ca119a |

Notes: states kept as one data class (ContentList, Settings) never change class, so they emit no `ready` and
`first_content` stays on the 60 s fallback for those landing tabs (row 11 of round 1, run
`telemetry-ready-data-states`). Rows 3 and 4 share a commit because both change the sink's session lifecycle.

### Review round 3 fixes

Triage-3 rows 1-3 (row 4 deferred to `telemetry-uptime-autooff`).

- Row 1: `SectionState.Content` implements `StartupContentState`, so a content-list startup tab
  records `first_content`. Deviation: `SectionState.Empty` is deliberately not marked. The
  paginator store starts in `Paginator.State.Empty` and replays it on `render` before the first
  load, so marking it stamped `first_content` before any data (the new section test failed on it).
  Tests: `StartupTabContentStateTest` (table over every `getStartupTabOptions()` entry incl.
  Cartoons/Anime) and `SectionVMTest.aSectionGoingFromLoadingToContentMarksFirstContent`.
- Row 2: `drainQueue` drops events with `t < originElapsedMs`; test
  `aStalledProducerEventOlderThanTheOriginIsDroppedWhenDrained`. Existing fixtures that recorded
  events at `t` below a 1000 ms clock now start their fake clock at 0 or use `clock.elapsed`.
- Row 3: the auto-off queue test restarts without moving the clock; it fails when the queue clear
  in `expire()` is removed (checked by mutation).
- Row 5: `encodingAndFileIoRunOnlyOnWriterThread` and `recordReturnsWhileStoreIsBlocked` use
  `FakeTelemetryClock(elapsed = 0)` so their nav events pass the pre-origin filter, and assert nav lines are written.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

### Readiness 1 - telemetry-core

Judge: final-judge, mode readiness, round 1. Date 2026-10-15.
Read against worktree `/home/user1/orca/workspaces/Puber/telemetry-core`, HEAD `e636e081` (base `39903d31`
+ spec `29b795a9` + plan). The worktree is clean. Inputs: spec (status approved), plan, `state.md`
(latest decision: minified device checks use `devBenchmarkRelease`, and `deploy` is unminified), AGENTS.md,
`.flow/roles/final-judge.md`.

#### Verdict: READY

No blocking defects. Every AC maps to a plan task and to Test design rows. Every citation I sampled points
to the symbol the plan names. Each task leaves a compilable tree, and the tasks are ordered by dependency.
The plan stays inside Out of scope, and none of the open questions blocks. The notes at the end are for the
implementer and reviewers. They are not preconditions.

#### 1. AC coverage (AC -> task -> tests)

| AC | Plan task(s) | Test design rows |
|---|---|---|
| AC-1 | 5 (fresh install), 9 (rows, indicator) | T01, T02, T03, T04, T05 (instr.), D2 |
| AC-1a | 5 (export via controller), 8 (paths XML), 9 (share UI) | T07, T08-T11, T12, T13, D4 |
| AC-2 | 3 (sink flag), 5 (off start), 6 (VM probes) | T15, T16, T17, T18 (P2), D1 |
| AC-3 | 1 (encoder), 2 (store), 3 (writer) | T19, T20, T21, T22, T23, D3 |
| AC-4 | 3 (drain auto-off), 5 (start auto-off) | T25-T28 |
| AC-5 | 2 (folders), 3 (caps, rotation, budget) | T29-T34 |
| AC-6 | 3 (writer thread) | T35, T36 |
| AC-7 | 1 (scrubber, names, encoder), 4 (err pipeline) | T38, T39, T40, T41, D3 audit |
| AC-8 | 1 (names), 8 (R8 rules) | T43, T44, T45, D5 on `devBenchmarkRelease` |
| AC-9 | 5 (startup marks), 6 (ready/act), 7 (nav), 2 (act folding) | T47-T53, D6 |
| AC-10 | 4 (tree, handler), 6 (coroutine path), 8 (auth probe, narrowed strip rule), 2/3 (fold, cap) | T55-T62, D7 |
| AC-10a | 5 (runtime enable/disable, prefs) | T65-T67, D8 |
| Static/regression | 6 (S1 grep, S3 doc), 9/10 (S4), 10 (S2) | S1-S4 |

The test seams from the Test design are all present as plan requirements: `TelemetryClock` with `FakeTelemetryClock`,
`TelemetryStore` with `FakeTelemetryStore` (thread names, throw, latch, unavailable), `TelemetryExecutor` with
`ManualTelemetryExecutor`, `Telemetry.install`/`resetForTests`, `TelemetryRuntime(prefs, clock, sink)` on
`FakeSharedPreferences` (Task 5 extends it with `getLong`/`putLong`), a test-local concrete `PuberVM`, and
`FakeSessionLogController`.

#### 2. Citation sample (opened in the worktree)

All of these resolve to the symbol the plan claims:

- `core/ui/PuberVM.kt:30` (class, `router`-only ctor), `:46` (`started`), `:74-80` (reified overload delegating), `:82-84` (`updateViewState`), `:97-99` (`DefaultExceptionHandler`), `:135-140` (`open fun onAction`, snackbar body).
- All 19 override pairs in Task 6 (for example `HomeVM.kt:176,197`, `PlayerVM.kt:632,678`, `CollectionsVM.kt:37,38`, `DeviceSettingsVM.kt:135,175`). `grep` finds no `onAction` override in `test/` or `androidTest/`, and `PagingVM` does not override it.
- `core/logger/Logger.kt:7` (message overload) and `:11-19` (`Timber.tag(...).w(sanitizedThrowable, ...)`). The WARN-level tree therefore sees it. `CoroutinesErrorHandler.kt:10-14` (`t.log(t)`).
- `core/logger/SensitiveRequestLogPolicy.kt:40` (`internal class`), `:106` (`sanitizeText`), `:116-120` (`sanitizeThrowable`), `:257-272` (`private class SanitizedLogThrowable`).
- `app/proguard-rules.pro:15` (`InnerClasses`), `:90` (`-keepnames` PuberScreen), `:94`, `:130-132` (`LoggerKt.log(...)` strip). The Timber strip at `:123-127` covers only static `d/v/i`, so `Tree.w` survives.
- `app/build.gradle.kts:183-184` (release minify/shrink), `:202` (`deploy` `isMinifyEnabled = false`, which confirms the state.md correction), `:212-215` (`benchmarkRelease`, `initWith(release)`, debug signing), `:223` (dev `.stage` suffix shared by every build type, so the installs keep the same package), `:386` (kotlinx-serialization-json).
- `PuberApp.kt:67-72` (`onCreate`), `:77-83` (modules), `:88-92` (`initLogger`). `MainActivity.kt:54-56`.
- `FlowComponent.kt:225-226` and the `if/else` ending `:268`; `:708-709` and the end `:746`. `TabComponent.kt:107`; `TabRouter.kt:32`; `AppRouter.kt:156-168` (`Command`).
- `KinoPubApiClient.kt:194-202`, `:224-227` (`sessionExpiredFailure`).
- `res/xml/update_file_paths.xml:3-5`; `AndroidManifest.xml:66-74`.
- `DeviceSettingsContent.kt:83-90`, `:176-183`, `:185-187` (Developer filter), `:197-204`, `:244-255`, `:338-345`, `:373-384`, `:403-411`, `:465`, `:762-770`. `DeviceSettingsInformationItems.kt:147-158`. `DeviceSettingsItems.kt:80-92` (`SettingsListItem` with `role`, `enabled`, `focusableWhenDisabled`) and `:207-231` (`SettingsToggleItem` with `description`). `DeviceSettingsScreen.kt:36, 48-54`. `DeviceSettingsViewState.kt:10-15, 22-47`. `DeviceSettingsActions.kt:8-39`. `DeviceSettingsVM.kt:39-51` (internal class, so internal ctor types are legal), `:53`, `:66-69`, `:71-88`, `:99-125`. The file has 522 lines.
- `ContentSharer.kt:6-23`, `AppUpdateInstaller.kt:38-42`, `data/di/modules.kt:81`, `core/ui/uikit/model/Actions.kt:5`, `AppLocale.kt:21, 35-38, 72-73` (the plan's correction of the spec's `:307` is right: the file has 74 lines), `PuberTab.kt:19, 37`, `PuberScreen.kt:7-10`, `HomeVM.kt:71`, `HomeViewState.kt:19`, `docs/architecture/viewmodel.md:18,22,45`, `values/device_settings_strings.xml:17`, `values-en/strings.xml:99`, `TimedActionDebugTrace.kt:15-17`, `buildSrc/.../Versions.kt:6` (MinSdk 24).
- Test files: `FakeResourceProvider.kt:8-16`, `FakeSharedPreferences` init block, `KinoPubApiClientFailureTest.kt:37-54, 112-139`, `EpisodeSchedulePrivacyTest.kt:26-33`, `DeviceSettingsVMAutoTrailerTest.kt:89-103`, `DeviceSettingsContentFocusTest.kt` (612 lines, `:239`, `:348`).

#### 3. Build stays green, order

- Tasks 1 -> 2 -> 3 -> 4 -> 5 build strictly on what the earlier tasks produced. Task 6 needs `Telemetry`, `Act`, `Ready` and `StartupMarks` (Tasks 1, 3, 5). Task 7 needs only Tasks 1 and 3. Task 8 needs Tasks 1 and 3. Task 9 needs the controller (Tasks 3 and 5) and the `handleAction` rename (Task 6). Task 10 is the full gate.
- Each task ends with the fast gate plus the touched tests and one commit. Task 6 makes `onAction` final, renames all 19 overrides and runs the whole unit suite in the same commit, so the tree is never half-renamed. `onAction` stays public, so callers and tests compile unchanged.
- The new `DeviceSettingsContent`/`DeviceSettingsPane` parameters have defaults, so the existing androidTest call sites still compile. Task 9 compiles `compileDevDebugAndroidTestKotlin`.
- Unit tests have no Android stubs (no `isReturnDefaultValues`). The plan keeps `android.*` off the paths that tests reach: `JvmTelemetryClock` is the default, and `Log.WARN` is a compile-time constant.

#### 4. Scope and rules

- Nothing from Out of scope (`key`, `key_lat`, `frames`, `mem`, `net*`, `play`, exit reason, summary tool, gzip) appears in the plan. The splash-frame mark is explicitly skipped, as the spec allows.
- AGENTS.md rules are respected. Builds use `./tools/agentw` and never `./gradlew`. Tests are JUnit 5 with `MainDispatcherExtension` and `FakeResourceProvider`. Strings go in `res/values*` and the VM reads them through `ResourceProvider`. Koin gets a global `telemetryModule` in `PuberApp`, and `puberViewModel`/`viewModelOf` are untouched. Commits have no prefixes and no Co-Authored-By. Device work is handed to qa-device (lease, `-s`, install-preserve), and instrumented tests run only through `make DEVICE=... itest`.
- State.md decisions are followed: the minified checks use `devBenchmarkRelease`, `deploy` is used only for the non-debug Developer section, and Q1-Q3 are respected (existing row styles only, `-keepnames` with `key` unchanged, rotate-then-zip closed files with the previous archive replaced).
- The plan's deviations from the spec are stated and justified: `telemetryStartupContentClass` is `internal` rather than `protected`, and `shareFile` takes a `File` rather than a `Uri`.

#### Notes (non-blocking, for implementer and reviewers)

1. Task 5 calls `executor.schedule(60_000)` "through a sink method", but Task 3 does not list such a method in Produces. Add something like `TelemetrySink.schedule(delayMs, task)` in Task 3 or 5 and record it.
2. `HomeVM.telemetryStartupContentClass`: prefer a getter (`get() = HomeViewState.Content::class.java`) over a backing field. A subclass field is still null while `PuberVM` code runs before `HomeVM` initialisers.
3. `Unavailable` keeps the switch off while `enabled_at_wall_ms` stays set, so a tap re-calls `setEnabled(true)`. Make sure `enable` never plants a second tree or wraps a second handler. The plan's "safe to call twice" covers this, and T65/T66 should assert a single tree.
4. In minified builds `ErrorScrubber`'s prefix filter does not drop library frames that R8 has renamed, and app frames are obfuscated (no retrace). D7 should report what `frames`/`et` actually look like in `devBenchmarkRelease` rather than claim "app frames only". AC-10 evidence must state this.
5. Task 1's "Done when" grep (`else ->` in `TelemetryEncoder.kt`) is file-wide. Scope it to the event `when`, or keep the other `when`s in that file free of `else`.
6. The spec's own Decisions table (spec line ~837) still lists the superseded "AC-8 on `deploy`" row without marking it superseded, and the spec's Baseline section calls `deploy` "R8 + resource shrinking". The conductor should sync the spec text with state.md when the docs are next touched. The plan's correction is already authoritative.
7. Device evidence: installing `devBenchmarkRelease` over `devDebug` is a same-package, same-signer `install -r`. Any owner-authorised install must be named in the gate Concerns.

#### Fix list

None.

### Verify round 1 fixes (AC-8, minified build)

- Defect 1 (`act.a` showed `CommonAction$ItemSelected`): `TelemetryNames.plain` now derives the name from `Class.name` (`simpleNameOf`: text after the last `.` and the last `$`) instead of `Class.simpleName`, which depends on the InnerClasses entry R8 may drop. Unit test `simpleNameIsDerivedFromTheBinaryName`. mapping.txt of devBenchmarkRelease shows the action classes keep their binary names (identity mapping).
- Defect 2 (`nav.from` obfuscated for `LoadingTab`): added `-keepnames class * implements cafe.adriel.voyager.navigator.tab.Tab` to `app/proguard-rules.pro`. mapping.txt maps `LoadingTab -> LoadingTab`.
- Static only; device re-check is QA's.
