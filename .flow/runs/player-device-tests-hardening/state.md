---
slug: player-device-tests-hardening
title: "Tighten three player device tests that can pass without exercising their scenario"
stage: done
size: S
tracks: []
kind: improvement
branch: prakunin/player-device-tests-hardening
worktree: /home/dev/orca/workspaces/Puber/player-device-tests-hardening
base: 13432572
spec:
plan:
rounds: {verify: 2, review: 2, judge: 2}
blocked_by: []
source: player-hermetic-fixtures
created: 2026-11-27
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Item 4: test-only OkHttp signal plus bounded remainder (A) or production seam (B)? | no | answered | A (recommended) |
| Q2 | Rename the item 3 test? | no | answered | no, keep the documented name (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1 option A; Q2 keep the test name | owner (standing instruction: recommended) |
| 2026-11-27 | Instrumented runs on the emulator host.docker.internal:5555 (instrumentation package not allowed on the box) | owner (standing instruction: recommended, as player-hermetic-fixtures Q1) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1 delta: AC-3 met by substitute mutant M2b (master path, gates on 360p, fails as required) instead of the 720p-gated mutant, which passes because this emulator prepares 720p first (itest-17/18) | owner (standing instruction: recommended by the implementer) |
| 2026-11-27 | Item 4 deviations accepted: application interceptor close signal (debug PlaybackController replaces the EventListener factory) plus a bounded main-queue wait | owner (standing instruction: recommended) |
| 2026-11-27 | triage-1 #2 reopened as major after a 1/6 emulator flake; fix round 2 on #2 (still test-only, option A) | conductor |
| 2026-11-27 | Gate 2: ship (judge-2 CONCERNS: AC-3 via substitute mutant M2b; latch scoping verified by reading, M4 survives) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-hermetic-fixtures: review round 2 of player-hermetic-fixtures (triage-2.md #1-#3): (1) hlsDelayedAndInterruptedSegment_enterBufferingThenRecover opens the gate on the first BUFFERING, which prepare() reaches before the segment is requested (PlaybackControllerNetworkTracksTest.kt:250-257): wait for delayed-segment to be pending first; (2) hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes gates only the 720p/english segments while the master also offers ungated 360p (:80-107): single-variant master and require the gated routes pending when tracks appear; (3) PlaybackControllerResourcesTest.kt:56 lost assertTrue(cache.getCachedSpans(url).isEmpty()) after removeResource; restore it and add a KDoc pointer to player-corrupt-cache-recovery on the test.
- 2026-11-27 item 4 from player-callback-gate triage-1 #2 (codex): switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream (PlaybackControllerStaleCallbackTest.kt:83-84) waits a fixed Thread.sleep(PARSE_WAIT_MS) for A's error to be queued; replace with a bounded wait on a playback-thread signal so a slow device cannot fail the probe precondition.
- 2026-11-27 preflight: base 13432572; stage triage
- 2026-11-27 research.md (option A, test-only)
- 2026-11-27 brief.md (improvement, S, 7 ACs); stage design
- 2026-11-27 test-design.md (P0 4, P1 2); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/player-device-tests-hardening; stage impl
- 2026-11-27 impl: 716e2b2e, 1f11d5a4, 32fe9d46, 9ccbb15c; STATUS blocked on AC-3 as written, resolved by gate 1 delta (M2b); check.full pass; stage verify
- 2026-11-27 qa-report round 1: check.full pass; AC-1,2,4,5,6,7 PASS; AC-3 FAIL as written, PASS via M2b (gate 1 delta); stage review
- 2026-11-27 review round 1 (blind, tests, codex; antigravity skipped: quota): triage-1: 2 fixes, 2 rejected
- 2026-11-27 fix round 1: 219d94f5; stale-callback test 5/6 on emulator (run 1 failed on the unmet precondition); #2 reopened
- 2026-11-27 fix round 2: c1de902f (bounded retry, up to 5 attempts); stale-callback test 10/10 on emulator
- 2026-11-27 review round 2 (edge, tests, codex): clean; check.full at c1de902f: exit 0; stage judge
- 2026-11-27 judge-1 FAIL: must fix = stale device evidence file (describes 9ccbb15c); evidence-only fix round
- 2026-11-27 qa round 2: evidence brought up to c1de902f (commit df7f2265), audit passed; stage judge
- 2026-11-27 judge-2 CONCERNS (no must-fix); gate 2 ship (standing instruction); stage handover
- 2026-11-27 squashed to 4 commits bb5ffafe..769533ee (tree unchanged)
- 2026-11-27 shipped: merge commit b31a490f (--no-ff) into master; Orca worktree removed; stage done
