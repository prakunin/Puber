---
slug: telemetry-uptime-autooff
title: "Session log: bound the 7-day auto-off by recorded uptime persisted across processes"
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
created: 2026-10-15
migrated:
updated: 2026-10-15
---

## Next
triage with /task:run telemetry-uptime-autooff

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-15 created (stage idea) from telemetry-core triage-3.md row 4 (edge F2, round 3): the
  monotonic auto-off bound restarts with each process, so an `enabled_at` stored under a clock that
  was wrong in the future keeps recording past 7 days across restarts while the row shows "7 days
  left". Fix: persist accumulated recorded uptime and expire on whichever limit comes first.
  Source: .flow/runs/telemetry-core/review-edge-3.md.
