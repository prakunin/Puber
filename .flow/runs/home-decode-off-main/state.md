---
slug: home-decode-off-main
title: "Decode Home and Details section payloads off the main thread"
stage: dropped
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
From the 2026-11-21 static perf audit (startup-splash-preload/perf-audit-2026-11-21.md, items 2 and 3): HomeVM/DetailsVM section collectors decode JSON on viewModelScope (Main) because ContentCacheRepository/CachedFeed flows have no flowOn; the same payload is decoded 3-6 times per load and CachedFeed stores JSON-in-JSON strings. Likely a direct cause of D-pad lag while Home loads.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
- 2026-11-21 folded into cache-decoded-memory-tier, which covers it entirely; stage dropped, the work continues there (owner standing instruction: recommended)
