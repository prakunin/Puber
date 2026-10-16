# Test design: telemetry-frames-keys-memory (S2)

Inputs: `brief.md` (AC-11, AC-11a, AC-12, AC-13, AC-14, AC-S2-OFF, AC-S2-NODIST, AC-S2-R8), `design-architect.md`
(D1-D16, risks 1-11), `state.md` (Q1-Q3 answered: no `slow_frame`, no `mem` on screen change, no key fast path),
S1 test design (`docs/superpowers/specs/2026-10-15-telemetry-core.md`, "Test design"), the S1 test seams in
`app/src/test/kotlin/com/kino/puber/core/telemetry/` and `.../util/`, `.flow/roles/test-designer.md`.
Base `cd16eb47`. Kind is feature, so there is no failing-first bug test.

`{run}/baseline.md` did not exist when this was written (qa captures it in parallel). The perf section below states
what it must contain; the numbers are filled in from it.

## Levels and conventions

- `unit`: JUnit 5 under `app/src/test/kotlin/com/kino/puber/core/telemetry/`. Fakes reused: `SinkHarness`
  (`TelemetryTestSupport.kt`), `FakeTelemetryClock`, `FakeTelemetryStore`, `ManualTelemetryExecutor`,
  `FakeSharedPreferences`; the real `ThreadTelemetryExecutor` only where thread identity is the thing under test
  (as `TelemetrySinkTest.encodingAndFileIoRunOnlyOnWriterThread` does). mockk only for collaborators that cannot
  exist on the JVM (an `Activity`), never for the class under test.
- `device`: manual observation on a leased emulator, recorded in `docs/superpowers/evidence/<date>-telemetry-frames-keys-memory-device.md`.
- `static`: grep, detekt, lint, `devBenchmarkRelease` build.
- No `instrumented` test is added: there is no UI change and no Robolectric, and JankStats needs a real window, so
  the glue is covered by a fake tracker (below) plus the device pass.

### Rules from the S1 review (every test below must satisfy them)

1. **No vacuous asserts.** An "off / none / no line" assertion is always paired, in the same test or its sibling
   with the same setup, with an "on" control that proves the same sequence does produce output. A test that
   asserts `lines.isEmpty()` without such a control does not count.
2. **No fixtures that hide the path.** Use values that distinguish the right answer from the wrong one: the
   key-code sentinel values `n=1`, `dur_ms=100000` cannot collide with any `KEYCODE_*` int; frame feeds put the
   p90 and p99 in different buckets; a screen-change test uses three different screens, not two.
3. **Origin filter `t >= origin`.** Every test that reads `t` from written lines asserts `t >= 0` (session
   origin) and, for back-dated windows, `t <= now`. The S1 bug was a filter that accepted events before the
   origin; here the risk is the `Frames` back-dating (D10) producing a `t` before the sink was enabled.
4. **Global reset.** Every test class that installs a sink, sets `Telemetry.screen`, or touches `KeyProbe`
   state resets in `@AfterEach`: `Telemetry.resetForTests()` (which per D3 also clears `screen`), plus
   `KeyProbe` state reset (see seams), `Timber` forest and default uncaught handler restore where the class
   already does so (`TelemetryRuntimeTest.tearDown` is the template).
5. **Behaviour through the public surface.** Tests assert emitted events, written lines and calls on injected
   collaborators; never a private field or an internal call order other than the documented one (pause order, D12).

### Seams the implementation must offer

The design names the classes but a few seams are needed to make the tests possible. The plan must carry them.

| Seam | Needed by |
|---|---|
| `FrameAggregator(emit: (TelemetryEvent.Frames) -> Unit, nowMs: () -> Long, nanoTime: () -> Long)`; production passes `Telemetry::record`, `Telemetry::nowMs`, `System::nanoTime` | `FrameAggregatorTest` |
| `KeyLatencyMatcher.arm(eventMs, dispatchNs, name, screen, handled)` / `onFrame(startNs, uiNs, emit)` as pure methods with an `emit` callback | `KeyLatencyMatcherTest` |
| `KeyBurstMachine(emit: (TelemetryEvent.Key) -> Unit)`, methods take primitives (D7/D8) | `KeyBurstMachineTest` |
| `KeyProbe` internal `resetForTests()` (burst machine and matcher back to empty) | rule 4 |
| `internal inline fun dispatchWithProbe(enabled: Boolean, superDispatch: () -> Boolean, probe: (handled: Boolean) -> Unit): Boolean`, called from `MainActivity.dispatchKeyEvent` and holding all of its logic | the D-pad no-regression test; without it `MainActivity` needs an Activity to test |
| `FrameProbe(tracker: FrameTrackerFactory, aggregator, matcher)` where `FrameTrackerFactory.create(activity, listener): FrameTracker` and `FrameTracker.isTrackingEnabled` are a tiny interface; production wraps `JankStats.createAndTrack` | `FrameProbeTest` (JankStats cannot run on the JVM) |
| `MemoryProbe(executor, sampler, registerTrim, unregisterTrim)` as D14; `MemorySampler.sample(withPss: Boolean, trim: Int)` | `MemoryProbeTest` |
| `ManualTelemetryExecutor` records the `periodMs` of each `scheduleRepeating` and exposes `repeatingCount` (test util change; owner: implementer of tests) | AC-14 period 60 000 |
| `TelemetryRuntime(probes = fake list, mainPost = queueing fake)` as D13 | lifecycle tests |
| `TelemetryActivityCallbacks(runtimeState, frameProbe, keyFlush, sink.requestDrain, memoryProbe)` taking those as constructor arguments | `TelemetryActivityCallbacksTest` |

## Risk profile

P = probability 1-3, I = impact 1-3. Score 6 or more needs a test at the level where it would fail.

