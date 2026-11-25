# Test design: showall-rail-back-exits-app

Test designer, 2026-11-25. Base f88f3079. Inputs: `brief.md`, `design-architect.md`, `baseline.md`; revision 2 after `triage-1.md` (A1-A7). Paths: `unit` under `app/src/test/kotlin/com/kino/puber/`, `instrumented` under `app/src/androidTest/kotlin/com/kino/puber/`. Instrumented runs: `make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>` under a lease.

## Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Wrong gate traps the user at a root or exits on a stray press (brief: Risks 1; design 2) | 2 | 3 | 6 | AC-5, AC-6, AC-7 instrumented; M5-M7 |
| R2 | `TabStackState` stale after a tab switch: `RailClosed` enabled on a pushed screen, or no Main handler at a root (design 2) | 2 | 3 | 6 | Guard: `TabStackStateTest.detach_byPreviousOwner_keepsNewAttachment` (owner check). `backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` catches only a stale tab A attachment that wins, not a cleared one |
| R3 | `onEnter` gate refuses entries the rail needs: Left, its own reveal request, the handoff bounce, on every tab (design 1) | 2 | 3 | 6 | `leftFromPushedGrid_opensRail`, AC-7 test focus assertion, existing drawer guards; M11 steps 1-4 |
| R4 | Harness composes Main and the tab in one pass and passes on base, or sends Left through Compose where the unhandled-key path never runs (brief: Risks 4; design 7) | 3 | 2 | 6 | Harness rules below; AC-1, AC-3 and `leftWithNothingFocused_opensRailAndBackReturnsToContent` run red on base first |
| R5 | Screen-level handlers (filter panel, menu, dialog) stop winning (brief: Risks 3) | 1 | 3 | 3 | `screenBackHandlerOnPushedScreen_winsOverMainHandlers`; existing AC-9 guards |
| R6 | Back swallowed in `HandingOff` on a pushed screen, up to 5 s (brief: Risks 2; design 4) | 1 | 2 | 2 | Accepted: bounded by the handoff budget; observed in M1 |
| R7 | Up, Down or Right pressed with nothing focused does nothing until a screen request lands; base revealed the rail (design 1) | 2 | 1 | 2 | Accepted: observed in M11 step 5 |
| R8 | Cold-entry frame at 1 s not explained by the chain (design 5) | 2 | 2 | 4 | Per-frame drawer value in `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` |
| R9 | `canPop` read in `DrawerMainContent` scope recomposes the drawer on each push (design 6) | 1 | 1 | 1 | Accepted: review item |
| R10 | Worktree build drops the box to pairing (brief: Risks 5) | 2 | 2 | 4 | Procedure: master build and `auth-save` before install (AGENTS.md) |
| R11 | With nothing focusable (Show all `Loading`, empty Bookmarks or History, error without a button) the gate leaves Left unable to reach the rail (design 1; readiness-1 fix 1) | 2 | 3 | 6 | `leftWithNothingFocused_opensRailAndBackReturnsToContent`, proven by two mutation runs on the box (see Mutation runs); M8 step 6; M11 step 5 |
| R13 | Rail opened by Left over a pushed screen with nothing focusable: Back cycles `HandingOff` -> `Open` and never pops (triage A5) | 2 | 3 | 6 | `backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff`: asserts a rail item focused 2.5 s into `HandingOff` and none after `awaitDrawer(Closed)`; mutation B2 kills it (see Mutation runs) |
| R14 | Exit armed at one tab root survives a rail tab pick; the next Back at the other root exits (triage A6) | 2 | 3 | 6 | `armedAtRoot_thenTabPick_nextBackOpensRailNotExit`; M12 |
| R15 | Center on a collapsed rail during `HandingOff` or after a failed close picks a tab the user cannot see; a collapsed rail item keeps focus (triage-2 B3) | 2 | 3 | 6 | `MainRailBackTest.centerOnCollapsedRail_duringHandoff_doesNotSwitchTab`; mutation B3 |
| R12 | Unhandled-Left listener reveals the rail under a dialog, over a root-pushed screen or with a view focused (design 3) | 1 | 3 | 3 | AC-9, AC-10 instrumented; `leftFromPushedGrid_opensRail` (focused card: one reveal, not two); M9, M10 |

