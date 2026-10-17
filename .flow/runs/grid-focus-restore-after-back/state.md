---
slug: grid-focus-restore-after-back
title: "Other grid screens restored after a root push and Back may focus the first visible card instead of the card that opened Details (ShowAll got a local fix)"
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
triage with /task:run grid-focus-restore-after-back

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-17 created (stage idea) from tab-stack-lost-on-root-push: spec Q5 (owner, standing instruction: recommended); ShowAllScreenContent.kt fix 6b47873d is the reference; rootAnchorCaptureRegistry only covers LazyListState screens.
- 2026-10-17 note from showall-focus-restore-hardening: consider extending the Show all last-resort grid focus (fallback C) to the live path (focused card gone while in the grid, all attempts failed).
