# Narrow the details plot when it fits - plan

**Spec:** `docs/superpowers/specs/2026-11-26-details-plot-adaptive-width.md`
**Goal:** `PlotBlock` draws a plot that keeps its 590 dp height at a narrower step at the narrowest such step (not below the floor), with the box height, the fact lines and the action row unchanged; long plots stay at 590 dp and scroll.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate (inside the worktree): `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug`. Never `./gradlew` in the worktree.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- `DSC` = `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`; `DSCTest` = `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt`; `stand` = `docs/design/content/index.src.html`. Lines are at base `b27d8e40`.
- Instrumented tests compile with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`; running them on the TV box (`make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.details.component.DetailsScreenContentTest`) is qa's, as are manual checks M1-M2 (spec, Test design).
- Out of scope (spec): growing the plot taller, baseline profile regeneration, `MediaScrim`, `MEDIA_WIDTH_FRACTION`, `HERO_LINE_WIDTH`. The skeleton bars (`DSC:741`) stay at `PLOT_WIDTH`.

---

## Tasks

### Task 1: Pure width choice with JVM tests

**Covers:** AC-1, AC-2, AC-3 (unit rows)
**Files:**
- Create: `app/src/main/java/com/kino/puber/ui/feature/details/component/PlotWidth.kt`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/details/component/PlotWidthTest.kt` (JUnit 5, `internal class`, same package and style as `DetailsScreenKeyTest.kt` in that folder)

**Consumes:** nothing.
**Produces:**
```kotlin
internal data class PlotFit(val widthPx: Int, val heightPx: Int)

/** stepsPx widest first; heightAt(w) = block height (text + border padding) at width w. */
internal fun choosePlotWidth(
    stepsPx: List<Int>,
    floorPx: Int,
    budgetPx: Int,
    minHeightPx: Int,
    heightAt: (Int) -> Int,
): PlotFit
```
Rule (spec D3-D6):
1. `widest = stepsPx.first()`, `natural = heightAt(widest)`.
2. `natural > budgetPx`: return `PlotFit(widest, budgetPx)` without calling `heightAt` again (D5).
3. `height = natural.coerceIn(minOf(minHeightPx, budgetPx), budgetPx)` (D6: the minimum is clamped to the budget).
4. Among the other steps with `step >= floorPx`, try them narrowest first; return the first with `heightAt(step) <= height` as `PlotFit(step, height)`; none: `PlotFit(widest, height)`. The height is always `height`, never the chosen step's text height (D4).
5. `budgetPx` may be `Constraints.Infinity` (`Int.MAX_VALUE`); the rule needs no special case for it.

Fake `heightAt` in the test: a map width -> line count, returning `lines * 18 + 2`; it records the widths asked. Steps 590 / 520 / 460, floor 460, min 110, budget 200 unless a row says otherwise.

| Test | Priority | Input -> expected |
|---|---|---|
| `choosePlotWidth_returnsNarrowestStepThatKeepsTheHeight_whenPlotIsShort` | P0 red | lines 3/4/4 -> `PlotFit(460, 110)` |
| `choosePlotWidth_acceptsStepAtExactlyTheHeight_andRejectsOneLineMore` | P0 red | lines 5/6/7 (110 at 520, 128 at 460) -> `PlotFit(520, 110)` |
| `choosePlotWidth_neverGoesBelowFloor_whenTextFitsEvenNarrower` | P0 red | steps 590/520/460/400, lines 3/3/3/3 -> width 460 |
| `choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase` | P0 red | short, boundary, overflowing and small-budget cases: `heightPx == natural590.coerceIn(min(minHeight, budget), budget)` |
| `choosePlotWidth_keepsHeightWithinBudget_whenBudgetIsBelowMinHeight` | P0 red | budget 108, min 110, lines 2/2/2 -> `heightPx == 108`, no throw |
| `choosePlotWidth_returnsWidestStep_whenPlotOverflowsAtWidest` | P0 red | lines 20/22/25 (362 px at 590 > budget 200) -> `PlotFit(590, 200)` |
| `choosePlotWidth_queriesOnlyTheWidestStep_whenPlotOverflowsAtWidest` | P2 | same input: recorded widths == `[590]` |

