# Readiness 1: telemetry-frames-keys-memory

Judge: final-judge, mode readiness, round 1, 2026-10-16.
Inputs read: spec `docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md` (ACs, Out of scope,
D1-D16, Test design, Open questions, Decisions), plan `docs/superpowers/plans/2026-10-16-telemetry-frames-keys-memory.md`
(untracked in the worktree at HEAD `10e7fa19`), run files `brief.md`, `design-architect.md`, `test-design.md`
(via the spec), `baseline.md`, `state.md`, `research.md: 5`. Code checked in the worktree; `git diff cd16eb47 10e7fa19`
touches only the spec, so the plan's line numbers are the base code.

**Verdict: READY**

## 1. Every AC has a plan task and Test design rows

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

## 2. Citations sampled (all resolve to the claimed symbol)

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

## 3. Build stays green, order follows dependencies

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

## 4. Scope and open questions

- Out of scope respected: no `slow_frame`, no `mem` on screen change, no sub-50 ms key answers, no image-cache
  size, no `frameDurationTotalNanos`; `PuberApp.kt`, `FlowComponent.kt`, `TabComponent.kt`, `TelemetryModule.kt`,
  `proguard-rules.pro` untouched.
- Q1-Q3 and architect OQ1-OQ2 are answered (spec Decisions); none blocks.

## 5. The seven plan refinements against the approved ACs

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

## Must fix

None.

## Notes for the implementer and the gate (not blocking)

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
