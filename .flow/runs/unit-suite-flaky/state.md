---
slug: unit-suite-flaky
title: "Make the full unit-test run deterministic"
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
created: 2026-10-10
migrated: 2026-10-12
updated: 2026-10-12
---

## Next
Seen during pairing-code-refresh: 2 of 4 full `testDevDebugUnitTest` runs failed on tests the branch does not touch (`HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus`, earlier `HomeVMIncrementalPublishTest.aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished`); each passes alone.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-10 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
