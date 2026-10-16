# Telemetry frames, keys and memory: JankStats windows, key bursts and latency, memory samples - plan

**Spec:** `docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md`
**Goal:** Add four opt-in probe families to the S1 session log sink: per-screen `frames` windows from
JankStats, one `key` line per press or burst with an allow-listed key name, a `key_lat` line for slow
key-to-frame answers, and `mem` samples once per foreground minute plus on trim, all free while the log is off.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/telemetry-frames-keys-memory` (branch
  `telemetry-frames-keys-memory`, base `cd16eb47`, spec commit `10e7fa19`). Never `./gradlew` there; every
  build goes through `./tools/agentw`, run as `cd <wt> && AGENT_GRADLE_SERIAL=1 ./tools/agentw <task>`.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate:
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests per task: `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn or package.*>'`.
- One task = green fast gate + green tests of the touched code + one commit. Commit message: one imperative
  English sentence, no prefixes, never a `Co-Authored-By` line (each task suggests one).
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing
  else. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/`,
  `config/` or `gradle/`. Every `path:line` below was opened at `10e7fa19`.
- Test-first: in every task the listed tests are written first, the touched test class is run and seen red
  (a compile failure of the not-yet-existing symbol counts as red for new classes), then the code. Kind is
  feature: there is no failing-first bug test.
- Unit tests: JUnit 5 under `app/src/test/kotlin/com/kino/puber/core/telemetry/` (unless named otherwise),
  fakes from `app/src/test/kotlin/com/kino/puber/util/`. **No Android stubs at runtime**
  (`app/build.gradle.kts` sets no `unitTests.isReturnDefaultValues`): code the JVM tests reach must not call an
  `android.*` method. `KeyEvent.KEYCODE_*`, `KeyEvent.ACTION_*` and `ComponentCallbacks2.TRIM_MEMORY_*` are
  compile-time constants and are fine. mockk only for an `Activity` token, never for the class under test. No
  `Thread.sleep`; real-thread tests use `CountDownLatch.await(5, SECONDS)`.
- Global state reset in `@AfterEach` of every class that installs a sink, sets `Telemetry.screen` or touches
  `KeyProbe`: `Telemetry.resetForTests()` (also clears `screen` from Task 3 on) and `KeyProbe.resetForTests()`
  (from Task 6 on). Template: `TelemetryRuntimeTest.tearDown` (`app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryRuntimeTest.kt:38-43`).
- Test rules from the spec's Test design (apply to every test): (1) every "nothing / no line" assertion has an
  "on" control with the same setup; (2) fixture values that tell the right answer from the wrong one; (3) every
  test that reads `t` from written lines asserts `t >= 0` (and `t <= now` for back-dated windows); (4) global
  reset as above; (5) assert emitted events, written lines and calls on injected collaborators, never private
  fields.
- Off means free (AC-S2-OFF): every probe path starts with a single `Telemetry.isEnabled` check; nothing runs,
  is scheduled or registered before it. The JankStats callback and `FrameAggregator.onFrame` allocate nothing,
  format nothing and call no `Telemetry` method except `emit` when a window closes (AC-12, TH3).
- Probes put only primitives, enums and `Class` references in an event (no `String`: the existing guard
  `TelemetrySinkTest.eventsHoldNoStringBuiltByTheProbe`,
  `app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetrySinkTest.kt:133-141`, checks every sealed member).
  Never a raw key code (AC-11a).
- detekt (`config/detekt/detekt.yml`): `MagicNumber` is active in main code (`:680-683`), so every threshold
  is a named `const`; `ReturnCount` max 3 (`:756-759`); `CyclomaticComplexMethod` 14 with
  `ignoreSingleWhenExpression: true` (`:90-93`), so `keyNameOf` must be one `when` expression;
  `SwallowedException` is active (`:260`), so swallowing a probe failure uses `runCatching { }`, not
  `catch (_: Throwable)`. No new entries in `config/detekt/detekt-baseline.xml`.
- Hot files: `MainActivity.kt` gets one method; `PuberApp.kt`, `core/ui/navigation/component/FlowComponent.kt`,
  `core/ui/navigation/component/TabComponent.kt` and `core/telemetry/di/TelemetryModule.kt` are not edited.
- Dependency versions only in `gradle/libs.versions.toml` (AGENTS.md).
- Clocks: `Telemetry.nowMs()` is `SystemClock.elapsedRealtime()` in production (`core/telemetry/TelemetryClock.kt:19-21`)
  and the sink drops any event with `t` before its session origin (`core/telemetry/TelemetrySink.kt:186`). Frame
  times (`FrameData.frameStartNanos`) are `System.nanoTime()` base; `KeyEvent.eventTime` is `uptimeMillis`. They
  are only ever subtracted within one base, except `lat_ms` (eventTime vs frame end), whose base agreement is
  the device check D7.

### Wire schema (S2 additions)

Envelope unchanged (`t`, `sid`, `type`, `scr`). For the four new types `scr` is the event's **own** screen
(`names.owner(event.screen)`, `""` when null), never the writer's `lastScr`.

| type | cap/min | fields after the envelope (whole ms or KB, floor) |
|---|---|---|
| `frames` | 12 | `frames`, `janky`, `p50`, `p90`, `p99`, `max` (UI-thread work per frame only, `FrameData.frameDurationUiNanos`; not GPU or present time, same meaning on every API level) |
| `key` | 120 | `key` (wire name below), `n` (DOWN events in the burst, >= 1), `dur_ms` |
| `key_lat` | 60 | `key`, `lat_ms` (key `eventTime` to end of UI work of the first frame that started after dispatch), `frame_ms` (that frame's UI duration) |
| `mem` | 10 | `java_kb`, `java_max_kb`, `native_kb`, then `pss_kb` on periodic lines only, or `trim` (the level) on trim lines only |

Key wire names (`KeyName`, the whole allow-list; every other code is `other`):

| `KeyEvent` code(s) | enum | wire |
|---|---|---|
| `KEYCODE_DPAD_UP` 19, `_DOWN` 20, `_LEFT` 21, `_RIGHT` 22, `_CENTER` 23 | `DPAD_UP` .. `DPAD_CENTER` | `dpad_up`, `dpad_down`, `dpad_left`, `dpad_right`, `dpad_center` |
| `KEYCODE_ENTER` 66, `KEYCODE_NUMPAD_ENTER` 160 | `ENTER` | `enter` |
| `KEYCODE_BACK` 4 | `BACK` | `back` |
| `KEYCODE_MENU` 82 | `MENU` | `menu` |
| `KEYCODE_MEDIA_PLAY_PAUSE` 85, `_STOP` 86, `_NEXT` 87, `_PREVIOUS` 88, `_REWIND` 89, `_FAST_FORWARD` 90, `_PLAY` 126, `_PAUSE` 127 | `MEDIA_*` | `play_pause`, `stop`, `next`, `previous`, `rewind`, `fast_forward`, `play`, `pause` |
| `KEYCODE_CHANNEL_UP` 166, `_DOWN` 167 | `CHANNEL_UP/DOWN` | `channel_up`, `channel_down` |
| `KEYCODE_PAGE_UP` 92, `_DOWN` 93 | `PAGE_UP/DOWN` | `page_up`, `page_down` |
| `KEYCODE_MOVE_HOME` 122, `_END` 123 | `MOVE_HOME/END` | `move_home`, `move_end` |
| anything else | `OTHER` | `other` |

### Refinements of the spec's design (decided here; reviewers check against these)

1. **Back-dated `frames.t` clamp (spec gap 2, owner decision "clamped to the session origin").** The aggregator
   does not know the sink origin, and the sink silently drops events before it (`TelemetrySink.kt:186`). So the
   aggregator records `openedMs = nowMs()` when a window gets its first frame and emits
   `t = max(nowMs() - (nanoTime() - lastEndNs) / 1_000_000, openedMs)`. A frame is only accepted while
   `Telemetry.isEnabled`, which the sink sets after taking its origin (`TelemetrySink.kt:63-68`), so
   `openedMs >= origin` and the written `t >= 0`. One clock read per window, none per frame.
2. **`KeyLatencyMatcher` holds one `AtomicReference<Pending?>`** (an immutable `Pending(eventMs, dispatchNs,
   name, screen)`, allocated once per armed press on main) instead of two `AtomicLong`s plus volatiles (D9).
   Same behaviour, but arm (main) and answer (FrameMetrics thread) can never mix fields of two presses (C13).
   An armed entry older than 1 000 ms is replaced by the next arm, so a press that no frame answered cannot
   block later ones (C9 "refused until expiry").
3. **`MemorySampler.sample(withPss: Boolean): MemReading`** (readings only). The probe adds `t`, `screen` and
   `trim` and records the event, so M1/M4 check production code, not the fake. The fake still logs
   `(withPss, thread name)`.
4. **`dispatchWithProbe` also takes the dispatch time** (`probe: (handled, dispatchNs) -> Unit`, `nanoTime`
   read inside, before `super`, only when enabled), so `MainActivity.dispatchKeyEvent` is one expression.
5. **`keyNameOf` lives next to `KeyName` in `TelemetryEvent.kt`** (Task 2 needs it for AC-11a), not in
   `KeyProbe.kt`.
6. **Event `t`** for `key` and `key_lat` is `nowMs()` at emit; for `mem` the sample time on the writer thread.
7. **The field note** for "frame time is UI-thread work only" (spec Open question 2) is the KDoc on
   `TelemetryEvent.Frames` plus the table above; no new doc file.

---

## Tasks

### Task 1: Add the metrics-performance dependency and the JankStats tracker seam

**Covers:** AC-S2-R8 (S1, S3), risk R7 (dependency resolution, API shape D2)
**Files:**
- Modify: `gradle/libs.versions.toml` - add `metricsPerformance = "1.0.0"` at the end of `[versions]`
  (after `kover` at `:36`); add `androidx-metrics-performance = { group = "androidx.metrics", name =
  "metrics-performance", version.ref = "metricsPerformance" }` under `[libraries]` (`:50`).
- Modify: `app/build.gradle.kts` - `implementation(libs.androidx.metrics.performance)` with a
  `// Frame metrics (session log)` comment, next to Timber (`implementation(libs.timber)` at `:408`).
- Create: `core/telemetry/FrameTracker.kt`.

