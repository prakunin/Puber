---
slug: tab-stack-lost-on-root-push
title: "A tab-local stack (Show all) is lost after a root push (Details) and Back: Voyager disposes the covered tab's saved state; the fix must flip TabHostTelemetry reappearingTop to the live top and restore the original AC-6"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/tab-stack-lost-on-root-push
worktree: /home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push
base: f14dcc84
spec: docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md
plan: docs/superpowers/plans/2026-10-17-tab-stack-lost-on-root-push.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-17
migrated:
updated: 2026-10-17
---

## Next
done; follow-ups: showall-focus-restore-hardening, tab-refresh-while-covered-flash, grid-focus-restore-after-back, showall-first-entry-rail-focus, settings-subselection-reset

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-17 | Option B (tab host owns a SaveableStateHolder) | owner (standing instruction: recommended) |
| 2026-10-17 | Q1 aim for exact card focus; small ShowAll-local fix allowed, bigger defers | owner (standing instruction: recommended) |
| 2026-10-17 | Q2 fold the TabFlowLifecycleTest substring fix (tab-flow-lifecycle-itest) into this run | owner (standing instruction: recommended) |
| 2026-10-17 | Design Q1: do not clear removed tabs' saved state (follow-up only on evidence) | owner (standing instruction: recommended) |
| 2026-10-17 | Design Q2: if the ShowAll focus fix is needed, record a follow-up idea for other grid screens | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1: approved as written (M, tracks none, AC-1..AC-11) | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 2: ship (judge-1 CONCERNS, no must-fix) | owner (standing instruction: recommended) |
| 2026-10-17 | Q3 keep settings-tab-spinner-stuck separate; record post-fix observation | owner (standing instruction: recommended) |

## Log
- 2026-10-17 created (stage idea) as a deferred finding of telemetry-tab-host-back-label: qa-report.md Defect 1, design-architect-2.md (root cause from Voyager 2.2.21 bytecode; FlowComponent.kt root Navigator disposeNestedNavigators default).
- 2026-10-17 run started; base f14dcc84; researcher spawned.
- 2026-10-17 research.md: root cause confirmed (Voyager nested dispose drops Tab: saveable state); option B recommended. Analyst spawned.
- 2026-10-17 brief.md: M, tracks none, 11 ACs; Orca worktree created.
- 2026-10-17 design-architect.md (11 decisions, 9 risks); test-designer spawned.
- 2026-10-17 test-design.md; spec assembled; gate 1 approved.
- 2026-10-17 plan written; readiness-1 READY; plan committed. Implementer tasks 1-2.
- 2026-10-17 tasks 1-2 done (9b2e0f50, 57f15531, a41722c2); qa-device pre-fix red run.
- 2026-10-17 qa-prefix-red.md: T1, T2, T5b red (timeouts waiting for tab-local screen), 7 green; TabFlowLifecycleTest green with substring fix. Implementer tasks 3-4.
- 2026-10-17 tasks 3-4 done (e1a0b4d1, b35239c3, 5a3d76f0); check.full green per implementer; check.fast confirmed. qa-device verify round 1.
- 2026-10-17 qa round 1: all ACs PASS except AC-2 PARTIAL (focus on first visible card, not opener) and AC-7 NOT CHECKED (T4 stand-in); itests 10/10 and 1/1; evidence a5568145. Task 5 condition met; implementer spawned.
- 2026-10-17 task 5 done (6b47873d), check.full green per implementer. Ideas recorded: grid-focus-restore-after-back (Q5), showall-first-entry-rail-focus, settings-subselection-reset (qa observations). qa round 2.
- 2026-10-17 qa round 2: all ACs PASS except AC-7 NOT CHECKED (T4 stand-in); evidence 59cdb9c3. Review round 1.
- 2026-10-17 review round 1: blind none; codex 1 minor; edge 2 minor; tests 2 minor. triage-1: #1-#3 fix, #4 verify on device vs base, #5 deferred (tab-refresh-while-covered-flash).
- 2026-10-17 fix b5919b85 (#1-#3; check.full green per implementer). Review round 2 spawned.
- 2026-10-17 base comparison: base also resets the Settings sub-selection -> triage-1 #4 pre-existing, settings-subselection-reset stays separate. qa on affected ACs at b5919b85.
- 2026-10-17 qa affected ACs PASS (evidence 62825360). Review round 2: one minor race (edge+codex) and test-strength items deferred to showall-focus-restore-hardening (cap). triage-2.md. Judge.
- 2026-10-17 judge-1 CONCERNS (no must-fix). Gate 2: ship.
- 2026-10-17 squashed to 4 commits (ac830d34..11a581d1, tree a91184b9 identical); shipped: merge db16ba9d (--no-ff); worktree removed (orca --force: daemon socket unreachable; branch merged and clean), branch deleted by Orca. tab-flow-lifecycle-itest closed as covered.
