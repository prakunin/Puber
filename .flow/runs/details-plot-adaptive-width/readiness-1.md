# Readiness 1: details-plot-adaptive-width

Verdict: **NOT READY**

Spec and plan read in `/home/dev/orca/workspaces/Puber/details-plot-adaptive-width` at `d1fa0402` (plan untracked).

## Checks

| Check | Result |
|---|---|
| AC coverage | AC-1 Task 1; AC-2 Tasks 1 and 3; AC-3 Tasks 1 and 3; AC-4 Task 3; AC-5 Tasks 2 and 3 + M1; AC-6 M2. Each has a Test design row |
| Citations (sampled) | All resolve at base: `DSC:303` `PlotBlock`, `DSC:316-317` `.width(PLOT_WIDTH)` / `.heightIn(min = PLOT_MIN_HEIGHT)`, `DSC:320` `.layout {`, `DSC:344` `focusable(enabled = scrollable, ...)`, `DSC:861` `PLOT_WIDTH`, `DSC:741` skeleton bars, `DSCTest:30/47/172/195/204`, `stand:626/691/736/843/892/918-919`, README `:27`/`:53`, `BaselineProfileContentTest.kt:19-22`, `vm/DetailsVM.kt:271-272` |
| Build stays green, order | T1 (pure function, unused `internal`) -> T2 (stand only) -> owner checkpoint -> T3; no task leaves the build red |
| Out of scope | Respected: skeleton, `MediaScrim`, `MEDIA_WIDTH_FRACTION`, `HERO_LINE_WIDTH` value, profiles untouched |
| Blocking open questions | Q2, Q4 answered; Q1, Q3, Q5, Q6 answered |
| P0 rows | Unit rows name their mutation and are `red`; instrumented P0 rows fail fix 1 and 2 below |

## Fix list

1. **AC-4 P0 red test cannot fail.** Plan Task 3 tells `aShortPlotNarrowsToTheFloorAndLeavesFocusOnTheActions` to use "plain `assert` with the width in the message". Kotlin `assert` is gated by `desiredAssertionStatus()`, which is always false on ART; nothing in `app/build.gradle*` or `Makefile` enables assertions. The width check is a no-op on the TV box, so the red run cannot be red. Use `org.junit.Assert.assertTrue` (already used by `CachedPayloadDaoTest.kt:10`). Apply the same to the "tops equal" check in `aShortPlotLeavesTheFactLinesWhereAOneLinePlotLeavesThem`, which names no assertion (`assertEquals` with a 0.5 dp tolerance).
2. **AC-3 P0 guard is vacuous.** `DSCTest:192` (`aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions`) checks focus with Kotlin `assert(focused != true)`, the same no-op, so "`:172` passes" (AC-3, spec Tests row "`:172` itself unchanged") proves nothing. Amend the spec row and plan Task 3 to replace that line with a JUnit assertion. Leave the test body as it is.

## Non-blocking

- `aPlotInACrampedHeroLaysOutWithoutCrashing`: `boundsInRoot` of the scrolled text is clipped to the snapped share, so the computed host shrink lands the raw share in 117-135 dp, not 110-125. Size from a target such as 113 dp, or assert the share after resizing.
- Test design rows M1/M2 are P0 with no `red`/`guard` mark. They are manual by nature (AC-6 is UNTESTABLE), so mark them `manual`.
- `DSCTest:152` uses the same no-op `assert`. That test is outside this run, so file a run for it.
