# Make the reveal gate fit a slow TV: bounded image hold after ready

Status: approved
Date: 2026-11-23
Slug: startup-reveal-gate-on-slow-tv
Kind: improvement
Size: M
Tracks: perf

## Problem / Why

In the compiled state (S1) Home is ready at ~1.4-2.2 s, then the splash waits 389-956 ms more for images (research.md: 3, verify-5 table), and when that wait crosses 2.5 s the start becomes a `cap` start; those carry the only first keys over 199 ms (research.md: 4, release-startup-compilation judge-1). The wait is unbounded because the cover rule waits for every in-flight request of the loader, including ones started after `ready` (`RevealGate.kt:109`), while `held` (the requests present at `ready`) is unused by it. Wanted: reveal by `ready` + G at the latest, cap unchanged, uncompiled starts (S0) unchanged.

## Acceptance criteria

| AC | Given | When | Then | Checked by |
|---|---|---|---|---|
| AC-1 | gate begun, `ready` taken effect with requests in `held` | all held requests end before G | splash uncovers at the last end with cause `ready` | unit (`RevealGateTest`) |
| AC-2 | gate begun, `ready` with held requests still in flight | G elapses after `ready` | splash uncovers with cause `grace`; `onRevealed` reports the requests still in flight | unit |
| AC-3 | gate begun, `ready` with `held` empty or drained | a request starts after `ready` | splash uncovers without waiting for it (cause `ready`) | unit |
| AC-4 | gate begun, no `ready` | 2.5 s pass from `begin()`; or `revealNow()` is called | cause `cap` / `forced` exactly as today; a later `ready` or G timeout changes neither the cause nor the reveal; a stale `ready` from before `begin()`/`reset()` arms no G | unit; existing `RevealGateTest`, `StartupRevealTest` pass, tests that assert waiting on post-ready requests are rewritten, not deleted |
| AC-5 | a `grace` reveal | the `startup` line is encoded | it carries `"cause":"grace"` and the other fields as today | unit (`TelemetryEncoderTest`, `StartupMarksTest`) |
| AC-6 | box 192.168.1.110:5555, stage build of the run head in S1 (`dumpsys package com.kino.puber.stage` shows `speed-profile`), 15 cold starts | each start's `startup` line is read | no start with `ready` before 2500 ms has cause `cap`; median of `revealed - ready` <= G + 50 ms; max <= G + 250 ms | device evidence |
| AC-7 | the same 15 starts, one DPAD_DOWN at anchor A (launch +3 s) and at anchor C (detection +0.7 s), and the baseline-method control (key at launch +2.44 s) | the key is handled | at most one first key over 199 ms across anchors A and C and the control, and the focus ring moves one row in every start; anchor B (first-pixel detection) keys and their position relative to `ready_drained` are recorded as an observation (design-ac7-diagnosis.md Q1) | device evidence |
| AC-8 | an S1 start where G ended the hold | a screenshot is taken right after the fade | skeletons in place of undecoded hero or card images are acceptable (owner judges) | device screenshot (not committed if account data shows) |
| AC-9 | box in S0 (as installed, `reason=install`), 5 cold starts | `startup` lines are read | cause `cap` and `revealed` within 2.5 s + 300 ms as before (observation, no threshold change) | device evidence |
| AC-10 | the AC-6 set | a key at reveal +2.5 s (deferred-work slots running, incl. the new WatchState catch-up of `183c10b1`) | latency recorded per start (observation; over 199 ms on more than one start files a follow-up run) | device evidence |

## Out of scope

| Item | Decision |
|---|---|
| Bounded image hold | In: only `held` requests keep the splash, at most G after `ready`; new cause `grace` when G ends it |
| G | Start at 300 ms (Q1); final value fixed from the S1 box set and written into the spec |
| Cap value | Unchanged 2.5 s (Q2) |
| Deferred-work window (old AC-7) | No code change; keys at a late anchor recorded as an observation (Q3) |
| S0 behaviour | Unchanged; recorded as an observation |

## Design

