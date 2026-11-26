# Narrow the details plot when it fits, so it covers less of the still

Status: approved
Date: 2026-11-26
Slug: details-plot-adaptive-width
Kind: improvement
Size: M
Tracks: ui

<!--
Each section has exactly one owning role; nobody else edits it. The conductor
copies each section verbatim from its owner's output and commits this file in
the worktree after each stage. Cite code as `path:line`; a claim with no
source is a guess.
-->

## Problem / Why

### Request

On the details hero, draw the plot narrower when it is short enough, so it overlaps the still less; long plots keep 590 dp and scroll. Owner text and the conductor's guards: `state.md` Log, 2026-11-26.

### Why M

One screen, but it changes when the plot takes focus (TV focus, a risk area in `.flow/roles/analyst.md`) and holds one design decision (steps and floor) that the owner settles on the stand first (`designing-a-screen`, sliders need the owner).

### Risks (analyst)

| Risk | Guard |
|---|---|
| Measurer style differs from the `Text` style (`bodyLarge` merged with 13/18 sp, width minus 2 x `PLOT_FOCUS_BORDER`), line count off by one, a short plot overflows and turns focusable | Measure with the merged `LocalTextStyle` at the inner width, or use `maxIntrinsicHeight`; AC-4 catches overflow |
| `heightIn(min = 110 dp)` before the snapping `layout {}`: at a 110-125 dp budget the snapped max (6 lines, 108 dp) may fall below `minHeight` and throw (unverified, research.md: 7) | Clamp `snapped` to `constraints.minHeight` when the chain is rewritten |
| Content rebuilt from cache, then network (`DetailsVM.kt:272`); a changed budget can step the width once | Accepted (Q3); revisit if seen on the TV |

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | Line counts per step for a plot whose lines at a narrower step fit the height it has at 590 dp | the width-choice function runs | it returns the narrowest such step, never below the floor (JVM unit test) |
| AC-2 | Any plot and budget | the width-choice function runs | the block height at the chosen width equals its height at 590 dp, so the fact lines and action row sit where they sit today (JVM unit test; on the TV, the facts and buttons of a short-plot title do not move against a master build) |
| AC-3 | A plot that overflows its block at 590 dp | details opens | the plot is 590 dp wide and UP from the actions focuses it; `DetailsScreenContentTest.kt:172` passes |
| AC-4 | A short plot (`HERO_DESCRIPTION`) | details opens | the plot text is no wider than the floor step, and UP from the actions leaves focus on the actions (new instrumented test, `make DEVICE=<serial> itest`) |
| AC-5 | The stand `docs/design/content` | the owner opens it | it has a fixed/adaptive plot-width switch, step and floor controls, and a short and a long sample plot; the README "Agreed sizes" table and the Compose constants hold the values the owner set |
| AC-6 | Short, medium and long plot titles on the TV box | each details screen is opened by D-pad | the narrowed plots read well and sit clear of the brighter part of the still (owner judges) |

## Out of scope

Letting the plot grow taller into the free space with pinned facts and buttons (owner Q4: only short plots). Baseline profile regeneration (Q6). Changes to MediaScrim, MEDIA_WIDTH_FRACTION or HERO_LINE_WIDTH.

## Design