**Consumes:** nothing.
**Produces** (`core/telemetry/FrameTracker.kt`):
```kotlin
internal fun interface FrameListener { fun onFrame(startNs: Long, uiNs: Long, isJank: Boolean) }
internal interface FrameTracker { var isTrackingEnabled: Boolean }
internal fun interface FrameTrackerFactory { fun create(activity: Activity, listener: FrameListener): FrameTracker }
/** Main thread only. Reads frameStartNanos, frameDurationUiNanos, isJank; never FrameData.states. */
internal object JankStatsTrackerFactory : FrameTrackerFactory
```
`JankStatsTrackerFactory.create` calls `JankStats.createAndTrack(activity.window) { data ->
listener.onFrame(data.frameStartNanos, data.frameDurationUiNanos, data.isJank) }` and wraps the returned
`JankStats` so `isTrackingEnabled` reads and writes `jankStats.isTrackingEnabled`. The listener lambda is
created once per `create` call (per resume), not per frame.

- [x] Add the catalogue entries and the `implementation` line.
- [x] Create `FrameTracker.kt` as above.
- [x] Run the fast gate. It downloads the artifact (not in `~/.gradle-agents`). If resolution fails, stop and
      report Blocked; do not change the version. If the API differs from the expected 1.0.0 shape
      (`createAndTrack(Window, OnFrameListener)`, `OnFrameListener.onFrame(FrameData)`,
      `FrameData.frameStartNanos/frameDurationUiNanos/isJank`, `var isTrackingEnabled`), adapt only this file
      and write the real signatures under "Deviations from the plan".
- [x] `rg -n "metrics" gradle/libs.versions.toml app/build.gradle.kts` shows one version, one alias, one
      `implementation`, no hardcoded version in `app/build.gradle.kts` (S3).
- [x] Commit: "Add the metrics-performance dependency behind a frame tracker seam".

**Done when:** the fast gate is green with the new dependency resolved, and the S3 grep shows exactly three hits.

---

### Task 2: Add the frames, key, key_lat and mem event types with the key-name allow-list

**Covers:** AC-11a (N1-N5), AC-11..AC-14 caps (X1, X2, X3), AC-14 field sets (M11), AC-S2-OFF (L14)
**Files:**
- Modify: `core/telemetry/TelemetryEvent.kt`
  - caps consts next to `ERR_CAP` (`:10`): `FRAMES_CAP = 12`, `KEY_CAP = 120`, `KEY_LAT_CAP = 60`, `MEM_CAP = 10`;
  - `TelemetryEventType` (`:13-21`): `FRAMES("frames")`, `KEY("key")`, `KEY_LAT("key_lat")`, `MEM("mem")`
    after `ERR`, before `META`;
  - `internal enum class KeyName(val wire: String)` with the entries of the table above, and
    `internal fun keyNameOf(code: Int): KeyName = when (code) { KeyEvent.KEYCODE_DPAD_UP -> KeyName.DPAD_UP ... else -> KeyName.OTHER }`
    (one `when` expression, `import android.view.KeyEvent`);
  - `internal sealed interface ScreenScoped { val screen: Class<*>? }` (top level);
  - four members inside `TelemetryEvent` (`:54-115`), each `: TelemetryEvent, ScreenScoped`:
    `Frames(t, screen, frames: Int, janky: Int, p50: Int, p90: Int, p99: Int, maxMs: Int)` (KDoc: the field
    note, see Refinement 7), `Key(t, screen, key: KeyName, n: Int, durMs: Long)`,
    `KeyLat(t, screen, key: KeyName, latMs: Long, frameMs: Long)`,
    `Mem(t, screen, javaKb: Long, javaMaxKb: Long, nativeKb: Long, pssKb: Long?, trim: Int?)`.
- Modify: `core/telemetry/TelemetryEncoder.kt` - four branches in the exhaustive `when` (`:37-92`), writing
  exactly the fields of the wire table (`max` from `maxMs`; `key` from `event.key.wire`; `mem` puts `pss_kb`
  only when non-null and `trim` only when non-null).
- Test: `TelemetryEncoderTest.kt` - add `FRAMES`, `KEY`, `KEY_LAT`, `MEM` (periodic: `pssKb` set, `trim` null)
  rows to `samples` (`app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryEncoderTest.kt:25-53`); the
  `@EnumSource` test at `:55-64` then demands exact key sets (X3). Add `memTrimLineCarriesTrimAndNoPss` (exact
  key set `envelope + {java_kb, java_max_kb, native_kb, trim}`) (M11).
- Test (new): `KeyNameTest.kt` - N1 `everyLetterDigitAndNumpadCodeMapsToOther` (`@ParameterizedTest` over
  `KEYCODE_A..Z`, `KEYCODE_0..9`, `KEYCODE_NUMPAD_0..9`, `KEYCODE_SPACE`, `KEYCODE_DEL`, `KEYCODE_UNKNOWN`, `0`,
  `9999`, `-1`), N2 `allowListIsExactlyTheDocumentedCodes` (codes `0..400`, explicit expected map of the table),
  N3 `encodedKeyAndKeyLatLinesNeverContainTheRawCode` (sentinels `n=1`, `durMs=100000`, `latMs=77777`,
  `frameMs=88888`; no JSON value equals the code as number or string; `key == "other"`), N4
  `wireNamesAreUniqueAndNeverASingleLetterOrDigit`, N5 `eventsCarryTheEnumNotAnInt` (reflection: no `Int`
  constructor parameter named `code`/`keyCode` on `Key`, `KeyLat`).
- Test: `TelemetryCapsTest.kt` (`app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryCapsTest.kt`) -
  X1 `newTypesHaveTheDeclaredPerMinuteCaps` (parameterised; cap+1 events in one wall minute through
  `SinkHarness` leave cap lines and one `meta dropped` with `counts[<wire>] == 1`), X2
  `keyLatFloodIsCountedNotLostSilentlyAndWindowResets` (200 `KeyLat`: 60 lines, `{"key_lat":140}`; after
  `clock.advance(60_000)` the next one is written).
- Test: `TelemetrySinkTest.kt` - L14 `memoryAndFrameTypesAreCountedAsDisabledNoOps`, same shape as
  `disabledRecordNeverReachesEncoderOrStore` (`TelemetrySinkTest.kt:23-33`) for the four new types (plus the
  existing `nav` as the control that the harness does write once started).

**Consumes:** `SinkHarness` (`app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryTestSupport.kt:18-39`),
`HomeScreen`/`ScreenScreen` (`.../TestScreens.kt:19,21`).
**Produces:** the four event classes, `ScreenScoped`, `KeyName`, `keyNameOf(code: Int): KeyName`, the caps.

- [x] Write the tests above; run `--tests 'com.kino.puber.core.telemetry.*'`: red (compile).
- [x] Implement the model and the encoder branches.
- [x] Run the fast gate and `--tests 'com.kino.puber.core.telemetry.*'`; commit:
      "Add frame, key, key latency and memory event types with an allow-listed key name".

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*'` is green,
including N1-N5, X1, X2, M11 and the four new `@EnumSource` cases.

---

### Task 3: Give screen-scoped events their own scr and keep the current screen in a holder

**Covers:** AC-12 (W1-W5, F12)
**Files:**
- Modify: `core/telemetry/TelemetryWriter.kt` - in `append` (`:109-127`) add `is ScreenScoped ->
  names.owner(event.screen)` to the `when` at `:110-115`, after the `Act` branch and before `else -> lastScr`.
  `lastScr` stays set only by `Nav` (`:126`). `names.owner(null)` returns `""` (`core/telemetry/TelemetryNames.kt:11-14`).
- Modify: `core/telemetry/Telemetry.kt` - `@Volatile internal var screen: Class<*>? = null` in `object
  Telemetry` (`:19-58`), KDoc "the screen on top while recording; written only by `recordNav`, reset on probe
  detach"; `resetForTests` (`:52-57`) sets it to `null`.
- Modify: `core/ui/navigation/component/NavTelemetry.kt` - in `recordNav` (`:21-24`), after the existing
  `isEnabled` return: compute `telemetryClassOf(to)` once (`core/telemetry/TelemetryNames.kt:43-47`, a tab
  becomes its content screen), use it for the event, and `if (cmd != NavCmd.ACTIVITY) Telemetry.screen = it`.
