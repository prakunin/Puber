---
slug: detail-poster-directional-change
title: Detail panel poster and text (title, description, metadata) change smoothly with a short directional shift and crossfade following the carousel's scroll direction
stage: done
size: M
tracks: [ui]
kind: feature
branch: prakunin/detail-poster-directional-change
worktree: /home/user1/orca/workspaces/Puber/detail-poster-directional-change
base: 01d1d305
spec: docs/superpowers/specs/2026-11-20-detail-poster-directional-change.md
plan: docs/superpowers/plans/2026-11-20-detail-poster-directional-change.md
rounds: {verify: 4, review: 4, judge: 1}
blocked_by: []
source: detail-poster-trailer-flicker
created: 2026-11-19
migrated:
updated: 2026-11-20
---

## Next
none (done). Owner checks AC-10 on the TV in the next release build; then detail-poster-fallback-empty-frame.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Variant: short shift (40-60 dp) plus crossfade, about 250 ms; the new poster enters from the side the carousel moves toward, the old one leaves the opposite way and fades. Not a full-width slide, not parallax | owner |
| 2026-11-20 | Scope widened: the panel text (title, description, metadata and the rest of the card text) must also change smoothly, matching the poster transition; today text appears abruptly | owner |
| 2026-11-20 | Q1 row change (Up/Down): crossfade only, no vertical shift | owner (standing instruction: recommended) |
| 2026-11-20 | Q2 keep the old text up to 3 s while new details load instead of the skeleton | owner (standing instruction: recommended) |
| 2026-11-20 | Q3 do not fold detail-poster-fallback-empty-frame; sequence it after this run | owner (standing instruction: recommended) |
| 2026-11-20 | Approach: research option A (UI-only direction tracker, text held through Loading, transient outgoing image layer) | owner (standing instruction: recommended) |
| 2026-11-20 | D1/D2 as recommended; AC-10 judged by the owner on the TV | owner (standing instruction: recommended) |
| 2026-11-20 | Gate 1: approved (size M, tracks ui, 11 ACs) | owner (standing instruction: recommended) |
| 2026-11-20 | Edge strip: ux option (b), outgoing layer opaque under the still until the motion ends; replaces the D2 remedy (shown not to work); overscan only if the owner still sees the seam on the TV | owner (standing instruction: recommended) |
| 2026-11-20 | Review cap reached with one major (regression from fix round 1): one more round | owner (standing instruction: recommended) |
| 2026-11-20 | Round caps lifted for now: "лимиты раундов пока не ограничиваем"; verify/review loops continue until clean, no cap question | owner |
| 2026-11-20 | Gate 2: ship (judge CONCERNS: AC-10 open for the owner on the TV, edge seam on Left presses) | owner (standing instruction: recommended) |

## Log
- 2026-11-19 created (stage idea). Owner after 1.12.1: "с постером, я бы предложил красивую анимацию смены с той стороны куда крутим карусель". Must keep the 1.12.1 gains (no skeleton flash, held poster during Loading) and must not reintroduce the trailer video regression (trailer-video-hidden-regression): nothing may draw over a rendered SurfaceView trailer.
- 2026-11-20 owner after 1.12.2 on the TV: trailer "уже лучше"; asked to also change the text smoothly: "надо также плавно научиться менять текстовое описание включая название, описание и т.д. нет плавности в появлении текста". Folded into this run.
- 2026-11-20 research.md written (option A recommended, 4 questions). Stage triage, base 01d1d305.
- 2026-11-20 brief.md written: feature, M, ui, 11 ACs. Recommended answers taken. Stage design.
- 2026-11-20 design-architect, design-ux-designer, test-design written; spec assembled; gate 1 approved on the standing instruction. Stage plan.
- 2026-11-20 plan written (5 tasks), readiness-1 READY. Stage impl.
- 2026-11-20 impl done: 5 commits bb800826..e78cd5ad, check.full green (implementer), check.fast green (conductor), named tests 0 missing. Stage verify.
- 2026-11-20 qa round 1: all checkable ACs PASS, AC-10 NOT CHECKED (owner on TV), AC-5 refresh path NOT CHECKED; D2 right-edge strip seen on a Left press -> per D2 shorten poster fade-in to 100 ms in review fixes. Stage review round 1.
- 2026-11-20 review round 1: blind, edge, tests, codex, ux saved; triage-1: 6 fix, 1 accept. Fix round next.
- 2026-11-20 fix round 1: ba4ee226, 4d9725b9; check.full green (implementer), check.fast green (conductor). Round 2: edge, tests, codex on 6eb4d15c..4d9725b9 and qa on affected ACs + Favourites M3.
- 2026-11-20 round 2: qa all checkable PASS (Favourites side-by-side verified); edge major (interrupted handover) + codex dup; triage-2: 1 fix, 3 accept. Round 3.
- 2026-11-20 fix round 2: b27800f6 (seeded outgoing layer rests to alpha 1/offset 0 while waiting); details tests 53/53 green (conductor --rerun). Round 3: edge, tests, codex, qa.
- 2026-11-20 round 3: codex clean, qa no defects (9642fcbb); edge+tests: warm-cache path of triage-2 #1 unfinished; triage-3: 3 fix. Round 4.
- 2026-11-20 fix round 3: 484e5adf; check.full green (2134 tests, implementer), check.fast green (conductor). Round 4: edge, tests, codex, qa.
- 2026-11-20 round 4: edge, tests, codex clean; qa no defects (a6c6d77f). triage-4: no findings. Stage judge.
- 2026-11-20 judge-1 CONCERNS (AC-10 for the owner). Gate 2 ship on the standing instruction. Squash before ship.
- 2026-11-20 squashed 15 commits into 3 (ba4ae39b, f1f083a5, 6448b1be); tree identical; pre-squash backup branch backup/detail-poster-directional-change-presquash.
- 2026-11-20 shipped: merge eb280de9 into master; worktree removed (orca). Stage done.
