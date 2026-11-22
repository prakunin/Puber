---
slug: player-cache-save-churn
title: "Reduce player cache writes and the 30 s save side effects"
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
Remaining after the 2026-11-21 split: ON_STOP only pauses (decoder and buffer kept in background); all API subtitles side-loaded and awaited at prepare. Media-cache writes are in cache-storage-bounds, markStale and TV home refresh per save in cache-targeted-invalidation.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
- 2026-11-21 scope moved: the media-cache write half goes to cache-storage-bounds (main player read-only), markStale per save and TV home refresh per save go to cache-targeted-invalidation; what stays here: ON_STOP only pauses, all subtitles side-loaded
