# Triage round 1: details-plot-adaptive-width

Reviewed head: b5e1c123 (base b27d8e40). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (clean), review-state-path-1.md (clean), review-ux-1.md. qa round 1: qa-report.md.
No finding against the app's layout logic (state-path, codex, antigravity agree). Findings are the stand, constants and test strength.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Stand plotSteps drops the floor: at 70/460 it offers 590/520 while the app ships 590/520/460 | ux F1, codex | major | fix | Add the floor as the last step when below the last step; rebuild index.html |
| 2 | Code steps are a hand-written list with a literal 70 dp; a different step/floor on the stand gives a different sequence | ux F2, edge F1 | minor | fix | PLOT_WIDTH_STEP = 70.dp; build PLOT_WIDTH_STEPS from PLOT_WIDTH/PLOT_WIDTH_STEP/PLOT_MIN_WIDTH with the stand's rule (step down while above the floor, then the floor) |
| 3 | Stand scroll check ignores the 2 dp border (boundary case differs from the app) | ux F4, codex | minor | fix | plotScrollable = plotLines * plotLine + 2 > snappedShare |
| 4 | Stand switch handler reconciles focus before render | codex | minor | fix | Render first, then focusChain() |
| 5 | Stand labels and short sample differ from the delta | ux F5 | nit | fix | Cheap: "фиксированная"/"короткое"; sample repeat(6) |
| 6 | Stand default and README present 70/460 as agreed though only start values | ux F3 | minor | fix | README: start values, not yet confirmed by the owner on the stand; default stays fixed until confirmed |
| 7 | Fact-line position test cannot fail (both plots sit on the 110 dp minimum) | blind F1 | minor | fix | Add a case with a plot taller than 110 dp that fits at 590 and narrows |
| 8 | Cramped-hero test does not assert it reached the below-minimum path | blind F2 | minor | fix | Assert the plot block height is at most PLOT_MIN_HEIGHT after the resize |
| 9 | Unit test recomputes expected heights with the production formula | blind F3 | nit | fix | Literal expected heights |
| 10 | AC-5: stand never opened in a browser (M1) | tests F1 | major | fix | qa round 2 opens the rebuilt stand headless if a browser can be had in the box; otherwise AC-5 goes to the owner as a manual check at gate 2 |
| 11 | AC-2 TV-vs-master and AC-6 short case not checked on the box | tests F2 | minor | accept | Owner manual checks at gate 2 (code side covered by unit and instrumented guards) |
| 12 | Intrinsic measures per remeasure; floor not clamped below 460 dp available width; minHeight above snapped budget | blind, edge unproven | - | reject | Spec R6 accepts the cost; only caller has ~949 dp and min height 0 |

Deferred: none (details-test-noop-asserts already exists for DSCTest:152). Accepted: #11 (owner checks).
