# Implementation record: bookmarks-context-menu-focus-loss

Base 8b116d22. Worktree branch prakunin/bookmarks-context-menu-focus-loss.

## Commits

| sha | subject |
|---|---|
| f80b9839 | Add focus tests for the context menu over Bookmarks and Favourites |
| 90ade1df | Park card focus requests in Bookmarks while its context menu is open |
| fd8f221e | Park card focus requests in Favourites while its context menu is open |

## Checks

- `:app:compileDevDebugKotlin :app:compileDevDebugAndroidTestKotlin`: green on the tests-only commit (production unchanged) and after both fixes.
- check.full (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`): BUILD SUCCESSFUL in 8m 38s, 44 actionable tasks.

## Red run (instrumented, not run by the implementer: no device)

| Test | Expected on base |
|---|---|
| `BookmarksScreenContentFocusTest.bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | red: neighbour card focused, menu action not |
| `FavoriteScreenContentFocusTest.favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | red: same |
| `BookmarksScreenContentFocusTest.bookmarksBackAfterItemChangeFocusesTheNeighbourCard` | guard, green on base |
| `BookmarksScreenContentFocusTest.bookmarksMenuOpenAndBackReturnsFocusToTheOpeningCard` | guard (qa: if red on base, reclassify) |
| `BookmarksScreenContentFocusTest.bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive` | guard |
| `FavoriteScreenContentFocusTest.favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` | guard, may be red on base; qa to record |

## Deviations

- Tests were cloned from `CollectionDetailScreenContentFocusTest`; cards set `showTitle = true` so the title text is on the merged node.
- Bookmarks opens the menu on "Card 5" (middle of a 3x3 grid), neighbour "Card 6". Favourites uses two rows of four, menu on "Card 12" (second row), neighbour "Card 13"; `selectedItem` is `VideoDetailsUIState.Loading.copy(isLoading = false)`.
- AC-3's Back assertion sits in the same Favourites test as the focus-in-menu assertion, as the test design says.
- The Bookmarks diff is large because the wrapped body is re-indented; `git diff -w` shows the real change.

## For reviewers

- Closing the Favourites menu has no `TvDialogFocusRestorer`: AC-4 on Favourites relies on the helper's entry effect aiming at the opening card.
- Tests unproven on a device; the AC-6 test asserts no card is focused with outer `false`.

## Fix round 1

| Finding | Result |
|---|---|
| qa defect 1: Back from the Favourites menu leaves no card focused (AC-4) | fixed, see commit below |

- Cause: Favourites provided no `TvDialogFocusRestorer`, so closing the menu fell back to the grid's first card, or to the rail.
- Change, `FavoriteScreenContent.kt` only: a restorer on the existing `mainContentFocus` requester (the Column around the details and the grid). It saves the focused child when the menu opens and restores it on close, falling back to `requestFocus()` on the Column. `VideoItemContextMenuDialog` moved inside the `CompositionLocalProvider`, so the overlay sees `LocalTvDialogFocusRestorer`. `VideoGrid` is unchanged.
- Check: `compileDevDebugKotlin`, `compileDevDebugAndroidTestKotlin`, `testDevDebugUnitTest`, `detektAll`, `lintDevDebug`: BUILD SUCCESSFUL in 6m 39s.
- Unproven on a device: `favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` is for qa-device. Reviewers: `saveFocusedChild` on an ancestor of the nested LazyColumn/LazyRow must restore the deepest card, not the row.

## Review fix round 1

| Triage row | Result |
|---|---|
| 1 AC-5 untested | added `favoriteRemoveFromMenuFocusesTheNeighbourCard` and `bookmarksRemoveFromMenuFocusesTheNeighbourCard` |
| 2 Favourites outer-inactive gate | added `favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive` |
| 3 split Back assertion | `favoriteBackAfterItemChangeFocusesTheNeighbourCard` split out |
| 4 dialog inside provider | one comment in both screens |
| 5 import order | fixed in `FavoriteScreenContent.kt` |

- Remove tests: cards are `isSaved = true`; the menu opens by long press, D-pad DOWN walks to "Remove from bookmarks" (`context_menu_remove_from_bookmarks`), DPAD_CENTER selects it. The test's `onAction` drops the card from its own Compose state on `CommonAction.ItemSavedChanged`, as the ViewModel would. No direct `requestFocus` on a card in Favourites; Bookmarks keeps its existing `requestFocus` entry.
- Test cards now carry `isSaved = true`; the existing tests are unaffected (first action is still "Watch movie").
- Production fallback (`mainContentFocus.requestFocus()`) left unchanged. I cannot see from the code that the Favourites remove test fails: after the card leaves, `restoreFocusedChild()` may fail and the fallback may hit the first focusable, or the grid reconciler may run after it. Unproven. If `favoriteRemoveFromMenuFocusesTheNeighbourCard` is red on the device, round 2 aims the fallback at the grid card.
- Check: `compileDevDebugKotlin`, `compileDevDebugAndroidTestKotlin`, `detektAll`: BUILD SUCCESSFUL (detekt reports compiler-error noise, as before).
- Removed after the verify cap: `bookmarksRemoveFromMenuFocusesTheNeighbourCard` and `favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive`. Both are red on base and on HEAD (pre-existing defects); deferred to the idea runs `bookmarks-remove-from-menu-focus` and `favourites-launch-focus-ignores-rail`. The Bookmarks `chooseRemoveFromMenu` and `MenuActionCount` went with the first; Favourites keeps its own copy for `favoriteRemoveFromMenuFocusesTheNeighbourCard`.
- Reviewers: the `MenuActionCount` loops (2 in Favourites, 3 in Bookmarks) assume the action order watch, [watched], saved.

## Squash

Tree before and after: 12dcc83021cb7f8f37cde12f29249dddb672429d (identical).

| New | Subject | Old |
|---|---|---|
| a81ae13d | Keep focus on the opening card when the Bookmarks context menu closes | 90ade1df, f80b9839 (Bookmarks part), fac7ce19, f918aedb (Bookmarks parts) |
| 0cf82d0a | Keep focus on the opening card when the Favourites context menu closes | fd8f221e, 25b00f7e, d938ea1e, f80b9839 (Favourites part), fac7ce19, f918aedb (Favourites parts) |
| 437cf03d | Record the device evidence for the Bookmarks and Favourites context menu focus fix | 36a9fa50 |