## Harness

`ui/feature/main/component/MainHostHarness.kt` (instrumented, shared): `createAndroidComposeRule<ComponentActivity>()`; `FlowComponent` root with a probe host screen that composes the real `DrawerMainContent` (design: Test seam) over `TabComponent`, modelled on `ProbeMainHost` in `core/ui/navigation/component/TabFlowLifecycleTest.kt:189-219`. Probe tab root: focusable cards and a push to a probe child (grid of cards, optional `Loading` with nothing focusable, optional own `BackHandler`); a root push to a probe Details. The probe captures `LocalDrawerState`.

| Need | How |
|---|---|
| Cold-start order | `tabRouter.openTab` only after the first `setContent` frame, so the tab composes a pass after Main |
| Tab-switch order | `openTab(other)` then `openTab(first)` |
| Details-return order | root push of the probe Details, then Back |
| Back | `activity.onBackPressedDispatcher.onBackPressed()` on the UI thread (`PlayerPromptFocusAndKeysTest.kt:160`) |
| Rail open | D-pad Left via `performKeyInput` from a focused card |
| Left with nothing focused | `InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)` off the UI thread; `performKeyInput` never reaches the view's unhandled-key path (R4). Precondition asserted first: drawer `Closed`, `onAllNodes(isFocused())` empty |
| Exit | `activity.isFinishing` |
| Exit arm | Harness seam reading the Main host's `ExitConfirmation` armed state (`ExitConfirmation.kt`); asserted right after `awaitDrawer(Closed)`, before any pop, because a pop disarms through `ExitDisarmOnTabPush` |
| Exit window | Harness records the arm time; tests that rely on an arm assert arm-to-final-Back is under the window (`ExitConfirmation.kt:12`) and fail, not pass, when it is exceeded |
| Never-focusable child | Probe child with no focusable node and no `Loading` end, so the handoff budget (`ContentFocusHandoff.kt:24`) runs out |
| Rail state | captured `drawerState.currentValue`, `waitUntil` Closed after a close |

The toast itself is not asserted; the arm behind it is, through the Exit arm seam.

## Traceability

