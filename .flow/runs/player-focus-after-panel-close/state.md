---
slug: player-focus-after-panel-close
title: "Closing the Episodes panel with Up must put visible focus back on the controls bar"
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
Deferred from player-overlay-autohide QA (Defect 1, predates the branch): after Up closes Episodes the bar has no focus ring and OK only restarts the hide timer. `requestControlsFocus` swallows a failed `requestFocus()` fired in the same frame as the bar's fade-in (`PlayerScreenContent.kt:84-88`). Second entry (player-overlay-autohide review, code #1 / codex #1): a non-last episode ends with Episodes open, viewer idles 6 s, the ended controls come up with focus aimed at `EpisodesButton` and likely no ring; not yet seen on a device.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-10 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
