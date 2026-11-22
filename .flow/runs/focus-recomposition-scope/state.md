---
slug: focus-recomposition-scope
title: "Stop every D-pad step from recomposing all visible cards"
stage: idea
size:
tracks: [perf]
kind: improvement
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
source: startup-splash-preload
created: 2026-11-21
migrated:
updated: 2026-11-21
---

## Next
Audit FYI: FocusTargetState reads focus in composition and Home reads focusedTarget at screen level, so every D-pad step recomposes all visible cards/rows; LocalContentFocusActive is a staticCompositionLocalOf that changes at runtime.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
