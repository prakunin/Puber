---
slug: startup-main-thread-io
title: "Move the remaining startup prefs and repository I/O off main and add StrictMode in debug"
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
updated: 2026-11-22
---

## Next
Audit item 10 and rest of item 1: two prefs files are read on main before the first frame; WatchStateRepository starts a full read and clearAllPending write on construction, competing with startup I/O; no StrictMode in debug to catch it. Also item 9: largeHeap inflates the Coil memory cache and player buffer tier (see perf-deferred P5).

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
- 2026-11-22 WatchStateRepository item done in cache-decoded-memory-tier (owner Q2): clearAllPending now runs after StartupSettled.await(); the eager watch_state snapshot read stays (Home filters watched items before its first publish). Prefs, StrictMode and largeHeap items remain here
