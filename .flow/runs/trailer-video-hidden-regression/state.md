---
slug: trailer-video-hidden-regression
title: Trailer preview plays audio but shows no video on the owner's TV after 1.12.1
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/trailer-video-hidden-regression
worktree: /home/user1/orca/workspaces/Puber/trailer-video-hidden-regression
base: 3ec85287
spec: /home/user1/orca/workspaces/Puber/trailer-video-hidden-regression/docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md
plan: /home/user1/orca/workspaces/Puber/trailer-video-hidden-regression/docs/superpowers/plans/2026-11-19-trailer-video-hidden-regression.md
rounds: {verify: 1, review: 1, judge: 1}
blocked_by: []
source: detail-poster-trailer-flicker
created: 2026-11-19
migrated:
updated: 2026-11-19
---

## Next
Owner checks T1-T3 on the TV with a main-checkout dev APK; if T1 fails, fallback B in a follow-up before 1.12.2; otherwise release 1.12.2.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Treat as a 1.12.1 regression: the same TV showed the trailer video on 1.12.0 | owner |
| 2026-11-19 | Verification on the physical TV is done by the owner from an APK the conductor builds; agents never touch the TV | owner |
| 2026-11-19 | Owner on the TV: while the trailer's audio plays, the poster stays fully visible, so the still never fades. Research option A alone would not fix that | owner |
| 2026-11-19 | Preferred fix direction: A (nothing drawn over the trailer once it shows, still kept in memory), B (TextureView) as the fallback | owner |
| 2026-11-19 | Q1: fix T (posted invalidate of the trailer SurfaceView once it gets a non-zero size) replaces option A, which does not address the confirmed cause | owner |
| 2026-11-19 | Q2: if T works on the emulator but not on the owner's TV, switch to option B (TextureView) within this run before 1.12.2 | owner |
| 2026-11-19 | Size M, tracks none (playback risk area), per the analyst | owner (standing instruction: recommended) |
| 2026-11-19 | Gate 1: spec approved (size M, tracks none, 7 ACs, fix T) | owner (standing instruction: recommended) |
| 2026-11-19 | AC-4's favourites part is N/A: `FavoriteScreenContent.kt` passes no `trailerUrl`, so favourites never plays a trailer (before or after this fix). AC-4 covers the details hero only; the owner skips favourites in T2 | owner (standing instruction: recommended) |
| 2026-11-19 | Gate 2: ship (judge-1 CONCERNS, no must-fix; T1-T3 and M5 are owner TV checks that decide fallback B before 1.12.2) | owner (standing instruction: recommended) |
| 2026-11-19 | Ship as release 1.12.2 (patch bump) after the fix | owner |

## Log
- 2026-11-19 created (stage triage). Owner report after installing release 1.12.1: "видео превью не показывает, хотя звук был". It worked on 1.12.0 on the same TV. Source run: detail-poster-trailer-flicker (merged in a7611630, released as v1.12.1 at 3ec85287).
- 2026-11-19 conductor hypothesis (not proven): the single PosterStill now stays composed after the SurfaceView player at graphicsLayer alpha 0 instead of leaving composition as on 1.12.0, and an offscreen alpha layer over the SurfaceView hole hides the video on some TV hardware.
- 2026-11-19 separate later run requested by the owner: directional poster change animation (short 40-60 dp shift + crossfade toward the carousel scroll direction, ~250 ms). Not part of this run.
- 2026-11-19 research.md written (options A/A'/B). Worktree created. A diagnostic agent instruments the worktree (uncommitted) to see on the emulator whether onRenderedFirstFrame fires and what alpha the still reaches.
- 2026-11-19 diagnosis.md: root cause reproduced on emulator-5554. On card changes after the first, the SurfaceView is laid out after the window's only pre-draw and never gets a surface. No first frame, so the still stays opaque. 1.12.0's fresh over-copy happened to force the extra traversal. A posted invalidate() fixed it in an experiment. The conductor's alpha hypothesis is ruled out. The emulator still has a preserving-install diagnostic build (1.12.0 poster code plus logging) on com.kino.puber.stage.
- 2026-11-19 brief.md written (kind bug, size M, 7 ACs, fix T). The owner answered Q1 and Q2 with the recommended answers. Stage design.
- 2026-11-19 design-architect.md and test-design.md written. Spec assembled and committed in the worktree (9dd98f83). Gate 1 approved on the standing instruction. Stage plan.
- 2026-11-19 Plan written. Readiness: round 1 NOT READY (test-design marks, M2) fixed by test-designer. Round 2 NOT READY: the conductor had copied the test design twice into the spec; removed in 3e70c43f. Round 3 READY. Stage impl.
- 2026-11-19 impl done: 1bdff891, d977f7e5, fc5faf69. check.full green per the implementer, red run recorded, named tests 0 missing. Stage verify.
- 2026-11-19 qa round 1: AC-1, 2, 6 and 7 PASS. AC-4 PASS for the hero; favourites NOT CHECKED because it passes no trailerUrl. AC-5 PASS on the emulator (M1-M3); M5 and the TV NOT CHECKED. AC-3 NOT CHECKED (owner's TV). Evidence committed in 4c3248cc. Stage review.
- 2026-11-19 Review round 1: blind 1 minor, edge none, tests 2 minors, codex clean. triage-1.md: 1 rejected (wiring proven on device, no Robolectric), 2 fixed (favourites N/A decision; red-run record).
- 2026-11-19 judge-1: CONCERNS, no must-fix, 4 manual TV checks. check.full re-run green at 2578a702. Gate 2: ship on the standing instruction.
- 2026-11-19 Squashed to 2 commits (989e8826 code, e921fca7 docs), tree 33182048 unchanged, check.fast green. Waiting for the owner's /task:ship.
- 2026-11-19 Shipped: merged into master as 54d4a06a; worktree removed. Stage done.
