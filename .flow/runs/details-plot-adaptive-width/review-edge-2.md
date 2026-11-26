# Review edge, round 2

LENS: edge  ROUND: 2  BASE: b27d8e40

### F1 [major] The new cramped-hero assertion can be red, depending on the device's layout slack
- Where: DetailsScreenContentTest.kt:325-350 (aPlotInACrampedHeroLaysOutWithoutCrashing, CRAMPED_PLOT_TARGET_DP = 113f)
- Problem: share is the budget rounded down to whole 18 dp lines; the real budget is share..share+18; shrinking by 2*(share-113) leaves 113..131 dp; at >= 126 it rounds to 126 (7 lines), so the new block <= PLOT_MIN_HEIGHT + 0.5 assertion fails.
- Failure scenario: e.g. budget 140 -> share 126 -> after resize 127 -> block 126 -> "блок описания выше минимума: 126"; about 5 of 18 layouts fail.
- Fix: CRAMPED_PLOT_TARGET_DP = 108f (budget then 108..126, always rounds to 108); update the comment.

### F2 [minor] The new tall-plot test depends on the hero leaving at least 7 lines for the plot
- Where: DetailsScreenContentTest.kt:276-309 (aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight)
- Problem: a candidate qualifies only if taller than 6 lines and not scrolling, i.e. budget >= 126 dp; otherwise "no repeat count gives a narrowed plot above the minimum" - a fixture failure, not a regression.
- Fix: check the precondition on the box, or measure the full-screen budget first and assumeTrue(share >= 126) / fail naming the budget.

Holds: plotWidthSteps matches the stand's plotSteps for every case; stand plotScrollable matches the app's natural > budget; focus reconcile re-renders only when focus moved; the fact-line shift equals the height difference.

### Unproven
- :293 bounds.width < PLOT_WIDTH - 2*border relies on soft-wrapped Text taking the full width.
