LENS: tests  ROUND: 2  BASE: cfd0a1f3

### F1 [minor] Closing the menu without an item change is not tested, so the opener-card refocus can break unnoticed
- Where: `/home/user1/orca/workspaces/Puber/grid-focus-restore-after-back/app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt:89-93`; the path at `/home/user1/orca/workspaces/Puber/grid-focus-restore-after-back/app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:234-239`
- Problem: the new assertion only covers closing the menu after a card was removed. On that path two requests fire on close: the entry request (`LaunchedEffect(isTargetRow, contentFocusActive)`) and the request parked when the card was removed (`LaunchedEffect(pendingFocus.value, contentFocusActive)`). Both point at the same requester. On the common path (open the menu, press BACK, list unchanged) nothing is parked, and only the entry request refocuses the opener.
- Failure scenario: a later change drops `contentFocusActive` from the entry effect's keys, or makes `rowHasFocusRef[0]` survive while the menu is open. After BACK on an unchanged list no card is focused. The extended test stays green, because the parked request still focuses the neighbour. Only Manual 7 would catch it.
- Fix: add a sibling test, roughly 10 lines with the same helpers. Open the menu with a long press, wait until the first action is focused, release, then `sendKeyDownUpSync(KEYCODE_BACK)` with no change to `shown`, then `waitUntil` the opener `title` is focused and `assertIsFocused()`.

Answer to the brief's question: yes. If D16 broke on the path the test drives, the extended assertion would fail:
- **Override removed** (`contentFocusActive && contextMenuItem == null` back to `contentFocusActive`): the parked request fires while the menu is open. The overlay (`TvDialogOverlay`) is an in-tree `focusGroup`, not a separate window, so the card takes focus. The existing "no card should hold focus" assertion (lines 84-87) fails before BACK is sent.
- **Close no longer issues the requests** (override never flips back, or both effects lose their `contentFocusActive` key): the overlay is disposed while it holds focus, nothing requests the grid, and `focusRestorer` only acts when focus enters the grid. `waitUntil` at line 92 times out.
- **Wrong card**: the neighbour (`items[index + 1]`) matches `resolveFocusedItemId` (`updatedItems.getOrNull(removedIndex)`, `FocusTargetResolver.kt:20-23`). `focusAwayFromTheFirstCard` lands on card 5 of 24, so index + 1 is always in bounds and on screen. Focus on any other card fails.

Other checks:
- BACK goes through the activity's `OnBackPressedDispatcher` into the overlay's `BackHandler`. In the test, `LocalTvDialogFocusRestorer` is null, so no other restorer can make the assertion pass.
- The change is tests and the plan only. No assertions were removed, and no new P0 test needs a red run (D16 is not an AC; the extension is a guard).
- Suite not run, as the brief says: qa-device runs `CollectionDetailScreenContentFocusTest` on HEAD. The Implementation record also says the implementer did not run it, so whether it is green is still open until qa-device reports.
