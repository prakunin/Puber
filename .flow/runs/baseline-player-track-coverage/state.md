---
slug: baseline-player-track-coverage
title: "Find why the mock-generated profile lost player track-matching rules (SubtitleTrackMerger, AudioTrackPreferenceResolver -16..-25%)"
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
source: baseline-mock-protocol-parity
created: 2026-11-27
migrated:
updated: 2026-11-27
---

## Next
triage with /task:run baseline-player-track-coverage

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-27 created (stage idea), deferred from baseline-mock-protocol-parity fix round 1: after the two-protocol mock and fixture changes, baseline-prof.txt player counts vs base 08f50b81: media3/exoplayer 3490 -> 3313, exoplayer/hls 387 -> 348, AudioTrackPreferenceResolver 57 -> 48, SubtitleTrackMerger 16 -> 12, PlayerTextTracks 12 -> 9. Lost rules: SubtitleTrackMerger.merge, enrichPlayerTracks, findExactIdentityMatch, <init>; AudioTrackPreferenceResolver exactLabelMatch, exactSubtitleUrlMatch, findAudioTrackIndex, findSubtitleTrackIndex. Not caused by the watched/bookmarked fixture (identical counts without it). Base item.json had no audios/subtitles/translation either. Candidates: HTTP/1.1 media timing vs HTTP/2, iteration noise, playback dwell. Evidence: worktree baseline-mock-protocol-parity, logcat /tmp/puber-baseline-profile/20261127-063806-logcat.txt (may be gone), Implementation record of that plan.