| # | Risk | P | I | Score | Where it would fail | Covered by |
|---|---|---|---|---|---|---|
| R1 | Main-thread or FrameMetrics-thread work distorts the measurement (allocation per frame, file IO or PSS on a hot thread) | 2 | 3 | 6 | `FrameAggregator.onFrame`, `DebugMemorySampler`, `KeyProbe` | F9, F10, M7, M8, K13, device D8, D9 |
| R2 | `dispatchKeyEvent` returns something other than `super`'s result, or calls `super` twice or not at all: D-pad, Back and `AppRemoteHotkeyHandler` break | 2 | 3 | 6 | `MainActivity` | K1-K4 unit, device D-pad A/B (D5) |
| R3 | Runtime toggle: tracking not started on an already-resumed Activity, not stopped on off, probe detach run on the writer thread touching JankStats, stale tracker on a recreated window | 2 | 3 | 6 | `TelemetryRuntime.plantProbes/detachProbes`, `TelemetryActivityCallbacks`, `FrameProbe` | L1-L12, device D6 |
| R4 | Raw key code reaches a line and reconstructs typed search text (AC-7) | 2 | 3 | 6 | `keyNameOf`, `TelemetryEncoder` | N1-N5, device D11 (scan) |
| R5 | `frames` window labelled with the wrong screen (writer `lastScr`, or holder read at close) | 3 | 2 | 6 | `TelemetryWriter.append`, `FrameAggregator` | W1-W5, F6, F7 |
| R6 | JankStats records nothing on the software-GPU emulator, so AC-12 cannot be shown on the only available device | 2 | 3 | 6 | environment | device D2 gate; unit tests keep the logic proven regardless |
| R7 | `metrics-performance:1.0.0` does not resolve, or the API shape differs (D2) | 2 | 3 | 6 | Gradle | plan task 1 compile; `FrameProbeTest` compiles against the real factory seam |
| R8 | Clock bases of `KeyEvent.eventTime` and `frameStartNanos` differ: `lat_ms` negative or huge, false `key_lat` lines | 3 | 2 | 6 | `KeyLatencyMatcher` | C1-C12 (logic), device D7 (the only place the base can be checked) |
| R9 | Tests pass while the behaviour is broken (S1 lesson) | 2 | 3 | 6 | the tests | conventions above; mutation spot-checks (below) |
| R10 | `Frames` back-dated `t` precedes the session origin or exceeds now | 2 | 2 | 4 | `FrameAggregator.close` | F8, F9b (still P0: it was the S1 bug class) |
| R11 | R8 minification obfuscates `scr` or strips the listener | 2 | 2 | 4 | release build | device D12 (AC-S2-R8); existing `ProguardRulesTest` keeps its guard |
| R12 | Window or memory leak: `resumedActivity` held after pause, trim callback never unregistered | 2 | 2 | 4 | `TelemetryActivityCallbacks`, `MemoryProbe` | L8, L10, M9 |
| R13 | `key_lat` floods on the software GPU into the cap and `meta dropped` | 3 | 1 | 3 | environment | cap test X2; device D8 expects it, not a defect |
| R14 | Trim-memory semantics: AC-14 says "no `mem` in the background" and "an extra `mem` line on `onTrimMemory`", but `MODERATE` and `BACKGROUND` are delivered only to background processes | 2 | 2 | 4 | `MemoryProbe.onTrimMemory` | M5, M6 pin D14's reading; see "Spec gaps" |
| R15 | Late `FrameMetrics` callbacks after `stop()` or after toggle-off emit lines or arm matcher | 2 | 2 | 4 | listener guard | FP5, FP6 |
| R16 | Concurrent arm (main) and answer (FrameMetrics thread) double-emit or lose a pending entry | 1 | 2 | 2 | `KeyLatencyMatcher` | C13 (P2 stress), accepted otherwise |
| R17 | Baseline profile stale for `dispatchKeyEvent` | 1 | 1 | 1 | accepted, no test (D11 of risks) | none |

### Spec gaps the tests pin (architect or planner to confirm; tests follow the stated reading)

1. **Rounding of ms fields.** D10 says whole ms: percentiles are the bucket index (floor), `max = maxNs / 1e6`
   (integer division, floor). F2 pins floor. If the plan chooses rounding, F2 changes, nothing else.
2. **Back-dated `t` lower bound.** D10 computes `t = nowMs - (nanoTime - lastEndNs)/1e6` with no clamp. A late
   `FrameMetrics` callback delivered after enable could yield `t` below the sink origin. F9b requires a clamp to
   `t >= 0` (origin) and the tests are written to fail without it.
3. **Trim in the background.** D14 runs a trim line unconditionally; AC-14's "no `mem` in the background" is read
   as "no periodic line". M6 pins that reading (a `MODERATE` trim in background is written, periodic is not).
   The device scenario uses `send-trim-memory MODERATE` (a background level) accordingly.
4. **Repeat without an open burst** (DOWN with `repeat > 0` and no burst open, for example logging enabled in the
   middle of a hold). K10 pins: it opens a burst with `n=1` and counts onward; it must not throw or emit
   a line with `n=0`.
5. **Unhandled `super` exception** in `dispatchWithProbe`: K4 pins that it propagates and the probe is not
   called (the key was not dispatched).

## Traceability

Test names are Kotlin method names. Classes are in `app/src/test/kotlin/com/kino/puber/core/telemetry/` unless
marked. P0 must exist before review, P1 should, P2 nice to have.

