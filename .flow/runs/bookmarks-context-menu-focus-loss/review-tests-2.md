LENS: tests  ROUND: 2  BASE: 36a9fa50 (range 36a9fa50..fac7ce19)

### F1 [minor] AC-5 Remove tests drop the card in the same call as the click, not after the menu closes
- Where: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContentFocusTest.kt` (`favoriteRemoveFromMenuFocusesTheNeighbourCard`) and `app/src/androidTest/kotlin/com/kino/puber/ui/feature/bookmarks/component/BookmarksScreenContentFocusTest.kt` (`bookmarksRemoveFromMenuFocusesTheNeighbourCard`).
- Problem: The `onAction` lambda swaps the list synchronously inside `ItemSavedChanged`. The menu's `onDismiss` and the restorer's `onDialogClosed` run in the same click, before the next frame. In the app, `FavoriteVM` and `BookmarksVM` change the list asynchronously, after the API call. `test-design.md` row 30 describes "close, then the card drops", and that is not the order the tests produce.
- Failure scenario: A restorer bug that appears only when the card leaves the grid after the close completed would pass. The close would then target a still-present card. Both orderings are plausible, so this is a gap in a guard test, not a wrong result.
- Fix: Make the order explicit. In the lambda, set a flag and let the test apply the list change after `waitForIdle()`, or note the simplification in the test-design row. Cheap, optional.

Checked and sound, no finding:
- **Entry path:** both remove tests open the menu with the same long-Enter path as the other tests. They select Remove with real D-pad keys (DOWN, then CENTER). They wait on `remove.isFocusedNow()`, so the walk is bounded and deterministic.
- **Menu rows:** `MenuActionCount` matches the menu. Bookmarks has Watch, Mark watched and Remove, which needs 2 downs, and the count is 3. Favourites has Watch and Remove, which needs 1 down, and the count is 2.
- **`isSaved = true`:** this is needed for the Remove row to exist. `TvContextMenu.kt:212` shows the title only for a saved item, so the fixture change is required.
- **Fail condition:** both remove tests assert `Neighbour` is focused after removing `OpeningCard`. They fail if the fallback `requestFocus()` lands on the first card or on nothing, so they would catch the d938ea1e regression.
- **Split test:** `favoriteBackAfterItemChangeFocusesTheNeighbourCard` uses the real BACK key and asserts a specific card. It is correctly marked `guard` in `test-design.md`.
- **Inactive-gate test:** `favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive` would fail if the `contentFocusActive &&` gate were dropped. Cards would then request focus on launch.
- **Production diff:** it is comment and import-order only (rows 4 and 5).

### Unproven
- AC-5 tests (both screens): `test-design.md` says "run on base first" and record the result. The qa report was not in scope for this round, so no red or green run was confirmed. If either Remove test is red on HEAD, row 1's "fix the fallback" branch is still open.
