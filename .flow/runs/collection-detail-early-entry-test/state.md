---
slug: collection-detail-early-entry-test
title: "CollectionDetail: an instrumented test for a focus entry that arrives before the first restore attempt (R1); two seams failed to reproduce the device"
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
source: grid-focus-restore-after-back
created: 2026-11-11
migrated:
updated: 2026-11-11
---

## Next
triage with /task:run collection-detail-early-entry-test

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-11 created (stage idea) from grid-focus-restore-after-back: verify cap reached with D3; the host-entry test was red on base and on HEAD 3e1530f0 (3/3), while the device restores the card (Manual 3, 6/6). Find out whether R1 is real or the seam is wrong; see that run's qa-report.md Round 2 and design-architect.md D14
