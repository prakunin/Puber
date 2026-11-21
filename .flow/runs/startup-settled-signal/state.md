---
slug: startup-settled-signal
title: "One startup-settled signal that defers background work and coalesces Home publishes"
stage: idea
size:
tracks: []
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
After startup-splash-preload ships: a single "startup settled" signal (after reveal) that defers MainVM.reportDeviceInformation, TvHomeSyncCoordinator.requestRefresh(immediate = true), the UpdatePromptVM auto check, DetailsPrefetcher and the watch-state sync (replacing its 5 s StartupSyncDelay), staggered; coalesce Home section publishes (HomeVM.publishSections) that arrive close together into one publish. Split out of startup-splash-preload (P1); see its research.md.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) as the P1 split of startup-splash-preload
