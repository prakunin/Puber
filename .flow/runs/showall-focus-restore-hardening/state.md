---
slug: showall-focus-restore-hardening
title: "Show all focus restore: a reload landing in the 3-frame entry window leaves no card focused; focus tests do not pin the entry fallback, the mid-retry rail check, or a truly off-viewport card"
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
created: 2026-10-17
migrated:
updated: 2026-10-17
---

## Next
triage with /task:run showall-focus-restore-hardening

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-17 created (stage idea) as deferred findings of tab-stack-lost-on-root-push review round 2 (review cap): review-edge-2.md F1 and review-codex-2.md (re-resolve restoreId before each attempt, or reconcile while the entry restore is pending), review-tests-2.md F1-F3 (entry fallback test without recomposing the live grid; mid-retry contentFocusActive test with a paused clock; deep-card guard index >= rows*3).
