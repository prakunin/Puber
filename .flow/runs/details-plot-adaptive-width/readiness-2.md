# Readiness 2: details-plot-adaptive-width

Verdict: **READY**

Spec and plan read in `/home/dev/orca/workspaces/Puber/details-plot-adaptive-width` at `0656e986` (base `b27d8e40`; the diff from base holds only the spec and plan).

## Checks

| Check | Result |
|---|---|
| AC coverage | AC-1 Task 1; AC-2 Tasks 1 and 3; AC-3 Tasks 1 and 3; AC-4 Task 3; AC-5 Tasks 2 and 3 + M1; AC-6 M2. Each has a Test design row |
| Citations (sampled at base) | Resolve: `DSC:303` `PlotBlock`, `DSC:316-317` `.width(PLOT_WIDTH)` / `.heightIn(min = PLOT_MIN_HEIGHT)`, `DSC:320-329` `.layout {}`, `DSC:344` `focusable(enabled = scrollable, ...)`, `DSC:356` `padding(PLOT_FOCUS_BORDER)`, `DSC:214-217` call site, `DSC:741` skeleton bars, `DSC:861-866` constants, `DSC:871-875` `HERO_LINE_WIDTH` KDoc, `DSCTest:30/47/172/192/195/204`, `stand:626/691-692/736-744/843/892/918-919`, README `:27`/`:53`, `DetailsVM.kt:271-272` |
| Build stays green, order | T1 (pure `internal` function) -> T2 (stand only) -> owner checkpoint (non-blocking) -> T3; no task leaves the build red |
| P0 rows | Six unit rows `red`, each naming its mutation; the Task 1 stub `PlotFit(stepsPx.first(), 0)` fails all six. AC-3 `aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions` `guard`; AC-4 `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` `red`; M1/M2 `manual` |
| Out of scope | Respected: skeleton, `MediaScrim`, `MEDIA_WIDTH_FRACTION`, `HERO_LINE_WIDTH` value, profiles untouched |
| Blocking open questions | Q2, Q4 answered; Q1, Q3, Q5, Q6 answered |

## Round 1 fix list

| # | Status | Where |
|---|---|---|
| 1 | Fixed: AC-4 width check and the AC-2 tops check use `org.junit.Assert.assertTrue` / `assertEquals` (0.5 dp) | Spec Test design rows AC-2, AC-4 and the Paths line; plan Task 3 table |
| 2 | Fixed: `DSCTest:192` Kotlin `assert` becomes `assertTrue("...", focused != true)`, rest of the body unchanged | Spec AC-3 P0 guard row; plan Task 3 "Test" bullet |
| NB cramped host | Fixed: sized from a 113 dp plot target | Spec AC-2 P1 row; plan Task 3 table |
| NB M1/M2 | Fixed: marked `P0 manual` | Spec Traceability |
| NB `DSCTest:152` | Deferred to run `details-test-noop-asserts` (stage `idea`) | `/home/dev/Puber/.flow/runs/details-test-noop-asserts/state.md` |

## Fix list

None.
