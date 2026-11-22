---
slug: release-startup-compilation
title: "Refresh the baseline profile, narrow R8 rules and compile after in-app updates"
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
Owner decision 2026-11-21 (separate run from startup-splash-preload). Audit items 7 and 8: baseline profile stale since 1.0.0 (no details/player rules, new startup classes missing); broad R8 -keep rules, unused coil-video and AppCompat; after an in-app update the app runs JIT until background dexopt. Related: perf-deferred P5, startup-benchmark-entry.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
