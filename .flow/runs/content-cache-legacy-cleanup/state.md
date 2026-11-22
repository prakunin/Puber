---
slug: content-cache-legacy-cleanup
title: "Stop the per-start legacy-row LIKE deletes and bound cached_payload"
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
Audit item 4: ensureLegacyRowsRemoved runs 6 unindexable LIKE full-table deletes on every process start under the cache mutex all Home sections wait on; cached_payload grows without bound.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
- 2026-11-21 folded into cache-storage-bounds (its research found this run a strict subset: legacy cleanup once + bounded cached_payload); stage dropped, the work continues there (owner standing instruction: recommended)