### AC-11: key bursts

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-11 | unit | P0 | K5 `KeyBurstMachineTest.longPressEmitsOneKeyWithCountAndDurationOnUp` | DOWN r0, then r1..r9, UP: the emit list is empty after the DOWNs (asserted before the UP) and has exactly one `Key(name=DPAD_DOWN, n=10, durMs=up-start)` after the UP |
| AC-11 | unit | P0 | K6 `KeyBurstMachineTest.threeSinglePressesEmitThreeKeysWithNOne` | three DOWN/UP pairs: three events, each `n=1`, `durMs` equals each UP minus its DOWN (different durations 40, 90, 15 so a shared value fails) |
| AC-11 | unit | P0 | K7 `KeyBurstMachineTest.differentKeyClosesOpenBurstWithDurationFromLastRepeat` | UP held 4 repeats, then DOWN of another key: burst 1 emitted with `n=4`, `durMs = lastRepeat - start`, and the new burst stays open (no emit yet) |
| AC-11 | unit | P0 | K8 `KeyBurstMachineTest.idleGapAboveTwoSecondsEmitsOnNextEventAndBoundaryDoesNot` | gap of 2000 ms keeps the burst; 2001 ms closes it with `durMs = lastMs - startMs`; the emitting event then opens its own burst |
| AC-11 | unit | P0 | K9 `KeyBurstMachineTest.flushEmitsOpenBurstOnceAndEmptyFlushEmitsNothing` | pause/detach flush emits the open burst once; a second flush and a flush with no burst emit nothing |
| AC-11 | unit | P1 | K10 `KeyBurstMachineTest.repeatWithoutOpenBurstOpensOneWithNOneAndNeverThrows` | spec gap 4 |
| AC-11 | unit | P1 | K11 `KeyBurstMachineTest.upOfAKeyThatIsNotOpenIsIgnored` | stray UP emits nothing and does not close the open burst of another key |
| AC-11 | unit | P0 | K12 `KeyBurstThroughSinkTest.heldDpadWritesOneKeyLineWithFields` | `SinkHarness` + installed sink + machine wired to `Telemetry.record`: after drain the store holds one `type=key` line, field set exactly envelope + `{key,n,dur_ms}`, `key == "dpad_down"` wire, `n == 10`, `dur_ms` as fed, `t >= 0`; control: the same feed with the sink off writes no `key` line |
| AC-11 | unit | P1 | K13 `KeyBurstMachineTest.repeatsAfterTheFirstDoNotAllocate` | `ThreadMXBean` allocated-bytes delta over 100 000 repeats stays under 64 KB; negative control inside the test (a loop allocating a `Pair` per iteration exceeds the bound) so the check cannot pass vacuously; skipped with `Assumptions` on a JVM without the bean |
| AC-11 | unit | P0 | K1 `DispatchWithProbeTest.offCallsSuperOnceAndNeverTheProbe` | `enabled=false`, `super` returns `true`, then `false`: returns the same value, `super` called once, probe zero calls |
| AC-11 / R2 | unit | P0 | K2 `DispatchWithProbeTest.onReturnsSupersValueAndProbesAfterSuper` | `enabled=true`, `super` true then false: same return value; call log is `[super, probe]` in that order; probe receives `handled` equal to `super`'s value |
| AC-11 / R2 | unit | P0 | K3 `DispatchWithProbeTest.probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch` | probe throws: dispatch still returns `super`'s value (the key path must survive a telemetry bug); fails if the probe is not guarded |
| AC-11 / R2 | unit | P1 | K4 `DispatchWithProbeTest.superThatThrowsPropagatesAndProbeIsNotCalled` | spec gap 5 |

### AC-11a: key-code allow-list

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-11a | unit | P0 | N1 `KeyNameTest.everyLetterDigitAndNumpadCodeMapsToOther` | `@ParameterizedTest` over `KEYCODE_A..Z`, `KEYCODE_0..9`, `KEYCODE_NUMPAD_0..9`, plus `KEYCODE_SPACE`, `KEYCODE_DEL`, `KEYCODE_UNKNOWN`, `0`, `9999`, `-1` |
| AC-11a | unit | P0 | N2 `KeyNameTest.allowListIsExactlyTheDocumentedCodes` | loop over codes `0..400`: the code maps to a non-`OTHER` name iff it is in the explicit expected map (D-pad x5, ENTER and NUMPAD_ENTER to `enter`, BACK, MENU, six media keys plus NEXT/PREVIOUS, CHANNEL_UP/DOWN, PAGE_UP/DOWN, MOVE_HOME/END) and each maps to its expected wire string; a code added to the allow-list without a test row fails |
| AC-11a | unit | P0 | N3 `KeyNameTest.encodedKeyAndKeyLatLinesNeverContainTheRawCode` | for every code of N1, build `Key(n=1, durMs=100000)` and `KeyLat(latMs=77777, frameMs=88888)` with `keyNameOf(code)`, encode, and assert no value of the JSON equals the code as number or string; sentinel values chosen so `n`/`dur` cannot equal a code (rule 2); `key == "other"` |
| AC-11a | unit | P1 | N4 `KeyNameTest.wireNamesAreUniqueAndNeverASingleLetterOrDigit` | `KeyName.entries.map(wire)` has no duplicate except none, and no wire has length 1 and none is all digits |
| AC-11a | unit | P1 | N5 `KeyNameTest.eventsCarryTheEnumNotAnInt` | reflection over the `Key` and `KeyLat` constructors: no `Int` parameter named `code`/`keyCode` (the structural guard for D6) |
| AC-11a | device | P0 | D11 below | an AC-7 style scan of the device session finds none of the characters typed into search |