Base `9158cf82`. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| What keeps the splash after `ready` | Cover rule becomes `begun && forcedCause == null && !revealedOnce && !(ready && held.isEmpty())` (`core/startup/RevealGate.kt:109`); `inFlight` leaves the rule | `held` is already the set present at `ready` (`RevealGate.kt:64`) and already drains in `onImageFinished` (`RevealGate.kt:83`); AC-1, AC-3 |
| How G ends the hold | New `RevealCause.GRACE("grace")` in `core/startup/RevealObserver.kt:4-8`; new `RevealGate.onGrace(generation)` sets `forcedCause = GRACE` only when `generation` matches, `ready` is true and `forcedCause == null` | Reuses the existing first-cause-wins path (`RevealGate.kt:72,75,111-113`), so a later cap, forced reveal or G changes neither cause nor reveal; generation guard is the same one `markReady` uses (`RevealGate.kt:55-61`); AC-2, AC-4 |
| Value of G | `internal const val REVEAL_GRACE_MS = 300L` beside `REVEAL_CAP_MS` (`RevealGate.kt:9`), passed as constructor param `graceMs`; the spec fixes the final value from the S1 set and the code constant follows it | One place for the number; tests inject their own |
| Where G is armed | In the `markReady` frame block (`RevealGate.kt:60-67`) right after `held.addAll(inFlight)`, when `begun && forcedCause == null && held.isNotEmpty()`; it captures `markedIn` and calls the injected scheduler | The ready edge is the only moment `held` is known; an empty `held` reveals at once and needs no timer |
| Scheduler shape | New constructor param `scheduleGrace: (delayMs: Long, task: () -> Unit) -> Unit`, default no-op; called inside the gate lock, so it must only enqueue, never run `task` inline | Same pattern as `afterFrame` (`RevealGate.kt:20`) and `StartupMarks.schedule` (`core/telemetry/StartupMarks.kt:31`); existing tests keep the no-op and are unaffected |
| Production timer thread | `StartupReveal` gets a `graceHook` beside `frameHook` (`core/startup/StartupReveal.kt:13`), default no-op on the JVM, set in `installFrameWait()` (`StartupReveal.kt:49-51`, called at `PuberApp.kt:82`) to an async main-looper handler (`HandlerCompat.createAsync(Looper.getMainLooper()).postDelayed`, androidx.core); `resetForTests()` (`StartupReveal.kt:76-79`) restores the no-op | The fade and the `revealed` mark run on main anyway (`MainActivity.kt:81,88`), so an off-main flip draws no earlier; an async message is not held behind the Choreographer sync barrier, unlike the Compose `delay` of the cap (`MainActivity.kt:83-86`) |
| Telemetry | No change to `StartupMarks` or `TelemetryEncoder`: `markRevealCause` stores any cause (`StartupMarks.kt:84-88`), the encoder writes `cause.wire` (`core/telemetry/TelemetryEncoder.kt:73`); a `grace` line is written on held drain or the 5 s bound like `cap` (`StartupMarks.kt:111-115`) | AC-5 holds by the enum value; the line keeps `ready_drained` for the evidence tables |
| `inFlight` on `onRevealed` | Unchanged: total requests in flight (`RevealGate.kt:113`) | AC-2 asks for the requests still in flight |
| Cap and `revealNow` | Unchanged (`RevealGate.kt:9,72,75`, `MainActivity.kt:83-86`) | Q2; AC-4 |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data path changes |
| ViewModel state and actions | N/A: Home still calls `StartupReveal.markReady()` (`StartupReveal.kt:59`) as today |
| Gate (`core/startup`) | `RevealGate.kt`: `REVEAL_GRACE_MS`, params `graceMs` and `scheduleGrace`, arming in `markReady`, `onGrace(generation)`, cover rule on `held`; KDoc of `markReady` (`RevealGate.kt:49-53`) and class (`RevealGate.kt:11-15`) updated. `RevealObserver.kt`: `GRACE`, KDoc line 3. `StartupReveal.kt`: `graceHook`, wiring into the gate constructor (`StartupReveal.kt:15-39`), install and reset |
| Screen and DI module | N/A: `MainActivity.kt` and Koin modules unchanged |
| Compose content | N/A: `StartupSplashHost.kt` fade unchanged |
| Resources | N/A |
| Tests (`app/src/test/kotlin/com/kino/puber/core/startup/`) | `RevealGateTest`: `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` asserts reveal at the drain of `b`, not of `c` (rewritten, AC-3/AC-4); new cases for AC-1 to AC-4 with a collecting scheduler; `RecordingRevealObserver` needs no change (logs `revealed:GRACE:n`). `TelemetryEncoderTest`, `StartupMarksTest`: a `GRACE` case (AC-5). Names and counts belong to test-design.md |

### Alternatives rejected

| Alternative | Why not |
|---|---|
| G timer as a `LaunchedEffect` in `MainActivity` next to the cap | Needs the ready time exposed to Compose and runs behind the frame; the gate already owns the ready edge |
| G timer on a background thread or executor | Adds a thread at startup; the visible reveal still waits for main |
| Time-based rule (`clock() - readyMs >= G` in `update`) without a timer | Nothing guarantees an `update` call after G |
| Hold only images visible on screen (per-request tagging) | Needs Coil request tagging per composable; larger than the AC set |
| Separate `graceExpired` flag beside `forcedCause` | Second path for first-cause-wins; `forcedCause` already gives it |

### Risks

| Risk | Where | Covered by |
|---|---|---|
| Earlier reveal moves `onSplashGone` and the deferred-work latch earlier (`MainActivity.kt:91`, `core/startup/StartupSettled.kt:46`), so slots (TvHome +0.5 s ... WatchState catch-up +2.0 s) overlap the held decodes still running and the first key | `StartupSettled.kt:12,18`, `ui/feature/main/vm/MainVM.kt` slots | AC-7, AC-10 |
| G overshoot: the async message still waits for the current main-thread message (Home composition, image callbacks), so `revealed - ready` exceeds G on a busy start | `StartupReveal` grace hook | AC-6 max bound |
| Stale or doubled G: a G posted for one `begin()` firing after `reset()`/`begin()`, or after a cap reveal | `RevealGate.onGrace` generation and `forcedCause == null` guards | AC-4 unit |
| Concurrency: `onGrace` on main races `onImageFinished` from Coil threads draining `held` | Both in `update` under `lock` (`RevealGate.kt:107`); whichever runs first wins the cause | AC-1/AC-2 unit |
| Skeletons at fade end for held images (reverses startup-splash-preload Q2) | `StartupSplashHost.kt:35-44`, Home hero and cards | AC-8, owner |
| `startup` line gains `"cause":"grace"`; evidence scripts or tables that split only `ready`/`cap` misfile it | `RevealObserver.kt:4-8`, `TelemetryEncoder.kt:73` | AC-5; evidence tables list the value |
| Baseline profile: the new `RevealGate` constructor signature and lambdas are not in the generated profiles (`app/src/main/generated/baselineProfiles/baseline-prof.txt:28173`, `startup-prof.txt:17698`), so they run interpreted/JIT in S1 | `app/src/main/generated/baselineProfiles/` | AC-6; Q1 below |

### Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Regenerate the baseline profile (`tools/generate-baseline-profile.sh`) so the changed `RevealGate` methods stay listed? | No: a few cold methods on the reveal path; regenerate in a later profile run if AC-6 misses | no |

## Test design

