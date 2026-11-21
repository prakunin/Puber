---
slug: http-stack-consolidation
title: "Cache DoH answers and share one HTTP stack"
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
Audit item 6: DoH answers are never cached; Coil, IntroDb and TheIntroDb each build a separate HTTP stack (connection pools, TLS sessions).

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
- 2026-11-21 folded into cache-shared-http-stack, which covers it entirely; stage dropped, the work continues there (owner standing instruction: recommended)
