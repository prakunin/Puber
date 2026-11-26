# Test design: details-plot-adaptive-width

Inputs: `brief.md`, `design-architect.md` (D1-D9, R1-R7), `design-ux.md`, `state.md` Q4-Q6. Kind improvement: no bug, so no failing-first fix test beyond the `red` rows. Perf track off.

Paths: `PlotWidthTest` = new `app/src/test/kotlin/com/kino/puber/ui/feature/details/component/PlotWidthTest.kt` (JUnit 5, plain px, fake `heightAt` lambda built from a line count per width: `lines x 18 + 2`); `DSCTest` = `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt`, run with `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.details.component.DetailsScreenContentTest`. Every instrumented check asserts with JUnit (`org.junit.Assert`), never Kotlin `assert`, which is a no-op on ART.

## Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| Line count measured off by one; a narrowed plot overflows, turns focusable and takes UP from the actions | brief, R1 | 2 | 3 | 6 | `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` (instrumented, measures the real chain) |
| Box takes the narrower text's height or loses the 110 dp minimum; facts and actions move | R2, D4, D6 | 2 | 3 | 6 | `choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase`, `aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem` |
| Budget under `PLOT_MIN_HEIGHT` + one line builds `Constraints` with min > max and crashes | brief, R3 | 2 | 3 | 6 | `choosePlotWidth_keepsHeightWithinBudget_whenBudgetIsBelowMinHeight`, `aPlotInACrampedHeroLaysOutWithoutCrashing` |
| Long plot narrows or loses focus | AC-3 | 1 | 3 | 3 | `choosePlotWidth_returnsWidestStep_whenPlotOverflowsAtWidest`, `DSCTest:172` |
| Stand rule differs from Compose (border, min, snap), owner dials values that behave otherwise on the TV | R4 | 2 | 2 | 4 | Manual M1, M2 |
| Width steps once when cached content is replaced from the network | brief, R5 | 2 | 1 | 2 | Accepted (brief Q3); watch in M2 |
| Up to one text layout per step per measure on the main thread | R6 | 1 | 2 | 2 | Accepted; `choosePlotWidth_queriesOnlyTheWidestStep_whenPlotOverflowsAtWidest` (P2) |
| `PlotWidthKt` absent from the shipped baseline profile | R7 | 1 | 1 | 1 | Accepted (Q6); `BaselineProfileContentTest` in check.full stays green |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `choosePlotWidth_returnsNarrowestStepThatKeepsTheHeight_whenPlotIsShort` | Steps 590/520/460, lines 3/4/4, min 110: returns 460 with height 110 px; fails if the loop stops at the first fitting step or picks the widest |
| AC-1 | unit | P0 red | `choosePlotWidth_acceptsStepAtExactlyTheHeight_andRejectsOneLineMore` | Lines 5/6/7 at 590/520/460 (110 px at 520, 128 px at 460): returns 520; fails on `<` instead of `<=` or a missing fit check |
| AC-1 | unit | P0 red | `choosePlotWidth_neverGoesBelowFloor_whenTextFitsEvenNarrower` | Steps include one under the floor where the text fits: result is the floor; fails if the floor filter is removed |
| AC-2 | unit | P0 red | `choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase` | Table of short, boundary, overflowing and small-budget cases: `heightPx` equals `natural590.coerceIn(min(minHeight, budget), budget)`; fails if the height follows the chosen step's text (D4) or the minimum is dropped (D6) |
| AC-2 | instrumented | P1 guard | `aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem` | Same composition, description switched from `HERO_DESCRIPTION` to `SHORT_DESCRIPTION` (both under 6 lines at 590): `assertEquals` of the `HERO_FACTS_LINE` and `PRIMARY_ACTION` tops in root between the two, 0.5 dp tolerance; fails if the box shrinks to the text height |
| AC-2 | unit | P0 red | `choosePlotWidth_keepsHeightWithinBudget_whenBudgetIsBelowMinHeight` | Budget 108 px (6 lines), min 110 px: returns height 108, no throw; fails if the minimum is not clamped to the budget (R3) |
| AC-2 | instrumented | P1 | `aPlotInACrampedHeroLaysOutWithoutCrashing` | `DetailsScreenContent` in a fixed-height host sized from a 113 dp plot target (`boundsInRoot` of the scrolled text is clipped to the snapped share, so the raw share lands at 117-135 dp): `PRIMARY_ACTION` is displayed; fails on a min > max `Constraints` crash in `PlotBlock` |
| AC-3 | unit | P0 red | `choosePlotWidth_returnsWidestStep_whenPlotOverflowsAtWidest` | Natural height above budget at 590: returns 590 and the snapped budget; fails if an overflowing plot is narrowed |
| AC-3 | instrumented | P0 guard | `aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions` (`DSCTest:172`; only the focus check at `:192` changes, from Kotlin `assert(focused != true)` to `assertTrue(focused != true)`) | UP from the actions leaves them for the plot; fails if a long plot stops being focusable |
| AC-3 | instrumented | P1 guard | `aPlotTooLongForItsBlockKeepsTheWidestWidth` | `LONG_DESCRIPTION` text node width is `PLOT_WIDTH - 2 x PLOT_FOCUS_BORDER` (588 dp, 0.5 dp tolerance); fails if long plots narrow |
| AC-4 | instrumented | P0 red | `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` | Fixture `SHORT_DESCRIPTION = "Довольно длинное описание. ".repeat(8)` (Q5): `assertTrue(textNode.getUnclippedBoundsInRoot().width <= PLOT_MIN_WIDTH - 2 x PLOT_FOCUS_BORDER)`, then UP from `PRIMARY_ACTION` leaves it focused; base draws it 588 dp wide, so it fails there; after the change fails if the measured line count is short by one and the plot scrolls |
| AC-5 | manual | P0 manual | M1 | Stand switch, sliders, samples, README and constants agree |
| AC-6 | manual | P0 manual | M2 | Owner's judgement on the TV; facts and buttons do not move against master |
| - | unit | P2 | `choosePlotWidth_queriesOnlyTheWidestStep_whenPlotOverflowsAtWidest` | The fake `heightAt` records widths: only 590 is asked; fails if D5's early return is lost (R6) |

