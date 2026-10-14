---
slug: manual-quality-hls4
title: "Play manual quality from the hls4 stream with a max video size, keeping manifest subtitles and audio names"
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
created: 2026-10-14
migrated:
updated: 2026-10-14
---

## Next
Triage with /task:run manual-quality-hls4.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-14 created (stage idea) from subtitle-sideload-merge design delta 2 (Q5, recommended answer taken under the owner's standing instruction): manual quality plays `url.hls` first (PlayerInteractor.kt:238-246), which has no subtitle renditions and no audio names ("Track 1"), so manifest subtitles vanish on 720p/480p. Proposed: play hls4 with a max video size. Also seen on device: on 480p/720p the new stream was once drawn at native size in the top-left corner over a frozen old frame. Context: ../subtitle-sideload-merge/design-architect-2.md, qa-report.md round 2.