- Test (new): `TelemetryWriterScrTest.kt` through `SinkHarness` - W1 `framesKeepTheirOwnScreenAfterALaterNav`
  (`Nav(to = ScreenScreen)` then `Frames(screen = HomeScreen)`: `scr == "Home"`; control: an `Act` with
  `HomeVM` still writes `Home`), W2 `keyKeyLatAndMemKeepTheirOwnScreen` (parameterised over the four types),
  W3 `screenScopedNullScreenWritesEmptyNotLastNav`, W4 `screenScopedEventsDoNotMoveLastScreen` (a `meta`
  written after `Frames(screen = HomeScreen)` still has the last `nav.to`; produce it with a `dropped` meta by
  exceeding a cap, or with a rotate), W5 `navStillSetsLastScreenAndOldTypesUnchanged`.
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt` - F12
  `recordNavSetsScreenHolderWhenOnAndNeverWhenOffOrActivity` (on: `PUSH` to `other` sets `ScreenScreen`, as the
  first assertion; a `TAB` to `tabOf(home)` sets `HomeScreen`; an `ACTIVITY` command leaves it unchanged; off
  (`recorder.isEnabled = false`, as `:71-77`): the holder stays `null`). The class already resets in `:37-39`.

**Consumes:** Task 2 events.
**Produces:** `Telemetry.screen`; writer rule "screen-scoped events carry their own `scr`".

- [x] Write W1-W5 and F12; run them: red.
- [x] Implement the three edits.
- [x] Run the fast gate and `--tests 'com.kino.puber.core.telemetry.*' --tests 'com.kino.puber.core.ui.navigation.NavTelemetryTest'`;
      commit: "Write the own screen on screen-scoped telemetry lines and track the current screen".

**Done when:** W1-W5 and F12 are green and the existing `TelemetrySinkTest.actAndReadyCarryTheScreenOfTheirOwnViewModelNotTheLastNavigation` still passes.

---

### Task 4: Aggregate frames into ten-second per-screen windows

**Covers:** AC-12 (F1-F11)
**Files:**
- Create: `core/telemetry/FrameAggregator.kt`.
- Modify (test util): `app/src/test/kotlin/com/kino/puber/core/telemetry/TestScreens.kt` - add
  `internal class HistoryScreen : TestPuberScreen()` next to `ScreenScreen` (`:21`) (three screens for F6).
- Test (new): `FrameAggregatorTest.kt`, `FrameAggregatorThroughSinkTest.kt`.

**Consumes:** `TelemetryEvent.Frames` (Task 2), `TelemetryEncoder` (for F11).
**Produces:**
```kotlin
internal class FrameAggregator(
    private val emit: (TelemetryEvent.Frames) -> Unit = Telemetry::record,
    private val nowMs: () -> Long = Telemetry::nowMs,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    @Synchronized fun onFrame(startNs: Long, uiNs: Long, isJank: Boolean, screen: Class<*>?)
    @Synchronized fun close()
}
```
Behaviour (D10 plus Refinement 1):
- State: `IntArray(101)` histogram (bucket `= (uiNs / 1_000_000).coerceIn(0, 100)`; 100 holds `>= 100 ms`),
  `frames`, `janky`, `maxNs`, `windowStartNs`, `lastEndNs`, `openedMs`, `screen`. Constants: `BUCKETS = 101`,
  `WINDOW_NS = 10_000_000_000L`, `NS_PER_MS = 1_000_000L`.
- `onFrame`: if `frames > 0` and (`screen !== this.screen` or `startNs - windowStartNs >= WINDOW_NS`) close
  first. If `frames == 0` (fresh window): `windowStartNs = startNs`, `openedMs = nowMs()`, `this.screen =
  screen`. Then add: bucket++, `frames++`, `if (isJank) janky++`, `maxNs = max`, `lastEndNs = startNs + uiNs`.
  Only field and array writes; no allocation, no string, no `Telemetry` call.
- `close()`: nothing if `frames == 0`. Percentile `p` (50, 90, 99): rank `= max(1, (frames * p + 99) / 100)`;
  walk the histogram cumulatively; the first bucket index whose sum reaches rank is the value; a percentile that
  lands in bucket 100 reports `maxNs / NS_PER_MS`. `max = (maxNs / NS_PER_MS).toInt()` (floor, spec gap 1).
  `t = max(nowMs() - (nanoTime() - lastEndNs) / NS_PER_MS, openedMs)`. Emit one `Frames`, then zero the
  histogram (`fill(0)`) and counters in place.

Tests (the spec's Traceability, AC-12):
- F1 `percentilesAndExactMaxOnCraftedFeed` (90 x 8 ms, 9 x 20 ms, 1 x 250 ms, 12 janky: `frames=100, janky=12,
  p50=8, p90=8, p99=20, max=250`).
- F2 `percentileInOverflowBucketReportsExactMaxAndSubMsFloors` (98 x 8 ms + 150 + 300: `p99 == max == 300`;
  a window of only 8 900 000 ns frames: `p50 == 8`, `max == 8`).
- F3 `windowRollsOverAtTenSecondsInclusive` (+9 999 999 999 ns joins; +10 000 000 000 ns closes; the closed
  event excludes it, the next window has `frames=1` after `close()`).
- F4 `secondWindowStartsFromZeroedHistogram`; F5 `closeEmitsOpenWindowOnceAndEmptyWindowEmitsNothing`;
  F6 `screenChangeClosesWindowWithTheOldScreen` (`HomeScreen` x5, `HistoryScreen` x1, then `ScreenScreen`:
  two events, `===` on `Class`, 5/1, third window open); F7 `nullScreenIsAWindowOfItsOwn`;
  F8 `idleGapBackDatesTToTheLastFrameNotTheClose` (fake `nowMs`/`nanoTime` advanced together; 30 s idle;
  `event.t == T`, `t < now`).
- F9 `FrameAggregatorThroughSinkTest.backDatedWindowNeverPrecedesTheSessionOrigin`: `SinkHarness` with
  `clock.elapsed = 5_000` before `start()` (origin 5 000), `Telemetry.install(h.sink, h.clock)`, aggregator with
  production `emit`/`nowMs` and a fake `nanoTime`; one frame whose end is 10 s before "now"; `close()`; drain:
  exactly one `frames` line exists, its `t >= 0` and `t <= h.clock.elapsed - 5_000`. (Without the clamp the
  sink drops the event and the test fails on "exactly one line".)
- F10 `thousandFramesInsideAWindowCallEmitNeverAndAllocateNothing`: 1 000 frames, `emit` count 0;
  `com.sun.management.ThreadMXBean.getThreadAllocatedBytes` delta over 200 000 `onFrame` calls below 64 KB,
  with a negative control in the same test (a loop allocating one `Pair` per iteration exceeds the bound);
  `Assumptions.assumeTrue` when the bean or allocation measurement is unavailable.
- F11 `listenerShapedCallsMatchGoldenEncodedLine`: the F1 window through `TelemetryEncoder` has exactly
  envelope + `{frames, janky, p50, p90, p99, max}` with the F1 numbers.

- [x] Write the tests; run `--tests 'com.kino.puber.core.telemetry.FrameAggregator*'`: red.
- [x] Implement `FrameAggregator`.
- [x] Run the fast gate and the tests; commit: "Aggregate frame durations into per-screen ten-second windows".

**Done when:** F1-F11 are green.

---

### Task 5: Fold key presses into bursts and match keys to their answering frame

**Covers:** AC-11 (K5-K11, K13), AC-13 (C1-C13), AC-S2-NODIST (TH2)
**Files:**
- Create: `core/telemetry/KeyProbe.kt` (this task: `KeyBurstMachine`, `KeyLatencyMatcher`; Task 6 adds the
  `KeyProbe` object and `dispatchWithProbe` to the same file).
- Test (new): `KeyBurstMachineTest.kt`, `KeyBurstThroughSinkTest.kt`, `KeyLatencyMatcherTest.kt`,
  `KeyLatencyMatcherConcurrencyTest.kt`, `KeyThreadingTest.kt`.

**Consumes:** `TelemetryEvent.Key`, `TelemetryEvent.KeyLat`, `KeyName`, `keyNameOf` (Task 2).
**Produces:**
```kotlin
/** Main thread only. */
internal class KeyBurstMachine(
    private val emit: (TelemetryEvent.Key) -> Unit = Telemetry::record,
    private val nowMs: () -> Long = Telemetry::nowMs,
) {
    fun onKey(action: Int, keyCode: Int, repeat: Int, eventMs: Long, screen: Class<*>?)
    fun flush()
    fun reset()
}

internal class KeyLatencyMatcher(private val nowMs: () -> Long = Telemetry::nowMs) {
    /** Main thread. */
    fun arm(eventMs: Long, dispatchNs: Long, name: KeyName, screen: Class<*>?, handled: Boolean)
    /** FrameMetrics thread. No allocation unless a line is emitted. */
    fun onFrame(startNs: Long, uiNs: Long, emit: (TelemetryEvent.KeyLat) -> Unit)
    fun clear()
}
```
`KeyBurstMachine` (D8; spec gaps 4): primitive fields `open: Boolean`, `code: Int`, `name: KeyName`, `screen`,
`startMs`, `lastMs`, `n`. Const `IDLE_GAP_MS = 2_000L`. On every `onKey`: if `open` and
`eventMs - lastMs > IDLE_GAP_MS`, close with `durMs = lastMs - startMs`. Then:
- `ACTION_DOWN`, `repeat == 0`: close any open burst (`durMs = lastMs - startMs`), open `n = 1`,
  `startMs = lastMs = eventMs`, `name = keyNameOf(keyCode)`, `screen = screen`.
- `ACTION_DOWN`, `repeat > 0`: same code and open: `n++`, `lastMs = eventMs`; otherwise close any open burst and
  open a new one with `n = 1` (never `n = 0`, never throws).
- `ACTION_UP`: same code and open: emit with `durMs = eventMs - startMs`, `open = false`; other code or nothing
  open: ignored.
- Other actions: ignored. `flush()`: emit the open burst (`durMs = lastMs - startMs`) once. Emit =
  `emit(TelemetryEvent.Key(nowMs(), screen, name, n, durMs))`, the only allocation, once per burst.

`KeyLatencyMatcher` (D9 plus Refinement 2): `private class Pending(val eventMs: Long, val dispatchNs: Long,
val name: KeyName, val screen: Class<*>?)`, `private val pending = AtomicReference<Pending?>(null)`. Consts
`SLOW_MS = 50L`, `EXPIRY_NS = 1_000_000_000L`.
- `arm`: return unless `handled`; `val cur = pending.get()`; return if `cur != null && dispatchNs -
  cur.dispatchNs <= EXPIRY_NS`; `pending.compareAndSet(cur, Pending(...))`.
- `onFrame`: `val p = pending.get() ?: return`; return if `startNs < p.dispatchNs`; if `startNs - p.dispatchNs >
  EXPIRY_NS` then `pending.compareAndSet(p, null)` and return; return unless `pending.compareAndSet(p, null)`;
  `lat = (startNs + uiNs) / 1_000_000 - p.eventMs`; if `lat > SLOW_MS` emit `KeyLat(nowMs(), p.screen, p.name,
  lat, uiNs / 1_000_000)`. A non-positive or small `lat` is consumed silently (C11). Respect `ReturnCount`
  max 3: split into a small private helper if needed.
- `clear()`: `pending.set(null)`. Repeats never arm (the caller arms only `DOWN` with `repeat == 0`, Task 6;
  the matcher test C5 feeds arm calls the way `KeyProbe` will).

Tests (spec Traceability AC-11, AC-13): K5 `longPressEmitsOneKeyWithCountAndDurationOnUp` (nothing emitted
before the UP; one `Key(DPAD_DOWN, n=10, durMs=up-start)` after), K6 `threeSinglePressesEmitThreeKeysWithNOne`
(durations 40, 90, 15), K7 `differentKeyClosesOpenBurstWithDurationFromLastRepeat`, K8
`idleGapAboveTwoSecondsEmitsOnNextEventAndBoundaryDoesNot`, K9 `flushEmitsOpenBurstOnceAndEmptyFlushEmitsNothing`,
K10 `repeatWithoutOpenBurstOpensOneWithNOneAndNeverThrows`, K11 `upOfAKeyThatIsNotOpenIsIgnored`, K12
`KeyBurstThroughSinkTest.heldDpadWritesOneKeyLineWithFields` (`SinkHarness`, `Telemetry.install(h.sink,
h.clock)`, machine with production emit: one `key` line, exact field set envelope + `{key, n, dur_ms}`,
`key == "dpad_down"`, `n == 10`, `t >= 0`; control: same feed before `h.start()` writes no `key` line), K13
`repeatsAfterTheFirstDoNotAllocate` (`ThreadMXBean` as F10, 100 000 repeats, negative control). C1-C12 as named
in the spec's AC-13 table (C1 `answerOverFiftyMsEmitsLatAndFrameMs`, C2 `fiftyIsNotSlowAndFiftyOneIs`, C3
`latencyStartsAtEventTimeNotAtDispatch`, C4 `frameStartingBeforeDispatchDoesNotAnswer`, C5
`heldKeyArmsOnceAndMeasuresFromTheFirstDown`, C6 `secondDownWhilePendingDoesNotReplaceTheEarliest`, C7
`unhandledKeyNeverArms`, C8 `pendingExpiresAfterOneSecondAndLateFrameEmitsNothing`, C9
`noFrameMeansNoLine` (a second arm within 1 s is refused; one after 1 s is accepted), C10
`answeredEntryIsConsumedAndNotEmittedTwice`, C11 `negativeLatencyFromAClockBaseMismatchIsNeverEmitted`, C12
`lineCarriesTheArmedScreenNotTheScreenAtAnswer`); C13
`KeyLatencyMatcherConcurrencyTest.armOnOneThreadAnswerOnAnotherNeverDoubleEmits` (10 000 rounds, latch,
emitted `<=` armed, no exception); TH2 `KeyThreadingTest.keyEmitAndLatencyEmitDoNoIoOnTheCallerThread` (real
`ThreadTelemetryExecutor` sink as in `TelemetrySinkTest.encodingAndFileIoRunOnlyOnWriterThread`,
`TelemetrySinkTest.kt:95-117`; burst closed on the test thread, `KeyLat` emitted from a thread named
`FrameMetricsHandler`; every `store.calls` thread is `ThreadTelemetryExecutor.THREAD_NAME`).

- [x] Write the tests; run `--tests 'com.kino.puber.core.telemetry.Key*'`: red.
- [x] Implement both classes.
- [x] Run the fast gate and the tests; commit: "Fold key presses into bursts and match each key to its answering frame".

**Done when:** K5-K13, C1-C13 and TH2 are green.

---

### Task 6: Hook the key probe into MainActivity behind one flag check

**Covers:** AC-11 / risk R2 (K1-K4), AC-S2-OFF (L13)
**Files:**
- Modify: `core/telemetry/KeyProbe.kt` - add:
```kotlin
/** All logic of MainActivity.dispatchKeyEvent. Returns exactly what superDispatch returns. */
internal inline fun dispatchWithProbe(
    enabled: Boolean,
    superDispatch: () -> Boolean,
    probe: (handled: Boolean, dispatchNs: Long) -> Unit,
): Boolean {
    if (!enabled) return superDispatch()
    val dispatchNs = System.nanoTime()
    val handled = superDispatch()
    runCatching { probe(handled, dispatchNs) }
    return handled
}

