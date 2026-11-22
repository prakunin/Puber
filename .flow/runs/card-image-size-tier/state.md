---
slug: card-image-size-tier
title: "Keep a card-size image tier on disk so 4K wide posters are not decoded into small cards"
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
KinoPub 'wide' posters are 3840x2160 / 2160x1215 (Models.kt:198-203 offers only small, medium, big, wide); Home cards decode them down to 533x300 on every cold start (~3.2 s CPU on the emulator, startup-splash-preload qa Round 1c). Keep a downscaled card-size copy on disk (or a size-keyed disk tier) so later starts decode small files; also each hero page change decodes a 4K source.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from startup-splash-preload design delta 1 Q3
