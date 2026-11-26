Round 1

CHECK: check.full -> pass (BUILD SUCCESSFUL; mostly up-to-date tasks, unit, detekt, lint, profile tests all green)
DEVICE: 192.168.1.110:5555 (com.kino.puber.stage; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlotWidthTest (in check.full) |
| AC-2 | PASS (unit, instrumented); TV vs master NOT CHECKED | choosePlotWidth height tests; aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem green; no master build compared |
| AC-3 | PASS | DetailsScreenContentTest 12/12; on TV a long plot was 590 dp, UP gave it the ring |
| AC-4 | PASS | red on base (588 dp, assertion fails), green on change; on TV a 6-line plot narrowed to about 465 dp and UP stayed on the actions |
| AC-5 | NOT CHECKED | no browser; README rows 70/460 match PLOT_WIDTH_STEPS and PLOT_MIN_WIDTH; stand not opened, owner's values not confirmed |
| AC-6 | NOT CHECKED | owner judgement; only a long and a medium title were opened, no short one |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/details-plot-adaptive-width/docs/superpowers/evidence/2026-11-26-details-plot-adaptive-width-device.md
DEFECTS: none. Note: on base the crampled-hero test also fails with IllegalArgumentException (Constraints min > max), confirming the R3 clamp fixes a real crash.
UNAVAILABLE: browser for M1
MANUAL: open docs/design/content/index.html (python3 docs/design/build.py content) and confirm the switch, sliders, samples and values; judge AC-6 on a short-plot title and compare fact lines and buttons to a master build on the TV box.

Evidence: /home/dev/orca/workspaces/Puber/details-plot-adaptive-width/docs/superpowers/evidence/2026-11-26-details-plot-adaptive-width-device.md


Round 2

CHECK: check.full -> pass (BUILD SUCCESSFUL)
DEVICE: 192.168.1.110:5555 (com.kino.puber.stage; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlotWidthTest in check.full |
| AC-2 | PASS (unit, instrumented) | aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem green on the box |
| AC-3 | PASS | long-plot tests green on the box |
| AC-4 | FAIL (test red) | aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight fails on the box: no repeat count 16..30 gives a narrowed plot above the minimum in a 1000 dp host (DetailsScreenContentTest.kt:308); the crash case aPlotInACrampedHeroLaysOutWithoutCrashing passes |
| AC-5 | NOT CHECKED | no browser; owner manual check |
| AC-6 | NOT CHECKED | account works, but the one title opened had a long plot (7 lines); no short title seen |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/details-plot-adaptive-width/docs/superpowers/evidence/2026-11-26-details-plot-adaptive-width-device.md
DEFECTS: 1. DetailsScreenContentTest 12/13 on the box; the failing test is aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight. Reproduce: make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.details.component.DetailsScreenContentTest. Either the test's text range cannot produce a narrowed plot taller than 110 dp on this device (test defect) or the width choice never narrows a taller plot (production); not determined.
UNAVAILABLE: browser for AC-5
MANUAL: AC-5: python3 docs/design/build.py content, open docs/design/content/index.html in a browser; confirm the plot-width switch, sliders (step 70, minimum 460, block width), samples and the displayed values match PLOT_WIDTH_STEPS and PLOT_MIN_WIDTH. AC-6: open a short-plot title on the box and compare fact lines and buttons with a master build.


Round 3

CHECK: check.fast -> pass (BUILD SUCCESSFUL); check.full not re-run (test-only change)
DEVICE: 192.168.1.110:5555 (lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 | PASS | DetailsScreenContentTest 13/13 on the box, incl. the round 2 red test |
| AC-1..3 | PASS | unchanged from round 2; long, short and cramped-hero tests green on the box |
| AC-5 | NOT CHECKED | no browser; owner manual check |
| AC-6 | NOT CHECKED | owner judgement |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/details-plot-adaptive-width/docs/superpowers/evidence/2026-11-26-details-plot-adaptive-width-device.md
DEFECTS: none
UNAVAILABLE: browser for AC-5
MANUAL: as in round 2 (design stand, short-plot title vs master)