internal object KeyProbe {
    val matcher = KeyLatencyMatcher()
    private val bursts = KeyBurstMachine()
    /** Main thread, only while recording. */
    fun onKey(action: Int, keyCode: Int, repeat: Int, eventMs: Long, dispatchNs: Long, handled: Boolean)
    fun flush()   // bursts.flush()
    fun detach()  // bursts.flush(); matcher.clear()
    @VisibleForTesting fun resetForTests()  // bursts.reset(); matcher.clear()
}
```
  `onKey` reads `Telemetry.screen` once, calls `bursts.onKey(action, keyCode, repeat, eventMs, screen)`, and
  only for `action == KeyEvent.ACTION_DOWN && repeat == 0` calls `matcher.arm(eventMs, dispatchNs,
  keyNameOf(keyCode), screen, handled)`.
- Modify: `MainActivity.kt` - add (class at `:27`, imports `:3-25`; `onCreate` at `:42-61` unchanged):
```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean =
    dispatchWithProbe(Telemetry.isEnabled, { super.dispatchKeyEvent(event) }) { handled, dispatchNs ->
        KeyProbe.onKey(event.action, event.keyCode, event.repeatCount, event.eventTime, dispatchNs, handled)
    }
```
  (`import android.view.KeyEvent`, `com.kino.puber.core.telemetry.KeyProbe`,
  `com.kino.puber.core.telemetry.dispatchWithProbe`; `Telemetry` is already imported at `:12`). This is the
  D-pad path: Compose focus and `AppRemoteHotkeyHandler` (`core/ui/navigation/component/FlowComponent.kt:141-146`)
  depend on the returned value being exactly `super`'s.
- Test (new): `DispatchWithProbeTest.kt` - K1 `offCallsSuperOnceAndNeverTheProbe`, K2
  `onReturnsSupersValueAndProbesAfterSuper` (call log `[super, probe]`, `handled` equals super's value, for
  `true` and `false`), K3 `probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch`, K4
  `superThatThrowsPropagatesAndProbeIsNotCalled`.
- Test (new): `KeyProbeOffTest.kt` - L13 `offDispatchNeverTouchesTheMachineOrMatcher`: 50 key events (DOWN,
  repeats, UP) through `dispatchWithProbe(enabled = false, ...)` with the probe lambda calling `KeyProbe.onKey`;
  then a `RecordingTelemetryRecorder` (`app/src/test/kotlin/com/kino/puber/util/RecordingTelemetryRecorder.kt:6-16`)
  installed and enabled, `KeyProbe.flush()` and `KeyProbe.matcher.onFrame(<slow frame>, Telemetry::record)`
  record nothing; control: the same feed with `enabled = true` records `Key` and `KeyLat` events. `@AfterEach`:
  `KeyProbe.resetForTests()`, `Telemetry.resetForTests()`.

**Consumes:** Task 5 classes, `Telemetry.screen` (Task 3), `Telemetry.isEnabled` (`core/telemetry/Telemetry.kt:33`).
**Produces:** `KeyProbe.flush`, `KeyProbe.detach`, `KeyProbe.matcher` (used by Tasks 7 and 9).

- [x] Write K1-K4 and L13; run them: red.
- [x] Implement `dispatchWithProbe`, `KeyProbe`, the `MainActivity` override.
- [x] Run the fast gate and `--tests 'com.kino.puber.core.telemetry.*'`; commit:
      "Record key bursts and key latency from MainActivity while the session log is on".

**Done when:** K1-K4 and L13 are green, and `git diff cd16eb47 -- app/src/main/java/com/kino/puber/MainActivity.kt`
shows only the imports and the one override.

---

### Task 7: Feed JankStats frames to the key matcher and the frame aggregator

**Covers:** AC-12, AC-13 (FP1-FP6, C14), AC-S2-OFF (FP4, FP5), AC-S2-NODIST (TH1)
**Files:**
- Create: `core/telemetry/FrameProbe.kt`.
- Test (new): `FrameProbeTest.kt`, `FrameThreadingTest.kt`.

**Consumes:** `FrameTrackerFactory`/`FrameTracker`/`FrameListener` (Task 1), `FrameAggregator` (Task 4),
`KeyLatencyMatcher` (Task 5), `KeyProbe.matcher` (Task 6), `Telemetry.screen` (Task 3).
**Produces:**
```kotlin
/** start/stop on the main thread only. */
internal class FrameProbe(
    private val trackers: FrameTrackerFactory = JankStatsTrackerFactory,
    private val aggregator: FrameAggregator = FrameAggregator(),
    private val matcher: KeyLatencyMatcher = KeyProbe.matcher,
) {
    fun start(activity: Activity)  // no-op when a tracker is active (FP1)
    fun stop()                     // isTrackingEnabled = false, drop the tracker, aggregator.close(); safe twice
}
```
The listener is one field, created once: `FrameListener { startNs, uiNs, isJank -> if (!active ||
!Telemetry.isEnabled) return@FrameListener; matcher.onFrame(startNs, uiNs, emitKeyLat);
aggregator.onFrame(startNs, uiNs, isJank, Telemetry.screen) }`, with `@Volatile private var active` set `true`
in `start` and `false` in `stop` before closing, and `private val emitKeyLat: (TelemetryEvent.KeyLat) -> Unit =
Telemetry::record` (a field, so no allocation per frame). Holds no `Activity`.

Tests: FP1 `startCreatesOneTrackerAndStartTwiceDoesNotCreateASecond`, FP2
`stopDisablesTrackingClosesAggregatorAndReleasesTheTracker`, FP3
`recreatedActivityGetsANewTrackerAndTheOldOneStaysStopped`, FP4 `listenerWhileOffTouchesNeitherAggregatorNorMatcher`
(control: same call while on counts a frame), FP5 `lateFrameAfterStopDoesNotEmitOrArm`, FP6/C14
`listenerFeedsMatcherThenAggregatorWithTheCurrentScreenHolder` (`Telemetry.screen = HomeScreen::class.java`;
the closed window carries it; a slow armed key is answered by the same call that closes a window). The fake
factory records `create(activity, listener)` and exposes the listener; the `Activity` is `mockk<Activity>()`
(if mockk cannot instantiate `android.app.Activity` on the JVM, record it under Deviations and use
`mockk<Activity>(relaxed = true)`; do not change the seam type). Emits go through a
`RecordingTelemetryRecorder` installed in `Telemetry`. TH1 `FrameThreadingTest.noTelemetryIoOrEncodingOnTheCallbackThread`:
real `ThreadTelemetryExecutor` sink and `FakeTelemetryStore`, listener invoked from a thread named
`FrameMetricsHandler`, window closed by a screen change; every `store.calls` thread name is
`ThreadTelemetryExecutor.THREAD_NAME` and `FrameMetricsHandler` appears nowhere.

- [x] Write the tests; run `--tests 'com.kino.puber.core.telemetry.Frame*'`: red.
- [x] Implement `FrameProbe`.
- [x] Run the fast gate and the tests; commit: "Feed JankStats frames to the key matcher and the frame aggregator".

**Done when:** FP1-FP6 and TH1 are green.

---

### Task 8: Sample memory once per foreground minute and on trim, on the writer thread

**Covers:** AC-14 (M1-M10), AC-S2-NODIST (M7, M8)
**Files:**
- Create: `core/telemetry/MemoryProbe.kt`.
- Modify (test util): `app/src/test/kotlin/com/kino/puber/util/ManualTelemetryExecutor.kt` - keep the period of
  each repeating task (`scheduleRepeating` at `:26-30`, list at `:9`): store `(periodMs, task)` entries, close
  removes that entry; add `val repeatingPeriods: List<Long>` and `val repeatingCount: Int`; `runRepeating`
  (`:41-44`) unchanged in behaviour.
- Test (new): `MemoryProbeTest.kt`, `MemoryProbeThreadTest.kt`.

**Consumes:** `TelemetryExecutor` (`core/telemetry/TelemetryExecutor.kt:7-12`, `scheduleRepeating` returns
`AutoCloseable` at `:10`, `ThreadTelemetryExecutor.THREAD_NAME` at `:49`), `TelemetryEvent.Mem` (Task 2),
`Telemetry.screen` (Task 3).
**Produces:**
```kotlin
internal class MemReading(val javaKb: Long, val javaMaxKb: Long, val nativeKb: Long, val pssKb: Long?)
internal fun interface MemorySampler { fun sample(withPss: Boolean): MemReading }

