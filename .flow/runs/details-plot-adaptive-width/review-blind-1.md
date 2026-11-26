# Review blind, round 1

LENS: blind  ROUND: 1  BASE: b27d8e40

### F1 [minor] The fact-line position test passes even when the height rule is broken
- Where: DetailsScreenContentTest.kt:245 (aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem)
- Problem: both HERO_DESCRIPTION and SHORT_DESCRIPTION sit on the 110 dp minimum at every width, so the facts/actions stay put whether height comes from the widest or the chosen width; the wiring Constraints.fixed(fit.widthPx, height) is uncovered for plots taller than 110 dp.
- Fix: use a plot taller than 110 dp that fits at 590 and narrows (7-8 lines).

### F2 [minor] The cramped-hero test only checks that the button is displayed, and its setup depends on clipped bounds
- Where: DetailsScreenContentTest.kt:274 (aPlotInACrampedHeroLaysOutWithoutCrashing)
- Problem: host height from boundsInRoot of the scrolled text + hardcoded 2f; nothing checks the plot really got under 110 dp; on 1.5 / 2.625 densities snapping can miss the boundary and the test passes without reaching it.
- Fix: assert the plot block height is at most PLOT_MIN_HEIGHT after the resize, or rely on the unit test choosePlotWidth_keepsHeightWithinBudget_whenBudgetIsBelowMinHeight.

### F3 [nit] The unit test recomputes the expected height with the production formula
- Where: PlotWidthTest.kt:68
- Fix: literal expected heights per case (110, 110, 146, 200, 108, 108).

### Unproven
- DetailsScreenContent.kt:335 maxIntrinsicHeight at up to three widths plus a fourth measure per remeasure.
- DetailsScreenContent.kt:330 floorPx not clamped; below ~460 dp available width the plot never narrows (only caller has ~949 dp).
