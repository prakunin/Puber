---
slug: detail-poster-directional-change
title: Detail panel poster changes with a short directional shift and crossfade following the carousel's scroll direction
stage: idea
size:
tracks: [ui]
kind: feature
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
source: detail-poster-trailer-flicker
created: 2026-11-19
migrated:
updated: 2026-11-19
---

## Next
triage with /task:run detail-poster-directional-change (after trailer-video-hidden-regression ships)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Variant: short shift (40-60 dp) plus crossfade, about 250 ms; the new poster enters from the side the carousel moves toward, the old one leaves the opposite way and fades. Not a full-width slide, not parallax | owner |

## Log
- 2026-11-19 created (stage idea). Owner after 1.12.1: "с постером, я бы предложил красивую анимацию смены с той стороны куда крутим карусель". Must keep the 1.12.1 gains (no skeleton flash, held poster during Loading) and must not reintroduce the trailer video regression (trailer-video-hidden-regression): nothing may draw over a rendered SurfaceView trailer.
