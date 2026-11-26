# Device evidence: details-plot-adaptive-width

Build: worktree dev APK (version 1.12.8-dev, sha256 fa93f7c4...47be5), installed with install-preserve (outcome installed, compatible_replace).
Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only. Base for the red run: b27d8e40.

## Instrumented: DetailsScreenContentTest (make DEVICE=192.168.1.110:5555 itest)

| Run | Result |
|-----|--------|
| This change | 12 tests, 0 failures |
| Base app/src/main (b27d8e40) plus the three constants made internal so the test compiles | 12 tests, 2 failures: aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions ("plot wider than the floor: 588.0.dp", AC-4 red on base) and aPlotInACrampedHeroLaysOutWithoutCrashing (IllegalArgumentException from Constraints.copy in PlotBlock, the min > max crash of the brief's risk R3) |

The base sources were restored with git checkout HEAD -- app/src/main; the worktree was clean afterwards.

## Manual M2 (D-pad, two titles, user pace, no retries)

| Title kind | Steps | Expected | Observed | Status |
|---|---|---|---|---|
| Long plot (7 lines at 590 dp, scrolls) | Home, hero card, DPAD_CENTER; DPAD_UP from the actions | 590 dp wide, UP takes focus with 1 dp ring | Text block 590 dp wide (1180 px at 2x), UP showed the ring around the plot | PASS (AC-3) |
| Medium plot (6 lines) | Home, DPAD_DOWN x2, DPAD_CENTER; DPAD_UP from the actions | Narrowed to the floor, UP stays on the actions | Text about 465 dp wide, ends on whole lines, no ring after UP, focus stayed on the play button | PASS (AC-4 behaviour) |
| Short plot (1-3 lines) | not opened | | no short title was reached in two opens | NOT CHECKED |
| Facts and buttons against a master build | not done | | no master build installed | NOT CHECKED (AC-2 on TV) |

AC-6 readability against the still is the owner's judgement; no screenshots are committed (they show account content).

## Manual M1 (design stand)

No browser in this environment. README rows (step 70 dp, floor 460 dp) match PLOT_WIDTH_STEPS (590, 520, 460) and PLOT_MIN_WIDTH in DetailsScreenContent.kt; the stand itself was not opened.


## Round 2 (after fix rounds 1-2, head 7aa415b9)

Device: TV box 192.168.1.110:5555, 1920x1080 at 320 dpi, com.kino.puber.stage; build 1.12.8-dev from this worktree, installed with install-preserve (outcome installed, compatible_replace, no destructive action); app data saved with auth-save first.

check.full: BUILD SUCCESSFUL (unit, detekt, lint, profile tests, instrumentation detekt and lint).

make DEVICE=192.168.1.110:5555 itest TESTS=...DetailsScreenContentTest: 13 tests, 12 passed, 1 failed.
- aPlotInACrampedHeroLaysOutWithoutCrashing: PASS.
- aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions, aPlotTooLongForItsBlockKeepsTheWidestWidth, aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions, aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem: PASS.
- aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight: FAIL, "no repeat count gives a narrowed plot above the minimum in a 1000 dp host" (DetailsScreenContentTest.kt:308). No repeat count from 16 to 30 produced a plot that is narrower than the widest width and taller than the 110 dp minimum on this device.

AC-6 short case on device: the account works. One title was opened at ordinary pace (Home, one RIGHT, one CENTER): a series whose plot wraps to seven lines at the widest width, i.e. a long plot, not a short one. Not a short-plot check. No loops, no further titles opened. Screenshot not committed (account content).


## Round 3 (test-only change e739bfd5)

Device: 192.168.1.110:5555 (com.kino.puber.stage, lease acquired and released). No app install, no API use.
Command: make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.details.component.DetailsScreenContentTest
Observed: BUILD SUCCESSFUL, 13 tests, 0 failures, 0 errors. Includes aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight (red in round 2) and aPlotInACrampedHeroLaysOutWithoutCrashing.
AC-4: PASS. check.fast (:app:compileDevDebugKotlin): pass.
