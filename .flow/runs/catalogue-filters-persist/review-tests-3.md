LENS: tests  ROUND: 3  BASE: ef5987ec (incremental 18d72ea4..b0cee092)

### F1 [minor] No test covers the new TabPick guard in `ShowAllEmpty`, and the reason the plan gives for skipping one is wrong
- Where: `ShowAllScreenContent.kt:191-196`; plan, review round 2 fixes, item 1.
- Problem: deleting `!handoffExpectsContent` keeps every test green. `ShowAllFilterFocusTest` provides no `LocalContentFocusHandoff`; `DrawerStateTest` never reaches the composable. A host exists: `ShowAllRailCloseFocusTest.RailWithShowAll` (~line 285) hosts `ShowAllScreenContent` behind the real `DrawerState`, `ContentFocusHandoff` and `ModalNavigationDrawer`; `DrawerState.beginHandoff(HandoffKind.TabPick)` is public; `restartKey = Unit` makes the ShowAll behave as the outgoing tab.
- Failure scenario: the guard is "simplified" away; on the box «Сброс» on the outgoing screen takes the focus meant for the arriving tab (edge-2 F1); no test goes red.
- Fix: give `RailWithShowAll` / `setUpScreen` a `state` parameter (default Content); add a case with Empty + active filter: wait for «Сброс» focused, Left, wait for Open, `runOnIdle { host.drawerState.beginHandoff(HandoffKind.TabPick) }`, advance a few frames (< 5 s timeout), assert «Сброс» not focused and `currentValue == HandingOff`. Record a red run with the guard removed. Or correct the "no host" sentence and mark the guard manual-only.

Rename changes only the name; nothing in the worktree refers to the old name. No tests run this round (instrumented only; qa-device red/green still due).

### Unproven
- `ShowAllScreenContent.kt:192` a failed TabPick returns the flag to false while this ShowAll is shown; skipped because `contentFocusActive` is false with the rail Open; matters only if `failHandoff(reopen = false)` closes the rail on the outgoing tab.
