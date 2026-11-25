# Triage round 2: showall-rail-back-exits-app

Reviewed incrementally: c6b25f49..773b60f2. Lenses: edge (no findings), tests, codex (ran), antigravity (ran, clean), state-path. qa round 4 at 773b60f2 (no defects; A1a/A1b/A2/A4/A6 mutations killed) and round 5 mutations at 4b0b1a4a.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| B1 | tests F1 | major | The two new P0 instrumented tests (A5, A6) had no red run and no device run | resolved | qa round 4: both green at HEAD; round 5: mutation (a) closeOnFail={false} kills the A5 test at MainRailBackTest.kt:166, mutation (c) kills the A6 test at :141 — equivalent red runs on the old behaviour |
| B2 | qa round 5 mutation (b) | major | Removing the A5 rail focus clear (clearFocus(force = true)) leaves every test green: either the line is dead in practice or assertNoRailItemFocused is blind to it | fix | A production line no test proves. Implementer determines which: if the clear is not needed (focus already leaves the rail), remove it and correct the design; if it is needed, make the test assert a rail item is focused at the moment the rail closes and nothing after the clear |
| B3 | codex minor, state-path F1 | minor | During HandingOff (and after a failed close) a collapsed rail item can hold focus; Center picks a tab the user cannot see | fix | Confirmed by both; part of the same concern as B2, and cheap: guard MainSideMenuItem.onClick on drawerState.currentValue == Open, with a test (Center on a collapsed rail does not switch the tab). Pre-existing for Right-then-Center, but this run widens the window |
| B4 | tests F2, state-path F2 | nit | M11 step 5 expectation contradicted A5 | resolved | Corrected in 6724d512 (plan, spec, test design) |
| B5 | tests/edge unproven | — | First-launch/dialog handoff failing over a pushed screen untested; canPop changing during HandingOff; Left after the failed close untested; re-picking the active tab disarms | reject | Unproven per the reviewers' own conditions; recovery paths traced as working (edge, state-path) |

Fix round: B2, B3 to the implementer; then incremental review round 3 (edge, tests, codex, antigravity, state-path) and qa round 6 (MainRailBackTest + the B2 mutation again + a device Center check).
