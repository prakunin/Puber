---
slug: tab-flow-lifecycle-itest
title: "Fix the instrumented test that fails on master"
stage: idea
size:
tracks: []
kind: idea
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-11
migrated: 2026-10-12
updated: 2026-10-12
---

## Next
`TabFlowLifecycleTest.refreshThenPlayerAndDetailsBackUsesTheRefreshedHistoryLifecycle` fails at `TabFlowLifecycleTest.kt:122`: `assertTextContains("content=0")` against node text `[content=0 vm=1]` needs `substring = true` (or the assertion is stale). Reproduced on master in upstream-sync-triage QA run 5.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-11 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
