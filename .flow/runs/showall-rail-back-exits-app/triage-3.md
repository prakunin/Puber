# Triage round 3: showall-rail-back-exits-app

Reviewed incrementally: 773b60f2..3a536877 (B2 test seam df0c3447, B3 click guard 15f0f3a5). Lenses: edge, tests, codex (ran, clean), antigravity (ran, clean), state-path. qa round 6 at 3a536877 (evidence 1c82598a): all classes green; mutation B2 killed at MainRailBackTest.kt:174, mutation B3 killed at :203; rail picks on Home and Movies PASS.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| C1 | tests F2 | major | New/changed P0 instrumented assertions never ran | resolved | qa round 6: green at HEAD, B2 and B3 mutations killed |
| C2 | tests F1, edge F1, codex P2 (rejected there) | major/minor | B3 test does not prove Down reached the unselected item | fix (cheap) | The B3 mutation kill shows Center did land on another tab in this run, so the test is not blind today; still cheap to pin with isNotSelected() after Down so it cannot silently go blind |
| C3 | state-path F1 | minor | The isOpen guard drops a corrective tab pick during a tab-pick handoff (new tab still loading) | fix | Regression from the last fix round (B3); base allowed it. Allow the click during HandingOff when the handoff expects new content (tab pick), block it only during a Back/Right handoff and in Closed; test for the corrective pick |
| C4 | edge unproven, tests unproven | — | Fixed 2.5 s sleep vs 5 s budget; test clock vs real time | reject | Unproven; green on the box in rounds 4-6 |

Fix round: C2, C3 to the implementer; then incremental review round 4 (edge, tests, codex, antigravity, state-path) and qa round 7.
