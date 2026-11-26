# Gate - details-plot-adaptive-width - round 1

**Decision:** PASS
**Head:** 76442083   **Base:** b27d8e40

## Done as asked

`git diff b27d8e40...HEAD` matches the spec's Design (D1-D9): `PlotBlock` drops `width(PLOT_WIDTH)` and `heightIn(min)` and chooses the width inside its `layout {}` through the pure `choosePlotWidth` (`PlotWidth.kt`), measuring with `maxIntrinsicHeight` and placing the child at `Constraints.fixed(width, heightAtWidest)`. Steps come from `plotWidthSteps(590, 70, 460)`, the same rule as the stand's `plotSteps`. The skeleton stays at `PLOT_WIDTH`. `MediaScrim`, `MEDIA_WIDTH_FRACTION` and `HERO_LINE_WIDTH` are untouched, no profile was regenerated and the plot does not grow taller (Out of scope respected). The stand gains the `plotFit` and `plotText` switches, the `plotStep` and `plotFloor` sliders and the readout. The README rows are marked as start values not yet confirmed by the owner. A rebuild of `index.html` from `index.src.html` is byte-identical.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | `PlotWidthTest`: `choosePlotWidth_returnsNarrowestStepThatKeepsTheHeight_whenPlotIsShort`, `_acceptsStepAtExactlyTheHeight_andRejectsOneLineMore`, `_neverGoesBelowFloor_whenTextFitsEvenNarrower` | check.full re-run by the judge at 76442083: exit 0 | PASS |
| AC-2 (unit, instrumented) | `choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase` (literal heights), `_keepsHeightWithinBudget_whenBudgetIsBelowMinHeight`; `aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem`, `aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight`, `aPlotInACrampedHeroLaysOutWithoutCrashing` | check.full; evidence Round 3: DetailsScreenContentTest 13/13 on the box; cramped-hero test red on base (min > max crash), evidence Round 1 | PASS |
| AC-2 (TV against master) | M2 step 4 | not done, evidence Round 1 | NOT CHECKED (manual, below) |
| AC-3 | `aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions` (`DSCTest:172`, now `assertTrue`), `aPlotTooLongForItsBlockKeepsTheWidestWidth`, `choosePlotWidth_returnsWidestStep_whenPlotOverflowsAtWidest` | 13/13 on the box; M2 long plot: 590 dp, UP shows the ring (evidence Round 1) | PASS |
| AC-4 | `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` | red on base ("588.0.dp"), green in 13/13 on the box; M2 six-line plot at about 465 dp, UP stays on the actions | PASS |
| AC-5 | M1 (stand in a browser, owner sets step and floor) | stand code and rebuild checked by the judge; no browser in the dev box; owner has not set the values | NOT CHECKED (manual, below) |
| AC-6 | M2 (owner judges readability) | short-plot title never opened; owner judgement | NOT CHECKED (manual, below) |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| t1#1 stand drops the 460 floor (major) | review-ux-1 F1, review-codex-1 | fixed | 22ab3b58; `plotSteps` pushes the floor |
| t1#2 literal 70 dp in the step list | review-ux-1 F2, review-edge-1 F1 | fixed | 545c6805; `PLOT_WIDTH_STEP`, `plotWidthSteps` + 3 tests |
| t1#3-#5 stand scroll border, focus reconcile order, labels | review-ux-1 F4-F5, review-codex-1 | fixed | 22ab3b58 |
| t1#6 README presents 70/460 as agreed | review-ux-1 F3 | fixed | 22ab3b58; README says start values |
| t1#7-#8 fact-line and cramped-hero tests could not fail | review-blind-1 F1-F2 | fixed | 682ce8fb, then 7aa415b9, e739bfd5 |
| t1#9 unit test recomputes heights with the production formula | review-blind-1 F3 | fixed | 545c6805; literal heights |
| t1#10 AC-5 stand never opened (major) | review-tests-1 F1 | moved to owner manual check | no browser here; manual by nature (browser plus owner's sliders), section below |
| t1#11 AC-2 TV and AC-6 short case not checked | review-tests-1 F2 | accepted minor | owner manual checks below |
| t1#12 intrinsics per measure, floor unclamped, minHeight above budget | review-blind-1, review-edge-1 unproven | rejected, reason accepted | spot-checked: only caller `DSC:214-217` uses `weight(1F, fill = false)` (min height 0) on a 949 dp column; R6 accepts the cost |
| t2#1 cramped-hero target slack (major) | review-edge-2 F1 | fixed | 7aa415b9; target 108 dp |
| t2#2 tall-plot test could find no case (major) | review-tests-2 F1, review-edge-2 F2 | fixed | 7aa415b9, e739bfd5; red in qa round 2, green in round 3 |
| t2#3 stand marks a 2 dp-over plot scrollable with maxScroll 0 | review-codex-2 | accepted minor | stand-only preview at one boundary |
| t2#4 fact-line test not tied to the 590 dp height | review-codex-2, review-tests-2 F2 | accepted minor | covered by `choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase` |
| t3#1 plan record lacks round 3; evidence says "No app install" | review-tests-3, review-codex-3 | accepted nit | documentation only |
| t3#2 fixture depends on device font and density | review-edge-3, review-tests-3 unproven | rejected, reason accepted | spot-checked: assertions name the miss; one target device, green there |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Up to three `maxIntrinsicHeight` queries per `PlotBlock` measure, one for an overflowing plot (P2 test). Measures run on content or constraint change only. No new state or recomposition scope. |
| Reliability | Removes the base min > max `Constraints` crash at cramped budgets (red on base). Unbounded height passes through `coerceIn`. No state, so nothing to restore after process death. Width can step once on the cache-to-network refresh (R5, accepted in brief Q3). |
| Maintainability | Conventions kept (pure content, JUnit 5 unit test, JUnit asserts in instrumented tests). One step rule in Kotlin and one in the stand, cross-referenced in KDoc. `heightIn` import removed, no dead code. Commits carry no Co-Authored-By. |
| Security / privacy | No secrets or account data in code or evidence. `tools/mobile-evidence-audit.sh` on the evidence file: passed. No screenshots committed. |
| TV | No new focusable element. A narrowed plot fits by construction and takes no focus (AC-4 on the box). The long-plot focus path is unchanged (AC-3). |

