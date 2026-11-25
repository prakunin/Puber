# Review triage - round 4

Run: catalogue-filters-persist   Base: ef5987ec   Reviewed head: d6ec6134 (incremental b0cee092..d6ec6134)

Sources: review-edge-4.md, review-tests-4.md, review-codex-4.md (ran: review, adversarial), review-antigravity-4.md (ran, clean), review-state-path-4.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | blocker | `ShowAllRailCloseFocusTest.kt:220,228,246` | New tests address the header «Сброс» (`[0]`), not the empty-state one | Read: header draws «Сброс» first in tree order; ShowAllFilterFocusTest uses `[1]` after asserting 2 nodes | fix | use the empty-state node |
| 2 | edge F1, codex, tests F2, state-path F1 | major | `ShowAllRailCloseFocusTest.kt:233-248` | Arriving-tab test calls `beginHandoff` while Closed; refused; tests nothing | Read `ModalNavigationDrawer.kt:164` `Closed -> null` | fix | open the rail, assert non-null id, compose Empty during the pending request |
| 3 | tests F3 | minor | plan:262-265 | No red runs recorded | - | fix (verification) | qa-device records red under each mutation and green on the box |
| 4 | codex | minor | `ShowAllScreenContent.kt:191-200` | Empty first composed on the outgoing tab during the pick window is treated as arriving and grabs «Сброс» briefly | state-path-4 traced it: transient, tab disposed on swap, arriving effect retries; same rule as `ContentFocusHandoffEffect` | accept | narrow window, transient, a known limit of the shared born-during-request pattern; listed for the owner |

## Rejected codex claims

- "medium" severity on item 4 - re-graded minor; not verified on device - process remark.

## Next

- Fix round 4 (items 1, 2; test-only) -> compile androidTest -> incremental review round 5 (tests, edge, codex, antigravity) -> qa-device: red/green of all new focus tests on the box, check.full, M-checks on the final APK; unproven lines: 1
