---
slug: showall-first-entry-rail-focus
title: "On first entry into a Show all grid from the tab root, focus sits on the expanded side rail; DPAD_RIGHT then focuses the first card"
stage: idea
size:
tracks: []
kind: bug
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-17
migrated:
updated: 2026-10-17
---

## Next
triage with /task:run showall-first-entry-rail-focus

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-17 created (stage idea) from tab-stack-lost-on-root-push: qa-report.md round 1 observation (not compared with base).
- 2026-11-24 from catalogue-browse-all qa round 3: the same expanded-rail-on-open also costs a second Back. After a details round trip Main recomposes, RailOpenBackHandler (ui/feature/main/component/MainScreenComponent.kt:199) registers after TabBackHandler (core/ui/navigation/component/FlowComponent.kt:752) and wins, so the first Back from a Show all grid only closes the rail. Reproduced through the shelf «Показать все» tile too (predates catalogue-browse-all).
- 2026-11-24 catalogue-browse-all qa round 4: the first Back after a details trip shows the toast «Нажми «Назад» ещё раз, чтобы выйти» while the grid stays (both via the browse entry and via the shelf «Показать все» tile), so the exit-confirmation handler may be the one that wins, not only the rail handler; re-trace at triage.