| AC | Level | Priority | Base | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | instrumented | P0 | red | `MainRailBackTest.railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart` | Rail Closed, child still shown, the card Left was pressed from is focused; fails if `TabBackHandler` stays enabled with the rail open (base: pops, `baseline.md` P1) |
| AC-2 | instrumented | P0 | red | `MainRailBackTest.railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip` | Right after `awaitDrawer(Closed)`, child still on top: exit not armed (seam). Then Back pops, Back at root opens the rail, not finishing. Fails if `if (!tabStack.canPop)` in `RailOpenBackHandler` (`MainScreenComponent.kt:216-219`) is dropped; the later pop disarms, so only the seam assertion catches it (base: child popped on press 1) |
| AC-2 | device | P1 | red | M1, M2 | Toast absent when the rail closes over Show all |
| AC-3 | instrumented | P0 | red | `MainRailBackTest.railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch` | As AC-1 after a tab switch; fails if the new tab's navigator is not attached to `TabStackState` (base: pops, P5) |
| AC-4 | instrumented | P0 | guard | `MainRailBackTest.backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory` | Cold, Details-return and tab-switch histories each pop to the root, opener focused, rail Closed; fails if `railBackOwner` gives `RailClosed` on a pushed screen |
| AC-5 | instrumented | P0 | red | `MainRailBackTest.fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth` | Per press: rail Closed with child and exit not armed (seam); root; rail Open; rail Closed; finishing only after press 5; fails on any reordering of the handlers or an arm on the pushed screen |
| AC-6 | instrumented | P0 | red | `MainRailBackTest.armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit` | Arm at root, push, Back pops, Back opens the rail, not finishing; arm-to-final-Back asserted under the window (`ExitConfirmation.kt:12`), so the run cannot pass by expiry. Fails if the `LaunchedEffect(tabStack.canPop)` disarm is removed (base: `finish()`, defect 2) |
| AC-6 | instrumented | P0 | red | `MainRailBackTest.armedAtRoot_thenTabPick_nextBackOpensRailNotExit` | Arm at tab A root, Left, pick tab B from the rail, Back opens the rail, not finishing; arm-to-Back under the window. Fails if a rail tab pick does not disarm (R14; base and c6b25f49: finishing) |
| AC-7 | instrumented | P0 | guard | `MainRailBackTest.backAtTabRoot_opensRailThenArmsThenExits` | Rail Open with focus inside the rail; rail Closed; finishing. Fails if the root arm is lost or the `onEnter` gate refuses the rail's own focus request after the reveal (R3) |
| AC-7 | device | P1 | guard | M7 | Home, Movies, History on the real build; catches a lost root arm (no toast or no exit on press 3) |
| AC-8 | instrumented | P0 | red | `MainTabPushRailFocusTest.pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` | Push by Center from a focused card into a `Loading` child, content supplied later; drawer value never `Open` on any frame, first card focused once loaded; fails if the `onEnter` gate is removed (base: rail Movies item, P1) |
| AC-8 | instrumented | P1 | red | `MainTabPushRailFocusTest.pushWithContentReady_railStaysClosed` | Cached-page path (P2); fails without the gate |
| AC-8 | instrumented | P1 | red | `MainTabPushRailFocusTest.pushAfterDetailsRoundTrip_railStaysClosed` | Details-return path (P3) |
| AC-8 | instrumented | P0 | guard | `MainTabPushRailFocusTest.leftFromPushedGrid_opensRail` | Left from a grid card reveals the rail and focuses it; fails if the gate refuses `FocusDirection.Left` |
| AC-8 | instrumented | P0 | red | `MainTabPushRailFocusTest.leftWithNothingFocused_opensRailAndBackReturnsToContent` | Child held in `Loading`, precondition nothing focused and drawer `Closed`; real Left; drawer `Open` with a rail item focused; Back; content supplied; first card focused, drawer `Closed`, child on top. Fails if `RevealOnUnhandledLeft` is removed or its `Closed`/`!contentHasFocus()` guard (`ModalNavigationDrawer.kt:338`, Compose focus of the content wrapper at :229) is inverted (R11); shown by the Mutation runs |
| AC-1 | instrumented | P0 | red | `MainRailBackTest.backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff` | Never-focusable child pushed, real Left opens the rail; Back: child still on top; drawer settles `Closed` after the handoff budget, not `Open`; next Back pops to the root. A rail item (`isFocused() and isSelectable()`) is focused 2.5 s into `HandingOff` and none after `awaitDrawer(Closed)`. Fails if a failed handoff with `tabStack.canPop` returns the rail to `Open`, or if the `clearFocus(force = true)` on close is removed (mutation B2, triage-2; R13; base: pops on press 1; c6b25f49: Back cycles, never pops) |
| AC-1 | instrumented | P0 | red | `MainRailBackTest.centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` | Loading child pushed, Left, Back (`HandingOff`), Down, Center: child still shown, the other tab's root absent (`assertRootAbsent`), rail item focused beforehand (`assertRailItemFocused`). Fails if `MainSideMenuItem.onClick` acts while `drawerState.currentValue != Open` (R15; mutation B3, red on `15f0f3a5^` at `assertShown(CHILD_SCREEN)`: the other root takes focus and settles the handoff) |
| AC-8 | device | P1 | red | M8 | Four entry paths on the box; catches a rail reveal by a non-Left entry on a path the probe does not model |
| AC-9 | instrumented | P1 | guard | `MainRailBackTest.screenBackHandlerOnPushedScreen_winsOverMainHandlers` | Child's own handler consumes Back; stack and rail unchanged; fails if a Main handler is registered after or wins over screen handlers |
| AC-9 | instrumented | P0 | guard | `ShowAllFilterFocusTest.panelBack_closesWithFocusOnFilters`, `ShowAllRailCloseFocusTest.showAllMenuOpenAndBackReturnsFocusToTheOpeningCard` | Existing; panel and menu Back unchanged |
| AC-10 | instrumented | P1 | guard | `MainRailBackTest.backOnRootPushedDetails_popsToMain` | Back on the probe Details returns to Main with the tab child still on top; fails if Main handlers outlive Main's composition |
| AC-10 | device | P1 | guard | M10 | Details and player |
| AC-11 | instrumented | P0 | guard | `MainSideMenuFocusTraversalTest`, `InitialDrawerContentFocusTest` (incl. `dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail`, the `HandingOff` bounce the gate must allow), `ShowAllRailCloseFocusTest` (incl. `leftFromFilters_opensTheRail`), `ShowAllScreenContentFocusTest`, `TabFlowLifecycleTest` | Existing, unchanged; fail if the gate or handler change breaks rail traversal, handoff or tab lifecycle |
| AC-11 | unit | P0 | guard | `ExitConfirmationTest`, `DrawerStateTest`, `check.full` | Existing, unchanged |
| R2 | unit | P1 | guard | `TabStackStateTest.detach_byPreviousOwner_keepsNewAttachment` | Fails if `detach` ignores the owner and tab A's late `detach` clears tab B's attachment |
| R2 | instrumented | P2 | guard | `MainRailBackTest.backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` | Tab A child pushed, switch to tab B root, Back opens the rail; fails only if tab A's stale attachment wins over tab B |
| design rule | unit | P1 | new | `core/ui/navigation/component/RailBackOwnerTest`: `railBackOwner_isRailOpen_whenDrawerOpenOrHandingOff`, `railBackOwner_isTab_whenDrawerClosedAndTabCanPop`, `railBackOwner_isRailClosed_whenDrawerClosedAndTabAtRoot`, `railBackOwner_isTabOrNull_withoutRailHost` | Every row of `brief.md` Intended Back; fails on any swapped branch |
| design rule | unit | P1 | new | `core/ui/navigation/component/TabStackStateTest`: `canPop_isFalse_whenNothingAttached`, `canPop_followsAttachedProvider` | Fails if `canPop` does not follow the attached provider |

