# Gate - startup-first-input-on-tv - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 80ca6fb6   **Base:** c506d0bd

## Done as asked

The diff in `app/src/main` is limited to the amended AC-1 and AC-2 telemetry: `RevealGate` with identity-tracked requests and `RevealObserver`, `StartupMarks` with deferred emission through `sink::schedule`, `KeyLatencyMatcher` with a pending list, a 5 s expiry and `wait_ms`, and dropped keys counted in `dispatchWithProbe`. AC-4, AC-7 and AC-8 (bounded image hold, cap, fade, parallelism, Home hero) are not touched. The dropped-key return path in `MainActivity.dispatchKeyEvent` is unchanged: it returns `true` without calling super. The head commit 80ca6fb6 adds evidence only, so the code is the same as at 9d454407.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `RevealGateTest`, `StartupMarksTest`, `TelemetryEncoderTest`, `TelemetryRuntimeTest` rows; restored start `revealNowBeforeBegin_reportsForcedOnceWithoutCovering`; Manual check 2 | check.full exit 0 at 80ca6fb6 (run here); verify-2-device.md: one line in each of 34 starts, `ready_req`/`ready` in all of them, `ready_drained` exactly where `ready_in_flight` > 0 | PASS |
| AC-2 | `KeyLatencyMatcherTest`, `KeyLatencyMatcherConcurrencyTest`, `DispatchWithProbeTest`, `KeyProbeGateTest`, `TelemetryEncoderTest.keyLatWritesWaitMs` | check.full exit 0; verify-2-device.md: 21 lines, `0 <= wait_ms <= lat_ms` on every line, none expired | PASS |
| AC-3 | Manual check 1 | baseline-device.md (5fb5de17); B/C used nominal anchors there too (baseline-device.md:109) | PASS (carried) |
| AC-5 | Manual check 2 | verify-device.md (18 starts, `ready - revealed` and `ready_req - first_content` per start); verify-2-device.md (34 starts) | PASS (observation) |
| AC-6, as installed, anchor A | Manual check 3 | verify-2-device.md: filter speed-profile, 22 starts, 9 lat lines, median/p95; atrace slices from verify-device.md (ed92c932) | PASS (observation) |
| AC-6, anchors B and C | Manual check 3 | verify-2-device.md "AC-3 anchors, as realised": B +1220..+2063 ms, C +1800..+2712 ms after `revealed` | NOT VERIFIED (not met as specified) |
| AC-6, compiled half | Manual check 4 | not authorised (`state.md` Decisions) | NOT CHECKED |
| AC-9 | check.full; `git diff f48beb86 HEAD` on the frozen tests | check.full exit 0 here (tasks up to date with qa round 2's inputs); frozen-test diff empty | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| AC-6 reported PASS with B/C off-anchor | tests-1 F1 (major) | fixed | re-measure 80ca6fb6; qa round 2 reports B/C NOT MET; carried to `startup-reveal-gate-on-slow-tv` (its state.md:34) |
| Restored start never reports FORCED | edge-1 F1, codex-1 | fixed | 9eec4d24, `revealNowBeforeBegin_reportsForcedOnceWithoutCovering` |
| `dropped_keys` modelled optional | blind-1 F2 | fixed | 9fe426e0 |
| `onStart` not-in-flight guard lost | tests-1 F2 | fixed | 874594d5, `StartupImageActivityUnmeasuredTest` |
| Concurrency test hides double emit | tests-1 F3 | fixed | 874594d5 |
| No-redraw key gets a later frame's latency | blind-1 F1 (minor) | rejected, accepted here | gate-1 spec f4e982e8 Risks: "A handled key that draws nothing is matched to the next frame, up to 5 s later"; the row was dropped from the spec at bb1af218 (Concerns 3) |
| codex rejected AC-6 anchors as non-code | codex-1 | superseded by tests-1 F1 | see the first row |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Telemetry only. When telemetry is off, the key path is unchanged except for the existing drop check. The matcher allocates one list per armed key and returns at once on an empty list. Gate observer calls only enqueue work. No measured change is claimed. |
| Reliability | The `startup` line now waits up to 5 s after the reveal, so a process killed in that window loses it (spec Risks; qa waits 17 s). The restored start is fixed. Keys pressed while no frame is drawn are held, at most 16. |
| Maintainability | AGENTS.md conventions are kept; `StartupImageActivity.reset` and `onImagesBusy` were removed with no callers left. One nit: the `KeyLat` KDoc is misindented (`TelemetryEvent.kt:215-218`). |
| Security / privacy | `tools/mobile-evidence-audit.sh` passes on the three evidence files (re-run here). No raw logs or tokens are stored. |

## Manual checks for the owner

1. AC-6 compiled half (Manual check 4). The owner authorises `adb -s 192.168.1.110:5555 shell cmd package compile -m speed-profile -f com.kino.puber.stage`. The conductor then:
   1. takes the lease;
   2. runs the command and records `dumpsys package com.kino.puber.stage` (Dexopt state);
   3. repeats Manual check 3 for at least 12 starts with the verify-2 trigger;
   4. records per-anchor median and p95 of `lat_ms`/`wait_ms` and `0 <= wait_ms <= lat_ms`;
   5. releases the lease.

   Until then this half stays NOT CHECKED.

## Must fix

none

## Concerns for the owner

1. AC-6 anchors B and C were not measured at reveal +300 / +1000 ms. A trigger outside the app cannot fire earlier than about +0.9 s, so the B and C numbers are for keys 1.2-2.7 s after the reveal. Decide between two options: amend AC-6 to the anchors as realised, or require the in-app trigger recorded in `startup-reveal-gate-on-slow-tv`.
2. At anchor A, 9 of 22 starts have neither a `key_lat` line nor a dropped key. The evidence cannot tell an unhandled key from a key answered in 50 ms or less, so the anchor A medians rest on 9 lines.
3. The accepted risk that a no-redraw key gets a later frame's latency (triage-1 row 6) is no longer in the shipped spec's Risks table, yet the matcher is permanent telemetry.
4. The AC-6 atrace slices come from ed92c932, not from the head build. The fix round changed only gate reporting and tests.
5. No `.auth` snapshot exists for the benchmark install, which is not debuggable. Login was intact before and after.