/** Writer thread only. One reused Debug.MemoryInfo, created on first PSS sample. */
internal class DebugMemorySampler : MemorySampler
// java = Runtime totalMemory - freeMemory, max = maxMemory, native = Debug.getNativeHeapAllocatedSize(),
// pss = Debug.getMemoryInfo(info); info.totalPss (already KB). Bytes / 1024.

/** attach/detach/onForeground on main; onTrimMemory on main; sampling on the executor. */
internal class MemoryProbe(
    private val executor: TelemetryExecutor,
    private val sampler: MemorySampler,
    private val registerTrim: (ComponentCallbacks2) -> Unit,
    private val unregisterTrim: (ComponentCallbacks2) -> Unit,
    private val emit: (TelemetryEvent) -> Unit = Telemetry::record,
    private val nowMs: () -> Long = Telemetry::nowMs,
) : ComponentCallbacks2 {
    fun attach()                        // idempotent; registerTrim(this) once; attached = true
    fun onForeground(foreground: Boolean) // only while attached: true -> one scheduleRepeating(60_000), false -> close it
    fun detach()                        // idempotent; close timer, unregisterTrim(this), attached = false
    override fun onTrimMemory(level: Int) // attached and level != TRIM_MEMORY_UI_HIDDEN -> executor.execute { sample(false, level) }
}
```
Consts `SAMPLE_PERIOD_MS = 60_000L`, `BYTES_PER_KB = 1024L`. The sample task: `runCatching { val r =
sampler.sample(withPss); emit(TelemetryEvent.Mem(nowMs(), Telemetry.screen, r.javaKb, r.javaMaxKb, r.nativeKb,
if (withPss) r.pssKb else null, trim)) }` (`trim` null on periodic lines), so a throwing sampler never escapes,
also on `ManualTelemetryExecutor` which does not catch (M10). `onConfigurationChanged` and `onLowMemory` are
empty (`@Deprecated` override of `onLowMemory` is fine; add `@Suppress("OVERRIDE_DEPRECATION")` if the compiler
warns). The timer restarts on each foreground entry; a trim in the background is written (spec gap 3, owner
decision), a periodic line in the background is not.

Tests (spec AC-14 table): the probe gets its own `ManualTelemetryExecutor` (`memExec`); its events go into a
started `SinkHarness` via `Telemetry.install(h.sink, h.clock)`, so `memExec.runRepeating()` samples and
`h.drain()` writes. M1 `foregroundSchedulesOnePeriodicSampleEverySixtySeconds` (`repeatingPeriods ==
[60_000]`, one tick, one `mem` line with exactly `{java_kb, java_max_kb, native_kb, pss_kb}`, sampler saw
`withPss = true`), M2 `noPeriodicLineInTheBackgroundAndTimerIsCancelled`, M3 `reenteringForegroundKeepsOneTimerNotTwo`,
M4 `trimWritesOneExtraLineWithLevelAndNoPss` (level 10 -> `trim == 10`, no `pss_kb`), M5 `uiHiddenTrimIsIgnored`
(20 writes nothing; control 10 writes one), M6 `backgroundTrimLevelIsWrittenButPeriodicIsNot` (60), M7
`MemoryProbeThreadTest.samplerRunsOnlyOnTheWriterThread` (real `ThreadTelemetryExecutor`; `onTrimMemory` from
the test thread returns before the sampler runs; sampler sees `THREAD_NAME` and `isWriterThread()`; latch),
M8 `periodicSampleRunsOnTheWriterThreadNotOnTheCaller` (sampler not called during `onForeground(true)`;
`memExec.isWriterThread()` true inside), M9 `detachUnregistersTrimClosesTimerAndLaterTrimIsIgnored`
(register count 1 after two `attach`, unregister 1, `repeatingCount == 0`, trim after detach writes nothing,
`detach` twice safe), M10 `samplerExceptionDoesNotStopTheTimer`. `ManualTelemetryExecutor` changes must keep
every existing test green.

- [x] Write the tests and the executor seam; run `--tests 'com.kino.puber.core.telemetry.MemoryProbe*'`: red.
- [x] Implement `MemoryProbe.kt`.
- [x] Run the fast gate and `--tests 'com.kino.puber.core.telemetry.*'`; commit:
      "Sample memory once per foreground minute and on trim on the telemetry thread".

**Done when:** M1-M10 are green and the rest of `com.kino.puber.core.telemetry.*` still passes.

---

### Task 9: Plant the probes with the log and follow the activity lifecycle

**Covers:** AC-S2-OFF (L1-L12), AC-12 (F13), AC-14 (foreground), wiring for every AC on the device
**Files:**
- Create: `core/telemetry/TelemetryProbe.kt`:
```kotlin
/** Both idempotent; always called on the main thread (TelemetryRuntime posts them). */
internal interface TelemetryProbe { fun attach(); fun detach() }

internal class TelemetryActivityCallbacks(
    private val isEnabled: () -> Boolean,
    private val frameProbe: FrameProbe,
    private val keyFlush: () -> Unit,
    private val requestDrain: () -> Unit,
    private val memoryProbe: MemoryProbe,
) : Application.ActivityLifecycleCallbacks {
    var resumedActivity: Activity? = null; private set
    val isForeground: Boolean  // startedCount > 0
}

/** The one TelemetryProbe for the frame, key and memory probes. */
internal class ActivityProbes(
    private val callbacks: TelemetryActivityCallbacks,
    private val frameProbe: FrameProbe,
    private val memoryProbe: MemoryProbe,
    private val keyDetach: () -> Unit,
) : TelemetryProbe
```
  Callbacks (D12), always: `onActivityStarted` `startedCount++`; `onActivityStopped` `startedCount =
  (startedCount - 1).coerceAtLeast(0)`; `onActivityResumed` `resumedActivity = activity`; `onActivityPaused` and
  `onActivityDestroyed` clear `resumedActivity` if it is that activity. Only when `isEnabled()`: resumed ->
  `frameProbe.start(activity)`; paused -> `frameProbe.stop()`, `keyFlush()`, `requestDrain()` in that order;
  started/stopped -> `memoryProbe.onForeground(isForeground)`. `ActivityProbes.attach`:
  `callbacks.resumedActivity?.let(frameProbe::start)`, `memoryProbe.attach()`,
  `memoryProbe.onForeground(callbacks.isForeground)`. `detach`: `frameProbe.stop()`, `keyDetach()`,
  `memoryProbe.detach()`.
- Modify: `core/telemetry/TelemetryRuntime.kt`
  - constructor (`:23-27`): add `private val probes: List<TelemetryProbe> = emptyList()` and
    `private val mainPost: (Runnable) -> Unit = { it.run() }`; update the class KDoc (`:19-22`);
  - `plantProbes` (`:90-94`): `if (probes.isNotEmpty()) mainPost { probes.forEach(TelemetryProbe::attach) }`;
  - `detachProbes` (`:96-102`): `Telemetry.screen = null`, then
    `if (probes.isNotEmpty()) mainPost { probes.forEach(TelemetryProbe::detach) }`. It also runs on the writer
    thread through `onExpired` (`:85-88`, called at `core/telemetry/TelemetrySink.kt:223`) and `onStopped`
    (`TelemetrySink.kt:234`), so the probes are never touched inline;
  - `install` (`:109-124`): create one `ThreadTelemetryExecutor` (today inline at `:114`) and pass it to the sink
    and to `MemoryProbe(executor, DebugMemorySampler(), app::registerComponentCallbacks,
    app::unregisterComponentCallbacks)`; `val frameProbe = FrameProbe()`; `val callbacks =
    TelemetryActivityCallbacks({ Telemetry.isEnabled }, frameProbe, KeyProbe::flush, sink::requestDrain,
    memoryProbe)`; `val main = Handler(Looper.getMainLooper())`; `TelemetryRuntime(prefs, SystemTelemetryClock,
    sink, listOf(ActivityProbes(callbacks, frameProbe, memoryProbe, KeyProbe::detach))) { main.post(it) }`;
    register `callbacks` where `PauseFlushCallbacks` is registered today (`:123`);
  - delete `PauseFlushCallbacks` (`:143-154`); its `requestDrain` on pause moves into the new callbacks.
  - `PuberApp.kt` (`TelemetryRuntime.install(this, appCreateMs)`) and `core/telemetry/di/TelemetryModule.kt` stay
    unchanged.
- Test (new): `TelemetryRuntimeProbesTest.kt` (setup and teardown as `TelemetryRuntimeTest.kt:31-48`, plus a
  counting fake `TelemetryProbe` and a queueing `mainPost` = `mutableListOf<Runnable>()::add`, drained by the test):
  L1 `freshProcessWithLogOffNeverAttachesAnyProbe` (also `executor.useCount == 0`, `store.calls` empty), L2
  `enablingAttachesOnceOnTheMainThreadQueue`, L3 `disablingDetachesAndOnOffOnEndsAttached` (attach/detach 2/1,
  final attached), L4 `autoOffAfterSevenDaysAndStorageFailureAlsoDetach` (clock setup of
  `TelemetryRuntimeTest.autoOffEmitsMetaClosesFileClearsPrefUprootsAndStatusOff`, `:149-162`; and
  `store.available = false`), L5 `detachRaisedOnTheWriterThreadIsPostedNotRunInline`, L6
  `screenHolderIsNullAfterDetach`, F13 `reenableWritesEmptyScrUntilTheNextNav`, L7
  `offAfterOnWritesNothingFurtherAndOpenWindowIsDropped` (real `ActivityProbes` with a `FrameProbe` on a fake
  tracker factory; control: the same 20 frames closed while on produce a `frames` line).
- Test (new): `TelemetryActivityCallbacksTest.kt` (real `FrameProbe` on a fake factory, real `MemoryProbe` on a
  `ManualTelemetryExecutor`, `mockk<Activity>()` tokens, a mutable `enabled` flag): L8
  `resumeWhenOffDoesNotStartTrackingButTracksTheActivity`, L9 `enableWhileResumedStartsTrackingOnThatActivity`
  (through `ActivityProbes.attach`; with no resumed Activity no `create`, and the next resume creates one), L10
  `pauseStopsFrameFlushesKeysThenRequestsDrainInThatOrder` (call log `[frame.stop, keyFlush, requestDrain]` from
  the fake tracker's `isTrackingEnabled = false`, `KeyProbe::flush` wrapper and a drain wrapper; end to end with
  `SinkHarness`: the open window and the open key burst are in the store after the drain), L11
  `destroyAndPauseClearTheResumedActivity`, L12 `startedCountTreatsTwoActivitiesAsForegroundUntilBothStop`.
- Existing `TelemetryRuntimeTest` must stay green unchanged (defaults `probes = emptyList()`, inline `mainPost`).

**Consumes:** Tasks 3, 6, 7, 8.
**Produces:** the production wiring; nothing later depends on it.

- [x] Write the tests; run `--tests 'com.kino.puber.core.telemetry.TelemetryRuntime*' --tests 'com.kino.puber.core.telemetry.TelemetryActivityCallbacksTest'`: red.
- [x] Implement `TelemetryProbe.kt` and the `TelemetryRuntime` changes; remove `PauseFlushCallbacks`.
- [x] Run the fast gate and `--tests 'com.kino.puber.core.telemetry.*'`; commit:
      "Plant the frame, key and memory probes with the session log and follow the activity lifecycle".

**Done when:** L1-L12 and F13 are green, `TelemetryRuntimeTest` is green unchanged, and
`rg -n "PauseFlushCallbacks" app/src` finds nothing.

---

### Task 10: Full gate, static review checks and mutation spot-checks

**Covers:** AC-S2-NODIST (TH3), AC-S2-R8 (S2), risk R9 (vacuous tests), all ACs at the static level
**Files:** only what the full gate reports in files this plan touched; no detekt baseline entries.

- [x] `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; fix findings, rerun until green.
- [x] TH3: `rg -n 'Telemetry\.|\$\{|Debug\.|getMemoryInfo' app/src/main/java/com/kino/puber/core/telemetry/FrameAggregator.kt app/src/main/java/com/kino/puber/core/telemetry/FrameProbe.kt`
      shows no `Telemetry.` call inside `onFrame` or the listener other than `Telemetry.isEnabled`,
      `Telemetry.screen` and the `emit`/`emitKeyLat` field defaults, no string template, no `Debug.`;
      `rg -n "DebugMemorySampler" app/src/main` hits only `MemoryProbe.kt` and `TelemetryRuntime.install`.
      Record the result under "For the reviewers".
