# Design (architect): details-plot-adaptive-width

Brief: `brief.md`; facts and line map: `research.md` sections 3 and 6. All code paths below are in the worktree at base `b27d8e40`; `DSC` = `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

| Alternative | Reason |
|---|---|
| `rememberTextMeasurer` line counts | Duplicates the merged style and the border inset in a second place; the brief's top risk |
| `BoxWithConstraints` / `SubcomposeLayout` | Subcomposition per frame for a value a layout modifier already has |
| `remember(description)` width state | Can overflow when the budget later shrinks, turning the plot focusable |
| Option B (full half-share budget) | Moves facts and actions (brief Q2) |
| Option C (continuous width) | Ragged right edge between titles, more measuring |

## Risks

| # | Risk | Lives in | Guard |
|---|---|---|---|
| R1 | Focus: a narrowed plot overflows its box, becomes `focusable` (`DSC:344`) and takes UP from the actions | `PlotBlock` `layout {}` | D2 measures the drawn chain itself; D3 accepts a step only if its height fits; AC-4 instrumented test |
| R2 | Facts and action row shift because the box takes the narrower text's height | `PlotBlock` `layout {}`, column `DSC:214-224` | D4 fixed child height; AC-2 unit test; TV comparison against master |
| R3 | `Constraints` with min > max when the budget is under `PLOT_MIN_HEIGHT` + one line | `DSC:317-327` today | D6; `PlotWidthTest` row with budget < min height |
| R4 | Stand rule differs from Compose (stand `naturalH` at `index.src.html:919` omits the 2 dp border; Compose includes it), so the owner dials values that behave otherwise on the TV | `index.src.html:918-928` vs `PlotWidth.kt` | Stand implements D3 with the same min/clamp/border terms; AC-6 on the TV |
| R5 | Width steps once when cached content is replaced from the network (`DetailsVM.kt:272`) and the budget changes | `PlotBlock` layout | Accepted (brief Q3) |
| R6 | Main-thread cost: up to one text layout per step on each `PlotBlock` measure (text intrinsics cache one width) | `PlotBlock` layout | Overflowing plots stop after one query (D5); measure passes happen on content change only |
| R7 | New `PlotWidthKt` class absent from the shipped baseline profile until the next regeneration | `app/src/main/generated/baselineProfiles/baseline-prof.txt` | Prefix rule in `BaselineProfileContentTest.kt:22` still passes; see Q2 |

## Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | AC-4 names `HERO_DESCRIPTION` (`DetailsScreenContentTest.kt:30`, "Short plot synopsis."): a one-line text is narrower than any floor with or without the change, so the width assertion cannot fail | Change AC-4's fixture to a short multi-line plot that is at most 6 lines at the floor (e.g. `"Довольно длинное описание. ".repeat(8)`, about 3 lines at 590 dp) and assert its text node width <= floor; keep the UP-stays-on-actions assertion | no |
| Q2 | Regenerate baseline profiles in this run? | No: one small pure function; the next profile run picks it up | no |
