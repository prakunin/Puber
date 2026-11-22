# Measure and fix the first D-pad response after the splash reveal on a real TV

Status: approved
Date: 2026-11-22
Slug: startup-first-input-on-tv
Kind: improvement
Size: M
Tracks: perf

## Problem / Why

Status: **plausible** for the box; the gate mechanism is **confirmed** from code, the dominant input on the box is not measured yet (AC-3 decides it).

| Finding | Where | Status |
|---|---|---|
| The splash lifts before the cap only when `ready` is set and no request of the whole loader is in flight; any request, visible or not, issued before or after `ready`, re-arms `imagesBusy` | `core/startup/RevealGate.kt:78`, `core/startup/StartupImageActivity.kt:109-115` | confirmed (code) |
| Requests enter "in flight" only after layout gives them a size, so row cards, LazyRow prefetch and hero pages keep joining after `ready`; 4K-class wide posters decode to 533x300 with 4 parallel decodes (`PuberApp.kt:109-145`, `VideoItemUIMapper.kt:83-89`) | research.md: Current state in code, Image work on Home | plausible (emulator trace only) |
| Nothing records why the splash lifted or when `ready` and the last image-idle edge happened; "revealed by cap" in past evidence is inferred | `core/telemetry/StartupMarks.kt:16-18` | confirmed |
| First-key cost after the reveal: RenderThread first frame (1.4 s of shader compile on swiftshader), decode burst, and `StartupSettled` work released ~250 ms after the reveal | research.md: Related work (Round 2b); `StartupSettled.kt:46-48`, `MainActivity.kt:93` | plausible; shader cost not expected on a hardware GPU (unverified for KM6) |
| `key_lat` drops keys answered more than 1 s late and keys within 1 s of a pending one; dropped-under-splash keys arm the matcher | `core/telemetry/KeyProbe.kt:9,84,94-96`, `core/startup/StartupKeyFilter.kt:22-33` | confirmed (code) |

### Resolved research questions

| Research Q | Resolution |
|---|---|
| Q1 base for the A/B | 2fd851a4 (with AC-1/AC-2 telemetry) is the gated baseline; one set of 8428c946 is recorded as a launch-to-content reference only (AC-3) |
| Q4 permanent telemetry and `KeyProbe` change | Yes, standing approval of the recommended answer; AC-1, AC-2 |
| Q2, Q3 | Open questions 1 and 2 below |

## Acceptance criteria

- AC-1. Given a cold start, When the `startup` telemetry line is emitted, Then it carries the reveal cause (`ready` / `cap` / `forced`), `ready_req` (when `RevealGate.markReady()` was called with the gate begun, first wins), the time `ready` took effect, `ready_drained` (recorded also after the reveal), the time of the last images busy-to-idle edge before the reveal, and the in-flight request count at the reveal; and the line is emitted once first content and the reveal exist and either the cause is `forced`, or `ready` has taken effect with its held requests drained (or none held), or 5 s have passed since the reveal; a start that never reveals still emits by the 60 s fallback (unit tests on `RevealGate`, `StartupMarks`, `TelemetryEncoder`, `TelemetryRuntime`).
- AC-2. Given a handled key whose first frame after dispatch starts 1-5 s later, or a second key while one is pending, When the frames arrive, Then a `key_lat` is emitted for each (none silently expired), each `key_lat` line, `expired` included, carries `wait_ms` (key event time to `dispatchKeyEvent`), and a key dropped under the splash is counted as dropped and does not arm the latency matcher (unit tests on `KeyLatencyMatcher`, `dispatchWithProbe`, `TelemetryEncoder`).
- AC-3. Met at 5fb5de17 (`docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md`). Given base 2fd851a4 plus AC-1/AC-2 only, When at least 10 cold starts of `devBenchmarkRelease` run on the box with keys sent at fixed (launch +3 s) and reveal-relative (reveal +300 ms, +1000 ms) anchors and an atrace of 3 starts, Then the evidence file records per start: cause, ready, revealed, first-key latency per anchor, dropped keys; plus one set of 8428c946 launch-to-visible-content (observation).
- AC-4. Removed, moved to `startup-reveal-gate-on-slow-tv`.
- AC-5. Given the build with the amended AC-1/AC-2 on the box, When at least 12 cold starts run as in AC-3, Then the evidence records per start `cause`, `first_content`, `ready_req`, `ready`, `ready_in_flight`, `ready_drained`, `revealed`, with `ready` present in every start that lands on Home content, and per start `ready - revealed` and `ready_req - first_content` (observation).
- AC-6. Given the build with the amended AC-1/AC-2 on the box, When keys are sent at the AC-3 anchors in at least 12 starts as installed, Then the evidence records the compiler filter (`dumpsys package com.kino.puber.stage`, Dexopt state), per start `lat_ms` and `wait_ms`, per-anchor median and p95, and for 3 atrace starts the main-thread slices over 100 ms by name, with `0 <= wait_ms <= lat_ms` on every line (observation, no threshold). Conditional: only if the owner authorises `cmd package compile -m speed-profile -f com.kino.puber.stage` on the box, the same set is repeated in that state; without authorisation that half is recorded as NOT CHECKED.
- AC-7. Removed, moved to `startup-reveal-gate-on-slow-tv`.
- AC-8. Removed, moved to `home-hero-late-insert`.
- AC-9. Given the full check, When `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then it passes, with `RevealGateTest`, `StartupMarksTest`, `TelemetryEncoderTest`, `KeyLatencyMatcherTest`, `TelemetryRuntimeTest` changed only where AC-1 or AC-2 change their contract, and `StartupSettledTest`, `StartupRevealTest`, `StartupKeyFilterTest`, `HomeLaunchFocusSettledTest`, `StartupImageActivityTest` unchanged since `f48beb86` (the phase-A commit; phase A rewrote `StartupImageActivityTest` per AC-1).

## Out of scope

From the brief and research.md: the 4K poster decoding stays in card-image-size-tier; changing the 250 ms fade is track `ui`. Amendment 2026-11-22 (gate-1 delta): the bounded image hold, cap value and deferred-work window go to startup-reveal-gate-on-slow-tv; the hero at fade end and the scrolled Home at the reveal go to home-hero-late-insert; the first-key target goes to release-startup-compilation.

## Design

Base c506d0bd; phase A 93e215a2..f48beb86 (spec Design, round 1, implemented and unchanged); box baseline 5fb5de17 (`docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md`, cited below as "baseline"). This round replaces round 1 for everything after plan Task 6. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/` or `tools/`.