- [x] S2: `ProguardRulesTest` green; `app/proguard-rules.pro:90` (`-keepnames class * implements
      com.kino.puber.core.ui.navigation.PuberScreen`) unchanged; no new keep rule (D16).
- [x] Mutation spot-checks from the spec (each one-line change, run the named tests, see red, revert; never
      committed): off branch calls the probe (K1, L13); `dispatchWithProbe` returns `true` (K2); `KEYCODE_A` in
      the allow-list (N1, N2, N3); `ScreenScoped` writer branch removed (W1, W2); `lat_ms` from dispatch (C3);
      `>= 50` (C2); window close `>` instead of `>=` (F3); `t` not back-dated / not clamped (F8 / F9); second
      timer on `onForeground(true)` (M3); `UI_HIDDEN` not ignored (M5); `ActivityProbes.attach` skips the
      resumed Activity (L9); `detachProbes` runs probes inline (L5); `Telemetry.screen` not reset on detach (L6,
      F13). Record each mutation and the test that went red under "For the reviewers".
- [x] Commit only if something changed: "Fix static analysis findings in the frame, key and memory probes".

**Done when:** the full gate is green on the branch head and every mutation above turned its named test red.

---

### Handoff to verification (qa-device, not implementer work)

Device verification is required (D-pad input, startup wiring, build variants, runtime-only behaviour). Rules:
lease from `tools/emulator-resource-lock.sh`, `adb -s <serial>` on every call, the worktree's own APKs built with
`AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug` and `:app:assembleDevBenchmarkRelease`
(`benchmarkRelease` is minified and debug-signed, `app/build.gradle.kts:212-215`), installed with
`tools/android-apk-install-preserve`; no uninstall, data clear, downgrade or signer change; the account is
already paired; the toggle is switched through the D-pad (Settings > For developers > Session log); session files
are in `/sdcard/Android/data/<pkg>/files/telemetry/`. Leave the toggle off at the end and release the lease.
Evidence: `docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md`, line structure, counts
and `jq` summaries only, scanned with `tools/mobile-evidence-audit.sh` before commit; host load (`uptime`) next
to every relative number.

1. Device scenario D1-D12 exactly as the spec's "Manual / device scenario" section: D1 off directory and runtime
   on/off/on with the Activity resumed; **D2 gate**: at least one `frames` line after 25 s of scrolling Home, else
   stop and escalate (no code change); D3 frames per screen across a navigation; D4 memory (125 s foreground =
   two periodic lines, 130 s background = none, `am send-trim-memory <pkg> MODERATE` = one `trim` line); D5 D-pad
   A/B on, off, on (and against the base build `cd16eb47` APK if available); D6 key bursts; **D7 clock base**:
   every `lat_ms` positive and under 1 000, else escalate; D8 volume under caps; D9 no ANR or skipped-frame burst
   tied to a `mem` tick (counts only, no raw logcat); D10 Activity recreation (locale change) and background /
   foreground twice; D11 privacy scan after typing `qwertzuiop12345` in search; D12 `devBenchmarkRelease`: all
   four types with readable `scr`. The emulator renders in software: assert presence, order, field sets, cadence
   and `scr`; never judge jank %, `p*`/`max` or `key_lat` values.
2. Perf re-measure against `.flow/runs/telemetry-frames-keys-memory/baseline.md` (emulator-5554, `devDebug`):
   cold start `am start -W` TotalTime, 12 runs per block, blocks OFF, ON, OFF, ON back to back at host load
   below about 8 (discard blocks whose 1-minute load crosses 10), compared with baseline blocks C/D (OFF 1559 ms,
   ON 1594 ms). Pass: OFF within `max(5 %, 2 x SD)` of the baseline OFF; ON within the same bound of OFF;
   re-measure once under equal load before calling a fail. Frames: `dumpsys gfxinfo <pkg> reset`, Home script
   6 Down, 8 Right, 8 Left, 6 Up at 0.35 s gaps, 3 runs per state, gross check only (p50 within the baseline
   spread 150-200 ms, jank not worse by more than 1 point). PSS after 75 s idle on Home, at least 3 samples per
   state. Log volume: bytes per line and per active minute from a real session, times 60 min under the 20 MiB
   budget. PSS cost: a temporary, uncommitted local timing around one `DebugMemorySampler.sample(true)` on the
   writer thread; report the number (follow-up if above a few ms on a `low_ram` device).
3. MANUAL M1 (real TV, owner authorization needed, not a blocker): true jank, `key_lat` and PSS cost on hardware.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** (base `cd16eb47`, plan commit `07a0756c`)
- `7ea12829` Add the metrics-performance dependency behind a frame tracker seam (T1)
- `cc4541e4` Add frame, key, key latency and memory event types with an allow-listed key name (T2)
- `bac62913` Write the own screen on screen-scoped telemetry lines and track the current screen (T3)
- `6840e1e3` Aggregate frame durations into per-screen ten-second windows (T4)
- `ce88acde` Fold key presses into bursts and match each key to its answering frame (T5)
- `7c1cb6b1` Record key bursts and key latency from MainActivity while the session log is on (T6)
- `fd8cd701` Feed JankStats frames to the key matcher and the frame aggregator (T7)
- `83face31` Sample memory once per foreground minute and on trim on the telemetry thread (T8)
- `6df9a08e` Plant the frame, key and memory probes with the session log and follow the activity lifecycle (T9)
- `1766bd65` Fix static analysis findings in the frame, key and memory probes (T10)

**Deviations from the plan:**
- T1: `metrics-performance:1.0.0` resolved; the API matched the expected shape
  (`JankStats.createAndTrack(Window, OnFrameListener)`, `FrameData.frameStartNanos/frameDurationUiNanos/isJank`,
  `var isTrackingEnabled`). No change to the plan's seam.
- T6: lint reports `RestrictedApi` on `ComponentActivity.dispatchKeyEvent` (androidx.core's
  `ComponentActivity`, group-prefix restricted), both on the override and on `super`. `MainActivity` carries
  `@SuppressLint("RestrictedApi")` on that one method with a comment. This is one extra import and one
  annotation in `MainActivity.kt`.
- T10: detekt forced three structural changes with no behaviour change. `TelemetryEncoder.encode` is now an
  envelope plus one `when` that calls a small `put*` function per event type (CyclomaticComplexMethod and
  LongMethod). `KeyLatencyMatcher.onFrame` split into `onFrame` plus a private `answer` (ReturnCount).
  `FrameProbe`'s listener is `FrameListener(::onFrame)` over a private method instead of an inline lambda
  (detekt reported `matcher` as unused for the lambda form).
- Tests (all within the plan's intent): new test util `AllocationProbe.kt` (shared ThreadMXBean helper and
  negative-control loop for K13/F10); `FakeFrameTracker`/`FakeFrameTrackerFactory` and `RecordingMemorySampler`
  live in `FrameProbeTest.kt` and `MemoryProbeTest.kt` and are reused by the lifecycle tests; `FakeFrameTracker`
  can report when tracking is switched off, which L10 uses to pin the pause order.