### AC-12: frame windows

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-12 | unit | P0 | F1 `FrameAggregatorTest.percentilesAndExactMaxOnCraftedFeed` | feed 100 frames: 90 x 8 ms, 9 x 20 ms, 1 x 250 ms, 12 flagged janky: window emits `frames=100`, `janky=12`, `p50=8`, `p90=8`, `p99=20`, `max=250` (p99 differs from p90 and from max, so a wrong rank or off-by-one fails) |
| AC-12 | unit | P0 | F2 `FrameAggregatorTest.percentileInOverflowBucketReportsExactMaxAndSubMsFloors` | 98 x 8 ms + 150 ms + 300 ms: `p99 == max == 300`; a frame of 8 900 000 ns counts in bucket 8 and `max` of a 8.9 ms-only window is 8 (spec gap 1) |
| AC-12 | unit | P0 | F3 `FrameAggregatorTest.windowRollsOverAtTenSecondsInclusive` | frame at start +9 999 999 999 ns joins the window; a frame at exactly +10 000 000 000 ns closes it and opens the next; the closing event excludes the triggering frame, the next window counts it (`frames=1`) |
| AC-12 | unit | P0 | F4 `FrameAggregatorTest.secondWindowStartsFromZeroedHistogram` | window 1 all 90 ms frames, window 2 all 5 ms frames: window 2 `p99 == 5`, `max == 5`, `janky` counts only its own |
| AC-12 | unit | P0 | F5 `FrameAggregatorTest.closeEmitsOpenWindowOnceAndEmptyWindowEmitsNothing` | `close()` with 3 frames emits one event; second `close()` and `close()` on a fresh aggregator emit nothing (control: the first emits) |
| AC-12 | unit | P0 | F6 `FrameAggregatorTest.screenChangeClosesWindowWithTheOldScreen` | frames on `HomeScreen`, one on `HistoryScreen`, then `ScreenScreen`: two emitted events carry `HomeScreen` and `HistoryScreen` (identity `===` on the `Class`), frame counts split 5/1, the third window is still open |
| AC-12 | unit | P0 | F7 `FrameAggregatorTest.nullScreenIsAWindowOfItsOwn` | `screen=null` then a real screen: the null window is closed and carries `null` (written as `scr ""`), not the next screen |
| AC-12 | unit | P0 | F8 `FrameAggregatorTest.idleGapBackDatesTToTheLastFrameNotTheClose` | last frame at `now=T`, clock advances 30 s idle, next frame closes the window: `event.t == T` (within 1 ms), and `t < now`; fails if `t` is the close time |
| AC-12 | unit | P0 | F9 `FrameAggregatorThroughSinkTest.backDatedWindowNeverPrecedesTheSessionOrigin` | late `FrameMetrics` frame whose `lastEndNs` is older than the sink origin: after drain the written `t >= 0` and `t <= now` (spec gap 2; this is the S1 origin-filter lesson) |
| AC-12 | unit | P0 | F10 `FrameAggregatorTest.thousandFramesInsideAWindowCallEmitNeverAndAllocateNothing` | 1000 frames inside one window: `emit` call count 0 (only the close calls it); `ThreadMXBean` delta over 200 000 `onFrame` calls under 64 KB with a negative control as in K13; skipped on a JVM without the bean |
| AC-12 | unit | P0 | F11 `FrameAggregatorTest.listenerShapedCallsMatchGoldenEncodedLine` | window from F1 through `TelemetryEncoder` yields JSON with exactly envelope + `{frames,janky,p50,p90,p99,max}` and the expected numbers |
| AC-12 | unit | P0 | W1 `TelemetryWriterScrTest.framesKeepTheirOwnScreenAfterALaterNav` | `Nav(to=ScreenScreen)` recorded, then `Frames(screen=HomeScreen)`: the `frames` line `scr == "Home"`, not the nav target; control: an `Act` sibling still follows its own VM |
| AC-12 | unit | P0 | W2 `TelemetryWriterScrTest.keyKeyLatAndMemKeepTheirOwnScreen` | same for `Key`, `KeyLat`, `Mem` (parameterised on the four types) |
| AC-12 | unit | P0 | W3 `TelemetryWriterScrTest.screenScopedNullScreenWritesEmptyNotLastNav` | `Frames(screen=null)` after a nav: `scr == ""` (D4: `names.owner(null)`), not the last nav |
| AC-12 | unit | P0 | W4 `TelemetryWriterScrTest.screenScopedEventsDoNotMoveLastScreen` | after `Frames(screen=Other)`, a `meta` line still has the last `nav.to` as `scr` |
| AC-12 | unit | P0 | W5 `TelemetryWriterScrTest.navStillSetsLastScreenAndOldTypesUnchanged` | regression of S1 behaviour (`nav/ready/act` rules) next to the new branch |
| AC-12 | unit | P0 | F12 `NavTelemetryTest.recordNavSetsScreenHolderWhenOnAndNeverWhenOffOrActivity` | on: holder is the `to` class after `PUSH`; `ACTIVITY` command leaves the holder unchanged; off: the holder stays `null` after the same sequence (the on control is the first assertion) |
| AC-12 | unit | P1 | F13 `TelemetryTest.detachResetsScreenHolderToNull` | after disable, `Telemetry.screen == null`; re-enable writes `scr ""` until the next nav (no stale name) |
| AC-12 | device | P0 | D3 below | real `frames` line per active screen, window cadence, correct `scr` across a navigation |

### AC-13: key-to-frame latency

Helper: `matcher.arm(eventMs, dispatchNs, name, screen, handled)` and `matcher.onFrame(startNs, uiNs, emit)`.
Times are chosen so `eventMs*1e6`, `dispatchNs` and frame times share one base, as D9 assumes.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-13 | unit | P0 | C1 `KeyLatencyMatcherTest.answerOverFiftyMsEmitsLatAndFrameMs` | key at 1000 ms, frame ends at 1070: one `KeyLat(latMs=70, frameMs=<that frame's ui duration>)` with the armed `key` |
| AC-13 | unit | P0 | C2 `KeyLatencyMatcherTest.fiftyIsNotSlowAndFiftyOneIs` | lat 49 and 50: no emit; 51: emit (boundary of `> 50`); each case followed by a control that the matcher is empty again |
| AC-13 | unit | P0 | C3 `KeyLatencyMatcherTest.latencyStartsAtEventTimeNotAtDispatch` | `eventTime` 30 ms before dispatch, frame ends 40 ms after dispatch: lat 70 emits (a version measuring from dispatch would see 40 and fail) |
| AC-13 | unit | P0 | C4 `KeyLatencyMatcherTest.frameStartingBeforeDispatchDoesNotAnswer` | frame with `start < dispatchNs` ignored, pending stays; the next frame with `start >= dispatchNs` answers |
| AC-13 | unit | P0 | C5 `KeyLatencyMatcherTest.heldKeyArmsOnceAndMeasuresFromTheFirstDown` | DOWN r0 arms, repeats 1..20 do not re-arm and do not replace the pending time; a slow frame emits exactly one `KeyLat` with lat from the r0 time |
| AC-13 | unit | P0 | C6 `KeyLatencyMatcherTest.secondDownWhilePendingDoesNotReplaceTheEarliest` | a second r0 DOWN of another key while one is pending: lat is still measured from the first |
| AC-13 | unit | P0 | C7 `KeyLatencyMatcherTest.unhandledKeyNeverArms` | `handled=false`: frames of any length produce nothing; control with `handled=true` produces a line |
| AC-13 | unit | P0 | C8 `KeyLatencyMatcherTest.pendingExpiresAfterOneSecondAndLateFrameEmitsNothing` | frame starting at dispatch + 999 ms answers (and emits if slow); frame at + 1001 ms drops the entry silently (no emit even though `lat` is huge); then a later key arms normally |
| AC-13 | unit | P0 | C9 `KeyLatencyMatcherTest.noFrameMeansNoLine` | arm, no `onFrame`: nothing, and a following `arm` is refused until expiry/answer (documents the one-slot rule) |
| AC-13 | unit | P0 | C10 `KeyLatencyMatcherTest.answeredEntryIsConsumedAndNotEmittedTwice` | after an emit, further slow frames emit nothing |
| AC-13 | unit | P0 | C11 `KeyLatencyMatcherTest.negativeLatencyFromAClockBaseMismatchIsNeverEmitted` | frame end before `eventMs`: no line, matcher cleared (R8: the line would otherwise be negative nonsense) |
| AC-13 | unit | P1 | C12 `KeyLatencyMatcherTest.lineCarriesTheArmedScreenNotTheScreenAtAnswer` | `arm(screen=Home)`, holder changes to another screen, answer: `KeyLat.screen === Home` |
| AC-13 | unit | P2 | C13 `KeyLatencyMatcherConcurrencyTest.armOnOneThreadAnswerOnAnotherNeverDoubleEmits` | 10 000 arm/answer rounds on two threads with a latch: emitted count `<=` arm count, never an exception |
| AC-13 | unit | P0 | C14 `FrameProbeTest.listenerFeedsMatcherThenAggregatorOnlyWhenOn` | see FP block |
| AC-13 | device | P0 | D7 below | clock bases agree on the emulator: every `lat_ms` is positive and under 1000 |

