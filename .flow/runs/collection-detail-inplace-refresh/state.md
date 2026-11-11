---
slug: collection-detail-inplace-refresh
title: "CollectionDetail reloads through Loading after Details returns content changes, which drops the focused card and the grid scroll"
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
source: grid-focus-restore-after-back
created: 2026-11-11
migrated:
updated: 2026-11-11
---

## Next
triage with /task:run collection-detail-inplace-refresh

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-11 created (stage idea) from grid-focus-restore-after-back: design Q2 (deferred; design-architect.md risk 2: CollectionDetailVM.kt:69-70,98-102)
- 2026-11-11 merged back from grid-focus-restore-after-back review round 1 (blind F1): reload through Loading after a Details change also drops the restored focus; reproduce it on a device when triaging
