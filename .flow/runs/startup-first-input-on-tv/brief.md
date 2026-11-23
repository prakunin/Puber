# Brief: startup-first-input-on-tv

Base 2fd851a4. Analyst, 2026-11-22.

Amendment 2026-11-22: Acceptance criteria, Risks and Open questions rewritten per the approved gate-1 delta (`design-architect.md` round 2: AC delta, Decisions; `state.md` Decisions 2026-11-22). Size stays M.

Builds on `research.md`; the lines relied on below (`RevealGate.kt:7,45-50,78`, `StartupImageActivity.kt:23-29`, `StartupSettled.kt:12-19,46-48`, `MainActivity.kt:59-71,85-90`, `KeyProbe.kt:8-9,81-100`, `StartupMarks.kt:16-18`) were re-read at master c506d0bd and hold. `devBenchmarkRelease` installs as `com.kino.puber.stage` (`app/build.gradle.kts:216,227`), so it may go on the box.

kind: improvement. size: M. tracks: perf. Device verification: **yes** (startup, reveal gate, key input, image loader; every AC number is measured on the box 192.168.1.110:5555, emulator not used for evidence).

Size reason: one goal (attribute the box's startup and first-key cost), across the reveal gate, startup telemetry and the key probe, which are risk areas in `.flow/roles/analyst.md`. Research option B (research.md: Options), narrowed by the amendment.

## Request
research.md: Request.

## Root cause
Status: **plausible** for the box; the gate mechanism is **confirmed** from code, the dominant input on the box is not measured yet (AC-3 decides it).

| Finding | Where | Status |
|---|---|---|
| The splash lifts before the cap only when `ready` is set and no request of the whole loader is in flight; any request, visible or not, issued before or after `ready`, re-arms `imagesBusy` | `core/startup/RevealGate.kt:78`, `core/startup/StartupImageActivity.kt:109-115` | confirmed (code) |
| Requests enter "in flight" only after layout gives them a size, so row cards, LazyRow prefetch and hero pages keep joining after `ready`; 4K-class wide posters decode to 533x300 with 4 parallel decodes (`PuberApp.kt:109-145`, `VideoItemUIMapper.kt:83-89`) | research.md: Current state in code, Image work on Home | plausible (emulator trace only) |
| Nothing records why the splash lifted or when `ready` and the last image-idle edge happened; "revealed by cap" in past evidence is inferred | `core/telemetry/StartupMarks.kt:16-18` | confirmed |
| First-key cost after the reveal: RenderThread first frame (1.4 s of shader compile on swiftshader), decode burst, and `StartupSettled` work released ~250 ms after the reveal | research.md: Related work (Round 2b); `StartupSettled.kt:46-48`, `MainActivity.kt:93` | plausible; shader cost not expected on a hardware GPU (unverified for KM6) |
| `key_lat` drops keys answered more than 1 s late and keys within 1 s of a pending one; dropped-under-splash keys arm the matcher | `core/telemetry/KeyProbe.kt:9,84,94-96`, `core/startup/StartupKeyFilter.kt:22-33` | confirmed (code) |

## Resolved research questions
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

## Risks
| Risk | Guard |
|---|---|
| `am force-stop` between starts kills the process before the delayed line is written | 5 s bound after `revealed`; qa waits at least 6 s after the visible reveal before the next force-stop |
| `schedule` called inside the gate lock blocks or re-enters | Enqueue only, emission on the telemetry thread (`design-architect.md`: Decisions, Where emission runs) |
| Completion and the 5 s bound race to emit | Single-shot `emitted` CAS in `StartupMarks.emit` |
| `wait_ms` mixes clocks | Both `CLOCK_MONOTONIC`; AC-6 checks `0 <= wait_ms <= lat_ms` |
| Baseline lines (emitted at the reveal) and re-measure lines (emitted later) compared field by field | Fields additive with unchanged meaning; only emission time moves |
| Unknown compile state makes JIT cost look like app work | Compiler filter recorded before every set (AC-6) |
| The box is shared and logged in with no display re-pairing | `com.kino.puber.stage` only, `auth-save` before the first install, install-preserve, lease (AGENTS.md) |

## Open questions
1. Authorise `adb -s 192.168.1.110:5555 shell cmd package compile -m speed-profile -f com.kino.puber.stage` for the compiled-state half of AC-6? Recommended: yes (reversible; the state background dexopt reaches by itself; the conductor runs it). Current decision: not authorised (`state.md` Decisions), half NOT CHECKED. Blocks: no.

Earlier questions 1-3 (box targets, bounded image hold, fade and parallelism) are superseded by the amendment (`state.md` Decisions, gate 1 delta).