### AC-14: memory

Executor: `ManualTelemetryExecutor` with `periodMs` recording (seam). Sampler: a fake `MemorySampler` returning fixed
values and logging `(withPss, trim, thread name)`.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-14 | unit | P0 | M1 `MemoryProbeTest.foregroundSchedulesOnePeriodicSampleEverySixtySeconds` | `attach` + `onForeground(true)`: one repeating task with `periodMs == 60_000`; one `runRepeating()` produces exactly one `mem` line with fields `java_kb,java_max_kb,native_kb,pss_kb` and no `trim`; sampler called with `withPss=true` |
| AC-14 | unit | P0 | M2 `MemoryProbeTest.noPeriodicLineInTheBackgroundAndTimerIsCancelled` | `onForeground(true)` then `(false)`: repeating count back to 0 and `runRepeating()` writes no `mem`; never in foreground yet: no timer was scheduled at all (the foreground case of M1 is the control) |
| AC-14 | unit | P0 | M3 `MemoryProbeTest.reenteringForegroundKeepsOneTimerNotTwo` | `onForeground(true)` twice, and background/foreground/background/foreground: repeating count is 1; one tick writes one line |
| AC-14 | unit | P0 | M4 `MemoryProbeTest.trimWritesOneExtraLineWithLevelAndNoPss` | `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` (10): after `runAll()` one `mem` line with `trim=10`, no `pss_kb`; sampler called with `withPss=false` |
| AC-14 | unit | P0 | M5 `MemoryProbeTest.uiHiddenTrimIsIgnored` | level 20 writes nothing; control: level 10 writes one (D14: `UI_HIDDEN` fires on every backgrounding) |
| AC-14 | unit | P1 | M6 `MemoryProbeTest.backgroundTrimLevelIsWrittenButPeriodicIsNot` | spec gap 3: `onForeground(false)`, `onTrimMemory(60)`: one trim line, no periodic line after `runRepeating()` |
| AC-14 / NODIST | unit | P0 | M7 `MemoryProbeThreadTest.samplerRunsOnlyOnTheWriterThread` | real `ThreadTelemetryExecutor`: `onTrimMemory` from the test thread returns before the sampler runs; the sampler records `Thread.currentThread().name == ThreadTelemetryExecutor.THREAD_NAME` and `isWriterThread`; awaited by `CountDownLatch` with timeout, no sleeps |
| AC-14 / NODIST | unit | P0 | M8 `MemoryProbeThreadTest.periodicSampleRunsOnTheWriterThreadNotOnTheCaller` | with the manual executor, the sampler is not called during `onForeground(true)` itself (only on `runRepeating()`, `isWriterThread == true` inside) |
| AC-14 | unit | P0 | M9 `MemoryProbeTest.detachUnregistersTrimClosesTimerAndLaterTrimIsIgnored` | `attach` registers once (a second `attach` still once); `detach` unregisters, repeating count 0, a trim after detach writes nothing; `detach` twice is safe |
| AC-14 | unit | P1 | M10 `MemoryProbeTest.samplerExceptionDoesNotStopTheTimer` | sampler throws on tick 1, succeeds on tick 2: tick 2 still writes a line, nothing escapes |
| AC-14 | unit | P0 | M11 `TelemetryEncoderTest` rows | `Mem` periodic and `Mem` trim sample rows added to `samples`; the parameterised `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` then demands exact key sets (the trim variant is a second parameterised case so `pss_kb` vs `trim` exclusivity is enforced) |
| AC-14 | device | P0 | D4 below | one line per foreground minute, none in background, trim line |

