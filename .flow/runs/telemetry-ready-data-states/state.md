---
slug: telemetry-ready-data-states
title: "Session log: emit ready events for view models whose state is a single data class"
stage: idea
size:
tracks: [perf]
kind: improvement
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-15
migrated:
updated: 2026-10-15
---

## Next
triage with /task:run telemetry-ready-data-states

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-15 created (stage idea) from telemetry-core review round 1, triage-1.md row 11 (blind F3):
  `PuberVM.recordReady` fires only on a state class change, so DeviceSettingsVM, MainVM,
  ContentListVM and NetworkDiagnosticsVM never write a `ready` line. Accepted for S1 by decision
  (architect Q2); needs an optional per-VM ready hook. Source: .flow/runs/telemetry-core/triage-1.md.
