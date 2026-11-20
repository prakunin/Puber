---
slug: update-prompt-focus
title: "The 'new version available' overlay must take focus when it appears"
stage: dropped
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
created: 2026-10-11
updated: 2026-11-20
---

## Next
Seen in upstream-sync-triage QA (run 3): on one launch the overlay buttons were not focusable, the D-pad reached Main underneath, and the first Back moved focus to the drawer instead of dismissing it; a later launch was fine. `UpdatePromptOverlay`, `App.kt`.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-20 | Folded into update-dialog-focus-trap as a duplicate | owner (standing instruction: recommended) |

## Log
- 2026-10-11 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
- 2026-11-20 dropped: duplicate of update-dialog-focus-trap
