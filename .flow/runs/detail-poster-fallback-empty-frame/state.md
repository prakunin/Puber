---
slug: detail-poster-fallback-empty-frame
title: The detail poster goes empty while a failed primary URL falls back to the next one
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
source: detail-poster-trailer-flicker
created: 2026-11-19
migrated:
updated: 2026-11-19
---

## Next
triage with /task:run detail-poster-fallback-empty-frame

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-19 created (stage idea), deferred from detail-poster-trailer-flicker triage-1 row 7 (codex). After the detail panel has shown a poster, a failed primary poster URL hides the image (`isFailed` alpha 0, no error painter) and the skeleton stays suppressed (`hasShownImage`), so the panel is empty (0.32 surfaceVariant) until the fallback URL loads, then fades in from empty. Decide: keep the previous picture or show the skeleton. Code: `core/ui/uikit/component/SkeletonAsyncImage.kt:42-62`, `VideoItemGridDetails.kt` `PosterStill`.