Brief: `brief.md`; facts and line map: `research.md` sections 3 and 6. All code paths below are in the worktree at base `b27d8e40`; `DSC` = `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`.

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Where the width is chosen | Inside `PlotBlock`'s existing `layout {}` (`DSC:320-329`), no state, no VM change | The budget is only known in layout; brief Q3 |
| D2 | How line counts are measured | `measurable.maxIntrinsicHeight(widthPx)` on the chain below the `layout {}` (focusable, border, `padding(PLOT_FOCUS_BORDER)`, `verticalScroll` column, `Text`) | Same merged `bodyLarge` + 13/18 sp style and the same 2 x 1 dp padding as the drawn text, so no second style to keep in sync; intrinsic queries do not count as a measure |
| D3 | Fit criterion | Option A: height at widest step = `natural.coerceIn(min(minHeight, budget), budget)`; pick the narrowest step >= floor whose intrinsic height <= that height | Brief Q2 |
| D4 | Box height at the chosen width | Measure the child once with `Constraints.fixed(chosenWidth, heightAtWidest)` and report that size | A narrower text that needs fewer lines must not shrink the box, or the facts and actions rise (AC-2) |
| D5 | Overflow at the widest step | Return the widest step without trying others | Narrower steps never have fewer lines; AC-3 path stays byte-identical in behaviour |
| D6 | Minimum height | Drop `.heightIn(min = PLOT_MIN_HEIGHT)` (`DSC:317`) and `.width(PLOT_WIDTH)` (`DSC:316`); apply the minimum inside the pure function, clamped to the snapped budget | Removes the min > snapped `Constraints` edge (research.md section 7); behaviour for budgets >= 126 dp is unchanged |
| D7 | Testable unit | New `internal fun choosePlotWidth(stepsPx: List<Int>, floorPx: Int, budgetPx: Int, minHeightPx: Int, heightAt: (Int) -> Int): PlotFit` and `internal data class PlotFit(val widthPx: Int, val heightPx: Int)` in a new `PlotWidth.kt` | Pure px arithmetic with a fake `heightAt`, so AC-1/AC-2 run on the JVM with no Compose |
| D8 | Constants | `PLOT_WIDTH` (`DSC:861`) stays the widest step; add `PLOT_WIDTH_STEPS: List<Dp>` (widest first) and `PLOT_MIN_WIDTH` next to it; values are the ones the owner sets on the stand (ux-designer) | One source per size; skeleton keeps reading `PLOT_WIDTH` |
| D9 | Skeleton | Unchanged at `PLOT_WIDTH` (`DSC:741`) | Brief Q3 |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: plot text already arrives in `DetailsScreenState.Content` |
| ViewModel state and actions | N/A: width is layout-only (D1) |
| Screen and DI module | N/A |
| Compose content | `DSC:303-366` `PlotBlock`: remove `DSC:316-317`; in the `layout {}` keep the line snap (`DSC:321-326`) as `budgetPx`, call `choosePlotWidth(PLOT_WIDTH_STEPS px, PLOT_MIN_WIDTH px, budgetPx, PLOT_MIN_HEIGHT px, measurable::maxIntrinsicHeight)`, coerce the result into `constraints`, `measurable.measure(Constraints.fixed(w, h))`. Call site `DSC:214-217` unchanged. Constants at `DSC:861-866` (D8). Update the `PLOT_WIDTH` mention in the `HERO_LINE_WIDTH` KDoc (`DSC:871-875`) |
| Compose content (new) | `app/src/main/java/com/kino/puber/ui/feature/details/component/PlotWidth.kt`: `choosePlotWidth`, `PlotFit` (D7) |
| Resources | N/A |
| Tests | New JVM `app/src/test/kotlin/com/kino/puber/ui/feature/details/component/PlotWidthTest.kt` (JUnit 5, sibling of `DetailsScreenKeyTest.kt`): AC-1, AC-2, overflow, floor, budget below minimum. New instrumented case in `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt` beside `:172` for AC-4; `:172` itself unchanged (AC-3). Rows are the test-designer's |
| Design stand | `docs/design/content/index.src.html`: step and floor keys in `SHIPPED` (`:626`), `CONTROLS` entries in group «Описание» after `plotW` (`:691`), a fixed/adaptive entry in `SWITCHES` (`:736`), short and long sample plots, and the D3 rule in `heroHTML` around `plotLines` (`:918-928`) using `measureLines` (`:843`) at `w - 2`; rebuild with `python3 docs/design/build.py content`; `docs/design/content/README.md` «Agreed sizes» (`:27`) gets the step and floor rows. Sizes: ux-designer and owner |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| `rememberTextMeasurer` line counts | Duplicates the merged style and the border inset in a second place; the brief's top risk |
| `BoxWithConstraints` / `SubcomposeLayout` | Subcomposition per frame for a value a layout modifier already has |
| `remember(description)` width state | Can overflow when the budget later shrinks, turning the plot focusable |
| Option B (full half-share budget) | Moves facts and actions (brief Q2) |
| Option C (continuous width) | Ragged right edge between titles, more measuring |

### Risks

