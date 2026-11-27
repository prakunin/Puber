# Gate - player-device-tests-hardening - round 1

**Decision:** FAIL
**Head:** c1de902f   **Base:** 13432572

## Done as asked

The diff (`git diff 13432572...c1de902f`) changes only instrumented test sources under `app/src/androidTestInstrumentation/.../player/vm/` and adds one evidence file; no `app/src/main` change (brief.md Q1, option A). Plan steps 1-4 are present: `pending(routeId)` and the BUFFERING condition (`PlaybackControllerNetworkTracksTest.kt:334-338`), single-variant `quality-low.m3u8` with 360p/audio gates (:88-104), cache assertion and KDoc (`PlaybackControllerResourcesTest.kt:36-63`), close signal `ClosedResponseSignals` (`PlayerTestControl.kt:141-183`) and the bounded retry (`PlaybackControllerStaleCallbackTest.kt:88-130`). The interceptor instead of an `EventListener` and the bounded retry are recorded deviations accepted in state.md Decisions.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` x3; mutant M1 | itest-1..3.xml 13/13, 0 failures (re-read); itest-16.xml M1 fails; code :334-338 | PASS |
| AC-2 | `hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` x3 | itest-1..3.xml; code :88-104 | PASS |
| AC-3 | mutant M2b (substitute, gate 1 delta in state.md) | itest-18.xml 1 failure (re-read); M2 as written survives (itest-15) | PASS (via accepted substitute) |
| AC-4 | `emptyCache_loadsNormally_...` x1 | itest-4.xml 3/3 (re-read); assertion after second `removeResource`, KDoc names `player-corrupt-cache-recovery`, name unchanged | PASS |
| AC-5 | read `PlaybackControllerStaleCallbackTest.kt:104-114` | no sleep between `outcome(path)` and `switchStream(B)`; close latch and `isIdle` wait both bounded by `PARSE_WAIT_MS` | PASS |
| AC-6 | `PlaybackControllerStaleCallbackTest` at c1de902f, 10 runs | itest-fix2-1..10.xml: 4 tests, 0 failures each (re-read); a counted attempt requires `probe.error != null`. QA did not re-run after the fixes; the committed evidence file does not cover this head (Must fix 1) | PASS |
| AC-7 | `check.full` run by the judge at c1de902f | exit 0 (BUILD SUCCESSFUL, both tooling scripts PASS) | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Close latch never reset per path | triage-1 #1 (blind F1, tests F2) | fixed | 219d94f5; M4 survives, scoping rests on reading (itest-fix1-m4.xml) |
| `isIdle` wait satisfied by any message; 1/6 flake | triage-1 #2, reopened major | fixed | c1de902f bounded retry, per-attempt paths; itest-fix2-1..10 |
| SignalingBody KDoc overclaims "parsed" | triage-1 #3 | fixed | 219d94f5, `PlayerTestControl.kt:142-147` |
| Corrupt-entry outcome unasserted | triage-1 #4 (blind F4) | rejected, accepted | run `.flow/runs/player-corrupt-cache-recovery/` exists; KDoc points to it |
| codex r1: `stateEvents` branch after BUFFERING | triage-1 rejected | rejected, accepted (spot-checked) | condition also needs `pending("delayed-segment")`, so READY cannot be reached while gated |
| codex r2: `awaitOnMain` throws instead of retrying; overlapping same-path requests | triage-2 rejected | rejected, accepted (spot-checked) | a 5 s idle queue after close is a real failure; `QUEUED_ATTEMPT_PATHS` gives each attempt its own path |
| Committed evidence stale and partly false at head | judge | open | Must fix 1 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Test-only; the retry adds at most 4 extra attempts (about 1.3 s each per implementation.md) to one case. |
| Reliability | Every new wait is bounded; all-attempts-missed fails with per-attempt diagnostics. A counted attempt is judged without retry. |
| Maintainability | `aRequested`/`aOutcome` stay as wrappers used by the other three cases; no dead helper. `docs/testing/player-playback.md:63` still reads correctly. |
| Security / privacy | No secrets; hermetic local server only; `tools/mobile-evidence-audit.sh docs/superpowers/evidence/` passed. |

## Manual checks for the owner

none

## Must fix

1. `docs/superpowers/evidence/2026-11-27-player-device-tests-hardening-device.md` describes HEAD 9ccbb15c: its AC-6 line says the `probe.error` non-null assertion remains (removed in c1de902f, replaced by the retry) and cites 6/6 runs of superseded code. Update it to c1de902f: fix-round runs (itest-fix1-1..6 with the 1/6 failure, M4 surviving, itest-fix2-1..10), the retry design for AC-5/AC-6, then re-run the evidence audit.

## Concerns for the owner

- AC-3 is met by the substitute mutant M2b under the gate 1 delta; M2 as written survives on this emulator.
- Per-path scoping of `ClosedResponseSignals` is verified by reading only (M4 survives).
