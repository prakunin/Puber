---
slug: telemetry-frames-keys-memory
title: "Session log: JankStats frame windows, key bursts and key-to-frame latency, memory samples"
stage: done
size: M
tracks: [perf]
kind: feature
branch: telemetry-frames-keys-memory
worktree: /home/user1/orca/workspaces/Puber/telemetry-frames-keys-memory
base: cd16eb47
spec: docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md
plan: docs/superpowers/plans/2026-10-16-telemetry-frames-keys-memory.md
rounds: {verify: 3, review: 3, judge: 1}
blocked_by: []
created: 2026-10-15
migrated:
updated: 2026-10-16
---

## Next
Nothing: shipped. Follow-ups live in the deferred idea runs.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Leave `slow_frame` lines out of S2? | no | answered | Yes; `max` in the frames window covers AC-12. |
| Q2 | Add `mem` sampling on screen change? | no | answered | No; one line per foreground minute plus trim, attributed by `scr`. |
| Q3 | Count key answers under 50 ms inside the frames window? | no | answered | No, skip in S2. |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-16 | Gate 2: ship (judge-1 CONCERNS, no must-fix; M1 real TV NOT CHECKED carried) | owner (standing instruction: recommended) |
| 2026-10-16 | Round-3 leftovers deferred, not fixed: transient Main label on Back to the tab host (130-380 ms, self-correcting), its Compose-level test, and an unanalysed rail mismatch after rapid Back | owner (standing instruction: recommended) |
| 2026-10-16 | Review cap reached with two cheap regressions from the round-1 fixes: one extra bounded fix round 3, no round 4; anything open after it goes to the owner | owner (standing instruction: recommended) |
| 2026-10-15 | Architect Q1-Q2: no image-cache size in `mem` for S2; frame time is UI-thread work only (stated in spec), no frameDurationTotalNanos switch in S2 | owner (standing instruction: recommended) |
| 2026-10-15 | Test-design spec pins accepted: ms fields floor; back-dated frames `t` clamped to the session origin; a TRIM_MEMORY_MODERATE trim line is written in background while the periodic line is not; a key repeat with no open burst opens one with n=1; an exception from super propagates and the probe is not called | owner (standing instruction: recommended) |
| 2026-10-15 | Gate 1: spec approved as assembled (M, perf, AC-11, AC-11a, AC-12, AC-13, AC-14, AC-S2-OFF, AC-S2-NODIST, AC-S2-R8) | owner (standing instruction: recommended) |
| 2026-10-15 | Q1-Q3 take the analyst's recommendations | owner (standing instruction: recommended) |

## Log
- 2026-10-15 created (stage idea) as part 2/5 of the split of
  usage-telemetry-log (size L). Analyst estimate M. Scope: AC-11 to AC-14. Needs telemetry-core. Depends on: telemetry-core.
  Source: .flow/runs/usage-telemetry-log/brief.md (event catalogue, ACs, owner answers Q1-Q3)
  and research.md.
- 2026-10-15 /task:run started (standing instruction: recommended answers end to end); research.md inherited from usage-telemetry-log; base cd16eb47.
- 2026-10-15 triage: brief.md (feature, M, perf, 8 ACs incl. AC-11a key-code allow-list); new dependency metrics-performance; Q1-Q3 on recommendation; stage design.
- 2026-10-15 design-architect.md (16 decisions, 11 risks; JankStats.createAndTrack, key_lat = first frame starting after dispatch, mem carries its own scr); test-designer next.
- 2026-10-15 test-design.md (P0 80, device D1-D12; real-TV pass MANUAL); waiting for baseline.
- 2026-10-15 baseline.md (low-load A/B: log off 1559 ms, on 1594 ms; PSS 220.6/223.7 MB single samples; host load dominates noise). Spec assembled, gate 1 on standing instruction; stage plan.
- 2026-10-16 plan written (10 tasks + qa-device handoff, 7 refinements); readiness-1 READY, no must-fix; stage impl.
- 2026-10-16 impl done: 12 commits 7ea12829..441612ee, check.full green (before docs-only commits), check.fast re-run green by conductor; stage verify.
- 2026-10-16 verify 1: qa-report.md, all ACs PASS (NODIST by proxy), perf within bounds, M1 real TV NOT CHECKED; evidence 284bd7a0; stage review.
- 2026-10-16 review 1: blind 3, edge 2, tests 6, codex 2 (review+adversarial). triage-1: 9 fix (2 major: wrong scr after parent pop; KeyProbe gate untested), 1 reject (empty scr per D3), 2 deferred -> telemetry-expiry-reenable-race, telemetry-expiry-flush-loss.
- 2026-10-16 fix round 1: 8 commits 29622208..5eba2142, check.full green. Verify 2 (qa-device): AC-12, AC-S2-OFF, AC-13, AC-14, D5 spot PASS; triage #11 closed (91096592). Review 2: edge 2+nit, tests 3, codex 2; triage-2: 6 fix, cap exceeded by one bounded round 3 (recorded decision).
- 2026-10-16 fix round 3: ada5bcf5..4ccfb96c, check.full green. Verify 3 (qa-device, 4d9c4e31): affected ACs PASS except transient Main label on Back to the tab host. Review 3: edge 1, tests 1, codex 1 (same as edge). triage-3: 3 deferred -> telemetry-tab-host-back-label, rail-content-mismatch-rapid-back; stage judge.
- 2026-10-16 judge-1: CONCERNS, no must-fix, check.full re-run green (1988 tests), co-authored-by 0. Gate 2 ship on standing instruction; stage handover.
- 2026-10-16 squashed to 4 commits 69a95b2e..667a6590, tree 45ad0b4a unchanged, check.fast green.
- 2026-10-16 shipped: merged --no-ff into master as d12f257e; Orca worktree removed.
