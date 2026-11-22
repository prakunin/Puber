---
slug: card-progress-watching-duration
title: "Card progress readers (Item.toWatchStateOrNull, VideoItemUIMapper.toLocalWatchState, WatchStateRepository) take the length from watching.duration, which item payloads leave out"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: idea
# Size: S | M | L (set by triage)
size:
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: idea
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: resume-progress-anomaly
created: 2026-11-22
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-22
---

## Next
triage with /task:run card-progress-watching-duration

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-22 created (stage idea)- 2026-11-22 deferred from resume-progress-anomaly (design-architect-2.md, Q4, risk 3): WatchingInfo.duration defaults to 0 because KinoPub item payloads carry the length on the video (videos[].duration), not in watching; the Details mapper is fixed in resume-progress-anomaly, the card readers at WatchStateRepository.kt:508 and VideoItemUIMapper.kt:125 may have the same gap
