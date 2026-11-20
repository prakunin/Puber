---
slug: update-gate-wiring-tests
title: Main's gate wiring and the root gate focus restore have no instrumented test
stage: idea
size:
tracks: []
kind: improvement
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
source: update-dialog-focus-trap
created: 2026-11-20
migrated:
updated: 2026-11-20
---

## Next
From update-dialog-focus-trap review round 1 (blind F3, tests F3): InitialDrawerContentFocusTest copies Main's `LaunchedEffect(outerActive) { beginInitialHandoff() }` instead of running DrawerMainContent, the D7 `outerActive` gating of the rail Back handlers is untested, and FlowComponent's GateFocusRestoreEffect (D8) has no test while the device run shows the handoff, not D8, returns focus. Either extract the wiring into a shared composable and test it, or test through MainScreenComponent/FlowComponent; consider removing D8 if it stays redundant. Also from judge-1: GateFocusRestoreEffect sets saved = true even when saveFocusedChild() returns false, unlike the spec's D8.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-20 created (stage idea) from update-dialog-focus-trap triage-1
