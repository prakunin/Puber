# Gate - player-device-tests-hardening - round 2

**Decision:** CONCERNS
**Head:** df7f2265   **Base:** 13432572

## Done as asked

`git diff 13432572...df7f2265`: instrumented test sources under `app/src/androidTestInstrumentation/.../player/vm/` plus the device evidence file; no `app/src/main` change (brief.md Q1, option A). df7f2265 changes only `docs/superpowers/evidence/2026-11-27-player-device-tests-hardening-device.md` (`git diff c1de902f df7f2265 --name-only`). Code scope as in judge-1.md "Done as asked".

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` x3; mutant M1 | itest-1..3.xml 13/13, itest-16.xml 1 failure (re-read) | PASS |
| AC-2 | `hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` x3 | itest-1..3.xml (re-read) | PASS |
| AC-3 | mutant M2b (gate 1 delta, state.md Decisions) | itest-18.xml 1 failure; M2 as written survives, itest-15.xml 13/13 (re-read) | PASS (via accepted substitute) |
| AC-4 | `emptyCache_loadsNormally_...` x1 | itest-4.xml 3/3 (re-read) | PASS |
| AC-5 | read `PlaybackControllerStaleCallbackTest.kt:97-130` at df7f2265 | no sleep between `outcome(path)` and `switchStream(B)`; close wait (:109) and queue wait (:112) bounded by `PARSE_WAIT_MS`; only `SETTLE_MS` after B ready (:119) | PASS |
| AC-6 | `PlaybackControllerStaleCallbackTest` x10 at c1de902f (test code unchanged at head) | itest-fix2-1..10.xml 4/4, 0 failures each (re-read); a counted attempt requires `probe.error != null` (:120) | PASS |
| AC-7 | `check.full` run by the judge at df7f2265 | exit 0, BUILD SUCCESSFUL, both tooling scripts all PASS | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| triage-1 #1-#4, codex r1/r2 rejections | triage-1, triage-2 | as in judge-1.md Findings | 219d94f5, c1de902f; rejections re-checked against :97-130 |
| Committed evidence stale at head | judge-1 Must fix 1 | fixed | df7f2265: evidence names c1de902f, fix1 runs incl. the 1/6 failure, M4 survival, fix2 10/10, retry design; counts match the XMLs; `tools/mobile-evidence-audit.sh docs/superpowers/evidence/` exit 0 (judge re-run) |
| Antigravity review | config review_extra | skipped both rounds (quota), recorded in triage-2 | config allows skip at quota |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Test-only; the retry adds at most 4 attempts to one case. |
| Reliability | Every wait is bounded; all attempts missed fails with per-attempt diagnostics. |
| Maintainability | No dead helper; project test conventions kept. |
| Security / privacy | Hermetic local server only; evidence audit passed. |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

- AC-3 is met only by substitute mutant M2b (gate 1 delta); M2 as written survives on this emulator (itest-15.xml).
- Per-path scoping of `ClosedResponseSignals` rests on reading only: mutant M4 survives (itest-fix1-m4.xml).
