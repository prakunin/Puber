---
slug: subtitle-restore-model
title: "Restore the chosen subtitle across player recreation with upstream's SubtitlePreference keys (7d5addd2, f9c9ff38, a563d6f6)"
stage: idea
size:
tracks: []
kind: feature
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-12
migrated:
updated: 2026-10-12
---

## Next
Triage with /task:run subtitle-restore-model after subtitle-sideload-merge lands.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-12 created (stage idea) as step 2 of option C in subtitle-sideload-merge (owner decision): SubtitlePreference with forced and descriptive-label pref keys, restore across playback recreation, old-to-new pref compatibility. Context: ../subtitle-sideload-merge/research.md
- 2026-10-14 deferred from subtitle-upstream-port review round 2 (triage-2.md #1, owner decision): PlaybackController.prepare() clears pendingSubtitleTrack on every prepare, so rebuilding the same media (buffer preset, Fast DNS) on a stream with no audio group drops the live subtitle pick while the picker still shows the row (VM restore runs only from onTracksUpdated, which needs an audio group). Fix candidates: clear the pending pick only on media change, or re-send the current row after initializePlayer, or decouple the restore from audio groups.
