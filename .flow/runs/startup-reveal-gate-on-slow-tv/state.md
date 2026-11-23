---
slug: startup-reveal-gate-on-slow-tv
title: "Make the reveal gate fit a slow TV: cap value, bounded image hold, deferred-work window"
stage: done
size: M
tracks: [perf]
kind: improvement
branch: prakunin/startup-reveal-gate-on-slow-tv
worktree: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv
base: 9158cf82
spec: docs/superpowers/specs/2026-11-23-startup-reveal-gate-on-slow-tv.md
plan: docs/superpowers/plans/2026-11-23-startup-reveal-gate-on-slow-tv.md
rounds: {verify: 1, review: 3, judge: 1}
blocked_by: []
source: startup-first-input-on-tv
created: 2026-11-22
migrated:
updated: 2026-11-23
---

## Next
Done; merged as 409e87df. Owner: AC-8 and the AC-7 focus clause on the box (judge-1.md). Follow-up: startup-post-reveal-key-latency.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Accept skeletons at fade end for images not decoded by ready + G (reverses startup-splash-preload Q2)? | no | answered | Yes; G starts at 300 ms and is fixed from the S1 box set |
| Q2 | Keep the 2.5 s cap? | no | answered | Keep; S0 goes to update-install-dex-metadata and home-hero-late-insert |
| Q3 | Reach S1 without a forced compile; deferred-work window in scope? | no | answered | Wait for background dexopt, check read-only with dumpsys; escalate a forced compile as one authorised action only if S1 is not reached; deferred-work window observation only (AC-10) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-23 | Gate 2: ship (judge CONCERNS accepted: AC-7 focus clause NOT CHECKED on counted starts, AC-6 limit ready - begin <= 2200 ms, anchor-B latency in startup-post-reveal-key-latency) | owner (standing instruction: recommended) |
| 2026-11-23 | G fixed at 300 ms | conductor |
| 2026-11-23 | AC-7 restated: at most one first key over 199 ms across anchors A and C and the baseline-method control; anchor B recorded as an observation against ready_drained (design-ac7-diagnosis.md Q1) | owner |
| 2026-11-23 | Follow-up idea run startup-post-reveal-key-latency for anchor-B latency (Q2) | owner |
| 2026-11-23 | Forced S1 compile on the box for com.kino.puber.stage (INSTALL_PROFILE, force-stop, compile -m speed-profile -f) authorised for this run and its re-measures; run by the conductor | owner |
| 2026-11-23 | Design Q4 (no baseline profile regeneration unless AC-6 misses); perf baseline reused from release-startup-compilation rounds 3-4 | owner (standing instruction: recommended) |
| 2026-11-23 | Gate 1: approved (G = 300 ms start, skeletons at fade end accepted, cap 2.5 s kept) | owner (standing instruction: recommended) |
| 2026-11-23 | Q1-Q3 on the analyst's recommended answers; codex required except at its usage limit (skip the round); caps lifted | owner (standing instruction: recommended) |

## Log
- 2026-11-22 created (stage idea), deferred from startup-first-input-on-tv design round 2 (its design-architect.md, baseline.md)
- 2026-11-22 from startup-first-input-on-tv review round 2: reveal-relative key anchors (+300/+1000 ms) cannot be hit from outside the app (detector lags 0.9-1.85 s); needs an in-app, benchmark-only key trigger at the revealed mark before AC-style anchors are used again
- 2026-11-23 /task:run started on base 9158cf82 (after release-startup-compilation and watch-index-catch-up shipped); researcher spawned
- 2026-11-23 research.md (option B: bound the image hold) and brief.md (improvement, M, perf, 10 ACs) written; Q1-Q3 answered on recommendations; Orca worktree /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv on prakunin/startup-reveal-gate-on-slow-tv; stage design
- 2026-11-23 design-architect.md (9 decisions, 7 risks) and test-design.md (P0 10, P1 7, MANUAL 9) written; spec dd599964 assembled; gate 1 approved; stage plan
- 2026-11-23 plan written (3 tasks); readiness-1 READY; stage impl, implementer spawned
- 2026-11-23 impl tasks 1-3 done (49179d55, 44b57e6a, 7ed2f1ec); check.full green; check-named-tests 0 missing; 4 red runs recorded; stage verify, qa-device spawned
- 2026-11-23 qa-device verify round 1: AC-1..5 PASS (unit), device ACs NOT CHECKED: box after install is S0 (speed-profile reason=install, profiles empty); forced S1 compile needs owner authorisation; worktree devBenchmarkRelease c31dab2d installed (not launched); evidence 79074a0a
- 2026-11-23 conductor S1 prep on the box: dump-profiles empty, INSTALL_PROFILE result=1, force-stop, compile -m speed-profile -f Success (13.4 s), dumpsys speed-profile reason=unknown; app not launched; lease released; qa-device round 1b spawned
- 2026-11-23 qa-device round 1b (S1 15 + control 15 + S0 5): AC-6 PASS (revealed-ready median 326 / max 379, 0 cap starts, grace 14/15), AC-8/9/10 PASS observation, AC-7 FAIL (anchor B, 100-230 ms after fade start: 5 of 15 keys 291-543 ms; anchors A/C fine); evidence 854a4981; box left in S0; architect diagnosis spawned
- 2026-11-23 design-ac7-diagnosis.md: slow anchor-B keys come from held images landing after the reveal; no G meets AC-7 as written with AC-6; owner restated AC-7 and filed startup-post-reveal-key-latency; under the restated AC-7 round 1b gives A+C 0/10, control 0/15; stage review round 1
- 2026-11-23 review round 1 (blind 2 minor, edge 0, tests 2 minor, state-path 0; check.full green 2466 tests; codex skipped at its limit): triage-1 3 fix, 1 reject; fixes a00eea2b, 7ef4b7dd, 0800302d
- 2026-11-23 review round 2 (edge: 1 major AC-7 focus clause over-claimed, 3 minor, 1 nit): triage-2 all fixed (61b05719, 36fcb051, cdb85fa1); a test-designer commit landed on master by mistake (b9148da7) and was reset by the conductor (file kept)
- 2026-11-23 review round 3 (edge: triage-2 holds; 1 minor, 2 nits on evidence text): fixed bf142737; check.full green at bf142737; stage judge
- 2026-11-23 final-judge gate round 1: CONCERNS, must-fix none, MANUAL 2 (AC-8, AC-7 focus clause); review-tests-1.md saved late by the conductor (missed at round 1); G recorded as final (300 ms); branch squashed to 3 commits (de0c9c8a, 2a5c3b31, 78d6541a; backup ref backup/startup-reveal-gate-pre-squash); Co-Authored-By 0; gate 2 ship; stage handover
- 2026-11-23 shipped: merged into master as 409e87df (--no-ff); Orca worktree removed
