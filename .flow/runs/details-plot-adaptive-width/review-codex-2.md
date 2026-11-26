# Review codex, round 2

CODEX: ran: review, adversarial (gpt-6.2-sol)

CONFIRMED:
- [minor] docs/design/content/index.src.html:967 - stand marks a plot scrollable when it exceeds the share only by the 2 dp border, but maxScroll stays 0 (plotVisibleLines = min(plotLines, capLines) == plotLines); a 6-line plot in a 108 dp budget shows "прокручивается с пульта" yet Down leaves at once. App uses scrollState.canScrollForward (scrolls 2 dp). Fix: derive maxScroll from the border-adjusted height, or plotScrollable = plotLines > capLines; rebuild.
- [minor] DetailsScreenContentTest.kt:277 - aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight derives the expected shift from the narrowed text's own height; would pass if narrowing made the box taller than at 590 dp; equal-height covered only by the choose unit tests. Fix: record the plot height at 590 dp and assert the narrowed height equals it.

REJECTED:
- Adversarial: compare the same synopsis at fixed 590 dp and at the selected width - no code path can force 590 dp in the same composition; choose unit tests assert equality.
- plotWidthSteps helper, its three tests, the PLOT_WIDTH_STEPS derivation and literal expected heights hold; 590/520/460 matches.

VERDICT: changes needed (minors only)