| # | Risk | Lives in | Guard |
|---|---|---|---|
| R1 | Focus: a narrowed plot overflows its box, becomes `focusable` (`DSC:344`) and takes UP from the actions | `PlotBlock` `layout {}` | D2 measures the drawn chain itself; D3 accepts a step only if its height fits; AC-4 instrumented test |
| R2 | Facts and action row shift because the box takes the narrower text's height | `PlotBlock` `layout {}`, column `DSC:214-224` | D4 fixed child height; AC-2 unit test; TV comparison against master |
| R3 | `Constraints` with min > max when the budget is under `PLOT_MIN_HEIGHT` + one line | `DSC:317-327` today | D6; `PlotWidthTest` row with budget < min height |
| R4 | Stand rule differs from Compose (stand `naturalH` at `index.src.html:919` omits the 2 dp border; Compose includes it), so the owner dials values that behave otherwise on the TV | `index.src.html:918-928` vs `PlotWidth.kt` | Stand implements D3 with the same min/clamp/border terms; AC-6 on the TV |
| R5 | Width steps once when cached content is replaced from the network (`DetailsVM.kt:272`) and the budget changes | `PlotBlock` layout | Accepted (brief Q3) |
| R6 | Main-thread cost: up to one text layout per step on each `PlotBlock` measure (text intrinsics cache one width) | `PlotBlock` layout | Overflowing plots stop after one query (D5); measure passes happen on content change only |
| R7 | New `PlotWidthKt` class absent from the shipped baseline profile until the next regeneration | `app/src/main/generated/baselineProfiles/baseline-prof.txt` | Prefix rule in `BaselineProfileContentTest.kt:22` still passes; see Q2 |

### Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | AC-4 names `HERO_DESCRIPTION` (`DetailsScreenContentTest.kt:30`, "Short plot synopsis."): a one-line text is narrower than any floor with or without the change, so the width assertion cannot fail | Change AC-4's fixture to a short multi-line plot that is at most 6 lines at the floor (e.g. `"Довольно длинное описание. ".repeat(8)`, about 3 lines at 590 dp) and assert its text node width <= floor; keep the UP-stays-on-actions assertion | no |
| Q2 | Regenerate baseline profiles in this run? | No: one small pure function; the next profile run picks it up | no |

### Visual (ux-designer)

### Governing stand

`docs/design/content/` governs this change (`README.md` "Agreed sizes", `index.src.html`,
built with `python3 docs/design/build.py content`). A stand exists, so the conductor does not
need to start `designing-a-screen` from scratch. One value is still open, though: the steps and
the floor. Those need the owner on the stand's sliders, so the stand has to gain the controls below
before the owner dials them (AC-5).

### Visual delta

- The plot box is narrowed only when the plot fits at a narrower step without growing taller than
  it is at 590 dp today (option A). The box height does not change, so nothing else on the hero
  moves: title, ratings, chips, fact lines, action row and rail stay where they are.
- In practice this narrows plots of up to 6 lines at 590 dp, because those sit on the
  `PLOT_MIN_HEIGHT` 110 dp floor (6 x 18 + 2 x 1 dp border). A plot of 7 lines or more at 590
  narrows only if a narrower step wraps it into the same number of lines, which is rare. Long
  plots that scroll stay at 590 dp. The owner should know this before dialling: medium plots barely
  change, short ones change the most.
- The still starts at x 331.2 dp, and the box spans x 11 to 11 + W. `MediaScrim.Details` is
  alpha 1.0 at 331.2, 0.50 at 394.1 (10 % of the 628.8 dp frame) and clear at 645.6 (50 %).

| Plot width W | Right edge | Under the still | Scrim alpha at the right edge |
|---|---|---|---|
| 590 (today) | 601 dp | 269.8 dp | 0.09 |
| 520 | 531 dp | 199.8 dp | 0.23 |
| 460 | 471 dp | 139.8 dp | 0.35 |
| 420 | 431 dp | 99.8 dp | 0.40 |
| 320 | 331 dp | 0 | 1.0 (fully clear of the still) |

  Each step narrower moves the end of the line onto denser scrim. That is the readability gain
  the owner asked for. Rough density at 13 sp is 6 to 7 dp per Cyrillic character: about 85 to 95
  characters a line at 590 and 65 to 75 at 460. The stand has to confirm this, because the TV
  typography guide gives no floor.
- The skeleton's plot bars stay at `PLOT_WIDTH` 590 (Q3).

### What the stand must gain

All of these go in `docs/design/content/index.src.html`, followed by a rebuild of
`index.html`:

1. **A switch** in `SWITCHES`: `{k:"plotFit", n:"ширина описания", opts:[["fixed","фиксированная"],["steps","по шагам"]]}`.
   It opens on `fixed` (what ships today). After "Зафиксируй" the default becomes `steps`.