Base `9158cf82`. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`. Unit tests under `app/src/test/kotlin/com/kino/puber/core/`; gate tests use `RevealGate(clock, afterFrame = { frames += it }, observer = RecordingRevealObserver())` as in `RevealGateTest`, plus a collecting `scheduleGrace = { delay, task -> graces += delay to task }` (same shape as `StartupMarksTest.queued`). Levels: `.flow/roles/test-designer.md`.

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Cover rule still counts requests started after `ready` (`RevealGate.kt:109`) | 2 | 3 | 6 | unit, AC-3 red |
| R2 | Stale G from an earlier `begin()` reveals the next start (generation guard missing in `onGrace`) | 2 | 3 | 6 | unit, AC-4 red |
| R3 | `graceHook` not installed or not passed to the gate in production, so G never fires (JVM default is a no-op) | 2 | 3 | 6 | device, AC-6 (`grace` causes, max bound) |
| R4 | Decodes after an earlier reveal delay the first key | 2 | 3 | 6 | device, AC-7 |
| R5 | Box not in S1 for the run head, so AC-6/AC-7 measure the wrong state | 2 | 3 | 6 | device step 2 (dumpsys precondition) |
| R6 | Box noise (load, API 502) skews a set | 3 | 2 | 6 | device: load per start, repeat a set, never drop starts |
| R7 | G message waits behind busy main-thread work, `revealed - ready` overshoots G | 2 | 2 | 4 | device, AC-6 max |
| R8 | Earlier reveal moves the deferred-work slots onto the first seconds | 2 | 2 | 4 | device, AC-10 observation |
| R9 | Skeletons at fade end | 3 | 1 | 3 | manual, AC-8 (owner) |
| R10 | A later cap / forced / G changes the cause or the reveal | 1 | 2 | 2 | unit P1; accepted: `revealedOnce` already blocks a second report |
| R11 | `onGrace` on main races `onImageFinished` on Coil threads | 1 | 2 | 2 | accepted: both under `lock`; both orders asserted in the AC-1/AC-2 tests |
| R12 | `grace` misfiled by `startup` line readers | 2 | 1 | 2 | unit AC-5; accepted |
| R13 | S0 starts change | 1 | 2 | 2 | device, AC-9 observation |
| R14 | New gate methods absent from the baseline profile (design Q1: no regeneration) | 2 | 1 | 2 | accepted; AC-6 miss triggers regeneration |

### Traceability

Red tests that call `scheduleGrace`, `onGrace` or `RevealCause.GRACE` fail on the base commit at compile time; the behavioural red on base is `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd`, which compiles there.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 guard | `RevealGateTest.imagesSettlingAfterReadyUncovers`, `RevealGateTest.sameRequestFinishedTwice_doesNotUncoverOthers` (unchanged) | fails if held requests stop holding the splash, or a doubled end releases another one |
| AC-1 | unit | P1 | `RevealGateTest.graceAfterHeldDrained_changesNothing` | held `a`, `b` end before the queued G runs: `revealed:READY:0` once, running G afterwards leaves log and `covered` unchanged; fails if `onGrace` ignores `revealedOnce` / reports twice |
| AC-2 | unit | P0 red | `RevealGateTest.graceWithHeldInFlight_revealsWithCauseGrace` | `a`, `b` held, `a` ends, G runs: `covered` false, log ends `revealed:GRACE:1`; `b` ending afterwards adds no reveal; fails if `onGrace` is not wired to `forcedCause` or `inFlight` is not reported |
| AC-2 | unit | P1 | `RevealGateTest.markReady_withHeldRequests_armsOneGraceOfGraceMs` | with `graceMs = 123`, ready with held requests queues exactly one task with delay 123, a second `markReady` queues none; fails if armed with `capMs`, armed twice or not armed |
| AC-3 | unit | P0 red | `RevealGateTest.heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (rewritten) | `a`, `b` held, `c` starts after ready: after `b` ends the log is `ready:2, drained, revealed:READY:1` and `covered` is false, before `c` ends; fails on base and if the cover rule keeps `inFlight` |
| AC-3 | unit | P0 guard | `RevealGateTest.imagesBusyAfterRevealDoNotCoverAgain`, `RevealGateTest.beginKeepsImagesBusyFromBeforeIt` (unchanged) | fails if a post-ready request re-covers, or requests from before `begin()` drop out of `held` |
| AC-3 | unit | P2 | `RevealGateTest.markReady_withNothingHeld_armsNoGrace` | empty `held` at ready: reveal `READY:0`, scheduler list empty |
| AC-4 | unit | P0 red | `RevealGateTest.graceFromBeforeAnotherBegin_isDropped` | G armed in start 1; `begin()`, `markReady()`, frame with `a` still in flight; running the start-1 task keeps `covered` true; fails if `onGrace` skips the generation check |
| AC-4 | unit | P0 guard | `RevealGateTest.cap_uncoversWithoutReady`, `revealByCap_reportsCauseCapWithInFlightCount`, `revealNow_reportsCauseForced`, `markReady_madeBeforeAnotherBeginIsDropped`, `remainingCapMs_countsFromBegin`, `heldDrained_reportedAfterACapReveal`; all of `StartupRevealTest` (unchanged) | fails if cap, forced reveal, cap timing or stale-ready handling change |
| AC-4 | unit | P1 | `RevealGateTest.graceAfterCapReveal_keepsCauseCap` | cap reveals with `a` held, then G runs: log holds only `revealed:CAP:1` |
| AC-4 | unit | P1 | `RevealGateTest.capAfterGraceReveal_keepsCauseGrace` | G reveals, then `onCap()` and `revealNow()`: log holds only `revealed:GRACE:n`, `covered` stays false |
| AC-4 | unit | P1 | `RevealGateTest.markReadyBeforeAnotherBegin_armsNoGrace` | stale ready whose frame runs after a new `begin()` queues no G and keeps `covered` true |
| AC-4 | unit | P2 | `RevealGateTest.graceAfterReset_isDropped` | G task run after `reset()` and a new `begin()` keeps `covered` true |
| AC-5 | unit | P0 red | `TelemetryEncoderTest.startupWritesGraceCause` | a `Startup` with `cause = RevealCause.GRACE` encodes `"cause":"grace"` with `ready`, `ready_drained`, `in_flight` as in `startupWritesRevealCauseAndImageFields`; fails if the enum value or its wire is missing or wrong |
| AC-5 | unit | P0 guard | `TelemetryEncoderTest.startupWritesRevealCauseAndImageFields`, `TelemetryEncoderTest.startupOmitsImageFieldsNotReached` (unchanged) | fails if other fields of the line change |
| AC-5 | unit | P1 | `StartupMarksTest.graceRevealLineWaitsForHeldDrain` | `markRevealCause(GRACE, 2)`, revealed, ready(2): no line until `markHeldDrained`, then one line with cause `GRACE` and `readyDrainedMs`; fails if `grace` emits early like `forced` (`StartupMarks.kt:115`) |
| AC-6 | device | P0 | manual step 4-5 | see Perf track |
| AC-7 | device | P0 | manual step 4 | at most one first-key `key_lat` over 199 ms across anchors A and C and the baseline-method control (key at launch +2.44 s), 15 starts; focus ring moves one row in every start; fails if a second slow first key appears. Anchor B keys and their position relative to `ready_drained` are recorded as an observation, not judged |
| AC-8 | device | P1 | manual step 6 | owner judges skeletons |
| AC-9 | device | P2 | manual step 8 | observation |
| AC-10 | device | P2 | manual step 4 | observation; more than one start over 199 ms files a follow-up run |

