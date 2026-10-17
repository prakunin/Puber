LENS: tests  ROUND: 2  BASE: f14dcc84

Suite state: I ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:compileDevDebugAndroidTestKotlin` at HEAD b5919b85 and it passed (BUILD SUCCESSFUL; 53 of 54 tasks were up to date). I did not run the five `ShowAllScreenContentFocusTest` cases because that needs a device lease. They have never run on a device, so neither the green state nor the red half of fix #3 is recorded.

Fix #3 mostly holds:
- **`showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry`:** five Down presses in a 3-column grid put focus on index 15. After restore, `rememberFocusRequesterOnLaunch` comes back as already requested. If the `restoreOnEntry` effect is removed, nothing takes focus and `waitUntil` times out. If the restore lands on the wrong item, the title check fails. So this case can fail.
- **`showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeavesTheList`:** the card at index 3 is removed while the grid is composed. Compose drops focus when a focused node is removed, so without `restoreWhenFocusedCardLeft` the neighbour "Card 5" never gets focus and the case fails. If the `inGrid` guard is reverted to a plain `hasFocus` flag, the case also fails.

### F1 [minor] The "left the list before re-entry" case actually runs the live-grid path, so the entry fallback in `resolveRestoreId` has no test that can fail
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:87-110; ShowAllScreenContent.kt (`GridFocusMemory.restoreId` initializer)
- Problem: `shown` is a `mutableStateOf`, and it is changed while the grid is still composed. `emulateSavedInstanceStateRestore` runs on idle, so the recomposition and the `LaunchedEffect(items)` → `restoreWhenFocusedCardLeft` path run first. That path moves focus to the neighbour, and `onCardFocused` saves the neighbour's id before the state is saved. On re-entry the saved id is already in the list, so the `else -> items[index.coerceIn(...)]` fallback is never used on entry. This case is a slower copy of the live-list case.
- Failure scenario: the `restoreId` initializer is changed to `lastFocusedId.value` with no fallback, which breaks the real case: the hide-watched reload happens while Details covers the grid, then Back. Both neighbour cases stay green.
- Fix: change the list without recomposing the live grid. Make `shown` a plain `var` and read it in `setContent`, so the old composition does not recompose and only the restored one sees the new list. Then assert the neighbour is focused.

### F2 [minor] Fix #1 (re-check `contentFocusActive` before each attempt) has no test that fails if it is reverted
- Where: ShowAllScreenContent.kt `requestRestoreFocus` (`if (!contentFocusActive()) return`); test `showAllDoesNotTakeFocusAfterReentryWhileContentFocusIsInactive` at :137-161
- Problem: the only inactive case sets `active = false` before restore. It also passes with the pre-fix code, which captured the value once at composition. Nothing turns the flag off between the restored composition and a later retry frame.
- Failure scenario: someone goes back to `val contentFocusActive = LocalContentFocusActive.current` read once. The rail opens in the first frames after Back, the retry pulls focus back into the grid, and the suite stays green.
- Fix: set `composeRule.mainClock.autoAdvance = false`. Restore with `active = true` and the opener card not yet composed (or advance only one frame), then set `active = false` and advance the remaining frames. Assert that no card has focus. If that cannot be made deterministic, record the reason in the plan as the documented check for #1.

### F3 [nit] The deep-card guard does not prove the card is outside the first viewport
- Where: ShowAllScreenContentFocusTest.kt:75
- Problem: `index >= DeepRows` checks index ≥ 5, which is only row 1 in a 3-column grid. The message "focus moved below the first row" does not match the test's name. On a device where five Down presses stop early, or the screen is taller, the precondition would still pass with no scroll at all.
- Fix: assert `index >= DeepRows * 3` (the column count). Also assert that the first card is not displayed before the restore, e.g. `onNodeWithText(items.first().title).assertDoesNotExist()` or `assertIsNotDisplayed()`.
