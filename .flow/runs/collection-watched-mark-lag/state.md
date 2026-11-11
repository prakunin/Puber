---
slug: collection-watched-mark-lag
title: "A refetched card can show a stale watched mark for a few seconds after Details marks it (server watch fields lag the write and win over the local record)"
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
source: collection-detail-inplace-refresh
created: 2026-11-11
migrated:
updated: 2026-11-11
---

## Next
triage with /task:run collection-watched-mark-lag

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-11 created (stage idea) from collection-detail-inplace-refresh: qa round 1 observation (1/18 stale watched mark 2.6 s after Back); architect addendum: server-side lag, WatchStateRepository.kt:223, pre-existing