### Findings

| Question | Answer | Evidence | Status |
|---|---|---|---|
| Why `ready` is absent from every line | The line is emitted when `first_content` and `revealed` both exist (`core/telemetry/StartupMarks.kt:70-72`), so a `ready` stamped after the reveal is never written; `markReady` is stamped whenever it comes (`StartupMarks.kt:47-49`, called from `core/startup/RevealGate.kt:56-63`, also after the reveal). Absent `ready` therefore means "not before the reveal", not "never" | code | confirmed |
| Why `ready` lands after the cap on the box | `ready` needs, in order: the Hot section value (`ui/feature/home/vm/HomeVM.kt:356`) published, the hero item composed (`ui/feature/home/component/HomeScreenContent.kt:279-293`), the claim (`:353-369`: one `scrollToItem(0)` plus up to 5 frames), a recomposition and `LaunchedEffect` (`:169-178,227-230`), the VM action (`HomeVM.kt:220`), then a main-looper post and two Choreographer frames (`core/startup/StartupReveal.kt:78-83`). That is 6-9 main-thread frames after the hero data; the baseline has 6-8 frames over 200 ms in the first 4.5 s. The 16 starts revealed under 3000 ms all have `in_flight` 4 and no hero on screen; the slower starts have 8-17 in flight and a skeleton hero, i.e. the hero arrived before the cap and its composition delayed the cap's own `LaunchedEffect` (`MainActivity.kt:83-88`). Hero data time and frame-chain time are not separable from the current line | baseline Per start; code | plausible |
| What the ~3.2 s first-key latency is made of | `lat_ms` = answering frame start + its UI-thread duration - `KeyEvent.eventTime` (`core/telemetry/KeyProbe.kt:120`, `core/telemetry/FrameTracker.kt:22`). `eventTime` is stamped by `input` at injection, so `lat_ms` includes the wait in the input queue while the main thread is busy; the baseline puts injection-to-app at 0.78-3.44 s (median 1.44 s), the rest is wait for the next frame plus that frame. Anchor A (key reaches the app about launch +3 s, before the splash is gone and before any `StartupSettled` work, `MainActivity.kt:91`, `core/startup/StartupSettled.kt:46-48`) has the same median as B and C, so the cost is a main thread saturated from about launch +3 s to +8 s, not the reveal moment and not the settled work | baseline Summary, Limits; code | confirmed (formula), plausible (split) |
| JIT / baseline profile | `benchmarkRelease` is `initWith(release)` (`app/build.gradle.kts:216-219`) with `profileinstaller` and a baseline profile merged from `app/src/main/generated/baselineProfiles/baseline-prof.txt` (`app/build.gradle.kts:435-446`), stale since 1.0.0 (`.flow/runs/release-startup-compilation/state.md`). An APK installed by `adb install` on Android 10 is not compiled from its embedded profile at install; it runs interpreted/JIT until background dexopt. The baseline's JIT pool busy 5.3-5.7 s matches. The compiler filter was not recorded, so both the baseline and 8428c946 sets ran in an unknown, probably uncompiled, state | code; baseline atrace row | plausible |
| Emit the line later | Yes: `ready`, `ready_drained` and the time Home asked for the reveal are what the "why after the cap" question needs (Decisions) | above | decided |
| Home scrolled at the reveal | Rows publish as sections answer and the hero item exists only once Hot has a value (`HomeScreenContent.kt:279-293`, comment `:303-307`); a cap reveal before that shows the rows at the top with no hero, and the hero then appears above them (or is inserted above a key-anchored first visible row) and the claim scrolls to it (`:358`). A key pressed first sets `userDroveFocus` and disables that claim (`:217,249`). It is a Home loading-layout and focus defect exposed by the cap reveal, not part of the gate or key path this run changes | baseline layout column (17 of 36); code | plausible |
| Phase B premise | AC-4 bounds an image wait that never runs on the box (no start reaches `ready` before the cap); AC-7 delays work that starts after the latency is already 3 s (anchor A). Neither addresses the saturated main thread, so AC-5, AC-6 and AC-8 cannot be met by the planned phase B | baseline; rows above | confirmed |

