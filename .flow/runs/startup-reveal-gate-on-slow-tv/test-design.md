# Test design: startup-reveal-gate-on-slow-tv

Base `9158cf82`. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`. Unit tests under `app/src/test/kotlin/com/kino/puber/core/`; gate tests use `RevealGate(clock, afterFrame = { frames += it }, observer = RecordingRevealObserver())` as in `RevealGateTest`, plus a collecting `scheduleGrace = { delay, task -> graces += delay to task }` (same shape as `StartupMarksTest.queued`). Levels: `.flow/roles/test-designer.md`.

## Risk profile

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

## Traceability

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

## Bugs

N/A: kind improvement.

## Manual checks

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

## Perf track

| Metric | Measured | Baseline (S1, rounds 3-4, APK `8251ff30`, 45 starts, `docs/superpowers/evidence/2026-11-23-release-startup-compilation-device.md:139-260`) | Pass (AC-6 / AC-7) |
|---|---|---|---|
| `revealed - ready` median | `startup` line, step 5 | 473 ms | <= G + 50 ms (350 at G = 300) |
| `revealed - ready` max | same | 642 ms | <= G + 250 ms (550 at G = 300) |
| cap starts with `ready` < 2500 ms | cause field | 3 of 45 (ready 2024, 2243, 2227) | 0 of 15 |
| first-key `key_lat` over 199 ms | `key_lat.lat_ms`, no line counts as 50 | 2 of 45 (215, 292; both cap) at launch +2.44 s; verify-5 anchors A/B/C: 0 of 15 | at most 1 of 15 |

Baseline is reused as recorded (owner, no new baseline run; RevealGate unchanged since `8251ff30`). Its key anchor (+2.44 s) differs from AC-7's verify-5 anchors; AC-7 is judged on its absolute bound. If AC-6 misses, the baseline profile is regenerated before a re-measure (design Q1).

## UNTESTABLE

None. Production wiring of `graceHook` (`Handler` on the main looper) has no JVM seam and is proved only on device (R3).
