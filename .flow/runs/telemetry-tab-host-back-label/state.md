---
slug: telemetry-tab-host-back-label
title: "Back from a root-pushed screen onto the tab host is labelled Main for 130-380 ms until the tab re-announces itself; add a Compose-level test of the tab host binding"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/telemetry-tab-host-back-label
worktree: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label
base: bdd2c792
spec: docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md
plan: docs/superpowers/plans/2026-10-17-telemetry-tab-host-back-label.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-16
migrated:
updated: 2026-10-17
---

## Next
done; follow-ups: tab-stack-lost-on-root-push, settings-tab-spinner-stuck

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-17 | Q1 record the tab top frozen at cover time | owner (standing instruction: recommended) |
| 2026-10-17 | Q2 skip replayed nav tab X->X line when tab unchanged | owner (standing instruction: recommended) |
| 2026-10-17 | Q3 wiring test is instrumented Compose; Robolectric out of scope | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1: approved as written (M, tracks none, AC-1..AC-10) | owner (standing instruction: recommended) |
| 2026-10-17 | Plan Q: D11 written as overload bind(host, top) / bind(host, top, reappearingTop), same contract | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 2: ship (judge-1 CONCERNS, no must-fix) | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1 delta: AC-6 rewritten (label names the screen shown after Back, today the tab root); design delta D9-D14; Q4-Q6 recommended | owner (standing instruction: recommended) |

## Log
- 2026-10-16 created (stage idea) as a deferred finding of telemetry-frames-keys-memory review round 3 (review-edge-3.md F1, review-codex-3.md, qa-report.md Round 3 defect 1, review-tests-3.md F1, see .flow/runs/telemetry-frames-keys-memory/triage-3.md).
- 2026-10-17 run started; base bdd2c792; researcher spawned.
- 2026-10-17 research.md written (option A recommended); analyst spawned.
- 2026-10-17 brief.md: M, tracks none, 10 ACs; Orca worktree created.
- 2026-10-17 design-architect.md, test-design.md; spec assembled and committed (8c9143b6); gate 1 approved.
- 2026-10-17 plan written; readiness-1 READY; plan committed. Conductor adds a red-on-base itest run between Task 2 and Task 3 (readiness note 1).
- 2026-10-17 impl tasks 1-2 done (d1768a57, 60495089, 8b84a6cf); 2 JVM tests red as planned; qa-device running the wiring itest pre-fix.
- 2026-10-17 conductor booted puber_tv_36; qa-prefix-red.md: AC-7/AC-4/AC-3 red as expected, AC-5 green, AC-6 red at a fixture wait. Implementer tasks 3-4 spawned.
- 2026-10-17 impl tasks 3-4 done (7482ad51, 4be887ec); check.full green per implementer; check.fast confirmed by conductor. qa-device verify round 1.
- 2026-10-17 qa round 1: check.full green (1994 tests); AC-6 FAIL: tab-local stack lost after root push + Back (pre-existing), frozen label names ShowAll while screen shows tab root. Routed to architect design delta (approved AC-6 premise false).
- 2026-10-17 design-architect-2.md (freeze reappearing root); gate 1 delta approved; new idea runs tab-stack-lost-on-root-push, settings-tab-spinner-stuck.
- 2026-10-17 plan delta Tasks 5-8 appended (D11 as overload); readiness round 2 spawned.
- 2026-10-17 readiness-2 READY (spec still carries superseded AC-6 in test design and M2: test-designer to update). Implementer tasks 5-8.
- 2026-10-17 tasks 5-8 done (58c58b4b..520c8206), full gate green per implementer; spec test design updated (e38d19fe); qa-device verify round 2.
- 2026-10-17 qa round 2: all 10 ACs PASS, check.full green (1995), AC-6 itest red at 70200dee and green at e38d19fe; evidence f5bfec56. Review round 1.
- 2026-10-17 review round 1: codex clean; edge F1 major (telemetry enabled while covered -> Main) and 3 cheap minors/nits marked fix; blind F1/F2 rejected as accepted design risks R3/R5. triage-1.md.
- 2026-10-17 fixes 572e252a, b256e3a5 (check.full green per implementer). Review round 2 + qa on affected ACs.
- 2026-10-17 review round 2: codex clean; edge/tests confirm fixes; spec drift (docs) to fix; 2 nits rejected. qa affected ACs PASS, finding #1 device path NOT CHECKED (unreachable in UI). triage-2.md.
- 2026-10-17 spec amended (69b74347). Final judge gate round 1.
- 2026-10-17 judge-1 CONCERNS (no must-fix; check.full green at 69b74347). Co-authored-by count 0. Gate 2: ship. Pre-squash tree 5a3860e2.
- 2026-10-17 squashed to 3 commits (df9e53cc, 400807bf, 6fed96d9); tree 5a3860e2 identical. Shipping.
- 2026-10-17 shipped: merge c185cc70 (--no-ff) into master; worktree removed (orca --force: daemon socket unreachable; branch merged and clean), branch deleted by Orca.
