# Triage round 2: details-plot-adaptive-width

Reviewed: b5e1c123..15d0117c. Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran), review-antigravity-2.md (clean).
Round 2 rule: fix blockers and majors only.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Cramped-hero assertion can be red depending on layout slack (113 dp target lands on a 126 dp snapped block) | edge F1 | major | fix | CRAMPED_PLOT_TARGET_DP = 108f; update the comment |
| 2 | Tall-plot fact-line test may find no qualifying text on the box (real budget <= 126 dp) and has never run | tests F1, edge F2 | major | fix | Host DetailsScreenContent in a taller fixed-height Box so the budget is >= 8 lines; fail with a message naming the budget if still unmet; note in the plan that this case does not occur on a 540 dp hero |
| 3 | Stand marks a plot scrollable by the 2 dp border with maxScroll 0 | codex | minor | accept | Stand-only preview at one boundary; app is correct. Listed |
| 4 | Fact-line test does not tie the block height to the 590 dp height | codex, tests F2 | minor | accept | Covered by choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase. Listed (implementer may add the 588 dp measure while touching the test) |

Deferred: none. Accepted: #3, #4.