Base `new`: the symbol does not exist at f88f3079.

## Failing first

The implementer runs every `red` row on the unchanged worktree first. Expected on base, from `MainScreenComponent.kt:119,159,171-210` and `FlowComponent.kt:749-757` at f88f3079:

| Test | Base result |
|---|---|
| `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart` | child popped (`TabBackHandler` registered last) |
| `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch` | child popped |
| `railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip` | activity finishing on press 3 |
| `fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth` | child popped on press 1 |
| `armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit` | activity finishing |
| `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` | drawer `Open`, focus in the rail |
| `leftWithNothingFocused_opensRailAndBackReturnsToContent` | drawer `Open` after the push (non-Left refocus revealed the rail); the nothing-focused precondition fails |
| `backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff` | base: child popped on press 1; c6b25f49: drawer back to `Open`, child never popped |
| `armedAtRoot_thenTabPick_nextBackOpensRailNotExit` | activity finishing (base and c6b25f49) |

`centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` is red by mutation B3, not on base: on base (and `15f0f3a5^`) the click switches the tab and `assertShown(CHILD_SCREEN)` fails; the implementer reverts the `onClick` guard to see it.

If the AC-1 or AC-3 test passes on base, the harness composes in one pass (R4); fix the harness, not the assertion. If `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` passes on base while M8 reproduces, the design's Q3 fallback applies: the gate, `MainTabPushRailFocusTest` and AC-8 move to `showall-first-entry-rail-focus`.

## Mutation runs (R11, R13, R15)

On the box, each test against its mutations of the fixed worktree, reverted after; results recorded in the qa report. First two rows: `leftWithNothingFocused_opensRailAndBackReturnsToContent`.

| Mutation | Expected |
|---|---|
| `RevealOnUnhandledLeft(...)` call removed (`ModalNavigationDrawer.kt:258`) | red at `awaitDrawer(Open)` after the real Left |
| Guard inverted to `contentHasFocus()` (`ModalNavigationDrawer.kt:338`) | red at `awaitDrawer(Open)` |
| `clearFocus(force = true)` on close removed (B2) | `backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff` red at `assertNoRailItemFocused()` after `awaitDrawer(Closed)` |
| `MainSideMenuItem.onClick` guard on `drawerState.currentValue == Open` removed (B3) | `centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` red at `assertShown(CHILD_SCREEN)` |

Green under either mutation means the test does not reach the unhandled-key path: the implementer changes the test until it does; the assertion stays.