### Decisions

| Question | Decision | Why |
|---|---|---|
| Run shape | Not size L. Descope: this run ships phase A (AC-1, AC-2, AC-3 done), the telemetry delta below and one attribution re-measure; AC-4, AC-7, AC-8 and the AC-5/AC-6 thresholds leave the run (AC delta, Open question 2) | Findings, "Phase B premise"; a fix needs the attribution this run can produce |
| When the `startup` line is emitted | Once `first_content` and `revealed` exist and one of: cause `forced`; `ready` stamped and (`ready_in_flight` = 0 or `ready_drained` stamped); 5000 ms since `revealed` (`LINE_WAIT_AFTER_REVEAL_MS`). The 60 s fallback (`core/telemetry/TelemetryRuntime.kt:55,125`) stays for a start that never reveals | Records `ready` and drain after a cap reveal; restored and update-prompt starts (`StartupReveal.kt:53,63-65`) still emit at once; a Home that never marks ready (error) still emits |
| Where emission runs | `StartupMarks` takes `schedule: (delayMs: Long, task: () -> Unit) -> Unit`, wired to `sink::schedule` (`TelemetryRuntime.kt:163`, `core/telemetry/TelemetrySink.kt:128-130`). A mark that completes the line, or `markRevealed` arming the 5 s bound, calls `schedule`; `emit` runs on the telemetry thread and stays single-shot (`StartupMarks.kt:74-75`) | Gate marks are called inside the gate lock (`core/startup/RevealObserver.kt:13`); `schedule` only enqueues on a `ScheduledThreadPoolExecutor` (`core/telemetry/TelemetryExecutor.kt:32-34`) and never calls back |
| New `startup` field `ready_req` | Time `RevealGate.markReady()` was called with the gate begun (`RevealGate.kt:54-55`), before the two-frame wait; first wins. New observer method `onReadyRequested()` with a no-op default in the interface | Splits `first_content` → `ready` into "Home asked" and "two frames later"; the default keeps `RecordingRevealObserver` and its tests as they are |
| `ready_drained` after the reveal | `onHeldDrained` is reported whenever `begun`, also after the reveal; `img_idle` stays before-the-reveal only (`RevealGate.kt:81-84`) | A cap reveal is followed by `ready` and its drain; AC-1 defines `img_idle` as the last edge before the reveal |
| New `key_lat` field `wait_ms` | `dispatchNs / 1e6 - eventMs` (time from the key's event time to `dispatchKeyEvent`), on every `key_lat` line including `expired` (`KeyProbe.kt:114-122`) | Splits each latency into input-queue wait and dispatch-to-frame per start, not only on 3 atrace starts. `System.nanoTime` and `uptimeMillis` are both `CLOCK_MONOTONIC` on Android |
| Compile state of measured builds | qa records the compiler filter of `com.kino.puber.stage` (`adb -s <serial> shell dumpsys package com.kino.puber.stage`, Dexopt state) before each set. The re-measure runs two states: as installed, then after `cmd package compile -m speed-profile -f com.kino.puber.stage` (Open question 1) | Separates JIT cost from app work; speed-profile is the state background dexopt reaches by itself |
| Rules G and Q | Removed with AC-4 and AC-7 | No grace or quiet window is built |
| `grace` cause (round 1 Open question 1) | Withdrawn; causes stay `ready`/`cap`/`forced` | No grace timer |

### AC delta (gate-1 delta)

| AC | Change |
|---|---|
| AC-1 | Amend: the line also carries `ready_req`; `ready_drained` is recorded after the reveal too; the line is emitted once first content and the reveal exist and either the reveal was forced, or `ready` has taken effect with its held requests drained (or none held), or 5 s have passed since the reveal. Cause list stays `ready`/`cap`/`forced` (the `grace` amendment is withdrawn). Unit tests on `RevealGate`, `StartupMarks`, `TelemetryEncoder` |
| AC-2 | Amend: each `key_lat` line also carries `wait_ms`, the time from the key's event time to its dispatch (unit tests on `KeyLatencyMatcher`, `TelemetryEncoder`) |
| AC-3 | Unchanged; met at 5fb5de17 |
| AC-4 | Removed; deferred to follow-up `startup-reveal-gate-on-slow-tv` |
| AC-5 | Replace: "Given the phase-B build on the box, When at least 12 cold starts run as in AC-3, Then the evidence records per start `cause`, `first_content`, `ready_req`, `ready`, `ready_in_flight`, `ready_drained`, `revealed`, with `ready` present in every start that lands on Home content, and per-start `ready - revealed` and `ready_req - first_content` (observation)" |
| AC-6 | Replace: "Given the phase-B build on the box, When keys are sent at the AC-3 anchors in at least 12 starts as installed and, if authorised, 12 starts after `cmd package compile -m speed-profile -f`, Then the evidence records per state the compiler filter, per start `lat_ms` and `wait_ms`, per-anchor median and p95, and for 3 atrace starts per state the main-thread slices over 100 ms by name (observation, no threshold)" |
| AC-7 | Removed; deferred to `startup-reveal-gate-on-slow-tv` if AC-6 attributes latency to settled work |
| AC-8 | Removed; the baseline hero count (1 of 36) and the scrolled-Home finding go to follow-up `home-hero-late-insert` |
| AC-9 | Amend: test classes change only where AC-1 or AC-2 change their contract; `StartupSettledTest`, `StartupRevealTest`, `StartupKeyFilterTest`, `HomeLaunchFocusSettledTest`, `StartupImageActivityTest` unchanged |

Follow-up runs (created as ideas by the conductor):

| Run | Carries |
|---|---|
| `release-startup-compilation` (exists) | First-key p95 target (old AC-6 threshold) measured in a recorded compile state; profile refresh |
| `startup-reveal-gate-on-slow-tv` (new) | Gate cost on the box (8428c946 rail visible 2.01 s vs splash gone 4.41 s), cap value, old AC-4, AC-5 threshold, AC-7; measured after the compilation run |
| `home-hero-late-insert` (new, track ui) | Home revealed before Hot settles: rows at the top, hero appearing above, claim scroll; old AC-8 |

### Changes by layer

API / repository: N/A, no repository changes.

ViewModel state and actions: N/A, `HomeVM.kt:63,220` and `core/ui/PuberVM.kt:94` keep calling `StartupReveal.markReady`.

Compose content and resources: N/A.

Startup core and telemetry:

| File | Change |
|---|---|
| `core/startup/RevealObserver.kt:15-34` | `fun onReadyRequested() {}` (default body); `None` inherits it |
| `core/startup/RevealGate.kt:54-56` | `markReady`: inside the existing `synchronized`, when `begun`, call `observer.onReadyRequested()` |
| `core/startup/RevealGate.kt:78-85` | `onHeldDrained` condition `begun` only; `onImagesIdle` keeps `begun && !revealedOnce` |
| `core/startup/StartupReveal.kt:18-34` | Observer forwards `onReadyRequested` to `Telemetry.startupMarks?.markReadyRequested()` |
| `core/telemetry/StartupMarks.kt:13-18` | Constructor param `schedule`; new `readyReqMs` atomic; `markReadyRequested()` first wins |
| `core/telemetry/StartupMarks.kt:36-72` | `markFirstContent`, `markRevealed`, `markReady`, `markHeldDrained`, `markRevealCause` call `schedule(0) { emitWhenComplete() }` (the first two may call it directly: main thread, no lock); `markRevealed` also `schedule(LINE_WAIT_AFTER_REVEAL_MS) { emit() }`; `emitWhenComplete` uses the rule in Decisions; `emit` passes `readyReqMs` |
| `core/telemetry/TelemetryRuntime.kt:163` | Pass `sink::schedule` |
| `core/telemetry/TelemetryEvent.kt:131-147` | `Startup.readyReqMs: Long? = null` |
| `core/telemetry/TelemetryEvent.kt:218-227` | `KeyLat.waitMs: Long? = null` |
| `core/telemetry/TelemetryEncoder.kt:67-80` | `ready_req` relative to process start, only when non-null |
| `core/telemetry/TelemetryEncoder.kt:121-126` | `wait_ms` when non-null |
| `core/telemetry/KeyProbe.kt:114-122` | `answer` sets `waitMs = p.dispatchNs / NS_PER_MS - p.eventMs` on both the expired and the latency line |

Tests (JUnit 5, `app/src/test/kotlin/com/kino/puber/core/`):

| Test | Change | AC |
|---|---|---|
| `startup/RevealGateTest.kt` | New: `markReady_reportsReadyRequestedBeforeTheFrameWait`, `heldDrained_reportedAfterACapReveal`; `imagesIdleEdge_reportedOnlyBeforeTheReveal` (`:204`) and `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (`:220`) unchanged | AC-1 |
| `telemetry/StartupMarksTest.kt` | Fixture takes an immediate or recording `schedule`. Contract change: `oneLineWithAscendingMarksOnFirstContent` (`:15`), `lineWaitsForRevealedAndCarriesBothMarks` (`:31`), `oneLineWhenRevealedComesBeforeFirstContent` (`:44`) also mark `ready`; `newMarksNeverEmitTheLine` (`:142`) becomes `gateMarksEmitOnlyThroughSchedule`. New: `lineWaitsForReadyAfterACapReveal`, `lineWaitsForHeldDrainWhenReadyHeldRequests`, `forcedRevealEmitsWithoutReady`, `lineEmitsFiveSecondsAfterRevealWithoutReady`, `readyRequestedIsCarriedOnTheLine` | AC-1 |
| `telemetry/TelemetryEncoderTest.kt` | New: `startupWritesReadyRequested`, `keyLatWritesWaitMs` | AC-1, AC-2 |
| `telemetry/KeyLatencyMatcherTest.kt` | New: `keyLatCarriesDispatchWait`, `expiredKeyLatCarriesDispatchWait` | AC-2 |
| `telemetry/TelemetryRuntimeTest.kt` | Guard: the 60 s fallback still emits a start that never reveals | AC-1 |

### Alternatives rejected

- Keep round-1 phase B (G, Q, parallelism A/B): targets mechanisms the baseline shows are not on the box's critical path.
- A second `startup_late` line for post-reveal marks: two lines per start for one fact.
- Emit from inside the gate observer: `record` would run inside the gate lock.
- Post emission to the main looper: the main thread is the saturated resource being measured.
- Fix the scrolled Home here: a Home loading-layout change, track ui, outside this run's layers.
- Lower the cap now: a product decision without compiled-state numbers.

### Risks

| Risk | Kind | Where it lives | Guard |
|---|---|---|---|
| qa kills the process (`am force-stop`) before the line is written, losing the start | data shape, rollout | `StartupMarks.kt:70-75`, `TelemetryRuntime.kt:55` | 5 s bound after `revealed`; method waits at least 6 s after the visible reveal before the next force-stop |
| `schedule` called inside the gate lock blocks or re-enters | concurrency | `RevealGate.update` (`RevealGate.kt:105-113`), `TelemetryExecutor.kt:32-34` | Enqueue only; emission on the telemetry thread; `gateMarksEmitOnlyThroughSchedule` |
| Two emit paths race (completion and 5 s bound) | concurrency | `StartupMarks.emit` (`:74-75`) | `emitted` CAS keeps one line |
| Compile-state change on the box carries into later measurements | rollout | box package state | Filter recorded before every set; as-installed set first; the next install-preserve resets it |
| `wait_ms` mixes clocks | data shape | `KeyProbe.kt:114-122` | Both `CLOCK_MONOTONIC`; device check `0 <= wait_ms <= lat_ms` |
| Lines from the baseline (emitted at the reveal) and the re-measure are compared field by field | data shape | `TelemetryEncoder.kt:67-80` | Fields are additive and keep their meaning; only emission time moves |

### Open questions

1. Authorise `adb -s 192.168.1.110:5555 shell cmd package compile -m speed-profile -f com.kino.puber.stage` on the box for the second AC-6 state? Recommended: yes (reversible, the state background dexopt reaches on its own; not in the standing list, so the owner, and the conductor runs it itself). Blocks: the compiled-state half of AC-6 only.
2. Approve the AC delta and descope (AC-4, AC-7, AC-8 removed; AC-5, AC-6 replaced by observations; three follow-up runs)? Recommended: yes. Blocks: yes (gate-1 delta).
3. Home scrolled at the reveal in scope? Recommended: no, follow-up `home-hero-late-insert` (track ui). Blocks: no.

## Test design

Round 2, after the gate-1 delta. Base c506d0bd; phase A f48beb86; box baseline 5fb5de17. ACs: `brief.md#acceptance-criteria` (amended). Design: `design-architect.md` round 2 (Decisions, Changes by layer). Unit tests are JUnit 5 under `app/src/test/kotlin/com/kino/puber/core/`; device rows run through qa-device on the box `192.168.1.110:5555`, `devBenchmarkRelease` (`com.kino.puber.stage`), never the emulator.

Fakes to reuse: `FakeTelemetryClock`, `RecordingRevealObserver`, the `now` clock and `afterFrame` list in `RevealGateTest`, `arm`/`frame` in `KeyLatencyMatcherTest`, `ManualTelemetryExecutor` (`scheduledDelays`, `runScheduled`) with a real `TelemetrySink` in `TelemetryRuntimeTest`. New in `StartupMarksTest`: a recording `schedule` (list of `delayMs` to task, run by the test) next to an immediate one (`{ _, task -> task() }`).

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Phase A or the round-2 `onHeldDrained` change alters when the splash lifts | 2 | 3 | 6 | AC-1 guard rows on `RevealGateTest`, `StartupImageActivityTest` |
| R2 | Reveal cause misreported (`ready`/`cap`/`forced`); AC-5 reads it | 2 | 3 | 6 | AC-1 cause rows |
| R3 | Dropped-key handling changes the `dispatchKeyEvent` return value or still arms the matcher | 2 | 3 | 6 | AC-2 rows on `DispatchWithProbeTest`, `KeyProbeGateTest` |
| R4 | `schedule` or `emit` runs inside the gate lock (blocks, re-enters) | 2 | 3 | 6 | `gateMarksEmitOnlyThroughSchedule` |
| R5 | `am force-stop` before the delayed line is written loses the start | 2 | 3 | 6 | `lineEmitsFiveSecondsAfterRevealWithoutReady` (bound is 5000 ms); Manual check 2 (wait, one line per start) |
| R6 | Line still emitted at the reveal, so `ready` stays absent after a cap reveal (the round-2 finding) | 3 | 2 | 6 | `lineWaitsForReadyAfterACapReveal`, `heldDrained_reportedAfterACapReveal` |
| R7 | Production wiring missing: `StartupReveal` does not forward `onReadyRequested`, or `TelemetryRuntime.kt:163` does not pass `sink::schedule` (both out of unit reach: `StartupRevealTest` frozen by AC-9, factory uses `Process`) | 2 | 2 | 4 | Manual check 2: `ready_req` and post-reveal `ready` present |
| R8 | Pending-list matcher double-emits or answers the wrong key across threads | 2 | 2 | 4 | AC-2 matcher rows, concurrency guard |
| R9 | Marks mix clock bases | 2 | 2 | 4 | `revealCauseAndImageMarksAreCarriedOnTheLine`, `readyRequestedIsCarriedOnTheLine` |
| R10 | Completion and the 5 s bound (or the 60 s fallback) both emit | 1 | 3 | 3 | `completionAndFiveSecondBoundEmitOneLine`, `revealedStartWritesOneLineWhenBoundAndFallbackBothFire` |
| R11 | Box login lost on install | 1 | 3 | 3 | qa-device procedure: lease, `auth-save`, install-preserve |
| R12 | `wait_ms` mixes clocks | 1 | 2 | 2 | Accepted: both `CLOCK_MONOTONIC`; Manual check 3 asserts `0 <= wait_ms <= lat_ms` |
| R13 | Compile state from one set carries into the next | 1 | 2 | 2 | Accepted: filter recorded before every set (Manual check 3) |
| R14 | Encoder change breaks existing `startup`/`key_lat` fields | 1 | 2 | 2 | Accepted: existing encoder guards (AC-9) |

### Traceability

`red` in the phase-A table: failed on c506d0bd, green since f48beb86. `red` in the round-2 table: fails on f48beb86 (behaviour or API absent) and is run there first. `guard`: expected values stay; call shape may change (the `schedule` constructor parameter).

#### Phase A (written, names kept)

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `RevealGateTest.revealByReady_reportsCauseReady` | fails if the reveal edge reports no cause or a wrong one when `ready` lifts it |
| AC-1 | unit | P0 red | `RevealGateTest.revealByCap_reportsCauseCapWithInFlightCount` | fails if `onCap` is reported as `ready` or the in-flight count at reveal is wrong |
| AC-1 | unit | P0 red | `RevealGateTest.revealNow_reportsCauseForced` | fails if `revealNow` is not reported as `forced` |
| AC-1 | unit | P0 red | `RevealGateTest.markReady_reportsInFlightCountWhenReadyTakesEffect` | fails if the count is taken at `markReady` instead of after the frame |
| AC-1 | unit | P0 red | `RevealGateTest.imagesIdleEdge_reportedOnlyBeforeTheReveal` | fails if the round-2 change lets post-reveal idle edges reach the observer |
| AC-1 | unit | P1 red | `RevealGateTest.heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` | fails if `onHeldDrained` fires while a held request is in flight, or a later request delays it |
| AC-1 | unit | P0 red | `RevealGateTest.sameRequestFinishedTwice_doesNotUncoverOthers` | fails if the identity set becomes a counter |
| AC-1 | unit | P0 guard | `RevealGateTest.readyWaitsForImagesInFlight`, `imagesSettlingAfterReadyUncovers`, `capUncoversWhileImagesInFlight`, `revealNowIgnoresContentAndImages`, `imagesBusyAfterRevealDoNotCoverAgain`, `beginKeepsImagesBusyFromBeforeIt`, `remainingCapMs_countsFromBegin` | fails if the covering rule or the 2.5 s cap changes (R1) |
| AC-1 | unit | P0 guard | `StartupImageActivityTest.requestIsInFlightFromResolvedSizeUntilSuccess`, `errorAndCancelEndARequest`, `cancelWithoutResolvedSizeLeavesIdle` | fails if forwarding misses an end callback or treats `onStart` as in flight |
| AC-1 | unit | P1 red | `StartupImageActivityTest.inFlightCountAtRevealCountsDistinctRequests` | fails if repeated callbacks for one request change the count |
| AC-1 | unit | P0 red | `StartupMarksTest.revealCauseAndImageMarksAreCarriedOnTheLine` | fails if `cause`, `readyMs`, `imagesIdleMs`, `inFlight`, `readyInFlight`, `readyDrainedMs` are missing or off the marks clock |
| AC-1 | unit | P1 red | `StartupMarksTest.revealCauseKeepsTheFirstValue`, `imagesIdleKeepsTheLastValue`, `imageMarksAreAbsentWhenNotReached` | fails if first/last-wins or null-when-unreached breaks |
| AC-1 | unit | P0 red | `TelemetryEncoderTest.startupWritesRevealCauseAndImageFields` | fails if wire names `cause`, `ready`, `img_idle`, `in_flight`, `ready_in_flight`, `ready_drained` are absent or not relative to process start |
| AC-1 | unit | P1 red | `TelemetryEncoderTest.startupOmitsImageFieldsNotReached` | fails if null fields are written |
| AC-1 | unit | P1 guard | `TelemetryEncoderTest.startupWritesRevealed`, `startupOmitsMarksNotReached`, `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` | fails if existing fields change (R14) |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.eachPendingKeyIsAnsweredByTheFirstFrameAfterIt`, `frameBetweenTwoPendingKeysAnswersOnlyTheEarlier` | fails if a second pending key is not armed or a frame answers a key dispatched after its start |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.keyAnsweredBetweenOneAndFiveSecondsIsEmitted` | fails if expiry returns to 1 s |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.keyUnansweredPastFiveSecondsIsEmittedExpiredWithoutLat` | fails if an expired key is dropped silently or carries `latMs` |
| AC-2 | unit | P2 red | `KeyLatencyMatcherTest.pendingListHoldsAtMostSixteenKeys` | fails if the list grows without bound |
| AC-2 | unit | P0 guard | `KeyLatencyMatcherTest.fiftyIsNotSlowAndFiftyOneIs`, `answeredEntryIsConsumedAndNotEmittedTwice`, `unhandledKeyNeverArms`, `frameStartingBeforeDispatchDoesNotAnswer` | fails if the 50 ms floor or single consumption breaks |
| AC-2 | unit | P0 guard | `KeyLatencyMatcherConcurrencyTest.armOnOneThreadAnswerOnAnotherNeverDoubleEmits` | fails if the pending list double-emits across threads (R8) |
| AC-2 | unit | P0 red | `DispatchWithProbeTest.droppedKeyReturnsTrueWithoutCallingSuper`, `droppedKeyIsProbedAsDropped` | fails if a dropped key reaches super, returns false, or is probed as handled (R3) |
| AC-2 | unit | P0 guard | `DispatchWithProbeTest.offCallsSuperOnceAndNeverTheProbe`, `onReturnsSupersValueAndProbesAfterSuper`, `probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch` | fails if the return value of a non-dropped key changes |
| AC-2 | unit | P0 red | `KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` | fails if a dropped key still arms (two lines 301 and 201 instead of one 201) |
| AC-2 | unit | P0 red | `KeyProbeGateTest.droppedFirstDownIsCountedOnceAndRepeatsAreNot` | fails if `dropped_keys` counts repeats or UP, or misses the first DOWN |
| AC-2 | unit | P1 red | `StartupMarksTest.droppedKeysAreCountedOnTheLine` | fails if `droppedKeys` is not carried to the line |
| AC-2 | unit | P1 red | `TelemetryEncoderTest.keyLatExpiredOmitsLatMs` | fails if an expired line writes `lat_ms` or omits `expired` |
| AC-3 | device | P0 | Manual check 1 (done, 5fb5de17) | baseline per start: cause, ready, revealed, per-anchor first-key `key_lat`, dropped keys; 8428c946 launch-to-content set |

#### Round 2

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `RevealGateTest.markReady_reportsReadyRequestedBeforeTheFrameWait` | observer gets `onReadyRequested` with `afterFrame` not yet run, and none when the gate is not begun; fails if reported after the frame, before `begin`, or not at all |
| AC-1 | unit | P0 red | `RevealGateTest.heldDrained_reportedAfterACapReveal` | begin, one request in flight, `onCap`, `markReady` + frame, request ends: `onHeldDrained` reported; fails if the `!revealedOnce` condition stays (`RevealGate.kt:81-84`) |
| AC-1 | unit | P0 red | `StartupMarksTest.lineWaitsForReadyAfterACapReveal` | first content, reveal with cause `cap`: no line; `markReady(0)` then queued tasks run: one line with `readyMs`; fails if the line is still written at the reveal (R6) |
| AC-1 | unit | P0 red | `StartupMarksTest.lineWaitsForHeldDrainWhenReadyHeldRequests` | `markReady(2)` after the reveal: no line until `markHeldDrained`; line carries `readyDrainedMs`; fails if `ready` alone completes a line with held requests |
| AC-1 | unit | P0 guard | `StartupMarksTest.forcedRevealEmitsWithoutReady` | cause `forced` with first content and reveal: line at once, `readyMs` null; fails if restored/update-prompt starts wait for `ready` or the bound |
| AC-1 | unit | P0 red | `StartupMarksTest.lineEmitsFiveSecondsAfterRevealWithoutReady` | `markRevealed` schedules a 5000 ms task; no line before it runs, one line after with `revealedMs` and null `readyMs`; fails if the bound is missing or not 5000 ms (R5) |
| AC-1 | unit | P0 red | `StartupMarksTest.readyRequestedIsCarriedOnTheLine` | `readyReqMs` stamped by `markReadyRequested` on the marks clock; fails if absent or taken at `markReady` |
| AC-1 | unit | P1 red | `StartupMarksTest.readyRequestedKeepsTheFirstValue` | fails if a later request overwrites the first |
| AC-1 | unit | P0 red | `StartupMarksTest.gateMarksEmitOnlyThroughSchedule` (replaces `newMarksNeverEmitTheLine`) | recording `schedule` not run: `markReady`, `markReadyRequested`, `markHeldDrained`, `markRevealCause`, `markImagesIdle`, `onKeyDropped` record nothing, even with the line complete; fails if any gate-side mark calls `record` on the caller's thread (R4) |
| AC-1 | unit | P1 red | `StartupMarksTest.completionAndFiveSecondBoundEmitOneLine` | completion task and the 5000 ms task both run: one line; fails if the single-shot guard is lost (R10) |
| AC-1 | unit | P1 guard | `StartupMarksTest.oneLineWithAscendingMarksOnFirstContent`, `lineWaitsForRevealedAndCarriesBothMarks`, `oneLineWhenRevealedComesBeforeFirstContent` (now also mark `ready`), `fallbackLineHasNoRevealedWhenItNeverCame`, `fallbackLineAfterSixtySecondsWithMarksSoFar`, `revealedKeepsTheFirstValue`, `appComposedKeepsTheFirstValue` | fails if marks or the fallback line change beyond the emission rule |
| AC-1 | unit | P0 red | `TelemetryEncoderTest.startupWritesReadyRequested` | fails if `ready_req` is absent, not relative to process start, or written when null |
| AC-1 | unit | P0 guard | `TelemetryRuntimeTest.startThatNeverRevealsEmitsAtTheSixtySecondFallback` | marks built on `sink::schedule`, first content only, scheduled tasks run: one `startup` line without `revealed`; fails if the 60 s fallback is dropped or the new rule blocks it |
| AC-1 | unit | P1 guard | `TelemetryRuntimeTest.coldStartResumeBuildsStartupMarksAndSchedulesFallback` | fails if `start` schedules anything but the 60 s fallback |
| AC-1 | unit | P1 red | `TelemetryRuntimeTest.revealedStartWritesOneLineWhenBoundAndFallbackBothFire` | marks on `sink::schedule`, first content and reveal: `scheduledDelays` holds 60000 and 5000, `runScheduled` writes exactly one `startup` line; fails if the bound does not go through the sink or two lines are written (R10) |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.keyLatCarriesDispatchWait` | `arm(970, 1_000)`, answering frame: `waitMs` 30; fails if `waitMs` is absent or uses frame time |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.expiredKeyLatCarriesDispatchWait` | expired line carries `waitMs`; fails if only latency lines set it |
| AC-2 | unit | P0 red | `TelemetryEncoderTest.keyLatWritesWaitMs` | fails if `wait_ms` is absent on latency or expired lines, or written when null |
| AC-5 | device | P0 | Manual check 2 | per-start `ready_req`, `ready`, `ready_drained` after a cap reveal reach the wire (R6, R7); one line per start (R5) |
| AC-6 | device | P0 | Manual check 3 | as-installed compiler filter, `lat_ms`/`wait_ms` per start, per-anchor median and p95, atrace slices; `0 <= wait_ms <= lat_ms` (R12) |
| AC-6 | device | P2 conditional | Manual check 4 | compiled-state half; NOT CHECKED unless the owner authorises the compile command (`state.md` Decisions) |
| AC-9 | unit | P0 guard | `check.full`: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; `git -C <wt> diff f48beb86 -- ` on `StartupSettledTest`, `StartupRevealTest`, `StartupKeyFilterTest`, `HomeLaunchFocusSettledTest`, `StartupImageActivityTest` empty | fails if a frozen test changes or the suite, detekt or lint break |

Failing-first order: `StartupMarksTest.lineWaitsForReadyAfterACapReveal` and `RevealGateTest.heldDrained_reportedAfterACapReveal` first, then the other round-2 red rows, each run on f48beb86 before the code.

### Bug

`KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` (phase A, written first, green since 4ed47c5f): on c506d0bd a dropped key arrives as `handled = true` (`MainActivity.kt:59-71`, `KeyProbe.kt:81-86`) and arms the matcher.

### Manual checks (qa-device, box `192.168.1.110:5555`)

Lease, `auth-save` before the first install, `tools/android-apk-install-preserve` of the worktree's `devBenchmarkRelease` APK, `adb -s 192.168.1.110:5555` only, `com.kino.puber.stage` only. Checks 2 and 3 use the same set of starts. Evidence in `docs/superpowers/evidence/`, audited with `tools/mobile-evidence-audit.sh`.

| # | AC | Steps | Expected | Must not happen |
|---|---|---|---|---|
| 1 | AC-3 | Done: `docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md` (5fb5de17) | Recorded | N/A |
| 2 | AC-5 | 1. Install the round-2 build. 2. Record the compiler filter (`adb -s 192.168.1.110:5555 shell dumpsys package com.kino.puber.stage`, Dexopt state). 3. 12+ cold starts: `am force-stop`, `am start -W`, wait at least 6 s after the visible reveal before the next force-stop. 4. Pull the `startup` lines | Exactly one `startup` line per start with `cause`, `first_content`, `ready_req`, `ready`, `ready_in_flight`, `ready_drained` (when `ready_in_flight` > 0), `revealed`; `ready` present in every start that lands on Home content, including `cap` starts; per start `ready - revealed` and `ready_req - first_content` tabled | A start with no line or two lines; `ready` absent on a Home start; splash stuck past the cap; crash; login lost; `com.kino.puber` touched |
| 3 | AC-6 | Same starts as check 2, no install in between: per start one `input keyevent KEYCODE_DPAD_DOWN` at an AC-3 anchor (launch +3 s, reveal +300 ms, reveal +1000 ms); atrace on 3 starts | Per start `lat_ms` and `wait_ms`; per-anchor median and p95; main-thread slices over 100 ms by name for the 3 atrace starts; `0 <= wait_ms <= lat_ms` on every latency line and `wait_ms >= 0` on `expired` lines | A `key_lat` line without `wait_ms`; `wait_ms > lat_ms`; focus lost or jumping two rows |
| 4 | AC-6 conditional | Only after the owner authorises `cmd package compile -m speed-profile -f com.kino.puber.stage`, run by the conductor: record the filter, repeat check 3 | Same set in the compiled state, filter `speed-profile` recorded | qa runs the compile itself; the half reported as met without authorisation (record NOT CHECKED instead) |

### Perf

| Metric | Source | Baseline | Pass threshold |
|---|---|---|---|
| `ready - revealed`, `ready_req - first_content` per start | `startup` line | N/A in 5fb5de17 (`ready` never written); first values come from Manual check 2 | None (AC-5 observation); set complete: 12+ starts, one line each |
| First-key `lat_ms` and `wait_ms` median, p95 per anchor | `key_lat` lines | `lat_ms` from 5fb5de17 (median about 3.2 s at all anchors); `wait_ms` absent there | None (AC-6 observation); `0 <= wait_ms <= lat_ms` on every line |
| Compiler filter per set | `dumpsys package`, Dexopt state | Not recorded in 5fb5de17 | Recorded before every set |

Same box, same build type, at least 12 cold starts.

UNTESTABLE: none

Perf baseline: .flow/runs/startup-first-input-on-tv/baseline.md and docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md (5fb5de17).

## Open questions

null

## Decisions

null
