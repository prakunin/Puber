# Review triage - round 1

Run: bookmarks-context-menu-focus-loss   Base: 8b116d22   Reviewed head: 36a9fa50

Sources: review-blind-1.md, review-tests-1.md, review-codex-1.md (ran: review; clean). Size S: no edge lens.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, blind F1 | major | qa-report AC-5; `FavoriteScreenContent.kt:73-86` | AC-5 (Remove from the menu) has no test and no manual run, and d938ea1e changed that close path; the restorer fallback `mainContentFocus.requestFocus()` goes to the Column's first focusable, not the opener or neighbour | Read the restorer: save/restore on the outer Column, fallback to the Column. The removal case is exactly when `restoreFocusedChild()` can fail | fix | instrumented tests `favoriteRemoveFromMenuFocusesTheNeighbourCard` and `bookmarksRemoveFromMenuFocusesTheNeighbourCard` (no account mutation: Compose state drops the card); if the Favourites one is red, fix the fallback to target the grid card |
| 2 | tests F2, blind F3 | minor | `FavoriteScreenContentFocusTest.kt` | No Favourites test that the outer `LocalContentFocusActive=false` still gates focus (AC-6 says either tab) | Read the test file: only two tests | fix (cheap) | add `favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive` |
| 3 | blind F3 | nit | `FavoriteScreenContentFocusTest.kt:42-60` | One test covers both the open-menu and the Back assertions | Read the test | fix (cheap) | split out `favoriteBackAfterItemChangeFocusesTheNeighbourCard` |
| 4 | blind F2 | minor | `FavoriteScreenContent.kt:90-122`, `BookmarksScreenContent.kt` | The dialog sits inside the provider, so it sees `LocalContentFocusActive=false` | Needed: the Favourites dialog must read `LocalTvDialogFocusRestorer` from the same provider; no dialog code reads the content-focus local today | fix (cheap, comment) | one comment stating the dialog must not read `LocalContentFocusActive` |
| 5 | blind F4 | nit | `FavoriteScreenContent.kt:22-24` | import order | read the file | fix (cheap) | reorder |

## Rejected codex claims

- none (codex clean)

## Next

- Fix round 1: implementer (rows 1-5), test-designer adds the rows, qa-device runs the two classes on HEAD (new tests) and the AC-5 test on base production files; then incremental review (blind? no: S runs tests + codex on the increment). Unproven lines: 2.
