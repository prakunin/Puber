LENS: tests  ROUND: 1  BASE: 35bfbbec
### F1 [minor] No test covers the `pendingEntry` gate on the grid fallback, so a mutation that removes it survives
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:213; app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt (U-AC6e, `reconcile_liveGridFocusedCardRemovedRetargetsToTheNeighbour`)
- Problem: Spec D5 and Out of scope limit fallback C to a pending entry. The live path must never call `requestGrid()`. Only one test reaches line 213 with `pendingEntry == false`: the live path with the focused card removed (U-AC6e). In that test `requestRestore` succeeds, so `reconcile()` returns before it reaches C. No test runs the live path with every targeted attempt failing.
- Failure scenario: someone changes line 213 to `if (contentFocusActive()) requestGrid()`. All 21 JVM tests and the 5 instrumented tests stay green. In production, a live reload whose 3 attempts all miss would then move focus to the grid's first card. That is the "first visible card" landing the spec ruled out for the live path.
- Fix: add one JVM case: `Harness(null, 0, cards(1..10), restoreOverride = { false })`, then `onGridFocusChanged(true)`, `onCardFocused(5, 4)`, `itemsVar = without(5, ...)` and `run`. Assert that the events are exactly three `restore:6:false` and that `"grid" !in events`.

### F2 [minor] Nothing tests the Compose wiring of `requestGrid` to `mainContentFocus`
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:163
- Problem: The JVM tests replace `requestGrid` with a lambda that only records an event. No instrumented test can reach C, because attempt 1 normally lands. So it is never checked that the production lambda really focuses the grid. The spec's UNTESTABLE section covers only the race timing and AC-1 on a device. It does not mention the fallback's wiring, and the plan records no reason it was left untested.
- Failure scenario: `requestGrid = { gridFocus.tryRequestFocus() }` is changed to `{ restoreFocus.tryRequestFocus() }` or to `{ false }`. The full suite and the itest class stay green. When the entry race happens on a device, nothing is focused, which is the defect this run fixes.
- Fix: add a line to the spec's UNTESTABLE section or the plan record saying the C wiring is checked by review only. Or add an androidTest that forces C. For example, re-enter with `LocalContentFocusActive` false so the targeted loop stops, then flip it to true before the attempts are used up. Only do this if it can be made deterministic; otherwise record it as untestable.

Suite state: I ran `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree and it passed (BUILD SUCCESSFUL, exit 0). The unit test and detekt tasks were UP-TO-DATE from earlier runs at the same inputs, and only `lintDevDebug` actually executed. The stored `GridFocusMemoryTest` result shows 21 tests, 0 skipped, 0 failures. I did not run the instrumented class myself. qa reports it at 5/5 green, with M-AC2 and M-AC7 each seen red once and then reverted.

Checked and left out:
- Every AC has a test or a recorded device check.
- The AC-1, AC-4 and AC-5 red-once runs are recorded in the plan's Implementation record. Traced by hand, U-AC1a/b/c fail when the restart is gone, and U-AC4 fails when the active check is read only once.
- No assertions were weakened. The rewritten AC-2 test runs only the entry fallback; the live-neighbour case still has its own test. The AC-7 guard is exactly index 15.
- If the effect key is reverted to `LaunchedEffect(Unit)`, the instrumented test `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeavesTheList` fails.
- No test mocks the class under test or uses real time.

Files:
- /home/user1/orca/workspaces/Puber/showall-focus-restore-hardening/app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt
- /home/user1/orca/workspaces/Puber/showall-focus-restore-hardening/app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt
- /home/user1/orca/workspaces/Puber/showall-focus-restore-hardening/app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt
