---
slug: showall-focus-restore-hardening
title: "Show all focus restore: a reload landing in the 3-frame entry window leaves no card focused; focus tests do not pin the entry fallback, the mid-retry rail check, or a truly off-viewport card"
stage: done
size: M
tracks: []
kind: improvement
branch: prakunin/showall-focus-restore-hardening
worktree: /home/user1/orca/workspaces/Puber/showall-focus-restore-hardening
base: 35bfbbec
spec: docs/superpowers/specs/2026-10-17-showall-focus-restore-hardening.md
plan: docs/superpowers/plans/2026-10-17-showall-focus-restore-hardening.md
rounds: {verify: 1, review: 2, judge: 1}
blocked_by: []
created: 2026-10-17
migrated:
updated: 2026-10-17
---

## Next
done; follow-ups: showall-entry-restore-foreign-landing, showall-rail-close-focus-shift

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-17 | Q1 fix shape B + C | owner (standing instruction: recommended) |
| 2026-10-17 | Q2 keep the helper ShowAll-local; only the pure resolver may move next to resolveFocusedItemId | owner (standing instruction: recommended) |
| 2026-10-17 | Design Q1: fallback C stays entry-only; live-path fallback recorded for grid-focus-restore-after-back | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1: approved as written (M, tracks none, AC-1..AC-10) | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 2: ship (judge-1 CONCERNS, no must-fix) | owner (standing instruction: recommended) |
| 2026-10-17 | Q3 JVM seam test for the mid-retry contentFocusActive check; androidTest only if shown red | owner (standing instruction: recommended) |

## Log
- 2026-10-17 created (stage idea) as deferred findings of tab-stack-lost-on-root-push review round 2 (review cap): review-edge-2.md F1 and review-codex-2.md (re-resolve restoreId before each attempt, or reconcile while the entry restore is pending), review-tests-2.md F1-F3 (entry fallback test without recomposing the live grid; mid-retry contentFocusActive test with a paused clock; deep-card guard index >= rows*3).
- 2026-10-17 run started; base 35bfbbec; researcher spawned.
- 2026-10-17 research.md: race and 3 test gaps confirmed; B+C recommended. Analyst spawned.
- 2026-10-17 brief.md: M (TV focus risk area), tracks none, 10 ACs; Orca worktree created.
- 2026-10-17 design-architect.md (9 decisions, 7 risks); test-designer spawned.
- 2026-10-17 test-design.md; spec assembled; gate 1 approved.
- 2026-10-17 plan; readiness-1 NOT READY (AC-7 red-once); plan round 2 + owner corrections; readiness-2 READY. Implementer tasks 1-3.
- 2026-10-17 tasks 1-3 done (abecf695, 49067777, 4aa7bb0e); JVM reds recorded locally; check.full green per implementer; check.fast confirmed. qa round 1.
- 2026-10-17 qa round 1: all ACs PASS; AC-1 JVM only; AC-9 opener-removed exact neighbour PARTIAL on device; mutations red once; evidence d6b51a2e. Review round 1.
- 2026-10-17 review round 1: blind none, codex clean, edge 2 minor, tests 2 minor; triage-1: #1-#3 fix, #4 deferred (showall-entry-restore-foreign-landing).
- 2026-10-17 fix cc66ec96 (#1-#3; 23 JVM tests; check.full green per implementer). Review round 2 + qa.
- 2026-10-17 review round 2: edge none, codex clean, tests 1 minor (effect key not pinned by a test). qa affected ACs PASS (evidence 3b0aeb04); open observation: rail close lands one row above -> base comparison running.
- 2026-10-17 base comparison: rail close shift is pre-existing (base 3/3). triage-2: tests F1 deferred into showall-entry-restore-foreign-landing; new idea showall-rail-close-focus-shift. Judge.
- 2026-10-17 judge-1 CONCERNS (no must-fix). Gate 2: ship.
- 2026-10-17 squashed to 3 commits (a50c4b2f, c83761db, 3c3b3bbf; tree 192d272d identical); shipped: merge 55bb5bfd (--no-ff); worktree removed (orca --force: daemon socket unreachable; branch merged and clean).
