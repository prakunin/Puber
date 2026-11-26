# Review tests, round 1

LENS: tests  ROUND: 1  BASE: b27d8e40

### F1 [major] AC-5 has no test, and its manual check M1 was never run
- Where: evidence 2026-11-26-details-plot-adaptive-width-device.md:25-28 (Manual M1); qa-report.md (AC-5 NOT CHECKED)
- Problem: nobody opened the stand; switch, sliders, samples and "same box height in both modes" are unverified; only README rows vs Compose constants were compared; implementer ran node --check and built the page. A wrong stand rule (spec R4) or runtime script error would not show.
- Fix: run M1 before gate 2 in a headless browser (toggle plotFit/plotText, move both sliders, record no console error and equal box height), or mark AC-5 open for the owner at gate 2.

### F2 [minor] M2 is incomplete: no short-plot title opened and no comparison with master on the TV
- Where: evidence :19-20
- Problem: AC-2 (facts/buttons vs master on the TV) and AC-6 short case NOT CHECKED; code side covered by choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase and aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem.
- Fix: open one short-plot title on the box next to a master screenshot at gate 2, or list both as owner checks.

Suite state: check.full exit 0 (mostly UP-TO-DATE). DetailsScreenContentTest 12/12 on device per evidence.
Traceability checked, no finding: all six P0 unit rows and the P2 row in PlotWidthTest fail under their mutation, red run recorded; D4 "height = chosen step's text height" mutation is equivalent; AC-4 red on base (588 dp > 458); cramped-hero test red on base (Constraints.copy IllegalArgumentException), so the 113 dp target reaches the path; AC-3 guard now a real assertTrue; no assertions removed.
Note: qa ran git checkout HEAD -- app/src/main inside the worktree to restore base sources after the red run.
