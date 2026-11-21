---
slug: startup-settled-signal
title: "One startup-settled signal that defers background work and coalesces Home publishes"
stage: done
size: M
tracks: [perf]
kind: improvement
branch: prakunin/startup-settled-signal
worktree: /home/user1/orca/workspaces/Puber/startup-settled-signal
base: 7bcbfd83
spec: /home/user1/orca/workspaces/Puber/startup-settled-signal/docs/superpowers/specs/2026-11-21-startup-settled-signal.md
plan: /home/user1/orca/workspaces/Puber/startup-settled-signal/docs/superpowers/plans/2026-11-21-startup-settled-signal.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
source: startup-splash-preload
created: 2026-11-21
migrated:
updated: 2026-11-21
---

## Next
Nothing; shipped. Manual checks for the owner in judge-1.md.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | codex: required (owner) | owner |
| 2026-11-21 | caps lifted (owner) | owner |
| 2026-11-21 | Q1-Q3 of brief.md: recommended answers taken (settled = splash leaves composition / restored activity at once / 5 s fallback, once per process; order prefetch, TV home, update check, device info, watch-state sync, 500 ms apart; emulator devBenchmarkRelease A/B vs 7bcbfd83, 5 cold starts) | owner (standing instruction: recommended) |
| 2026-11-21 | Q4: baseline shows every start revealed by the cap; folded into startup-first-input-on-tv (Log line) | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1: approve (M, perf, 13 ACs, option A) | owner (standing instruction: recommended) |
| 2026-11-21 | T5: test aWarmHeldForStartupTakesNoSlot dropped (no test can observe gate-vs-permit order; every warm is held by the same global gate) | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-21 | Standing instruction: every gate on the recommended answer | owner (standing instruction: recommended) |

## Log
- 2026-11-21 created (stage idea) as the P1 split of startup-splash-preload
- 2026-11-21 /task:run started; base 7bcbfd83; research spawned
- 2026-11-21 research.md written (option A recommended, 5 questions); triage spawned
- 2026-11-21 brief.md: M, perf, 13 ACs; Q1-Q3 answered as recommended; worktree /home/user1/orca/workspaces/Puber/startup-settled-signal created; design + baseline spawned
- 2026-11-21 design-architect.md, test-design.md (P0 22), baseline.md (publishes 8, revealed 2857 ms, all capped); spec assembled and committed a96c6972; gate 1 approved; plan spawned
- 2026-11-21 plan written (7 tasks); readiness spawned
- 2026-11-21 readiness-1 READY; plan committed d98f937a; implementer spawned
- 2026-11-21 impl done: 7 commits 72c6d769..aacf6035, check.full green (implementer), check.fast green and named tests 0 missing (conductor); qa verify spawned
- 2026-11-21 qa-report round 1: no defects; all ACs PASS except AC-12 device half NOT CHECKED; publishes 8 -> 5; evidence 5686efd0. Review round 1 spawned (ux-designer not matched: no Compose layout change, only an onGone callback)
- 2026-11-21 review round 1: 1 major (tests), minors; triage-1.md: fix T1-T6, reject T7-T10; implementer fix spawned
- 2026-11-21 fix round 1 done (17f8710a..b846ea7d); full suite showed KinoPubApiClientAuthProbeTest UncompletedCoroutinesError (2), passes alone, untouched class (known class: unit-suite-flaky); round 2 spawned
- 2026-11-21 round 2: codex clean, edge 0, tests 1 minor (accepted); qa round 2 green (904573a3); triage-2.md; judge spawned
- 2026-11-21 judge-1 PASS (manual 2); gate 2 ship recorded; squash spawned
- 2026-11-21 squashed to 3 commits (08271680, e6b42269, a927fb05), tree 44eb1f84 unchanged, check.fast green
- 2026-11-21 shipped: merged as 7b7530da (master had cache-shared-http-stack meanwhile; merged tree compiles, testDevDebugUnitTest green in main); worktree removed, branch deleted
