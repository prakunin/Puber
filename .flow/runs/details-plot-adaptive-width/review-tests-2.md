# Review tests, round 2

LENS: tests  ROUND: 2  BASE: b27d8e40

### F1 [major] The new fact-line test has never run, and the box evidence suggests it cannot find its case
- Where: DetailsScreenContentTest.kt:277-309 (aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight)
- Problem: never run; needs a text >= 7 lines at 590 that fits the snapped budget and narrows; round-1 box evidence shows a 7-line plot scrolls at 590, so the real budget is <= 126 dp and no candidate may qualify; the test hero is only slightly shorter.
- Failure scenario: on the box at 960x540 dp with a 108/126 dp budget no candidate exists and the suite is red, or it passes only because the test hero is shorter than any real one.
- Fix: run on the box; host DetailsScreenContent in a taller fixed-height Box (as the cramped test does) so the budget is >= 8 lines; note in the plan this case does not occur in production on a 540 dp hero.

### F2 [minor] The new fact-line test checks only that the layout agrees with itself
- Where: :298-307
- Fix (optional): assert tallHeight equals the text height measured at 588 dp.

Suite state: PlotWidthTest 10/10 (round 2 run). Instrumented tests not run (no device).
Clean: literal heights match; plotWidthSteps tests red on a stub (2) + guard; PLOT_WIDTH_STEPS = plotWidthSteps(590,70,460); cramped-hero assertion proves the below-minimum path; no assertions removed; stand plotSteps follows the Kotlin rule; default plotFit fixed. AC-5 still open (#10, qa round 2).

### Unproven
- :292 whether 16..30 repeats narrows depends on the device font.
