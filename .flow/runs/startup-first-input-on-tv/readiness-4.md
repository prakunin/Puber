# Readiness 4: startup-first-input-on-tv

Verdict: **READY**

Base c506d0bd; phase A f48beb86; spec and plan at f368c8b4 (worktree clean); test design round 2 (`.flow/runs/startup-first-input-on-tv/test-design.md`).

## Readiness-3 fix list

| # | Status | Evidence |
|---|---|---|
| 1 | Resolved | Spec `## Test design` at `:159` (Risk profile `:165`, Traceability `:184`, Bug `:250`, Manual checks `:254`, Perf `:265`). `diff` against test-design.md differs only in heading levels and one added line linking the baseline (5fb5de17). Plan Global constraints and Task 10 references now resolve |
| 2 | Resolved | AC-9 in spec `:40` and brief `:43` states "unchanged since `f48beb86`"; spec AC-9 row `:246`, plan `:16` and Task 9 Done when use the same reference |

## Regression checks

| Check | Result |
|---|---|
| Code unchanged since round 3 | `git diff --stat f48beb86 HEAD -- app` empty; f368c8b4 touches docs only |
| Citations re-sampled at HEAD | `StartupMarks.kt:9-18` (constructor), `RevealGate.kt:54-55` (`markedIn`), `KeyProbe.kt:114-122` (`answer`), `TelemetryRuntime.kt:163` (`StartupMarks(...)`), `TelemetrySink.kt:128-130` (`schedule`), `StartupMarksTest.kt:44`, `:142` resolve |
| Round-2 rows to plan tasks | Every new test name of spec Round 2 appears in Tasks 7-9; the AC-1 P1 guard names not in the plan (`lineWaitsForRevealedAndCarriesBothMarks`, `fallbackLineHasNoRevealedWhenItNeverCame`, `fallbackLineAfterSixtySecondsWithMarksSoFar`, `revealedKeepsTheFirstValue`, `appComposedKeepsTheFirstValue`) exist in `StartupMarksTest.kt` and are covered by Task 7 "other guards stay as they are" |
| Build order, P0 rows, scope | Unchanged from readiness-3 Checks; still pass |

## Note

- Spec AC delta row `:88` still lists the frozen classes as "unchanged" without the `f48beb86` reference; AC-9 `:40` governs.
