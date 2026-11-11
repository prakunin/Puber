# Implementation record: showall-rail-close-focus-shift

Base 6b087697, worktree branch prakunin/showall-rail-close-focus-shift. Fix shape A.

## Commits
- 78b3d64d Answer a side rail focus handoff in the Show all grid by refocusing the remembered card (plan steps 1-2)
- 33068672 Test the Show all grid answer to a side rail focus handoff (step 3)
- dda4eb23 Add an instrumented test for Show all focus after the side rail closes (step 4)

## What changed
- `ShowAllScreenContent.kt`: `rememberGridFocusMemory` latches `LocalContentFocusHandoff.current?.pendingRequestId` (same pattern as `RestoreItemFocusOnTabReentryEffect`) and runs `memory.onHandoff(id)` from `LaunchedEffect(latchedHandoffId, contentFocusActive)`. New `GridFocusMemory.onHandoff(requestId)`: ignores an id already answered; returns without consuming it while content is inactive; otherwise marks the id answered, and if the grid is unfocused and a card is remembered, re-resolves `restoreId` and runs the existing 3-attempt `requestRestoreFocus()`. `pendingEntry` untouched; null memory is a no-op; no `requestGrid` fallback. Shared handoff contract unchanged.
- The once-per-id guard lives in `GridFocusMemory` (`answeredHandoffId`), so it is unit-testable (T-U4 not moved to instrumented).

## Tests added
- Unit (`GridFocusMemoryTest`, 8 new cases): T-U1 remembered card requested; T-U2 null memory; T-U3 inGrid; T-U4 single fire per id; T-U5 inactive / flips off; inactive-then-active answers once; T-U6 dropped card -> neighbour (6); T-U7 pendingEntry untouched. Existing cases green.
- Instrumented (new `ShowAllRailCloseFocusTest`, compiled only, not run; qa runs it on a device): T-I1 lower-row card (5 Downs, scrolled), LEFT then RIGHT x3; T-I1b top-row card x3. Hosts `ShowAllScreenContent` in a scaffold with real `DrawerState`, `ModalNavigationDrawer`, `MainSideMenuContent`, `ContentFocusHandoffEffect`, and a tab box with `focusRestorer` like `FlowComponent`. Red on base was NOT captured (no device access): qa should run it on base for the red evidence.
- Not written (P1/P2): T-I2, T-I3, T-I4, T-I5, T-I6. Left to device checks D3-D5.

## Checks
- fast: `:app:compileDevDebugKotlin` BUILD SUCCESSFUL.
- `:app:compileDevDebugAndroidTestKotlin` BUILD SUCCESSFUL.
- full: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL in 3m 30s.

## Deviations
None from the design. The instrumented test is a new file instead of an addition to the existing one (the plan allowed either).

## For reviewers
- The latch/effect keyed on `contentFocusActive`: an id arriving while inactive stays unanswered until the content turns active; an already-answered id never re-fires.
- Device checks D1-D5 (incl. hardening M2: rail opened right after Back) are open for qa-device.

## Fix round 1

Triage rows #2 and #3. Tests only, in `ShowAllRailCloseFocusTest.kt`; no production change. Not run on a device (qa runs the class).

| Test | Change |
|------|--------|
| `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling` | Renamed from the top-row test; adds the scrolled case (7 rows down, 1 up); comment marks the top-row half as a guard that also passes on base |
| `showAllRailCloseWithoutMemoryBehavesAsBase` (T-I2) | Rail starts open, auto-focus off, grid never focused; RIGHT; Card 1 is focused |
| `showAllHandoffResponseDoesNotStealFocusFromADialog` (T-I3) | After a settled handoff a popup node inside the tab box takes focus; content active flips false then true; popup stays focused |
| `showAllRecompositionAfterSettleDoesNotRefocus` (T-I4) | After settle the user moves RIGHT to another card; active flips false then true; focus stays |

- Harness: `Host` holds the drawer state and the switches `forcedInactive` and `popupShown`; `setUpScreen` takes the initial drawer value and auto-focus.
- T-I3 models the dialog as an in-tree focusable, not a Compose `Dialog` window: a second window is unreliable under the compose rule. It discriminates the once-per-id latch (without it the flip restores the remembered card and takes focus from the popup).
- T-I4 discriminates the `inGrid` guard only; the latch is covered by T-I3 and unit T-U4.
- Red run: not done (no device); T-I2, T-I3, T-I4 are `guard` rows by design (behaviour exists in the fix).
- Check: `:app:compileDevDebugKotlin :app:compileDevDebugAndroidTestKotlin :app:detektAll` BUILD SUCCESSFUL; `check-named-tests.py`: 8 names, 0 missing.
- Reviewers: the flip in T-I3 and T-I4 runs `advanceTimeBy(500)` so the 3 restore frames would have elapsed if a steal happened.

## Fix round 2

- Comments only in `ShowAllRailCloseFocusTest.kt`: T-I4 is marked a smoke check of the `inGrid` path (the latch is covered by T-I3 and unit T-U4); T-I2 is marked a guard that also passes on base.
- Check: `:app:compileDevDebugAndroidTestKotlin` BUILD SUCCESSFUL.

## Squash

Six commits `6b087697..134c5032` squashed into three. Tree `acfbe3d7f97f40091f0a5887e1ab1183380d244a` before and after. `:app:compileDevDebugKotlin` passes.

| New | Old |
|---|---|
| 95fd3a8e Refocus the remembered Show all card when the side rail hands focus back | 78b3d64d, 33068672 |
| 995b1dd9 Add instrumented tests for Show all focus after the side rail closes | dda4eb23, e9595021, 134c5032 |
| 5a55abfe Record device evidence for the Show all rail close focus fix | 97be2417 |