- [x] Write `PlotWidthTest` with the seven tests and a `choosePlotWidth` stub that returns `PlotFit(stepsPx.first(), 0)`; run it and record the red run (all P0 rows fail)
- [x] Implement the rule above in `PlotWidth.kt` (KDoc: why the height is the widest step's)
- [x] Run `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.details.component.PlotWidthTest'` and the fast gate; commit "Add the pure width choice for the details plot"

**Done when:** that test command reports 7 passed, 0 failed, and the fast gate is green.

---

### Task 2: Stand gains the adaptive plot width

**Covers:** AC-5 (stand half)
**Files:**
- Modify: `stand` - all five items of the spec's "What the stand must gain" (Visual, ux-designer):
  - `SHIPPED` (`stand:626`): add `plotStep:70, plotFloor:460`;
  - `CONTROLS` group «Описание» after `plotW` (`stand:691-692`): `plotStep` «шаг ширины» 10-200 step 5 dp, `plotFloor` «минимум ширины» 320-590 step 5 dp with note «кадр начинается на 331 dp»;
  - `SWITCHES` (`stand:736-742`): `plotFit` «ширина описания» fixed/steps (opens on `fixed`) and `plotText` «текст описания» item/short/long, the samples replacing only the current item's plot (short 120-200 chars, long 1,500+ chars);
  - `heroHTML` (`stand:892`), the plot block at `stand:917-933`: base height = `max(min(plotMin, snappedShare), visibleLines x plotLine + 2)` capped by the share snapped to whole lines, lines per step from `measureLines` (`stand:843`) at `step - 2`, narrowest step with `lines x plotLine + 2 <= base`, widest when none fits or the plot scrolls at `plotW`; the block keeps the base height in both modes;
  - readout: applied width, its lines, lines per step, overlap with the still, scrim alpha at the right edge; guide line at the applied width plus a faint one at `plotW`.
- Modify: `docs/design/content/README.md` - "Agreed sizes" (`:27`, table `:33-78`): rows «шаг ширины описания» (было —, стало **70** dp) and «минимум ширины описания» (было —, стало **460** dp) after «ширина описания» (`:53`); a short paragraph on the rule (option A, the box keeps its height) and the note that narrowed plots end on denser scrim, per the spec's "README follow-ups".
- Build output: `docs/design/content/index.html` via `python3 docs/design/build.py content`.

**Consumes:** nothing. **Produces:** the stand the owner dials; README rows that Task 3 reads.

- [x] Edit the stand and README, rebuild `index.html`
- [x] Open the built page headless or in a browser: switch `plotFit` and `plotText`, move both sliders; no console error, box height equal in both modes
- [x] Run the fast gate; commit "Add the adaptive plot width to the content design stand"

**Done when:** `python3 docs/design/build.py content` exits 0 and `git grep -n "plotStep\|plotFloor\|plotFit\|plotText" docs/design/content/index.src.html` shows the four keys in `SHIPPED`/`CONTROLS`/`SWITCHES` and `heroHTML`.

**Owner checkpoint (conductor, between Task 2 and Task 3):** the owner sets the step and floor on the stand. It does not block Task 3: if they differ from 70 / 460, the conductor puts them in the README rows before Task 3; with no answer the start values (70 dp step, 460 dp floor) stand and the owner may tune them on the stand later (Q1).

---

### Task 3: PlotBlock picks its width in layout

**Covers:** AC-2, AC-3, AC-4, AC-5 (constants half)
**Files:**
- Modify: `DSC`
  - Constants `DSC:861-866`: `PLOT_WIDTH` stays 590 and becomes `internal`; add `internal val PLOT_MIN_WIDTH` (README floor) and `private val PLOT_WIDTH_STEPS: List<Dp> = listOf(PLOT_WIDTH, PLOT_WIDTH - step, ..., PLOT_MIN_WIDTH)` written out from the README step and floor (start values: 590, 520, 460); `PLOT_FOCUS_BORDER` becomes `internal` (`DSCTest` reads all three). KDoc on `PLOT_WIDTH_STEPS` names the stand sliders it comes from.
  - `PlotBlock` (`DSC:303-366`): delete `.width(PLOT_WIDTH)` and `.heightIn(min = PLOT_MIN_HEIGHT)` (`DSC:316-317`); replace the body of the `layout {}` (`DSC:320-329`) with:
    ```kotlin
    val line = PLOT_LINE.roundToPx().coerceAtLeast(1)
    val budget = if (constraints.hasBoundedHeight) {
        (constraints.maxHeight / line * line).coerceAtLeast(line)
    } else {
        constraints.maxHeight
    }
    val fit = choosePlotWidth(
        stepsPx = PLOT_WIDTH_STEPS.map { it.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth) },
        floorPx = PLOT_MIN_WIDTH.roundToPx(),
        budgetPx = budget,
        minHeightPx = PLOT_MIN_HEIGHT.roundToPx(),
        heightAt = measurable::maxIntrinsicHeight,
    )
    val height = fit.heightPx.coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(fit.widthPx, height))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    ```
    The `measurable` below the `layout {}` is the chain `onPreviewKeyEvent` -> `focusable` (`DSC:344`) -> border -> `padding(PLOT_FOCUS_BORDER)` (`DSC:356`) -> `Box` -> `Column(verticalScroll)` -> `Text` (`DSC:358-363`), so `maxIntrinsicHeight(w)` is the drawn text height at `w - 2 dp` plus the 2 dp padding (spec D2). Keep the line-boundary comment (`DSC:318-319`) and add one line on the width choice. Remove imports left unused (`width`, `heightIn` if no other user); add `androidx.compose.ui.unit.Constraints` and `Dp` if missing.
  - KDoc of `HERO_LINE_WIDTH` (`DSC:871-875`): "stays at [PLOT_WIDTH]" becomes "is at most [PLOT_WIDTH]".
  - Call site `DSC:214-217` and skeleton `DSC:741` unchanged.
- Test: `DSCTest` - add `private val SHORT_DESCRIPTION = "Довольно длинное описание. ".repeat(8)` next to `LONG_DESCRIPTION` (`DSCTest:47`); `HERO_DESCRIPTION` (`:30`) unchanged. In `aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions` (`:172-193`, AC-3 P0 guard) only the focus check at `:192` changes: Kotlin `assert(focused != true) { ... }` becomes `assertTrue("кнопка удержала фокус вместо описания", focused != true)` (`org.junit.Assert.assertTrue`; Kotlin `assert` is a no-op on ART); the rest of the body stays. Every instrumented check in this task asserts with `org.junit.Assert`, never Kotlin `assert`. New tests use `seriesContent(description = ...)` (`:204`) and `waitForFocus` (`:195`):

| Test | AC | Priority | Body |
|---|---|---|---|
| `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` | AC-4 | P0 red | `SHORT_DESCRIPTION`; `onNodeWithText(SHORT_DESCRIPTION).getUnclippedBoundsInRoot().width <= PLOT_MIN_WIDTH - PLOT_FOCUS_BORDER * 2` (`org.junit.Assert.assertTrue` with the width in the message; ui-test 1.12 has no `assertWidthIsAtMost`); wait for focus on `PRIMARY_ACTION`, press `DirectionUp`, `waitForIdle`, `assertIsFocused` on `PRIMARY_ACTION` |
| `aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem` | AC-2 | P1 guard | description held in `mutableStateOf(HERO_DESCRIPTION)`; read `getUnclippedBoundsInRoot().top` of `HERO_FACTS_LINE` and `PRIMARY_ACTION`; switch to `SHORT_DESCRIPTION`, `waitForIdle`; `org.junit.Assert.assertEquals` of each top against its first read, tolerance 0.5 dp |
| `aPlotInACrampedHeroLaysOutWithoutCrashing` | AC-2 | P1 | host `Box` whose height is `mutableStateOf`: first full size with `LONG_DESCRIPTION`, read the plot share as the `LONG_DESCRIPTION` node's `boundsInRoot` height + 2 dp; set the host height to root height - 2 x (share - 113 dp), sized from a 113 dp plot target (`boundsInRoot` of the scrolled text is clipped to the snapped share, so the raw share lands at 117-135 dp); `waitForIdle`; `PRIMARY_ACTION` `assertIsDisplayed` |
| `aPlotTooLongForItsBlockKeepsTheWidestWidth` | AC-3 | P1 guard | `LONG_DESCRIPTION`; `onNodeWithText(LONG_DESCRIPTION).assertWidthIsEqualTo(PLOT_WIDTH - PLOT_FOCUS_BORDER * 2)` (default 0.5 dp tolerance) |

**Consumes:** `choosePlotWidth`, `PlotFit` (Task 1); step and floor from the README rows (Task 2 and the owner checkpoint).
**Produces:** `internal val PLOT_WIDTH`, `PLOT_MIN_WIDTH`, `PLOT_FOCUS_BORDER` in `DSC`.

- [x] Add the four tests to `DSCTest` first, with the three constants made `internal` and `PLOT_MIN_WIDTH` added but `PlotBlock` unchanged; compile with `:app:compileDevDebugAndroidTestKotlin`; hand the red run of `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` (expected: width 588 dp > floor - 2) to qa via "For the reviewers"
- [x] Change `PlotBlock` and the KDoc as above
- [x] Run the fast gate, `:app:compileDevDebugAndroidTestKotlin` and the full gate; commit "Choose the details plot width in layout so short plots narrow"

**Done when:** the full gate is green; `rg -n "width\(PLOT_WIDTH\)|heightIn\(min = PLOT_MIN_HEIGHT\)" app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt` finds nothing; `PLOT_WIDTH_STEPS` and `PLOT_MIN_WIDTH` equal the README rows; qa's `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.details.component.DetailsScreenContentTest` passes (AC-3, AC-4), followed by M1 and M2 (AC-5, AC-6).

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- c3ae5ddf Add the pure width choice for the details plot
- 3595c650 Add the adaptive plot width to the content design stand
- Choose the details plot width in layout so short plots narrow (Task 3, sha in git log)

**Red run** (P0 `red` tests on the unchanged code):

- red: all six P0 `PlotWidthTest` rows plus the P2 row, against the stub `PlotFit(stepsPx.first(), 0)` - each fails on its assertion (7 tests, 7 failed).
- Instrumented reds (`aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions`, guards) are qa's: the test was written with the constants `internal`, not run on base here (no device).

**Deviations from the plan:**

- Task 3 red step: the androidTest was compiled together with the production change, not before it; no device here, so the order has no observable red.
- `PlotWidth.kt` carries `@file:Suppress("MatchingDeclarationName")`: detekt wants the file named for `PlotFit`; the plan names the file for the function.
- Task 2 stand: the page was not opened in a browser (none in the box); the script was checked with `node --check` and the page built. Plot block height now includes the 2 dp border (`visible x plotLine + 2`) in both modes, as the spec asks.
- Owner values: start values 70 dp step, 460 dp floor.
- Fix round 2, defect 1: `aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight` now uses a fixed fixture of eight short lines joined with newlines (each shorter than the floor width), replacing the repeat-count scan; the tall host and the assertions are unchanged. Compiled only, not run (no device here).

**Files touched outside the plan:**

- `docs/design/content/index.html` (build output of the stand).

**For the reviewers:**

- qa: run `DetailsScreenContentTest` on the box, red-check `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` against base (expected width 588 dp > 458), then M1 and M2.
- `aPlotInACrampedHeroLaysOutWithoutCrashing` reads `boundsInRoot` with the density captured from `LocalDensity`; check the 113 dp target actually reaches the budget-below-minimum path.
- `PLOT_WIDTH_STEPS` is now built by `plotWidthSteps(590, 70, 460)` (fix round 1, #2); the stand's `plotSteps` follows the same rule.

### Fix round 1 (triage-1 #1-#9)

Commits:

- 682ce8fb Make the details plot layout tests discriminate the fact-line and cramped-hero cases
- 545c6805 Build the plot width steps from the step and floor and test the sequence
- 22ab3b58 Fix the content stand's plot width steps, scroll check, switch focus and labels

| # | Result |
|---|---|
| 1 | Fixed: stand `plotSteps` appends the floor when the last step stops above it (70/460 gives 590/520/460) |
| 2 | Fixed: `PLOT_WIDTH_STEP`, `plotWidthSteps()` in `PlotWidth.kt`, same rule as the stand |
| 3 | Fixed: `plotScrollable = plotLines * plotLine + 2 > snappedShare` |
| 4 | Fixed: switch handler renders, then reconciles focus and re-renders if it moved |
| 5 | Fixed: labels «фиксированная» / «короткое» / «длинное», short sample repeat(6) |
| 6 | Fixed: README says 70/460 are start values, switch stays on fixed until the owner confirms |
| 7 | Fixed: `aNarrowedPlotTallerThanTheMinimumMovesTheFactLinesByItsHeight` (scans repeat counts 16..30 for a narrowed plot above 110 dp; asserts the facts move by its height). Not run here: no device, qa runs it |
| 8 | Fixed: cramped-hero test asserts the block is at most `PLOT_MIN_HEIGHT` (now `internal`) |
| 9 | Fixed: literal expected heights in the height test |

Red run: red: `plotWidthSteps_endsAtTheFloor_whenTheLastStepFallsBelowIt` - expected [590, 520, 460] but was [590] (stub `listOf(widest)`); red: `plotWidthSteps_doesNotRepeatTheFloor_whenAStepLandsOnIt` - expected [590, 530, 470, 410] but was [590]; green on base: `plotWidthSteps_isTheWidestOnly_whenTheFloorIsNotBelowIt` - guard row for the stub's own answer. #7 and #8 are androidTest changes to tests, not production: no red run without a device. #9 only pins values the production code already returns.

Stand: rebuilt with `python3 docs/design/build.py content`, `node --check` on its script passes; still not opened in a browser (#10 is qa's).
Check: `:app:compileDevDebugKotlin`, `PlotWidthTest`, `:app:compileDevDebugAndroidTestKotlin` green; check.full BUILD SUCCESSFUL in 2m 42s.

**Blocked:**

### Fix round 2 (triage-2 #1, #2, #4)

| # | Result |
|---|---|
| 1 | Fixed: `CRAMPED_PLOT_TARGET_DP` is 108, under the 110 dp minimum, so the resize lands on the below-minimum path whatever the snapped block (about 126 dp) leaves |
| 2 | Fixed: the tall-plot fact-line test hosts `DetailsScreenContent` in a `requiredHeight(1000.dp)` box so the budget reaches eight lines; the failure message names the host height. A 540 dp hero never gives that budget, so this case does not occur on the box's own hero. Not run here: no device, qa runs it |
| 4 | Not done: accepted, covered by `choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase` |

Red run: n/a (androidTest-only changes, no device here).
Check: `:app:compileDevDebugKotlin` and `:app:compileDevDebugAndroidTestKotlin` green.

## Readiness

Verdict: **READY**

Spec and plan read in `/home/dev/orca/workspaces/Puber/details-plot-adaptive-width` at `0656e986` (base `b27d8e40`; the diff from base holds only the spec and plan).

### Checks

| Check | Result |
|---|---|
| AC coverage | AC-1 Task 1; AC-2 Tasks 1 and 3; AC-3 Tasks 1 and 3; AC-4 Task 3; AC-5 Tasks 2 and 3 + M1; AC-6 M2. Each has a Test design row |
| Citations (sampled at base) | Resolve: `DSC:303` `PlotBlock`, `DSC:316-317` `.width(PLOT_WIDTH)` / `.heightIn(min = PLOT_MIN_HEIGHT)`, `DSC:320-329` `.layout {}`, `DSC:344` `focusable(enabled = scrollable, ...)`, `DSC:356` `padding(PLOT_FOCUS_BORDER)`, `DSC:214-217` call site, `DSC:741` skeleton bars, `DSC:861-866` constants, `DSC:871-875` `HERO_LINE_WIDTH` KDoc, `DSCTest:30/47/172/192/195/204`, `stand:626/691-692/736-744/843/892/918-919`, README `:27`/`:53`, `DetailsVM.kt:271-272` |
| Build stays green, order | T1 (pure `internal` function) -> T2 (stand only) -> owner checkpoint (non-blocking) -> T3; no task leaves the build red |
| P0 rows | Six unit rows `red`, each naming its mutation; the Task 1 stub `PlotFit(stepsPx.first(), 0)` fails all six. AC-3 `aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions` `guard`; AC-4 `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` `red`; M1/M2 `manual` |
| Out of scope | Respected: skeleton, `MediaScrim`, `MEDIA_WIDTH_FRACTION`, `HERO_LINE_WIDTH` value, profiles untouched |
| Blocking open questions | Q2, Q4 answered; Q1, Q3, Q5, Q6 answered |

### Round 1 fix list

| # | Status | Where |
|---|---|---|
| 1 | Fixed: AC-4 width check and the AC-2 tops check use `org.junit.Assert.assertTrue` / `assertEquals` (0.5 dp) | Spec Test design rows AC-2, AC-4 and the Paths line; plan Task 3 table |
| 2 | Fixed: `DSCTest:192` Kotlin `assert` becomes `assertTrue("...", focused != true)`, rest of the body unchanged | Spec AC-3 P0 guard row; plan Task 3 "Test" bullet |
| NB cramped host | Fixed: sized from a 113 dp plot target | Spec AC-2 P1 row; plan Task 3 table |
| NB M1/M2 | Fixed: marked `P0 manual` | Spec Traceability |
| NB `DSCTest:152` | Deferred to run `details-test-noop-asserts` (stage `idea`) | `/home/dev/Puber/.flow/runs/details-test-noop-asserts/state.md` |

### Fix list

None.
