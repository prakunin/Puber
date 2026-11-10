---
slug: prompt-hide-focus-under-controls
title: Return focus to the controls when a focused skip / next-episode prompt hides while the controls are up
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
source: seek-blocked-by-skip-prompt
created: 2026-11-10
migrated:
updated: 2026-11-10
---

## Next
Triage with /task:run prompt-hide-focus-under-controls; first observe on the device what the next key does after the hide.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-10 created (stage idea) from the seek-blocked-by-skip-prompt judge-1 Concern 1: scope (d) said focus returns to the controls target when a prompt hides with the controls up; `PlayerScreenContent.kt:83-91` acts only with the controls hidden. Path: controls up, Up onto the chip, Right seeks and hides the prompt; the next key's effect was not observed. Heals when the controls auto-hide.
