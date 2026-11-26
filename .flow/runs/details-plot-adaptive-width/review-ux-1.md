# Review ux, round 1

Reviewed from code and the stand only (no device). Compose follows the design delta; the stand does not offer the 460 dp floor at the README values. (Reviewer ran build.py content in the worktree; git status shows no diff.)

### F1 [major] At 70 / 460 the stand never offers the 460 dp floor, but the code and README do
- Where: docs/design/content/index.src.html:862-864 (plotSteps)
- Problem: the stand adds a step only while last - plotStep >= plotFloor; with 70/460 it yields [590, 520] (520-70=450 < 460). PLOT_WIDTH_STEPS (DetailsScreenContent.kt:880) and README.md:82 say 590/520/460.
- Failure scenario: on the stand a short plot stops at 520 dp; on the TV it narrows to 460 (qa ~465). Stand and device disagree on the values the owner is asked to set (AC-5).
- Fix: after the loop add plotFloor when below the last step (if (steps.at(-1) > g.plotFloor) steps.push(g.plotFloor);) and rebuild index.html.

### F2 [minor] The step is a literal 70 dp inside the list, not a named constant
- Where: DetailsScreenContent.kt:880
- Fix: PLOT_WIDTH_STEP = 70.dp and build PLOT_WIDTH_STEPS from PLOT_WIDTH, PLOT_WIDTH_STEP and PLOT_MIN_WIDTH with the stand's rule (after F1).

### F3 [minor] The stand opens on the fixed width and nobody confirmed 70 / 460 on it
- Where: index.src.html:783 (plotFit:"fixed"); README.md:54-55
- Fix: default plotFit "steps" once the owner confirms; until then README says 70/460 are not yet confirmed on the stand.

### F4 [nit] Stand decides "scrolls" differently from the code when the plot exactly fills the space
- Where: index.src.html:966; PlotWidth.kt:27
- Fix: plotScrollable = plotLines * g.plotLine + 2 > snappedShare.

### F5 [nit] Labels and the short sample differ from the delta
- Where: index.src.html:747,755 ("одна"/"короткий" vs "фиксированная"/"короткое"; sample 215 chars vs 120-200)
- Fix: align labels; trim to repeat(6).

Not checked: device pixels, short-plot titles on the TV, AC-2 against master on the TV, the stand in a browser (AC-5), readability (AC-6).
Checked in code: 590 widest step and skeleton; box keeps its 590 dp height; scrolling plot stays 590; focus ring unchanged and only on scrolling plots; scrim, still width and HERO_LINE_WIDTH untouched.

VISUAL: not measured (code and stand only); the stand does not match the code and README (F1).
