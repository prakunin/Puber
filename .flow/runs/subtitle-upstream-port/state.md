---
slug: subtitle-upstream-port
title: "Bring upstream's subtitle fixes into this fork"
stage: review
size:
tracks: []
kind: idea
branch: subtitle-player-fixes
worktree: /home/user1/orca/workspaces/Puber/subtitle-player-fixes
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
First batch: subtitles Off by default (6123fdbc), no positional HLS subtitle match, language names / AI / forced labels, `ControlsStateMachine.initialize` (f3eb3533, Back on fresh media hides the controls first). Static gate green; device pass on emulator-5554: evidence/2026-10-10-subtitle-upstream-port-device.md (in the worktree). Not seen on device: Off across a player rebuild, a DEFAULT/FORCED manifest subtitle, forced/variant labels. Later candidates: side-load API subtitles on HLS (003d4c55), the full merge model.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-10 created on the board (stage review)
- worktree `subtitle-player-fixes` has uncommitted changes; device evidence: docs/superpowers/evidence/2026-10-10-subtitle-upstream-port-device.md (in the worktree)
- 2026-10-12 migrated from the former board file (board stage `review` -> `review`); the board row Notes are copied verbatim into ## Next
