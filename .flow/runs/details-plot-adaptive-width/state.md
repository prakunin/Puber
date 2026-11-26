---
slug: details-plot-adaptive-width
title: "Narrow the details plot when there is room below, so it overlaps the poster less"
stage: done
size: M
tracks: [ui]
kind: improvement
branch: prakunin/details-plot-adaptive-width
worktree:
base: b27d8e40
spec: docs/superpowers/specs/2026-11-26-details-plot-adaptive-width.md
plan: docs/superpowers/plans/2026-11-26-details-plot-adaptive-width.md
rounds: {verify: 3, review: 3, judge: 0}
blocked_by: []
source:
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run details-plot-adaptive-width

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q4 | Only short plots narrow under criterion A; also medium ones (plot grows into free space, facts and buttons pinned)? | yes | answered | Only short (A) |
| Q5 | (architect) AC-4 fixture: use a short plot that wraps to several lines but stays within 6 lines at the floor? | no | answered | Yes |
| Q6 | (architect) Regenerate baseline profiles in this run? | no | answered | No |
| Q1 | Steps and floor? | no | answered | Start 590/520/460 dp, floor 460; owner sets final values on the stand |
| Q2 | Fit criterion: today's plot height at 590 dp (A) or the full half-share budget (B)? | yes | answered | A: no taller than today at 590 dp, so nothing else moves |
| Q3 | Skeleton bars stay 590; width recomputed in layout with no state; stand switch, step/floor sliders, short/long sample plots? | no | answered | Yes to all |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q3 as recommended; codex and antigravity every round; device checks on the TV box at user pace | owner (standing instruction: recommended) |
| 2026-11-26 | Q4: only short plots narrow (A) | owner |
| 2026-11-26 | Q5, Q6 as recommended | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved (M, ui, 6 ACs) | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship; owner manual checks: AC-5 stand values, AC-6 look on the TV, AC-2 vs master on the TV | owner (standing instruction: recommended) |

## Log
- 2026-11-26 created (stage idea) from the owner: on the film/series details screen the description nicely overlaps the poster, but the overlap should shrink when there is enough room below - make the description narrower then.
- Current code: PlotBlock has a fixed width PLOT_WIDTH = 590.dp (app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt:861), 18 sp line (PLOT_LINE), min height 110 dp, gets half of the hero column's free height and scrolls from the remote when it does not fit; MediaScrim.Details fades out by about 10 % of the frame, so long lines sit over the artwork; fact lines placed by HERO_FACTS_ABOVE.
- Conductor's proposal (owner agreed to create the run): adaptive width - the narrowest of a few steps (e.g. 590 / 520 / 460 dp) at which the whole plot fits the height it already gets without scrolling; long plots keep 590 dp and scroll as today. Guards: a floor width (readability at 18 sp on TV, about 420-460 dp; pick on the stand), width computed once per title (no jump while facts or seasons load), fact lines and the action row must not move.
- Process: screen has a design stand docs/design/content - dial min width and steps there with short/medium/long plots via the designing-a-screen skill before porting to Compose. Device check on the TV box (user-pace API use only).
- 2026-11-26 /task:run started (base b27d8e40); research
- 2026-11-26 research.md (plot text is 13 sp on an 18 sp line; safe rule: narrowest step no taller than at 590 dp; possible heightIn/snap crash at 110-125 dp budgets; stand has measureLines; 5 questions). Analyst.
- 2026-11-26 brief.md: M, track ui, 6 ACs; Q1-Q3 as recommended. Stage design.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/details-plot-adaptive-width; architect + ux-designer design
- 2026-11-26 design-architect.md (9 decisions, 7 risks; Qs: AC-4 fixture, no profile regen) and design-ux.md written. Finding: under criterion A only plots of up to 6 lines at 590 dp narrow; 7+ lines barely change. Asking the owner whether that matches the intent.
- 2026-11-26 owner answered Q4: only short plots (A). Q5, Q6 as recommended. test-designer.
- 2026-11-26 test-design.md (P0 10, P1 4, P2 1, manual 2); spec assembled and committed; gate 1 approved. Stage plan.
- 2026-11-26 plan (3 tasks + owner stand checkpoint; start values 70/460 stand if no answer). Readiness.
- 2026-11-26 readiness-1 NOT READY: Kotlin assert is a no-op on Android (new AC-4 width check; "tops equal" check; existing :172 guard at :192). test-designer + architect round 2. DSCTest:152 deferred -> details-test-noop-asserts.
- 2026-11-26 readiness-2 READY, pasted into the plan. Stage impl.
- 2026-11-26 impl done: c3ae5ddf, 3595c650, d9aa125f (start values 70/460); check.full green; named tests: checked 1 name(s): 0 missing, 0 skipped; co-authored 0. Stage verify (TV box).
- 2026-11-26 qa round 1: check.full green; itest 12/12 on the box; AC-1, AC-3, AC-4 PASS (AC-4 red on base "588.0.dp"); AC-2 PASS (tests) / TV vs master NOT CHECKED; AC-5 NOT CHECKED (no browser for the stand); AC-6 NOT CHECKED (owner). Base also crashes in the cramped-hero test (fixed by the change). Account worked on the box (Home and details loaded). Evidence b5e1c123. Review round 1 at b5e1c123.
- 2026-11-26 review round 1 at b5e1c123: ux 1 major + stand minors, tests 1 major + 1 minor, codex 3 minor (stand), edge 1 minor, blind 2 minor + 1 nit, state-path and antigravity clean. triage-1.md: fix #1-#10, accept #11, reject #12. Implementer fix round.
- 2026-11-26 fix round 1: 22ab3b58, 545c6805, 682ce8fb, 15d0117c (#1-#9); check.full green; new plotWidthSteps tests red on stub. Review round 2 on b5e1c123..15d0117c; qa round 2 waits for the box.
- 2026-11-26 review round 2: edge 1 major + 1 minor, tests 1 major + 1 minor, codex 2 minor, antigravity clean. triage-2.md: fix #1-#2 (test robustness), accept #3-#4. Implementer fix round 2.
- 2026-11-26 fix round 2: 7aa415b9 (cramped target 108; tall host requiredHeight(1000.dp)); compile green. qa round 2 on the box.
- 2026-11-26 qa round 2: check.full green; itest 12/13 - aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight red ("no repeat count gives a narrowed plot above the minimum in a 1000 dp host"); conductor's reading: test defect - repeated full-width sentences always gain lines when narrowed, so criterion A never narrows them (by design, ux note); evidence 19bce322. AC-5/AC-6 owner checks. Implementer fixes the test fixture (7-8 hard-wrapped short lines).
- 2026-11-26 test fixture fix e739bfd5 (eight short hard-wrapped lines). qa round 3 (itest only).
- 2026-11-26 qa round 3: itest 13/13 on the box (evidence 76442083). Review round 3 (test-only diff) on 15d0117c..76442083.
- 2026-11-26 review round 3 (test-only): edge, tests, antigravity clean; codex clean. triage-3.md. Stage judge.
- 2026-11-26 judge-1 PASS (manual 3: AC-5 stand, AC-6 TV look, AC-2 vs master on TV). Gate 2: ship (standing instruction). Squash.
- 2026-11-26 squashed to 4 commits (f4ae026c, ec10ae06, 38b19c5e, 4d7db667); tree identical to 76442083; co-authored 0. Next: /task:ship details-plot-adaptive-width (owner).
- 2026-11-26 shipped: merged prakunin/details-plot-adaptive-width into master as 00283c63 (--no-ff); compile green on merged master; Orca worktree removed.
