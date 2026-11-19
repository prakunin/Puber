---
slug: detail-poster-trailer-flicker
title: Remove the flash when the detail panel's poster gives way to the trailer or to the next poster
stage: done
size: M
tracks: [ui]
kind: bug
branch: prakunin/detail-poster-trailer-flicker
worktree: /home/user1/orca/workspaces/Puber/detail-poster-trailer-flicker
base: 76a8bc2a
spec: /home/user1/orca/workspaces/Puber/detail-poster-trailer-flicker/docs/superpowers/specs/2026-11-19-detail-poster-trailer-flicker.md
plan: /home/user1/orca/workspaces/Puber/detail-poster-trailer-flicker/docs/superpowers/plans/2026-11-19-detail-poster-trailer-flicker.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
source:
created: 2026-11-19
migrated:
updated: 2026-11-19
---

## Next
Shipped. Release 1.12.1 follows; owner watches the trailer manual checks in judge-1.md.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Text-column skeleton too, or poster only? | yes | answered | Poster only; separate idea run if the text flash still bothers the owner |
| Q2 | Keep-previous-image behaviour global or opt-in? | no | answered | Opt-in, used only by PosterStill |
| Q3 | Move the trailer to a TextureView? | yes | answered | No; keep the SurfaceView and fade the still over the video |
| Q4 | Add a Compose UI test harness? | no | answered | No (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Q1: poster only (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Q2: opt-in on SkeletonAsyncImage, used only by PosterStill (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Q3: keep SurfaceView, option A (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Q4 (architect): no Compose UI test harness (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Gate 1: approved as designed (M, ui, AC-1..AC-10) | owner (standing instruction: recommended) |
| 2026-11-19 | Gate 2: ship (judge CONCERNS, 6 manual checks for the owner in judge-1.md) | owner (standing instruction: recommended) |
| 2026-11-19 | Release after ship as 1.12.1 (patch bump) | owner |

## Log
- 2026-11-19 created (stage triage)
- 2026-11-19 research.md written; brief.md written (M, ui, bug, 10 ACs)
- 2026-11-19 Q1-Q3 answered on recommendations; worktree created; stage design
- 2026-11-19 design-architect.md, design-ux-designer.md, test-design.md written; spec assembled; gate 1 approved on recommendations; stage plan
- 2026-11-19 plan written; readiness-1 READY; stage impl
- 2026-11-19 impl done (5204db80..27a29f51), check.full green, named tests present; stage verify
- 2026-11-19 qa-report round 1: AC-4/9/10 PASS, AC-8 partial, trailer ACs NOT CHECKED (headless emulator); stage review
- 2026-11-19 review round 1 done; triage-1: 6 fix, 1 deferred (detail-poster-fallback-empty-frame)
- 2026-11-19 fix round 1 (9ede97aa); review round 2 clean (2 minors accepted); qa round 2 AC-4/8/10 PASS; stage judge
- 2026-11-19 judge-1 CONCERNS (no must-fix, 6 manual checks); gate 2 ship; stage handover
- 2026-11-19 squashed to 2 commits (db7a524c, c992db1f), tree fde5d0dc unchanged
- 2026-11-19 shipped: merged into master as a7611630; worktree removed, branch deleted; stage done