## Manual checks (device)

Box 192.168.1.110:5555, worktree dev APK, keys by `adb -s 192.168.1.110:5555 shell input keyevent` (Left 21, Right 22, Center 23, Back 4), screenshot after each step. Never across all checks: app leaves the foreground before the step that expects it, focus lost (no highlighted node), rail and content both looking focused.

| # | AC | Steps | Expected |
|---|---|---|---|
| M1 | AC-1 | 1. Cold start, Movies via rail. 2. Center on «Все фильмы», wait for the grid. 3. Right to a card, Left. 4. Back. | Step 3 rail expands. Step 4 rail collapses, grid stays, the card from step 3 focused, no toast. A Back during the collapse animation is swallowed, not a pop (R6) |
| M2 | AC-2 | 1. From the Movies root open a Details page by Center, Back. 2. Repeat M1 steps 2-4. | As M1, no toast |
| M3 | AC-3 | 1. Rail: Home, then Movies. 2. Repeat M1 steps 2-4. | As M1 |
| M4 | AC-4 | In each history of M1-M3, rail collapsed on the grid, Back once. | Tab root, «Все фильмы» focused, no toast, rail collapsed |
| M5 | AC-5 | M1 steps 1-3, then Back 5 times about 0.5 s apart. | Rail closes; Show all pops; rail opens; rail closes with the toast; app exits on press 5 only |
| M6 | AC-6 | 1. Movies root, Back (rail opens), Back (toast). 2. Within 3 s Right, Center on «Все фильмы». 3. Back, Back. | Press 1 of step 3 pops Show all; press 2 opens the rail; app stays in the foreground |
| M7 | AC-7 | On Home, Movies and History roots, rail collapsed: Back, Back, Back. | Rail opens; rail closes with the toast; app exits (as base) |
| M8 | AC-8 | Center on «Все фильмы» from the Movies root: 1. cold first entry, 2. second entry, 3. after a Details round trip, 4. through a shelf «Показать все» tile (a row with a short list; NOT REACHED if none, as in `baseline.md` P4). Screenshots at about 1 s and 3 s. 5. Left from the first card. 6. During `Loading` of entry 1, press Left once and record the result. | Steps 1-4: rail collapsed at 1 s and 3 s, first card focused once loaded. Step 5: rail expands. Step 6: rail expands with Movies focused (R11); the app must not leave Show all |
| M9 | AC-9 | 1. Show all, Up to «Фильтры», Center (panel), Back. 2. Long-press Center on a card (context menu), Back. | Panel closes, focus on «Фильтры»; menu closes, focus on the card; Show all stays, rail collapsed |
| M10 | AC-10 | 1. Show all card, Center (Details), Back. 2. Start a trailer or episode, Back until the player closes. | Returns to the screen underneath with its card focused, as base; no toast, rail collapsed |
| M12 | AC-6 (R14) | 1. Movies root, Back (rail opens), Back (toast). 2. Within 3 s Left, pick Home. 3. Back. | Step 3 opens the rail; app stays in the foreground |
| M11 | R3, R7, R11 | 1-4. On Home, Movies, History, Bookmarks, Settings: Left opens the rail; Right closes it with focus back on content; pick another tab from the rail; open and dismiss a dialog. 5. On a screen with nothing focusable (empty Bookmarks or History if the account has one, else Show all during `Loading`): Up, Down, Right, then Left, then Back. | Steps 1-4: rail opens and closes as base; focus lands in content after a tab pick and after the dialog; rail never expands without a Left. Step 5: Up, Down, Right do nothing (R7); Left expands the rail with the selected tab focused; Back collapses it once content can take focus; nothing focusable: At a tab root with nothing focusable the rail reopens after 5 s (`ContentFocusHandoff.kt:24,137`). Over a pushed screen (Show all during Loading or Error) a failed handoff ends Closed with no rail item focused, and the next Back pops to the root (A5). Must not: app exit, tab pop |

## perf

N/A: perf track off.

## UNTESTABLE

Toast text in AC-2, AC-5, AC-6: no instrumented hook for `Toast`; checked on the device (M1, M2, M5, M6); the arm behind it is asserted through the Exit arm seam.
