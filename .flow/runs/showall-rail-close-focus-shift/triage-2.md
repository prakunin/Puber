# Review triage - round 2

Run: showall-rail-close-focus-shift   Base: 6b087697   Reviewed head: e9595021 (incremental 97be2417..e9595021, tests only)

Sources: review-tests-2.md. Incremental round on a tests-only diff: no production change, so lens edge and codex are not re-run (codex was clean in round 1 on the same production code). qa round 2: check.full pass, ShowAllRailCloseFocusTest 5/5 and ShowAllScreenContentFocusTest 5/5 on emulator-5554.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | `ShowAllRailCloseFocusTest.kt` T-I4 | T-I4 cannot fail on a re-fire: the remembered card follows focus (`onCardFocused`), so a re-fired attempt targets the card already focused | Read `GridFocusMemory.onCardFocused` / `onHandoff` (`ShowAllScreenContent.kt:204-241`): any in-grid move rewrites `lastFocusedId`, so inside the grid no stale target can exist; the only stale case is focus outside the grid, which T-I3 covers | fix | relabel T-I4 in a header comment as a smoke check of the `inGrid` path; the latch is covered by T-I3 (focus outside the grid) and unit T-U4. Comment only |
| 2 | tests F2 | minor | `ShowAllRailCloseFocusTest.kt` T-I2 | T-I2 passes on base and is not labelled as a guard | Read test-design R4: T-I2 is a regression guard | fix (cheap) | one-line guard comment |

## Rejected codex claims

- none (codex not re-run, see Sources)

## Next

- Fix round 2: comments only in the test file; nothing to re-run per change class (comments). Then judge. Unproven lines: 1 (T-I1b scrolled row; qa run green, row position not recorded).