### Bugs

N/A: kind improvement.

### Manual checks

Box `192.168.1.110:5555` only, package `com.kino.puber.stage` only, lease held throughout, `adb -s 192.168.1.110:5555` on every call. Key method as `docs/superpowers/evidence/2026-11-23-startup-first-input-on-tv-verify-5-device.md:11` (on-device injector, anchors A launch +3 s, B first-pixel detection, C detection +0.7 s, one anchor per start, 5 starts each).

1. Build `devBenchmarkRelease` of the run head in the worktree (same build type as the baseline), install with `tools/android-apk-install-preserve`; record APK and on-device `base.apk` sha256. Expected: `installed`, `compatible_replace`, `destructive_action` false.
2. `dumpsys package com.kino.puber.stage` until `[status=speed-profile]`. Expected: S1 reached by background dexopt. If not before verification: escalate the forced compile (brief Q3); do not run it unauthorised.
3. Record load average before each start.
4. One uncounted warm-up, then 15 cold starts (force-stop, 2.5 s, `am start`, 17 s): one DPAD_DOWN at the start's anchor (A, B or C; B is an observation for AC-7) and, on the anchored starts only, a second DPAD_DOWN at detection +2.5 s (AC-10); plus 15 control starts with the key at launch +2.44 s and no second key. Expected: focus ring moves one row per key; one `startup` line per start with `prof` 1.
5. Per start record cause, `ready`, `ready_drained`, `revealed`, `in_flight`, `revealed - ready`, `lat_ms` per key.
6. On a start with cause `grace`: screenshot right after the fade (AC-8); kept out of Git when account data shows.
7. Repeat a set disturbed by API 502 or a load spike as a whole; report both sets.
8. S0 (AC-9): same-APK install-preserve, dumpsys shows `[reason=install]`, 5 cold starts, record cause and `revealed`. Run after the S1 set: it leaves S1.
9. Remove the injector from `/data/local/tmp`, release the lease, run `tools/mobile-evidence-audit.sh` on the evidence dir.

