---
slug: startup-first-input-on-tv
title: "Measure and fix the first D-pad response after the splash reveal on a real TV"
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
startup-splash-preload left AC-13 (revealed median 2789 vs 2292 ms) and AC-15 (first key after reveal 330-930 ms on swiftshader) unmet and waived. Needs a real-TV measurement (owner authorization for the TV) of launch-to-visible-content, dropped inputs and first focus response at fixed and reveal-relative anchors, release build vs master; then controlled experiments: no/short fade, bitmapFactoryMaxParallelism(2), bounded image wait. See its qa-report.md Rounds 1c, 2, 2b and codex-consult-ac13-ac15.md. Related: card-image-size-tier, release-startup-compilation, startup-settled-signal. Also from qa Round 2b: 1 of 9 cold starts under load revealed with skeletons before the cap (image requests may start after the two-frame wait), and one content-link reveal at 4693 ms.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-21 created (stage idea) from the AC-13/AC-15 waiver of startup-splash-preload
