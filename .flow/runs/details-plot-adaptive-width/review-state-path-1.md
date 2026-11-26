# Review state-path, round 1

LENS: state-path  ROUND: 1  BASE: b27d8e40

No findings.
- No ViewModel/UIAction/handleAction changes; only the PlotBlock .layout block and the pure choosePlotWidth (PlotWidth.kt).
- Focus: plot focusable only when scrollable; a narrower width is chosen only when heightAt(narrow) <= height, so it fits and cannot scroll; UP stays on the actions. A plot overflowing at the widest width returns PlotFit(widest, budget) - old width and height, long-plot scroll/focus path unchanged.
- heightAt is maxIntrinsicHeight on the same chain measured with Constraints.fixed(width, height), incl. the 1 dp padding.
- Text change after reload: height still from the widest width; facts and actions do not move; first button's focusRequester stays composed (aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem).
- Constraint edges: unbounded height -> Infinity then coerceIn; budget below 110 dp -> min = minOf(minHeight, budget); available width below 460 dp -> widths clamped, narrower ones dropped by the floor filter.