2. **Two sliders** in `CONTROLS`, group "Описание", added to `SHIPPED` and used by `geom()`:
   - `plotStep`, "шаг ширины": range 10 to 200, step 5 dp, starting at 70.
   - `plotFloor`, "минимум ширины": range 320 to 590, step 5 dp, starting at 460, with the
     note "кадр начинается на 331 dp".

   The candidate widths run `plotW`, `plotW - plotStep`, ... while each stays above
   `plotFloor`, and `plotFloor` itself is always the last one. The starting values give
   590 / 520 / 460 (Q1). The existing `plotW` slider is still the widest step. `BEFORE` has
   no value for either new slider, so no grey "было" appears.
3. **The same rule as the code**, so the stand cannot disagree with Compose:
   - base height = `max(plotMin, lines590 x plotLine + 2)`, capped by the share snapped down to
     whole lines (the Compose `layout {}` does this; the stand currently skips the snapping);
   - lines at each step = `measureLines(text, step - 2, plotSp, plotLine)`;
   - pick the narrowest step where `lines x plotLine + 2 <= base height`;
   - fall back to `plotW` when no step fits, or when the plot scrolls at `plotW`.

   The block keeps the base height, and only its width changes.
4. **Two sample plots**, chosen with a switch `{k:"plotText", n:"текст описания", opts:[["item","из карточки"],["short","короткое"],["long","длинное"]]}`.
   The switch replaces only the plot of the current item, so the artwork, facts and buttons stay
   real.
   - Short: one or two sentences, about 120 to 200 characters, 1 to 2 lines at 590.
   - Long: about 1,500 or more characters, enough to overflow the share at 590 so the plot scrolls
     and takes focus.

   The four cached items (300 to 560 characters) cover the medium case. An edited text is enough
   for both samples.
5. **Readout and guides.** The spec block reports the width that was applied, its line count, the
   line count at every candidate step, the overlap with the still in dp and the scrim alpha at the
   right edge. The "описание" guide line follows the applied width, and a faint second line stays
   at `plotW` so the gain is visible.

### Numbers the screen must keep (README "Agreed sizes", `DetailsScreenContent.kt`)

| Item | Value | Constant |
|---|---|---|
| plot width, widest step (long plots, skeleton bars) | **590** dp | `PLOT_WIDTH` |
| plot font size | **13** sp | `PLOT_TEXT_SIZE` |
| plot line height | **18** sp | `PLOT_LINE` |
| plot minimum height | **110** dp | `PLOT_MIN_HEIGHT` |
| focus border, corner radius | **1** dp, **4** dp | `PLOT_FOCUS_BORDER`, `PLOT_FOCUS_RADIUS` |
| chips to plot | **3** dp | `GAP_CHIPS_TO_PLOT` |
| plot to fact lines | **13** dp | `GAP_PLOT_TO_FACTS` |
| position of the "Дорожек" line | **95** % of the free height | `HERO_FACTS_ABOVE` 0.95 |
| fact line width | **940** dp | `HERO_LINE_WIDTH` (does not depend on the plot width) |
| still width | **65.5** % | `MEDIA_WIDTH_FRACTION` |
| scrim | **10** % / **50** % / **50** % | `MediaScrim.Details` |
| left margin | **11** dp | `SIDE_EDGE` |