- `FrameAggregatorTest.jankyFramesAreCounted`, `jankyCountOfSecondWindowIsItsOwn` and
  `backDatedTIsClampedToWhenTheWindowOpened` are additions; F1 checks percentiles and `max` only, the
  `janky=12` check is in F11 and `jankyFramesAreCounted`.
- Red-first: tasks 2, 4, 5, 6, 7 and 8 were run red (compile failure of the missing symbol). T3 and T9 were
  written together with their code and run green on the first run, so their red was not observed directly; the
  T10 mutation checks (below) show their tests (W1/W2, L5, L6, L9, F13) go red when the code is removed. T1 has
  no test (dependency seam).
- The test names L1-L14 that the plan maps to `TelemetryRuntimeProbesTest` and `TelemetryActivityCallbacksTest`
  carry the plan's names (`freshProcessWithLogOffNeverAttachesAnyProbe`, ...); the `ProguardRulesTest` was not
  changed and is green.

**Files touched outside the plan:** none in `app/src/main`. In `app/src/test`:
`core/telemetry/AllocationProbe.kt` (new helper), `TelemetrySinkTest.kt` (L14), `TelemetryCapsTest.kt` (X1, X2),
`TestScreens.kt` (`HistoryScreen`, planned), `util/ManualTelemetryExecutor.kt` (periods, planned).

**For the reviewers:**
- `MainActivity.dispatchKeyEvent` returns exactly `super`'s value on both branches; the probe runs after
  `super`, inside `runCatching`. Diff of `MainActivity.kt` is the imports, the `@SuppressLint` and one method.
- TH3 grep (`Telemetry\.|\${|Debug\.|getMemoryInfo` over `FrameAggregator.kt`, `FrameProbe.kt`): only
  `Telemetry.isEnabled` and `Telemetry.screen` in `FrameProbe.onFrame`, no string template, no `Debug.`.
  `DebugMemorySampler` appears only in `MemoryProbe.kt` and `TelemetryRuntime.install`. `app/proguard-rules.pro`
  is untouched, `ProguardRulesTest` green (S2).
