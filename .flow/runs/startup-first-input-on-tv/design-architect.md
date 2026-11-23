# Design (architect), round 2: startup-first-input-on-tv

Base c506d0bd; phase A 93e215a2..f48beb86 (spec Design, round 1, implemented and unchanged); box baseline 5fb5de17 (`docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md`, cited below as "baseline"). This round replaces round 1 for everything after plan Task 6. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/` or `tools/`.

## Findings

| Question | Answer | Evidence | Status |
|---|---|---|---|
| Why `ready` is absent from every line | The line is emitted when `first_content` and `revealed` both exist (`core/telemetry/StartupMarks.kt:70-72`), so a `ready` stamped after the reveal is never written; `markReady` is stamped whenever it comes (`StartupMarks.kt:47-49`, called from `core/startup/RevealGate.kt:56-63`, also after the reveal). Absent `ready` therefore means "not before the reveal", not "never" | code | confirmed |
| Why `ready` lands after the cap on the box | `ready` needs, in order: the Hot section value (`ui/feature/home/vm/HomeVM.kt:356`) published, the hero item composed (`ui/feature/home/component/HomeScreenContent.kt:279-293`), the claim (`:353-369`: one `scrollToItem(0)` plus up to 5 frames), a recomposition and `LaunchedEffect` (`:169-178,227-230`), the VM action (`HomeVM.kt:220`), then a main-looper post and two Choreographer frames (`core/startup/StartupReveal.kt:78-83`). That is 6-9 main-thread frames after the hero data; the baseline has 6-8 frames over 200 ms in the first 4.5 s. The 16 starts revealed under 3000 ms all have `in_flight` 4 and no hero on screen; the slower starts have 8-17 in flight and a skeleton hero, i.e. the hero arrived before the cap and its composition delayed the cap's own `LaunchedEffect` (`MainActivity.kt:83-88`). Hero data time and frame-chain time are not separable from the current line | baseline Per start; code | plausible |
| What the ~3.2 s first-key latency is made of | `lat_ms` = answering frame start + its UI-thread duration - `KeyEvent.eventTime` (`core/telemetry/KeyProbe.kt:120`, `core/telemetry/FrameTracker.kt:22`). `eventTime` is stamped by `input` at injection, so `lat_ms` includes the wait in the input queue while the main thread is busy; the baseline puts injection-to-app at 0.78-3.44 s (median 1.44 s), the rest is wait for the next frame plus that frame. Anchor A (key reaches the app about launch +3 s, before the splash is gone and before any `StartupSettled` work, `MainActivity.kt:91`, `core/startup/StartupSettled.kt:46-48`) has the same median as B and C, so the cost is a main thread saturated from about launch +3 s to +8 s, not the reveal moment and not the settled work | baseline Summary, Limits; code | confirmed (formula), plausible (split) |
| JIT / baseline profile | `benchmarkRelease` is `initWith(release)` (`app/build.gradle.kts:216-219`) with `profileinstaller` and a baseline profile merged from `app/src/main/generated/baselineProfiles/baseline-prof.txt` (`app/build.gradle.kts:435-446`), stale since 1.0.0 (`.flow/runs/release-startup-compilation/state.md`). An APK installed by `adb install` on Android 10 is not compiled from its embedded profile at install; it runs interpreted/JIT until background dexopt. The baseline's JIT pool busy 5.3-5.7 s matches. The compiler filter was not recorded, so both the baseline and 8428c946 sets ran in an unknown, probably uncompiled, state | code; baseline atrace row | plausible |
| Emit the line later | Yes: `ready`, `ready_drained` and the time Home asked for the reveal are what the "why after the cap" question needs (Decisions) | above | decided |
| Home scrolled at the reveal | Rows publish as sections answer and the hero item exists only once Hot has a value (`HomeScreenContent.kt:279-293`, comment `:303-307`); a cap reveal before that shows the rows at the top with no hero, and the hero then appears above them (or is inserted above a key-anchored first visible row) and the claim scrolls to it (`:358`). A key pressed first sets `userDroveFocus` and disables that claim (`:217,249`). It is a Home loading-layout and focus defect exposed by the cap reveal, not part of the gate or key path this run changes | baseline layout column (17 of 36); code | plausible |
| Phase B premise | AC-4 bounds an image wait that never runs on the box (no start reaches `ready` before the cap); AC-7 delays work that starts after the latency is already 3 s (anchor A). Neither addresses the saturated main thread, so AC-5, AC-6 and AC-8 cannot be met by the planned phase B | baseline; rows above | confirmed |

## Decisions

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

## AC delta (gate-1 delta)

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

## Changes by layer

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

## Alternatives rejected

- Keep round-1 phase B (G, Q, parallelism A/B): targets mechanisms the baseline shows are not on the box's critical path.
- A second `startup_late` line for post-reveal marks: two lines per start for one fact.
- Emit from inside the gate observer: `record` would run inside the gate lock.
- Post emission to the main looper: the main thread is the saturated resource being measured.
- Fix the scrolled Home here: a Home loading-layout change, track ui, outside this run's layers.
- Lower the cap now: a product decision without compiled-state numbers.

## Risks

| Risk | Kind | Where it lives | Guard |
|---|---|---|---|
| qa kills the process (`am force-stop`) before the line is written, losing the start | data shape, rollout | `StartupMarks.kt:70-75`, `TelemetryRuntime.kt:55` | 5 s bound after `revealed`; method waits at least 6 s after the visible reveal before the next force-stop |
| `schedule` called inside the gate lock blocks or re-enters | concurrency | `RevealGate.update` (`RevealGate.kt:105-113`), `TelemetryExecutor.kt:32-34` | Enqueue only; emission on the telemetry thread; `gateMarksEmitOnlyThroughSchedule` |
| Two emit paths race (completion and 5 s bound) | concurrency | `StartupMarks.emit` (`:74-75`) | `emitted` CAS keeps one line |
| Compile-state change on the box carries into later measurements | rollout | box package state | Filter recorded before every set; as-installed set first; the next install-preserve resets it |
| `wait_ms` mixes clocks | data shape | `KeyProbe.kt:114-122` | Both `CLOCK_MONOTONIC`; device check `0 <= wait_ms <= lat_ms` |
| Lines from the baseline (emitted at the reveal) and the re-measure are compared field by field | data shape | `TelemetryEncoder.kt:67-80` | Fields are additive and keep their meaning; only emission time moves |

## Open questions

1. Authorise `adb -s 192.168.1.110:5555 shell cmd package compile -m speed-profile -f com.kino.puber.stage` on the box for the second AC-6 state? Recommended: yes (reversible, the state background dexopt reaches on its own; not in the standing list, so the owner, and the conductor runs it itself). Blocks: the compiled-state half of AC-6 only.
2. Approve the AC delta and descope (AC-4, AC-7, AC-8 removed; AC-5, AC-6 replaced by observations; three follow-up runs)? Recommended: yes. Blocks: yes (gate-1 delta).
3. Home scrolled at the reveal in scope? Recommended: no, follow-up `home-hero-late-insert` (track ui). Blocks: no.
