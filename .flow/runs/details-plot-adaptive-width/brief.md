# Brief: details-plot-adaptive-width

kind: improvement   size: M   tracks: ui   device check: yes (TV UI and focus; TV box 192.168.1.110:5555, `com.kino.puber.stage`, user-pace API use)

## Request

On the details hero, draw the plot narrower when it is short enough, so it overlaps the still less; long plots keep 590 dp and scroll. Owner text and the conductor's guards: `state.md` Log, 2026-11-26.

## Code

Findings and line map: research.md: 3 "Current state in code". The facts this brief relies on, checked at base `b27d8e40`:

| Item | Where |
|---|---|
| `PlotBlock` call, `weight(1F, fill = false)` between chips and the 1 : 1 spacers | `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt:214-224` |
| `PlotBlock`: `width(PLOT_WIDTH)`, `heightIn(min)`, line-snapping `layout {}`, `focusable(enabled = scrollable)`, 1 dp border padding, `Text(13 sp / 18 sp)` on `bodyLarge` | `DetailsScreenContent.kt:303-366` |
| Constants `PLOT_WIDTH` 590, `PLOT_TEXT_SIZE` 13 sp, `PLOT_LINE` 18 sp, `PLOT_MIN_HEIGHT` 110 dp | `DetailsScreenContent.kt:861-866` |
| Skeleton plot bars at `PLOT_WIDTH` | `DetailsScreenContent.kt:739-741` |
| Long-plot focus test | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt:172` |
| Design stand (`plotW` slider, `measureLines`, `SWITCHES`) | `docs/design/content/index.src.html:626, 691, 736, 843, 891-933`; research.md: 4 "Stand" |

Chosen approach: research.md: 6, option A. Inside `PlotBlock`'s layout, take the height the block has today at 590 dp (`max(PLOT_MIN_HEIGHT, lines590 x PLOT_LINE)` capped by the snapped budget), pick the narrowest step whose line count still fits that height, measure the child once at that width. The width choice lives in a pure function so a JVM test covers it.

## Why M

One screen, but it changes when the plot takes focus (TV focus, a risk area in `.flow/roles/analyst.md`) and holds one design decision (steps and floor) that the owner settles on the stand first (`designing-a-screen`, sliders need the owner).

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | Line counts per step for a plot whose lines at a narrower step fit the height it has at 590 dp | the width-choice function runs | it returns the narrowest such step, never below the floor (JVM unit test) |
| AC-2 | Any plot and budget | the width-choice function runs | the block height at the chosen width equals its height at 590 dp, so the fact lines and action row sit where they sit today (JVM unit test; on the TV, the facts and buttons of a short-plot title do not move against a master build) |
| AC-3 | A plot that overflows its block at 590 dp | details opens | the plot is 590 dp wide and UP from the actions focuses it; `DetailsScreenContentTest.kt:172` passes |
| AC-4 | A short plot (`HERO_DESCRIPTION`) | details opens | the plot text is no wider than the floor step, and UP from the actions leaves focus on the actions (new instrumented test, `make DEVICE=<serial> itest`) |
| AC-5 | The stand `docs/design/content` | the owner opens it | it has a fixed/adaptive plot-width switch, step and floor controls, and a short and a long sample plot; the README "Agreed sizes" table and the Compose constants hold the values the owner set |
| AC-6 | Short, medium and long plot titles on the TV box | each details screen is opened by D-pad | the narrowed plots read well and sit clear of the brighter part of the still (owner judges) |

## Risks

| Risk | Guard |
|---|---|
| Measurer style differs from the `Text` style (`bodyLarge` merged with 13/18 sp, width minus 2 x `PLOT_FOCUS_BORDER`), line count off by one, a short plot overflows and turns focusable | Measure with the merged `LocalTextStyle` at the inner width, or use `maxIntrinsicHeight`; AC-4 catches overflow |
| `heightIn(min = 110 dp)` before the snapping `layout {}`: at a 110-125 dp budget the snapped max (6 lines, 108 dp) may fall below `minHeight` and throw (unverified, research.md: 7) | Clamp `snapped` to `constraints.minHeight` when the chain is rewritten |
| Content rebuilt from cache, then network (`DetailsVM.kt:272`); a changed budget can step the width once | Accepted (Q3); revisit if seen on the TV |

## Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Which steps and floor? | Start the stand at 590 / 520 / 460 dp, floor 460; the owner sets the final values on the stand | no |
| Q2 | Fit criterion: the height the plot has today (option A) or the full half-share budget (option B, can push the facts down)? | Today's height (A) | yes |
| Q3 | Skeleton, budget changes and stand: keep the skeleton bars at 590; recompute the width in layout with no state; add the stand switch, step/floor sliders and short/long sample plots? | Yes to all three | no |
