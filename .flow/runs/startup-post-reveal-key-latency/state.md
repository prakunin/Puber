---
slug: startup-post-reveal-key-latency
title: "A key in the first half second after a grace reveal waits for the held images to land (0.3-0.65 s on the box)"
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
source: startup-reveal-gate-on-slow-tv
created: 2026-11-23
migrated:
updated: 2026-11-23
---

## Next
triage with /task:run startup-post-reveal-key-latency. Start with an atrace of 3 grace starts with the key at anchor B (100-230 ms after the fade starts); then the cheapest fix (crossfade off for startup-held requests, a cheaper alpha mode on the splash layer, or smaller decode sizes). Bound: anchor B at most 1 of 15 first keys over 199 ms on the box in S1. Evidence: startup-reveal-gate-on-slow-tv design-ac7-diagnosis.md and its round-1b device evidence.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-23 created (stage idea), deferred from startup-reveal-gate-on-slow-tv (AC-7 restated; owner chose a follow-up run)