Must not happen: crash or ANR; splash covering again after the fade; splash beyond 2.5 s + 300 ms; a start with no or two `startup` lines; focus ring missing or jumping two rows; any action on `com.kino.puber`; uninstall, data clear, downgrade.

### Perf track

| Metric | Measured | Baseline (S1, rounds 3-4, APK `8251ff30`, 45 starts, `docs/superpowers/evidence/2026-11-23-release-startup-compilation-device.md:139-260`) | Pass (AC-6 / AC-7) |
|---|---|---|---|
| `revealed - ready` median | `startup` line, step 5 | 473 ms | <= G + 50 ms (350 at G = 300) |
| `revealed - ready` max | same | 642 ms | <= G + 250 ms (550 at G = 300) |
| cap starts with `ready` < 2500 ms | cause field | 3 of 45 (ready 2024, 2243, 2227) | 0 of 15 |
| first-key `key_lat` over 199 ms | `key_lat.lat_ms`, no line counts as 50 | 2 of 45 (215, 292; both cap) at launch +2.44 s; verify-5 anchors A/B/C: 0 of 15 | at most 1 of 15 |

Baseline is reused as recorded (owner, no new baseline run; RevealGate unchanged since `8251ff30`). Its key anchor (+2.44 s) differs from AC-7's verify-5 anchors; AC-7 is judged on its absolute bound. If AC-6 misses, the baseline profile is regenerated before a re-measure (design Q1).

### UNTESTABLE

None. Production wiring of `graceHook` (`Handler` on the main looper) has no JVM seam and is proved only on device (R3).

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Accept skeletons at fade end for images not decoded by ready + G (reverses startup-splash-preload Q2)? | no | answered | Yes; G starts at 300 ms and is fixed from the S1 box set |
| Q2 | Keep the 2.5 s cap? | no | answered | Keep |
| Q3 | Reach S1 without a forced compile; deferred-work window in scope? | no | answered | Wait for background dexopt (read-only dumpsys check); escalate a forced compile only if S1 is not reached; window observation only (AC-10) |
| Q4 | Regenerate the baseline profile for the changed RevealGate methods? | no | answered | No; only in a later profile run if AC-6 misses |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-23 | Q1-Q4 on the recommended answers | owner (standing instruction: recommended) |
| 2026-11-23 | Perf baseline reused from release-startup-compilation rounds 3-4 (45 S1 starts, RevealGate unchanged since) | conductor |
| 2026-11-23 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-23 | G fixed at 300 ms (REVEAL_GRACE_MS): AC-6 met on the S1 box set (revealed - ready median 326 / max 379) | conductor, from the round-1b evidence |
| 2026-11-23 | AC-7 restated after verify round 1b (design-ac7-diagnosis.md Q1); anchor-B latency goes to startup-post-reveal-key-latency | owner |
