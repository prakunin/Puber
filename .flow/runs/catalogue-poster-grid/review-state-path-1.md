LENS: state-path  ROUND: 1  BASE: ad2c7f0c

### F1 [major] Show all has no dialog focus restorer, so closing the context menu sends focus to the rail (this predates the change)
- Where: ShowAllScreenContent.kt:132-140 (onContextMenu = { contextMenuItem = item }, VideoItemContextMenuDialog with no LocalTvDialogFocusRestorer)
- Problem: when TvDialogOverlay closes, nothing returns focus to the opening card; focus reaches DrawerSheet, whose onFocusChanged reveals the rail.
- Cause: not the card swap; base has no LocalTvDialogFocusRestorer either; the same symptom on Bookmarks was fixed by a81ae13d, Favourites by 0cf82d0a.
- Failure scenario: Show all, long-press a card, Back: rail expanded and focused; a second Back or Right restores the card.
- Fix: as FavoriteScreenContent.kt:70-96: TvDialogFocusRestorer (saveFocusedChild on opening; restoreFocusedChild or restoreFocus/mainContentFocus on close), provide LocalTvDialogFocusRestorer, gate LocalContentFocusActive with contextMenuItem == null, add a test like favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard.

Trace at ad2c7f0c and f68f3524: long select -> null restorer -> contextMenuItem set -> menu focus -> Back -> overlay leaves -> null onDialogClosed -> focus to DrawerSheet -> reveal(). Rest of change: no VM or action handling touched; no undoing writes found.