### AC-S2-OFF: nothing runs when the log is off

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| OFF | unit | P0 | L1 `TelemetryRuntimeProbesTest.freshProcessWithLogOffNeverAttachesAnyProbe` | fake probe list, queueing `mainPost`: after `start` with no pref, attach 0 and detach 0, main queue empty, store untouched, `executor.useCount == 0` |
| OFF | unit | P0 | L2 `TelemetryRuntimeProbesTest.enablingAttachesOnceOnTheMainThreadQueue` | enable: attach is not run until the main queue drains, then runs exactly once |
| OFF | unit | P0 | L3 `TelemetryRuntimeProbesTest.disablingDetachesAndOnOffOnEndsAttached` | on, off, on with a queued main post: order preserved, final state attached, attach/detach counts 2/1 |
| OFF | unit | P0 | L4 `TelemetryRuntimeProbesTest.autoOffAfterSevenDaysAndStorageFailureAlsoDetach` | expiry (reuse the T25 clock setup) and a throwing store each end with detach called once and `Telemetry.isEnabled == false` |
| OFF | unit | P0 | L5 `TelemetryRuntimeProbesTest.detachRaisedOnTheWriterThreadIsPostedNotRunInline` | trigger `onExpired` inside `executor.runAll()`: `probe.detach` has not run until the main queue drains (a JankStats call off main would otherwise crash on the device) |
| OFF | unit | P0 | L6 `TelemetryRuntimeProbesTest.screenHolderIsNullAfterDetach` | holder set while on, null after off |
| OFF | unit | P0 | L7 `TelemetryRuntimeProbesTest.offAfterOnWritesNothingFurtherAndOpenWindowIsDropped` | on, record 20 frames in an open window, toggle off, drain: the store's line count and file set are unchanged after the toggle and a later `close()` emits no line (the control asserts the 20 frames would have produced a `frames` line when still on, so the window really was open) |
| OFF | unit | P0 | L8 `TelemetryActivityCallbacksTest.resumeWhenOffDoesNotStartTrackingButTracksTheActivity` | mockk `Activity`; off: resume does not call `frameProbe.start`, `memoryProbe.onForeground` untouched; the next enable starts tracking for the already-resumed Activity (L9 control) |
| OFF | unit | P0 | L9 `TelemetryActivityCallbacksTest.enableWhileResumedStartsTrackingOnThatActivity` | resume while off, then enable: exactly one `frameProbe.start(activity)`; enable while no Activity is resumed: none, and the next resume starts it |
| OFF | unit | P0 | L10 `TelemetryActivityCallbacksTest.pauseStopsFrameFlushesKeysThenRequestsDrainInThatOrder` | recorded call order is `frame.stop, keyFlush, requestDrain`; open frame window and open key burst are present in the store after the drain (proves "closing before the drain" end to end) |
| OFF | unit | P0 | L11 `TelemetryActivityCallbacksTest.destroyAndPauseClearTheResumedActivity` | a leaked reference check via a second enable after pause: no `start` on the old Activity |
| OFF | unit | P1 | L12 `TelemetryActivityCallbacksTest.startedCountTreatsTwoActivitiesAsForegroundUntilBothStop` | start A, start B, stop A: still foreground; stop B: `onForeground(false)` once |
| OFF | unit | P0 | L13 `KeyProbeOffTest.offDispatchNeverTouchesTheMachineOrMatcher` | combined with K1: after a feed of 50 key events through `dispatchWithProbe(enabled=false)` the machine and matcher are in their reset state and `Telemetry.record` calls on a counting sink are zero; on control feeds the same events and gets the lines |
| OFF | unit | P0 | L14 `TelemetrySinkTest` addition `memoryAndFrameTypesAreCountedAsDisabledNoOps` | `record(Frames/Key/KeyLat/Mem)` with the flag off leaves encoder, store, clock and executor with zero calls (extends T15 to the new types) |
| OFF | device | P0 | D1 below | empty telemetry directory with the toggle off, cold-start A/B, runtime on/off/on with an Activity already resumed |

### Probe glue (JankStats) and threading, AC-S2-OFF / AC-S2-NODIST

`FrameProbeTest` uses a fake `FrameTrackerFactory` that records `create(activity, listener)` and exposes the
listener, so the production listener body is exercised without JankStats.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| OFF | unit | P0 | FP1 `FrameProbeTest.startCreatesOneTrackerAndStartTwiceDoesNotCreateASecond` | idempotent start |
| OFF | unit | P0 | FP2 `FrameProbeTest.stopDisablesTrackingClosesAggregatorAndReleasesTheTracker` | `isTrackingEnabled == false`, open window emitted once, a later `stop` is safe |
| OFF | unit | P0 | FP3 `FrameProbeTest.recreatedActivityGetsANewTrackerAndTheOldOneStaysStopped` | start(A), stop, start(B) (config or locale change): two trackers, first disabled, second bound to B |
| OFF | unit | P0 | FP4 `FrameProbeTest.listenerWhileOffTouchesNeitherAggregatorNorMatcher` | listener invoked with the flag off: zero aggregator and matcher changes; control: same invocation on gives a counted frame |
| OFF | unit | P1 | FP5 `FrameProbeTest.lateFrameAfterStopDoesNotEmitOrArm` | listener called after `stop()`: no line, no pending key (R15) |
| AC-13 | unit | P0 | FP6 `FrameProbeTest.listenerFeedsMatcherThenAggregatorWithTheCurrentScreenHolder` | `Telemetry.screen` set to `HomeScreen`: aggregator frame carries it, matcher is called before the aggregator (a slow answer still gets recorded when the aggregator closes a window on the same call) |
| NODIST | unit | P0 | TH1 `FrameThreadingTest.noTelemetryIoOrEncodingOnTheCallbackThread` | listener called from a test thread named `FrameMetricsHandler`, window closed by a screen change, with real `ThreadTelemetryExecutor` and `FakeTelemetryStore`: every `store.calls` thread name equals `ThreadTelemetryExecutor.THREAD_NAME`, encoder spy saw only that thread, and the callback thread name appears nowhere (as S1 T35/T36 did) |
| NODIST | unit | P0 | TH2 `KeyThreadingTest.keyEmitAndLatencyEmitDoNoIoOnTheCallerThread` | burst closed on the "main" test thread and `KeyLat` emitted on the "frame" thread: file IO and JSON only on the writer thread |
| NODIST | static | P0 | TH3 review checklist, `rg` | `FrameAggregator.onFrame` and the listener contain no `Telemetry.` call other than via `emit` on close, no string template, no `Debug.`/`getMemoryInfo`; `DebugMemorySampler` is only referenced from `MemoryProbe` |

### Caps, caps flood

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-11..14 | unit | P0 | X1 `TelemetryCapsTest.newTypesHaveTheDeclaredPerMinuteCaps` | parameterised: `FRAMES` 12, `KEY` 120, `KEY_LAT` 60, `MEM` 10; cap+1 events in one wall minute leave exactly cap lines and a `meta dropped` with `counts[type] == 1` using the wire names |
| AC-13 | unit | P1 | X2 `TelemetryCapsTest.keyLatFloodIsCountedNotLostSilentlyAndWindowResets` | 200 `KeyLat` in one minute: 60 lines, `dropped` counts 140; after the minute rolls the next one is written |
| all | unit | P0 | X3 `TelemetryEventTypeTest.everyTypeHasAnEncoderSampleAndACap` | extends the existing parameterised test so a new type without a sample or cap fails |