## Manual checks for the owner

1. **AC-5, stand.** `cd /home/dev/orca/workspaces/Puber/details-plot-adaptive-width && python3 docs/design/build.py content`, then open `docs/design/content/index.html` in a browser. Toggle «ширина описания» (фиксированная / по шагам) and confirm the plot box height does not change. Switch «текст описания» to короткое (it narrows to the narrowest fitting step, not below the floor) and to длинное (it stays at «ширина блока» and scrolls). Move «шаг ширины» and «минимум ширины» to the values you want. Then press «Зафиксируй». If the values differ from 70 / 460, `PLOT_WIDTH_STEP` and `PLOT_MIN_WIDTH` in `DetailsScreenContent.kt` and the README «Agreed sizes» rows must change to match. If they stay, the README "start values" paragraph and the `plotFit:"fixed"` default become "confirmed" and `steps`.
2. **AC-2 on the TV.** On the box `192.168.1.110:5555` (`com.kino.puber.stage`), open the same short-plot title (1-3 lines) once on a master build and once on this worktree's build. Screenshot each and confirm the fact lines and the action row sit at the same y.
3. **AC-6.** On the worktree build, open by D-pad one short, one medium (4-6 lines) and one long title. Judge whether the narrowed plots read well and sit clear of the brighter part of the still. Press UP from the actions on the short and medium titles and confirm no focus ring appears.

## Must fix

none

## Concerns for the owner

none
