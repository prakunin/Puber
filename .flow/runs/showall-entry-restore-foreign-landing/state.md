---
slug: showall-entry-restore-foreign-landing
title: "Show all entry restore: a foreign focus landing (handoff or D-pad) plus a list change inside the restore window clears pendingEntry and drops the opener restore"
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
triage with /task:run showall-entry-restore-foreign-landing

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-17 created (stage idea) as a deferred finding of showall-focus-restore-hardening review round 1 (review-edge-1.md F2, triage-1.md): narrow timing window that base handled; suggested fix: clear pendingEntry only on the targeted landing or C, or snapshot it when the loop starts.
- 2026-10-17 also from showall-focus-restore-hardening review round 2 (review-tests-2.md F1): add an instrumented test that pins the LaunchedEffect(items, contentFocusActive) key (re-entry with content inactive, then active -> opener focused; red once by reverting the key) and correct the plan note that the key is review-only.