The floor in AC-4 is the owner-set `PLOT_MIN_WIDTH`; make it `internal` (or mirror it in a test constant) so the test reads one value. `SHORT_DESCRIPTION` must wrap to at least two lines at the floor on the TV box, or the width assertion cannot fail on base: check that its base width is 588 dp when writing it red.

## Manual checks

Device: TV box `192.168.1.110:5555`, `com.kino.puber.stage` only, worktree dev APK via `tools/android-apk-install-preserve`, D-pad by `adb -s 192.168.1.110:5555 shell input keyevent`, at user pace (one title open at a time, no loops). Master build screenshots for the same titles are the reference for M2 step 4.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. `cd {wt} && python3 docs/design/build.py content`, open `docs/design/content/index.html`. 2. Toggle «ширина описания» fixed / по шагам. 3. Move «шаг ширины» and «минимум ширины». 4. Switch «текст описания» item / short / long. 5. Compare README «Agreed sizes» and `PLOT_WIDTH_STEPS` / `PLOT_MIN_WIDTH` in `DetailsScreenContent.kt` | Short sample narrows to the narrowest fitting step at or above the floor; long sample stays at `plotW` and scrolls; plot box height identical in fixed and adaptive mode; readout shows applied width and lines per step; README rows and constants equal the owner's values | Box height changing between modes; a step under the floor; README and constants disagreeing |
| M2 | 1. Install, launch, open a title with a short plot (1-3 lines), a medium one (4-6 lines at 590) and a long one (scrolling), each by D-pad from Home with `DPAD_CENTER`. 2. On each, press `DPAD_UP` from the action row. 3. On the long one press `DPAD_DOWN` / `DPAD_UP` inside the plot. 4. Screenshot each and compare fact lines and action row with master. 5. Back out and reopen one title once | Short and medium plots narrower, end on whole lines, no focus ring; UP stays on the actions; long plot 590 dp, takes focus with the 1 dp ring and scrolls; fact lines and action row at the same y as master; owner judges readability against the still | A narrowed plot taking focus or showing a ring; a clipped last line; facts or buttons shifted; a width jump visible after the network refresh; crash |

## UNTESTABLE

AC-6 readability "reads well and sits clear of the brighter part of the still": owner judgement, covered only by M2.