- Full gate `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL on the
  branch head (`1766bd65`). No detekt baseline entry added.
- Mutation spot-checks (one-line change, named tests run, reverted, never committed). Every one went red:
  - off branch calls the probe: `DispatchWithProbeTest.offCallsSuperOnceAndNeverTheProbe` (the L13 test
    `KeyProbeOffTest` stayed green: its probe lambda is only reached via `dispatchWithProbe`, and the mutation
    passes `0L`, so K1 is the guard for this mutation);
  - `dispatchWithProbe` returns `true`: `onReturnsSupersValueAndProbesAfterSuper`,
    `probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch`;
  - `KEYCODE_A` allow-listed: `KeyNameTest.allowListIsExactlyTheDocumentedCodes`,
    `encodedKeyAndKeyLatLinesNeverContainTheRawCode`, `everyLetterDigitAndNumpadCodeMapsToOther`;
  - `ScreenScoped` writer branch removed: `TelemetryWriterScrTest.framesKeepTheirOwnScreenAfterALaterNav`,
    `keyKeyLatAndMemKeepTheirOwnScreen`, `screenScopedNullScreenWritesEmptyNotLastNav`,
    `screenScopedEventsDoNotMoveLastScreen`;
  - `lat_ms` from dispatch: `KeyLatencyMatcherTest.latencyStartsAtEventTimeNotAtDispatch` and three more;
  - `>= 50`: `fiftyIsNotSlowAndFiftyOneIs`;
  - window close `>` instead of `>=`: `FrameAggregatorTest.windowRollsOverAtTenSecondsInclusive`,
    `secondWindowStartsFromZeroedHistogram`;
  - `t` not back-dated: `idleGapBackDatesTToTheLastFrameNotTheClose`, `backDatedTIsClampedToWhenTheWindowOpened`;
  - `t` not clamped: `FrameAggregatorThroughSinkTest.backDatedWindowNeverPrecedesTheSessionOrigin`;
  - second timer on `onForeground(true)`: `MemoryProbeTest.reenteringForegroundKeepsOneTimerNotTwo`;
  - `UI_HIDDEN` not ignored: `uiHiddenTrimIsIgnored`;
  - `ActivityProbes.attach` skips the resumed Activity:
    `TelemetryActivityCallbacksTest.enableWhileResumedStartsTrackingOnThatActivity`;
  - `detachProbes` runs probes inline: `TelemetryRuntimeProbesTest.detachRaisedOnTheWriterThreadIsPostedNotRunInline`;
  - `Telemetry.screen` not reset on detach: `screenHolderIsNullAfterDetach`, `reenableWritesEmptyScrUntilTheNextNav`.
- Readiness note 2 (accepted, no test): if the log is switched off and on again before the posted detach runs,
  a window opened in the old session can close with a `t` before the new origin and the sink drops it. That
  loses a line; it never mislabels one.
- `mockk<Activity>()` works on the JVM here (no relaxed mode needed).
- Device-only unknowns stay for qa-device: clock base of `KeyEvent.eventTime` vs `frameStartNanos` (D7),
  whether JankStats reports frames on the software-GPU emulator (D2 gate), `send-trim-memory MODERATE`
  delivery on API 36, R8 (`devBenchmarkRelease`), PSS cost.

**Blocked:** none.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

Judge: final-judge, mode readiness, round 1, 2026-10-16.
Inputs read: spec `docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md` (ACs, Out of scope,
D1-D16, Test design, Open questions, Decisions), plan `docs/superpowers/plans/2026-10-16-telemetry-frames-keys-memory.md`
(untracked in the worktree at HEAD `10e7fa19`), run files `brief.md`, `design-architect.md`, `test-design.md`
(via the spec), `baseline.md`, `state.md`, `research.md: 5`. Code checked in the worktree; `git diff cd16eb47 10e7fa19`
touches only the spec, so the plan's line numbers are the base code.

**Verdict: READY**

#### 1. Every AC has a plan task and Test design rows

| AC | Plan task(s) | Test design rows carried by the plan | Device |
|---|---|---|---|
| AC-11 key bursts | T5 (machine), T6 (hook) | K1-K13 | D5, D6 |
| AC-11a allow-list | T2 | N1-N5 | D11 |
| AC-12 frame windows | T3 (own `scr`, holder), T4 (aggregator), T7 (glue), T9 (F13) | F1-F13, W1-W5, FP6 | D2 gate, D3 |
| AC-13 key_lat | T5 (matcher), T7 (feed) | C1-C14, X2 | D7 (clock base) |
| AC-14 mem | T8, T2 (M11), T9 (foreground wiring) | M1-M11 | D4 |
| AC-S2-OFF | T2 (L14), T6 (L13), T7 (FP4, FP5), T9 (L1-L12) | L1-L14, FP1-FP5 | D1 |
| AC-S2-NODIST | T5 (TH2), T7 (TH1), T8 (M7, M8), T10 (TH3) | TH1-TH3, M7, M8, F10, K13 | D9 |
| AC-S2-R8 | T1 (S1, S3), T10 (S2) | S1-S3 | D12 |

Every spec test id (K, N, F, W, C, M, L, FP, TH, X, S) appears in a task. Renames accepted: F13 lives in
`TelemetryRuntimeProbesTest` instead of `TelemetryTest`; C14 is merged into FP6; X3 is the existing
`TelemetryEncoderTest.everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` `@EnumSource` test (the spec's
`TelemetryEventTypeTest` does not exist; `requireNotNull(samples[type])` and the required `capPerMinute`
constructor argument give the same guard). Mutation spot-checks (T10) and the perf re-measure against
`baseline.md` blocks C/D (handoff 2) are carried.

#### 2. Citations sampled (all resolve to the claimed symbol)

| Citation | Found |
|---|---|
| `core/telemetry/TelemetrySink.kt:186` | `if (event.t >= originElapsedMs) writer.write(event)` in `drainQueue` |
| `TelemetrySink.kt:63-68` | `start`: origin taken (`:65`) before `isEnabled = true` (`:66`) |
| `TelemetrySink.kt:223,234` | `onExpired()` and `onStopped()` on the writer thread |
| `core/telemetry/TelemetryWriter.kt:110-115`, `:126` | `val scr = when (event)` with `else -> lastScr`; `lastScr` set only for `Nav` |
| `core/telemetry/TelemetryNames.kt:11-14`, `:43-47` | `owner(null)` returns `""`; `telemetryClassOf` maps a tab to `contentScreenClass` |
| `core/telemetry/TelemetryEvent.kt:10`, `:13-21`, `:54` | `ERR_CAP`; `TelemetryEventType`; `sealed interface TelemetryEvent` |
| `core/telemetry/TelemetryEncoder.kt:37` | exhaustive `when (event)` |
| `core/telemetry/Telemetry.kt:19-58`, `:33`, `:52-57` | `object Telemetry`, `isEnabled`, `resetForTests` |
| `core/telemetry/TelemetryClock.kt:19-21` | `SystemTelemetryClock.elapsedMs = SystemClock.elapsedRealtime()` |
| `core/telemetry/TelemetryRuntime.kt:23-27`, `:85-88`, `:90-94`, `:96-102`, `:114`, `:123`, `:143-154` | constructor, `onExpired`, `plantProbes`, `detachProbes`, inline `ThreadTelemetryExecutor()`, `registerActivityLifecycleCallbacks(PauseFlushCallbacks(sink))`, `PauseFlushCallbacks` |
| `core/telemetry/TelemetryExecutor.kt:10`, `:49` | `scheduleRepeating(...): AutoCloseable`; `THREAD_NAME = "puber-telemetry"` |
| `core/ui/navigation/component/NavTelemetry.kt:21-24` | `recordNav` with the `isEnabled` return; `NavCmd.ACTIVITY` and `NavCmd.TAB` exist (`TelemetryEvent.kt:31-32`) |
| `MainActivity.kt:12`, `:27`, `:42-61` | `Telemetry` import, class, `onCreate`; no key override today |
| `core/ui/navigation/component/FlowComponent.kt:141-146` | `.onKeyEvent { remoteKeyHandler?.invoke(...) ?: false }` |
| `app/build.gradle.kts:212-215`, `:408` | `benchmarkRelease` debug-signed; `implementation(libs.timber)` |
| `gradle/libs.versions.toml:7`, `:36`, `:50` | `composeBom`, `kover`, `[libraries]` |
| `app/proguard-rules.pro:90` | `-keepnames class * implements ...PuberScreen` |
| `config/detekt/detekt.yml` `:90-93`, `:260`, `:680-683`, `:756-759` | CyclomaticComplexMethod 14 + `ignoreSingleWhenExpression`; SwallowedException; MagicNumber; ReturnCount 3 |
| test `TelemetryEncoderTest.kt:25-53`, `:55-64` | `samples` map; `@EnumSource` field-set test |
| test `TelemetrySinkTest.kt:23-33`, `:95-117`, `:133-141`, `:188` | disabled no-op; writer-thread IO test; `eventsHoldNoStringBuiltByTheProbe`; `actAndReady...` |
| test `TelemetryRuntimeTest.kt:38-43`, `:149-162` | `tearDown`; `autoOffEmitsMetaClosesFileClearsPrefUprootsAndStatusOff` |
| test `NavTelemetryTest.kt:37-39`, `:71-77` | `tearDown`; `nothingIsRecordedWhenOff` |
| test util `ManualTelemetryExecutor.kt:9`, `:26-30`, `:41-44`; `RecordingTelemetryRecorder.kt:6-16`; `TelemetryTestSupport.kt:18-39`; `TestScreens.kt:19,21` | as claimed (`useCount` exists; `inWriter` does not catch, as M10 assumes) |

No wrong citation found.

#### 3. Build stays green, order follows dependencies

- T1 dependency + seam alone; resolution failure stops the run as Blocked (no version fallback). T2 adds the
  enum entries, sealed members, encoder branches and the `@EnumSource` samples in one commit, so the exhaustive
  `when` and the parameterised test never go red between commits. The writer `when` has an `else`, so T2 compiles
  before T3 adds the `ScreenScoped` branch. No other exhaustive `when` over `TelemetryEvent` or
  `TelemetryEventType` exists in `app/src/main` (grep).
- Consumes/produces chain is acyclic: T2 -> T3 -> T4 -> T5 -> T6 -> T7 (needs `KeyProbe.matcher`) -> T8 -> T9
  (needs T3, T6, T7, T8) -> T10. T6 lands the `MainActivity` hook before the lifecycle wiring; harmless in
  between (bursts close on UP, the matcher just stays armed).
- `TelemetryRuntime` keeps default `probes = emptyList()` / inline `mainPost`, so `TelemetryRuntimeTest:47` and
  `SessionLogControllerTest:40` compile unchanged. `PauseFlushCallbacks` has no other reference.
- Constructor shapes line up across tasks (`Frames`, `Key`, `KeyLat`, `Mem` used in T4, T5, T8 match T2).
  `LongParameterList` is inactive in detekt; `warningsAsErrors = false`.

#### 4. Scope and open questions

- Out of scope respected: no `slow_frame`, no `mem` on screen change, no sub-50 ms key answers, no image-cache
  size, no `frameDurationTotalNanos`; `PuberApp.kt`, `FlowComponent.kt`, `TabComponent.kt`, `TelemetryModule.kt`,
  `proguard-rules.pro` untouched.
- Q1-Q3 and architect OQ1-OQ2 are answered (spec Decisions); none blocks.

#### 5. The seven plan refinements against the approved ACs

1. `frames.t` clamped to the window's first-frame time (`openedMs`): stricter than the owner's "clamped to the
   session origin" and implies it (`openedMs >= origin` because frames enter only while `isEnabled`, set after
   the origin at `TelemetrySink.kt:65-66`). F9 fails without the clamp. Within AC-12.
2. One `AtomicReference<Pending?>`: same semantics as D9 (C1-C12 unchanged), fixes the field mixing C13 tests,
   and replacing an expired entry matches spec row C9 ("refused until expiry/answer"). One allocation per armed
   press on main, not per frame. Within AC-13 and AC-S2-NODIST.
3. `MemorySampler.sample(withPss): MemReading`: the probe builds the event, so M1/M4 test production code. M4
   still checks `withPss = false`. Within AC-14.
4. `dispatchWithProbe` reads `nanoTime` before `super` only when on: equals D7's `startNs`; the off path is still
   one flag check (AC-S2-OFF, K1). K2-K4 still hold.
5. `keyNameOf` in `TelemetryEvent.kt`: file placement only; `KEYCODE_*` are inlined constants, so the JVM test
   loads no Android class. Within AC-11a.
6. Event-time rules (`key`/`key_lat` at emit, `mem` at sample): no AC fixes these times; all are after the origin.
7. Frame-time note as KDoc on `Frames` plus the wire table: answers OQ2 ("say so in the spec and the field note").

None widens or narrows an AC.

#### Must fix

None.

#### Notes for the implementer and the gate (not blocking)

1. AC-12's "no `Telemetry` call except on window close" is read, as D11 and TH3 already do, as no
   `record`/`nowMs`/formatting per frame; the listener's `Telemetry.isEnabled` and `Telemetry.screen` reads and the
   `KeyLat` emit required by AC-13 are the accepted exceptions. The gate will hold the implementation to that
   list and nothing more.
2. Refinement 1 edge: if the log is switched off and on again before the posted detach runs, a window opened in
   the old session could close with `t` before the new origin and be dropped by the sink. That loses a line; it
   never mislabels one. Worth one sentence under "For the reviewers", no test needed.
3. From API 34, `onTrimMemory` only delivers `UI_HIDDEN` and `BACKGROUND` to apps, so on newer TVs trim lines
   will be background-only in practice. D4's `am send-trim-memory MODERATE` on the API 36 emulator should still
   deliver the level; if it does not, record it and do not treat it as a code defect.
4. `mainPost { ... }` invoked on a `(Runnable) -> Unit` value may need an explicit `Runnable { }`; the compile
   gate settles it.
5. T1 depends on downloading `metrics-performance:1.0.0` (not in `~/.gradle-agents`); D2's API shape is
   unverified until then. A failed download is Blocked, not a reason to change the version.

### Fix round 1 (triage-1)

- #1 `FlowComponent.kt`: the flow runner now records the navigator that Back or FinishFlow actually changes
  (`navigatorChangedBy`), for both `from` and the new top, so a pop that falls through to a parent no longer
  labels the screen just left. The tab runner is unchanged (its Back and FinishFlow never reach a parent).
  Test: `NavigatorChangedByTest`.
- #2 `NavTelemetry.kt`, `TabComponent.kt`: `TabHostTelemetry.content` mirrors the tab host's current tab
  content class (set on every `TabCommand.Open`); `recordNav` names that class when the screen is `MainScreen`,
  falling back to `MainScreen` while no tab was opened yet. Process-global by design: one tab host exists at a
  time and `MainVM` reopens the tab on every host creation. Tests in `NavTelemetryTest`.
- #3 (UI toggle only) `TelemetryRuntime.setEnabled(false)` detaches the probes inline, before `sink.stop()`,
  when on the main thread (new `isMainThread` constructor argument, default true, real Looper check in
  `install`); off the main thread it keeps the posted detach. Expiry and storage-failure paths unchanged
  (deferred run). Deviation: L7 `offAfterOnWritesNothingFurtherAndOpenWindowIsDropped` pinned the old behaviour
  (the open window was dropped); it is renamed `offAfterOnFlushesTheOpenWindowAndWritesNothingFurther` and now
  expects the open window written once and nothing after. The spec's OFF row still describes the old wording;
  the spec was left untouched.
- #6 `KeyProbeGateTest` (new): repeats, UP and unhandled DOWN never produce a `KeyLat`, a handled first DOWN
  does; verified red with the `repeat == 0` gate removed.
- #7, #8 `TelemetryActivityCallbacksTest`, `MemoryProbeTest`: memory timer from `attach` when already in the
  foreground; `scr == Home` on frames, key and mem lines. #9 `java_max_kb`, `native_kb` asserted.
- #10 `KeyProbeOffTest`: recorder installed before the off feed, feed ends on an open DOWN (control counts 11
  `Key` lines).
- #4, #5, #11 not handled here (rejected, deferred, qa).
- Reviewers: the inline detach in `setEnabled(false)` runs under `@Synchronized` on the main thread; probes
  only touch main-thread state and `Telemetry.record`.

### Fix round 3 (triage-2, rows #1-#6)

- #1 `TelemetryRuntime.kt`: the posted attach returns early when the sink is off, and the detach is now always
  posted (also after the inline flush; it is idempotent), so it trails a queued attach. The `probesDetached`
  parameter is gone. Test `toggleOffBeforeTheQueuedAttachRunsLeavesNoProbeAttached` (red without the check).
  `CountingProbe` now tracks attached state instead of comparing counts; the storage-failure test expects the
  queued attach to be skipped (0 attaches, 1 detach).
- #2, #3 `TabHostTelemetry` is now a binding `(host instance, top provider)`. `TabFlowNavigator` binds the live
  tab navigator's `lastItemOrNull` in a `DisposableEffect` and unbinds on dispose. `MainScreen` implements the
  core marker `TabHostScreen` and provides itself as `LocalTabHostScreen`; `navClassOf` uses the binding only
  when it was made for that very host instance, else the fallback class. The `TabCommand.Open` assignment is
  removed; `TabRootScreen` became internal with `contentScreenClass` and `telemetryClassOf` unwraps it, so a
  tab root is named after its content (outside the plan: this also fixes tab-runner `nav` lines that named
  the private wrapper). Tests in `NavTelemetryTest`: live top, tab-local push then Back, stale host after
  NewRoot, cleared binding, fallback.
- #4, #5 `NavigatorChangedByTest`: FinishFlow with a two-screen child; `onBackWithNavigator` is now internal and
  a test runs it and asserts the navigator whose stack shrank is the one `navigatorChangedBy` returns.
- #6 `from` on a push from the host is the tab content (`pushFromTheHostIsFromTheTabContent`); the binding has
  its own tests (above).
- Reviewers: the binding is read on the main thread (command runners); the initial host `nav` line before the
  first tab composes names `MainScreen`. Not driven on a device: qa should check AC-12 and Back from a
  tab-local push.