Still open, to be set by the owner on the stand: the step (start 70 dp, which gives 590 / 520 /
460) and the floor (start 460 dp). The new rows go into the README "Agreed sizes" table and into
the constants in the same change. Suggested rows: "шаг ширины описания" (было —) and
"минимум ширины описания" (было —, since today's width is fixed at 590).

### Focus

- The change adds no focusable element.
- The plot still takes focus only when it scrolls (`focusable(enabled = scrollable)`). Under
  option A, a plot narrower than 590 never scrolls by construction, so it never takes focus, and
  UP from the actions stays on the actions (AC-4). The 1 dp primary ring, radius 4 dp, therefore
  only ever appears around a 590 dp block, exactly as today (AC-3).
- Visual check: a narrowed plot must not end mid-line or clip a descender at its bottom edge. The
  base height already holds whole lines plus the 2 dp border padding, so a correct line count
  guarantees this. A count off by one would show as a plot that scrolls and takes a focus ring.
  That is the visible failure to look for on the TV.

### Shared constants: parameterise, do not retune

- `PLOT_WIDTH` stays 590 and becomes the widest step. Add the new constants (for example
  `PLOT_WIDTH_STEP` and `PLOT_MIN_WIDTH`, or an explicit list of steps) next to it in
  `region Sizes`, and do not change 590 in place. The skeleton reads `PLOT_WIDTH`.
- Leave `MediaScrim.Details` and `MEDIA_WIDTH_FRACTION` alone (`VideoItemGridDetails.kt` is
  shared with the catalogue). Narrowing does not call for a different scrim.
- Keep `HERO_LINE_WIDTH` independent of the plot width.
- Measure at the same inner width and style that `Text` uses: the step minus 2 x
  `PLOT_FOCUS_BORDER`, with `bodyLarge` merged with 13 sp / 18 sp. The stand measures at
  `step - 2` to match.

### README follow-ups in the same change

- Add the rows to "Agreed sizes" and a short paragraph on the rule (option A: narrowest step no
  taller than at 590; the plot box keeps its height).
- Leave "The picture behind the long lines has not been judged" open, but note that narrowed plots
  now end on denser scrim. The fact lines at 940 dp are unchanged, so the bright-still question
  still applies to them.
- Republishing the claude.ai copy is optional. The repo copy is the one that counts.

## Test design

Inputs: `brief.md`, `design-architect.md` (D1-D9, R1-R7), `design-ux.md`, `state.md` Q4-Q6. Kind improvement: no bug, so no failing-first fix test beyond the `red` rows. Perf track off.

Paths: `PlotWidthTest` = new `app/src/test/kotlin/com/kino/puber/ui/feature/details/component/PlotWidthTest.kt` (JUnit 5, plain px, fake `heightAt` lambda built from a line count per width: `lines x 18 + 2`); `DSCTest` = `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt`, run with `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.details.component.DetailsScreenContentTest`. Every instrumented check asserts with JUnit (`org.junit.Assert`), never Kotlin `assert`, which is a no-op on ART.

### Risk profile

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

### Traceability

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

### Manual checks

Device: TV box `192.168.1.110:5555`, `com.kino.puber.stage` only, worktree dev APK via `tools/android-apk-install-preserve`, D-pad by `adb -s 192.168.1.110:5555 shell input keyevent`, at user pace (one title open at a time, no loops). Master build screenshots for the same titles are the reference for M2 step 4.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. `cd {wt} && python3 docs/design/build.py content`, open `docs/design/content/index.html`. 2. Toggle «ширина описания» fixed / по шагам. 3. Move «шаг ширины» and «минимум ширины». 4. Switch «текст описания» item / short / long. 5. Compare README «Agreed sizes» and `PLOT_WIDTH_STEPS` / `PLOT_MIN_WIDTH` in `DetailsScreenContent.kt` | Short sample narrows to the narrowest fitting step at or above the floor; long sample stays at `plotW` and scrolls; plot box height identical in fixed and adaptive mode; readout shows applied width and lines per step; README rows and constants equal the owner's values | Box height changing between modes; a step under the floor; README and constants disagreeing |
| M2 | 1. Install, launch, open a title with a short plot (1-3 lines), a medium one (4-6 lines at 590) and a long one (scrolling), each by D-pad from Home with `DPAD_CENTER`. 2. On each, press `DPAD_UP` from the action row. 3. On the long one press `DPAD_DOWN` / `DPAD_UP` inside the plot. 4. Screenshot each and compare fact lines and action row with master. 5. Back out and reopen one title once | Short and medium plots narrower, end on whole lines, no focus ring; UP stays on the actions; long plot 590 dp, takes focus with the 1 dp ring and scrolls; fact lines and action row at the same y as master; owner judges readability against the still | A narrowed plot taking focus or showing a ring; a clipped last line; facts or buttons shifted; a width jump visible after the network refresh; crash |

### UNTESTABLE

AC-6 readability "reads well and sits clear of the brighter part of the still": owner judgement, covered only by M2.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Steps and floor? | no | answered | Start 590/520/460 dp, floor 460; owner sets final values on the stand |
| Q2 | Fit criterion? | yes | answered | A: no taller than today at 590 dp |
| Q3 | Skeleton at 590; width in layout with no state; stand switch, sliders, short/long samples? | no | answered | Yes to all |
| Q4 | Only short plots narrow, or medium too (plot grows, facts pinned)? | yes | answered | Only short (A) |
| Q5 | AC-4 fixture: short plot wrapping to several lines within 6 lines at the floor? | no | answered | Yes |
| Q6 | Regenerate baseline profiles here? | no | answered | No |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q3, Q5, Q6 as recommended | owner (standing instruction: recommended) |
| 2026-11-26 | Q4: only short plots narrow | owner |
| 2026-11-26 | Gate 1: spec approved | owner (standing instruction: recommended) |
