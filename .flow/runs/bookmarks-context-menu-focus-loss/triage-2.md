# Review triage - round 2

Run: bookmarks-context-menu-focus-loss   Base: 8b116d22   Reviewed head: fac7ce19 (incremental 36a9fa50..fac7ce19); f918aedb after it removes two tests only

Sources: review-tests-2.md, review-codex-2.md (ran: review; clean). qa (review fix round 1): Favourites remove test green 3/3 on HEAD, red on base; Bookmarks remove test and Favourites inactive-gate test red on HEAD and on base (pre-existing) -> removed and deferred by owner decision (state.md).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `favoriteRemoveFromMenuFocusesTheNeighbourCard` | The test drops the card synchronously inside the click, not after the menu closes as the app does | Read the test: `onAction` filters `shown` on `ItemSavedChanged` | accept | round-2 minor; noted on the test-design row; listed for the owner |
| 2 | qa defect 1 | major (pre-existing) | Bookmarks remove path | Neighbour does not take focus after Remove from the menu; red on base too | qa-report "Review fix round 1" | defer | idea run bookmarks-remove-from-menu-focus |
| 3 | qa defect 2 | major (pre-existing) | `FavoriteScreenContent.kt` `rememberFocusRequesterOnLaunch` | Favourites takes focus on launch while content focus is inactive; red on base too | Read FocusOnLaunchRequester: it does not read `LocalContentFocusActive` | defer | idea run favourites-launch-focus-ignores-rail |

## Rejected codex claims

- none (codex clean)

## Next

- No more fixes in this run (caps reached); go to judge. Unproven lines: 1.