### AC-S2-R8 and the dependency

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| R8 | static | P0 | S1 `./tools/agentw :app:compileDevDebugKotlin` after plan task 1 | the catalogue entry and `implementation(libs.androidx.metrics.performance)` resolve; the real JankStats API shape is recorded in the plan (D2) |
| R8 | static | P1 | S2 `ProguardRulesTest` unchanged and still green | existing keep rule for screen classes (`proguard-rules.pro:90`) is intact; D16 adds no rule |
| R8 | device | P0 | D12 below | `devBenchmarkRelease`: the four line types appear with readable `scr` |
| deps | static | P1 | S3 grep `rg "metrics" gradle/libs.versions.toml app/build.gradle.kts` | one version, one library alias, one `implementation`; no hardcoded version string in `app/build.gradle.kts` |

## Mutation spot-checks (against R9; run by the test writer or reviewer, recorded in the evidence)

Each one-line change must turn at least the named test red; if it does not, the test is vacuous.

| Mutation | Must fail |
|---|---|
| `Telemetry.record` guarded by nothing in `dispatchWithProbe`, off branch calls the probe | K1, L13 |
| `dispatchWithProbe` returns `true` after probing | K2 |
| `keyNameOf` returns the raw-mapped name for letters (add `KEYCODE_A` to the allow-list) | N1, N2, N3 |
| `ScreenScoped` branch removed from the writer | W1, W2 |
| `lat_ms` measured from dispatch not `eventTime` | C3 |
| threshold `>= 50` instead of `> 50` | C2 |
| window close uses `>` not `>=` for 10 s | F3 |
| `t` not back-dated, or not clamped | F8, F9 |
| `onForeground(true)` schedules a second timer | M3 |
| `UI_HIDDEN` not ignored | M5 |
| `plantProbes` skips the already-resumed Activity | L9 |
| `detachProbes` runs inline on the writer thread | L5 |
| `Telemetry.screen` not reset on detach | L6, F13 |

## Manual / device scenario (`device`, recorded in the evidence file)

Preconditions: leased emulator (`tools/emulator-resource-lock.sh`), `adb -s <serial>` everywhere, the worktree's
own `devDebug` APK installed with `tools/android-apk-install-preserve`, no uninstall, no data clear, account
already paired (a worktree build cannot log in). Release the lease at the end. Host load is stated in the
evidence next to every relative number.

**What is meaningful on a software-GPU emulator, and what is not**

| Meaningful (assert it) | Not meaningful (record, never judge) |
|---|---|
| presence, order and field sets of every line (`jq` key sets as in S1) | jank percentage (about 99 %, every frame janky) |
| one `key` line per held press, `n` and `dur_ms` plausible | absolute `key_lat` values and the `frame_ms` numbers |
| `frames` cadence (about 6 per active minute), `scr` correct across a navigation, `frames/janky` counts consistent | `frames.p50/p90/p99/max` values (they will be 150-300 ms) |
| `mem` once per foreground minute, none in background, trim line | absolute PSS or heap sizes as a quality statement |
| clock-base sanity: every `lat_ms` positive and below 1000 | the share of presses that produced a `key_lat` line (nearly all will) |
| volume under caps, `meta dropped` for `key_lat` is expected here | |
| relative A/B within one build and one load window | any comparison across hosts or sessions |

Steps. Numbers are the evidence ids used above.

1. **D1: off baseline and runtime toggle (AC-S2-OFF).** Fresh install state with the toggle off (do not clear data:
   toggle off in Settings). `adb shell run-as`-free listing of `getExternalFilesDir/telemetry` via the sanctioned
   `adb -s <serial> shell ls` on the external files path: empty or absent before and after navigating Home, a list,
   holding D-pad and a minute in the foreground. Then toggle on while the Activity is resumed (no restart): lines
   for `frames` start within about 10-20 s of scrolling. Toggle off: the file stops growing (size unchanged over
   30 s of scrolling and a held D-pad); toggle on again: a new file. Must not happen: any `*.jsonl` created or
   grown while off; a `frames` or `key` line after the off.
2. **D2: JankStats actually records (gate, R6).** Toggle on, scroll Home for 25 s. Expected: at least one `frames`
   line. If none appears the software window is not tracked: stop, record, and escalate; the unit tests still stand
   but AC-12 then needs the owner's real-TV pass (MANUAL M1). Do not weaken the code.
3. **D3: frames (AC-12).** Scroll Home ~25 s, open History and scroll ~15 s, press Back right after a window
   boundary. Expected: `frames` lines with `scr` `Home` and `History` (their own window each; the line that closes
   right after the navigation still says the old screen), field set exactly `frames,janky,p50,p90,p99,max`,
   `janky <= frames`, `p50 <= p90 <= p99 <= max`, `t` non-decreasing and `>= 0`. Must not happen: a Home window
   labelled `History`, an empty `scr` while a nav has happened, a negative or decreasing `t`.
4. **D4: memory (AC-14).** Stay in the foreground for 125 s on a quiet screen: exactly two periodic `mem` lines
   (fields `java_kb,java_max_kb,native_kb,pss_kb`). `adb shell input keyevent HOME`, wait 130 s: no new periodic
   `mem` line. Resume: the timer restarts (a line about 60 s later). `adb -s <serial> shell am send-trim-memory
   <pkg> MODERATE` while the app is running: one extra `mem` with `trim` and no `pss_kb`. Must not happen: a
   periodic line in the background, two periodic lines in one minute after a background/foreground cycle.
5. **D5: D-pad behaves exactly as before.** With logging on, then off, then on: Home to a list, DPAD_DOWN x10,
   DPAD_RIGHT x5, DPAD_CENTER on an item (opens detail), BACK x2, and a long press (`input keyevent --longpress
   KEYCODE_DPAD_DOWN`). Expected: focus moves one item per press, the same items are reached in both modes
   (record the title or position after each step), Back pops one screen per press, the long press scrolls and
   stops on release. Run the same script on the base build for the comparison when the base APK exists. Must not
   happen: a swallowed or doubled key, a focus jump, Back that needs two presses, an `AppRemoteHotkeyHandler`
   shortcut that stops working. This is the R2 device guard.
6. **D6: key lines (AC-11).** Hold DPAD_DOWN (`--longpress`): one `key` line with `n > 1` and `dur_ms`
   consistent with the hold, not one line per repeat. Then three separate DPAD_DOWN presses: three lines `n=1`.
   Field set exactly `key,n,dur_ms`.
