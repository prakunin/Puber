---
slug: pairing-renewal-retry
title: "A failed request for a new pairing code must not leave a dead code on screen"
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
Deferred from the pairing-code-refresh review (code #2): a network failure at renewal ends the auth flow and `AuthVM` only shows a snackbar, so the expired code and 00:00 stay until a restart. Needs a retry or a visible "get a new code" action.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-10 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
