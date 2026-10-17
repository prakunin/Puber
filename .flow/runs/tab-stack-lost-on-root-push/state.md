---
slug: tab-stack-lost-on-root-push
title: "A tab-local stack (Show all) is lost after a root push (Details) and Back: Voyager disposes the covered tab's saved state; the fix must flip TabHostTelemetry reappearingTop to the live top and restore the original AC-6"
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
triage with /task:run tab-stack-lost-on-root-push

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-17 created (stage idea) as a deferred finding of telemetry-tab-host-back-label: qa-report.md Defect 1, design-architect-2.md (root cause from Voyager 2.2.21 bytecode; FlowComponent.kt root Navigator disposeNestedNavigators default).