7. **D7: `key_lat` and clock bases (AC-13, R8).** Press DPAD_DOWN 10 times, 1 s apart, with logging on. Expected:
   lines (many on this renderer) with `lat_ms` strictly positive and under 1000, `frame_ms` positive, field set
   `key,lat_ms,frame_ms`. If any `lat_ms` is negative or in the hundreds of thousands, the clock-base assumption
   is wrong: record, escalate, do not change the threshold. A burst of presses fast enough to exceed 60/min must
   show `meta dropped` with `counts.key_lat`; that is the expected flood, not a defect.
8. **D8: volume and caps.** After 10 minutes of mixed use: the per-type line counts per minute stay under the caps
   (frames 12, key 120, key_lat 60, mem 10), the file size stays within the S1 budget arithmetic (bytes per line
   measured, not assumed). Must not happen: a file over 2 MiB or more than the 20 MiB archive cap.
9. **D9: no work on hot threads (NODIST).** With logging on, `adb shell` `strace`-free check is not possible, so
   use a `StrictMode`-free proxy: run `dumpsys gfxinfo` before and after scrolling and a `logcat -s` filtered to
   the package for `Skipped frames` or ANR lines and attach only counts, not raw logcat (AGENTS.md). Expected: no
   ANR, no `Skipped N frames` burst correlated with a `mem` tick (a PSS sample on main would show as one). The
   PSS cost is measured once: log the time around one sample on the writer thread; if above a few ms on a
   `low_ram` device, record it for the plan's follow-up (brief risk 1).
10. **D10: toggling and lifecycle.** Rotate or change locale while logging is on (Activity recreation); scroll:
    `frames` lines keep coming for the new window, no duplicates from a stale tracker (line count per window
    stays one). Background and foreground the app twice: frames resume.
11. **D11: privacy scan (AC-11a, AC-7).** With logging on, type a known string into search (`qwertzuiop12345`), run
    a session, then scan all session files with `tools/mobile-evidence-audit.sh` and `grep` for each typed character
    sequence and for `"key":` values: only allow-list wire names or `other` appear.
12. **D12: minified build (AC-S2-R8).** Build `devBenchmarkRelease` once through `./tools/agentw`, install-preserve,
    toggle on, scroll, hold a key, wait one `mem` period. Expected: `frames`, `key`, `key_lat` and `mem` lines,
    `scr` is a readable class name, no `a.b.c`-style names. Do not generate a baseline profile.

Evidence is only the line structure, counts and the `jq` summaries, not raw files (no account data, no
authenticated responses).

### MANUAL (owner / real TV, not a blocker for this run)

- M1: real-TV pass: `frames` under a hardware GPU (true jank, true `p99`), `key_lat` realistic, `mem` PSS cost.
  Needs the owner's explicit authorization for a physical device.

## Perf track

Metrics (all relative, one emulator, one build type, alternating sets, same host-load window stated):

| Metric | How measured | Off vs base threshold | On bound |
|---|---|---|---|
| cold start | `am start -W` TotalTime after `am force-stop`, 8 runs per set, first run dropped, alternating BASE and NEW sets, A/B as in the S1 evidence | pooled median delta within `max(5 percent, 2 x SD of baseline runs)` (S1: +74 ms on a 2429 ms median, accepted) | information only, but the on median must not exceed off by more than the same `max(5 percent, 2 x SD)` |
| frame cost on the UI thread | `dumpsys gfxinfo <pkg> reset`, then the fixed script (Home: 6 DOWN, 8 RIGHT, 8 LEFT, 6 UP), 3 runs per mode, record `Total frames rendered`, `Janky frames`, p50/p90/p95/p99 | `p50` of frame time off within the baseline run-to-run spread; jank percentage not worse than 1 point (jank is about 99 percent here, so this is a sanity bound, not evidence) | `p50` on within the same spread of off; if above, record the load and repeat once before failing |
| `dispatchKeyEvent` cost | main-thread timing of `dispatchWithProbe` on/off is not directly observable from outside; use the unit proof (K1: off path is one flag read) plus review, and on the device compare `p50` above | n/a | n/a |
| memory sample cost | one-shot: log `nanoTime` before and after `DebugMemorySampler.sample` on the writer thread (a temporary local measurement, not committed) | n/a | PSS under a few ms on this device, else follow brief risk 1 |
| allocation per frame | unit F10 and K13 (`ThreadMXBean`, with negative control) | zero by construction | under 64 KB over 200 000 calls |
| log volume | bytes per line measured from a real session; file bytes per active minute | n/a | on-device per-minute bytes times 60 min stays under the 20 MiB budget stated in S1 |

`{run}/baseline.md` must contain, captured on the base commit `cd16eb47` build before implementation: cold start
median, SD and 7 runs; the gfxinfo summary (frames, janky, p50/p90/p95/p99, 3 runs); the host load during capture
(`uptime`); the exact script; and the emulator serial and build variant. Pass rule: logging off is within noise of
that baseline by the cold-start and gfxinfo thresholds above (re-measured A/B on the same load if the host load
moved by more than a factor of two, as S1 did); logging on cost is bounded as in the table and recorded as
information beside the off result. A result outside the threshold that disappears on re-measure under equal load
is reported as noise with both sets shown; one that persists is a fail and goes back to the code.

## UNTESTABLE

- `AC-12` "the callback allocates nothing" in the strict sense: only the proxy is testable (F10 and K13 with a
  negative control), the absolute statement stays a code-review item (TH3).
- `AC-S2-NODIST` "no PSS on the main thread" on a real device: unit tests prove the sampler's thread through the
  injected `MemorySampler`, but `DebugMemorySampler` itself (`android.os.Debug`) cannot run on the JVM; covered by
  TH3 review and device D9.
- `AC-13` clock-base agreement (R8) between `KeyEvent.eventTime` and `frameStartNanos`: testable only on a device
  (D7); the unit tests assume a shared base.
- `AC-12` on a real GPU and absolute jank numbers: not available on the emulator (MANUAL M1).
- `MainActivity.dispatchKeyEvent` itself without an Activity: its logic is moved to `dispatchWithProbe` and
  tested there; the one-line call from the override is covered by D5 and review.
