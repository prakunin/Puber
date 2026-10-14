---
slug: playback-controller-split
title: "Extract text-track matching and debug info from `PlaybackController`"
stage: idea
size: M
tracks: []
kind: refactor
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-12
updated: 2026-10-14
---

## Next
From the code-quality-audit report (full scope there): SOLID-4, STR-3; refactor, M, SOLID. Device: yes (playback)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-12 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
- 2026-10-14 deferred from subtitle-upstream-port review round 1 (triage-1.md #2): text-off default, FORCED flag and the removed positional fallback in PlaybackController have no regression test; cover them when text-track matching is extracted.
- 2026-10-14 from subtitle-sideload-merge gate 1 (owner, Q9): keep side-loaded subtitle hosts out of the debug "stream source" line (updateEffectiveStreamSource; side-load loads report TRACK_TYPE_UNKNOWN). Also: that run's text-track matching extraction is done there as SubtitleTrackSelector; this run keeps the text-off-at-build test, debug-info extraction and the package move.
