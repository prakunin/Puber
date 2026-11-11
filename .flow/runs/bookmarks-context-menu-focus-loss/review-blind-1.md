LENS: blind  ROUND: 1  BASE: 8b116d22

### F1 [minor] Favorites restorer fallback can send focus to the first card or to the details pane
- Where: app/src/main/java/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContent.kt:75-86
- Problem: `onDialogClosed` calls `mainContentFocus.restoreFocusedChild()`, and on failure `mainContentFocus.requestFocus()`. `mainContentFocus` is on the outer `Column`, which also holds `VideoItemGridDetails`. `requestFocus` on that Column moves focus to its first focusable descendant, not to the opening card. `VideoGrid` already saves and restores its own row focus (`VideoGridUIState.kt:487`), so two mechanisms now act on the same close.
- Failure scenario: A menu action removes the focused card, so the saved child is gone. `restoreFocusedChild()` returns false and focus goes to whatever the Column picks first, not the neighbour the tests expect. Only the case where the card is removed while the menu is open is covered, and only on one fixture shape.
- Fix: Restore through the grid's own requester, or keep the fallback to the grid's target item as Bookmarks does with `itemFocus.focusRequester`. Add a test where the opening card is the only item in its row.

### F2 [minor] The whole dialog subtree sees `LocalContentFocusActive = false`
- Where: FavoriteScreenContent.kt:90-122 and BookmarksScreenContent.kt:76-170
- Problem: `VideoItemContextMenuDialog` is composed inside the `CompositionLocalProvider` whose `LocalContentFocusActive` is false while the menu is open. `LocalContentFocusActive` is a `staticCompositionLocalOf`, so flipping it recomposes the entire subtree: the grid and the dialog on every open and close. Anything in the dialog that reads the local, now or later, is parked while the menu is open.
- Failure scenario: A menu action that reads `LocalContentFocusActive` (for example a focus-on-launch helper inside the dialog) does not request focus. The new tests do show the first action gets focus today, so this is a latent coupling and not a current bug.
- Fix: Compose the dialog outside the provider, or leave a comment saying the dialog must not read the local.

### F3 [nit] Favorites test checks two behaviours in one method
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContentFocusTest.kt:42-60
- Problem: `favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` also presses Back and asserts focus lands on the neighbour card. Bookmarks splits these into two tests. A failure in the second half is reported under the first name. Favorites also has no test for `LocalContentFocusActive = false`, which Bookmarks has.
- Fix: Split the test as in Bookmarks and add the inactive-focus case.

### F4 [nit] Import order
- Where: FavoriteScreenContent.kt:22-24
- Problem: `PuberTheme` is placed before the `LocalTvDialogFocusRestorer` and `TvDialogFocusRestorer` imports, so the block is no longer alphabetical.

### Unproven
- BookmarksScreenContent.kt `onDialogClosed` runs the fallback `requestFocus` before the `contextMenuItem = null` recomposition. If the card request in `rememberReconciledItemFocus` is still parked at that moment, something must re-issue it. Evidence needed: a device run on a folder where the last item is removed through the menu.
