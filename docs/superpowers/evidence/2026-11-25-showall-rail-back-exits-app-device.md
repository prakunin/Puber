# Device evidence: showall-rail-back-exits-app (QA round 1)

Build: worktree HEAD 8cc8daa0, dev debug APK sha256 d0ddf76c1bd33776e5bf4f73c4f1c0334034cc5195d1c196a01fe8edaba88428 (device base.apk sha256 identical; install-preserve outcome `installed`, compatible_replace).
Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held and released. auth-save ran first (exit 0). Remote input by `adb shell input keyevent`; screenshots kept outside Git.

## Instrumented at HEAD (make itest, one class per run)

| Class | Result |
|-------|--------|
| TabFlowLifecycleTest | PASS |
| ui.feature.showall.content.ShowAllRailCloseFocusTest | PASS |
| core.ui.uikit.component.drawer.InitialDrawerContentFocusTest | PASS |
| ui.feature.main.component.MainSideMenuFocusTraversalTest | PASS (3/3) |
| MainTabPushRailFocusTest | 4 of 5 pass; FAIL leftWithNothingFocused_opensRailAndBackReturnsToContent (ComposeTimeout at awaitDrawer(Open), MainTabPushRailFocusTest.kt:94, real Left through the input pipeline) |
| MainRailBackTest | 5 of 10 pass; FAIL backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail (awaitDrawer, MainRailBackTest.kt:172), railOpenOnPushedScreen_* x3 and fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth (awaitDrawer(Open) inside openRailFrom, MainHostHarness.kt:255) |

Harness fix 93c4fefd: backAtTabRoot_opensRailThenArmsThenExits and leftFromPushedGrid_opensRail are green. Three runs of MainRailBackTest gave the same failures. A throw-away edit (reverted, not committed) that waited for CHILD_CARD_FIRST focus before openRailFrom turned the three railOpenOnPushedScreen_* tests green; fiveBacks and backAtOtherTabRoot stayed red.

## Manual checks on the box

| Check | AC | Steps | Observed | Result |
|-------|----|-------|----------|--------|
| M1 | AC-1 | Cold start, Movies, Center on "Все фильмы", Right, Left, Back | Rail expanded after Left; Back: rail collapsed, grid stays, first card focused, no toast | PASS |
| M2 | AC-2 | Movies root, Center on a shelf card (Details), Back; Center on "Все фильмы"; Right, Left, Back | Same as M1, no toast | PASS |
| M3 | AC-3 | Cold start Home, rail to Movies, Show all, Right, Left, Back | Rail open over the grid with Movies selected; Back: rail closed, grid and focused card stay | PASS |
| M4 | AC-4 | After each of M1-M3 Back once more | Pops to Movies root, "Все фильмы" focused, rail collapsed, no toast | PASS |
| M5 | AC-5 | Rail open over Show all, 5 x Back at 0.45 s, twice | Press 4: stage app still focused; press 5: launcher focused. Intermediate frames not individually asserted on the second run | PASS (sequence by foreground window; per-press screenshots taken on run 1) |
| M6 | AC-6 | Movies root: Back (rail), Back (toast), Center on "Все фильмы" within 3 s, Back, Back | Show all entered; Back 1 popped to root; Back 2 opened the rail; app stayed in foreground | PASS |
| M7 | AC-7 | Home, Movies, History roots: Back, Back, Back | Toast "Нажми «Назад» ещё раз, чтобы выйти" after press 2 (Movies, History); launcher focused after press 3 on all three | PASS |
| M8 | AC-8 | Center on "Все фильмы": cold first entry, second entry, after Details round trip; screenshots about 0.3-1 s and 3 s | Rail collapsed, first card focused once loaded, all three entries. Shelf "Показать все" tile: not reached (as baseline.md P4) | PASS (3 of 4 paths) |
| M8.5 | AC-8 | Left from a grid card | Rail expands | PASS |
| M8.6 | AC-8 | Center on "Все сериалы", Left 150 ms later | Rail expanded, app stayed on Show all; the grid was probably cached, so a loading state is not proven | NOT CHECKED (inconclusive) |
| M9 | AC-9 | Up to "Фильтры", Center, Back; long-press Center on a card, Back | Panel closed, focus on "Фильтры"; menu closed, card focused; Show all stays, rail collapsed | PASS |
| M10 | AC-10 | Details Back; trailer Back | Details Back returns to the root with the card focused. A Center on a Home card started an episode in the player (0:04), Back x2 returned to Home. Not a planned step; see the report | PASS (unplanned playback start) |
| M11 | R3, R7, R11 | Left on Home and Movies root; Right closes; tab pick from the rail | Rail opens on Left, closes on Right, tab pick lands focus in content. Settings, Bookmarks, empty-screen step 5, dialogs not run | PARTIAL |

## Round 2 (HEAD 6379bd8b)

Build: dev debug APK sha256 02e60fba0ae7e3f5877d8a710e829eb6e0b3aeb7310e132c54baaa4c507bf648 (device base.apk sha256 identical; install-preserve outcome `installed`, compatible_replace). Device: TV box 192.168.1.110:5555, com.kino.puber.stage only. auth-save ran first. Lease acquired and released. No Center was sent on a continue-watching card; no playback started this round. Remote input by `adb shell input keyevent`; screenshots kept outside Git.

### Instrumented at HEAD (make itest, one class per run)

| Class | Result |
|-------|--------|
| MainRailBackTest | PASS, run 1 (10 tests); PASS, run 2 |
| MainTabPushRailFocusTest | 4 of 5 pass; FAIL leftWithNothingFocused_opensRailAndBackReturnsToContent, ComposeTimeout at awaitDrawer(Open) (MainTabPushRailFocusTest.kt:94, via MainHostHarness.kt:273). Same failure on two runs; the 1fd30c7a change did not turn it green |
| TabFlowLifecycleTest | PASS |
| ui.feature.showall.content.ShowAllRailCloseFocusTest | PASS |
| core.ui.uikit.component.drawer.InitialDrawerContentFocusTest | PASS |
| ui.feature.main.component.MainSideMenuFocusTraversalTest | PASS |

### Manual checks on the box

| Check | AC | Steps | Observed | Result |
|-------|----|-------|----------|--------|
| D-pad Home | R3, R7 | Home, focus on first continue-watching card; Left; Right | Left expanded the rail with focus on the item at the card's row (Док. Сериалы); Right closed it and focus returned to the same card. No Center sent | PASS |
| M7 Home | AC-7 | Cold start, Back x3 at 0.45, 1.0, 1.5 and 2.2 s gaps (no screenshots between presses) | Launcher focused after the third press at all four gaps; after press 2 the toast "Нажми «Назад» ещё раз, чтобы выйти" was seen (frame at 0.4 s) | PASS |
| M7 Movies | AC-7 | Movies root, Back x3 at 0.7 s | Launcher focused after press 3 | PASS |
| M1 | AC-1 | Movies, Show all (cold entry, spinner then grid), Left from first card, Back | Rail expanded over the grid; Back closed it, grid stayed, first card focused, no toast | PASS |
| M4 | AC-4 | Back once more | Popped to the Movies root, "Все фильмы" focused, rail collapsed | PASS |
| M8 cold entry | AC-8 | Cold start, Movies, Center on "Все фильмы", frames at 0.3 s and 3.5 s | 0.3 s: spinner, rail collapsed; 3.5 s: grid, first card focused, rail collapsed | PASS |
| M8 Left from card | AC-8 | Left from a grid card, Right | Rail expanded, Right returned to the first card | PASS |
| M8 Left during Loading | AC-8 | Center then Left at 0.1 s, three attempts | The grid was already cached at the first frame (0.15-0.25 s) each time, so no Loading state was under the Left; rail opened | NOT CHECKED |
| M9 panel | AC-9 | "Фильтры", Center, Back | Panel closed, focus on "Фильтры", rail collapsed, Show all stays | PASS |
| M9 menu | AC-9 | Long-press Center on a card, Back | Context menu closed, card focused, rail collapsed | PASS |
| M11 step 5 | R11 | Screen with nothing focusable | No such screen found on the account | NOT CHECKED |

Observation, not a pass or fail: when a `screencap` ran between the Back presses of M7 on Home (presses 1.4 to 2.2 s apart), the third Back opened the rail again instead of leaving (three runs, Home). The same presses without a capture between them left the app on all four gaps. The cause is not established; a capture slowing the display is the only difference.

## Round 3 (HEAD 1ed3df50, box 192.168.1.110:5555, auth-save first, lease released)

| Class | Result |
|-------|--------|
| MainTabPushRailFocusTest | PASS run 1, PASS run 2 (gradle BUILD SUCCESSFUL, all 5 tests incl. leftWithNothingFocused_opensRailAndBackReturnsToContent) |
| MainRailBackTest | PASS, 10 tests, 0 failures (result XML) |

check.full (testDevDebugUnitTest, :app:detektAll, :app:lintDevDebug): BUILD SUCCESSFUL. No manual device steps this round.

## Round 4 (HEAD 773b60f2, box 192.168.1.110:5555, auth-save first, lease released)

Build: dev APK sha256 d1a31193...18f15e (device base.apk identical), install-preserve outcome installed (compatible_replace).

Instrumented at HEAD (make itest, one class per run, all BUILD SUCCESSFUL): MainRailBackTest 12 of 12 (incl. backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff, armedAtRoot_thenTabPick_nextBackOpensRailNotExit), MainTabPushRailFocusTest, InitialDrawerContentFocusTest, MainSideMenuFocusTraversalTest, ShowAllRailCloseFocusTest, ShowAllFilterFocusTest, ShowAllScreenContentFocusTest, TabFlowLifecycleTest. Only the last class's result XML survives a run, so the test count is recorded for MainRailBackTest (12) and TabFlowLifecycleTest (1).

Mutation runs (temporary edits, reverted with git checkout, tree clean after):

| Mutation | Run | Result |
|----------|-----|--------|
| A1a: RevealOnUnhandledLeft(drawerState, contentHasFocus) call removed (ModalNavigationDrawer.kt) | MainTabPushRailFocusTest | 1 of 5 red: leftWithNothingFocused_opensRailAndBackReturnsToContent, ComposeTimeoutException at MainTabPushRailFocusTest.kt:90 (awaitDrawer(Open) after the real Left) |
| A1b: !contentHasFocus() inverted to contentHasFocus() | MainTabPushRailFocusTest | same test red at :90 |
| A2: RailOpenBackHandler arms unconditionally (if (true)) | MainRailBackTest | 4 of 12 red |
| A4: ExitDisarmOnTabPush body emptied | MainRailBackTest | 1 of 12 red |
| A6: rail ItemSelected no longer disarms | MainRailBackTest | 1 of 12 red |

Device (tab Док. Сериалы and Фильмы; keys by adb input keyevent):

| Check | AC | Steps | Observed | Status |
|-------|----|-------|----------|--------|
| M1 | AC-1, AC-2 | Show all "Все док. сериалы" by Center, rail collapsed with first card focused; Left (rail expands), Back | Rail collapsed, grid and first card unchanged, no toast, app in foreground | PASS |
| M4 | AC-4 | Back with rail collapsed on the grid | Popped to the tab root, "Все док. сериалы" focused, rail collapsed, no toast | PASS |
| M12 | AC-6 (R14) | Back (rail opens), Back (arm), Left, Down, Center (other tab picked), Back, all within about 2.5 s of the arm | Rail opened, app stayed in the foreground | PASS |
| M7 | AC-7 | Movies root, rail collapsed: Back x3 in one shell command, 0.8 s apart | Press 2 shows the exit toast, press 3 leaves the app | PASS |

Notes: a first M12 attempt began with the rail already open, so its Back pair closed the rail, armed and exited; that was a setup slip, not a defect. The M7 run with a screencap and a dumpsys between presses 2 and 3 reopened the rail (gap over 3 s, arm expired), the same observation as round 2; with no capture between presses the third Back exits.

check.full (testDevDebugUnitTest, :app:detektAll, :app:lintDevDebug): BUILD SUCCESSFUL.

## Round 6 (HEAD 3a536877, box 192.168.1.110:5555, auth-save first, lease released)

Build: dev debug, APK sha256 616edb279a4cdce992473ca941f88adde44f0a85fa534ccc824714f1a0d3775f, install-preserve outcome installed (compatible_replace), on-device base.apk sha256 identical.

Instrumented tests at HEAD (make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest, one class per run), all BUILD SUCCESSFUL: MainRailBackTest (13 tests in the result XML, not 14), MainTabPushRailFocusTest, MainSideMenuFocusTraversalTest, core.ui.uikit.component.drawer.InitialDrawerContentFocusTest.

Mutations, each applied alone, MainRailBackTest rerun, then git checkout of the file, tree clean after each:

| Mutation | Observed | Verdict |
|----------|----------|---------|
| B2: ModalNavigationDrawer.kt delete focusManager.clearFocus(force = true) | 1 of 13 red: backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff, assertNoRailItemFocused (MainHostHarness.kt:337) called from MainRailBackTest.kt:174, "found 1 node (Focused = true, Selected defined)" (run twice, same result) | killed (round 5 survivor now caught) |
| B3: MainScreenComponent.kt:315 if (drawerState.isOpen) -> if (true) | 1 of 13 red: centerOnCollapsedRail_duringHandoff_doesNotSwitchTab, host.assertShown(CHILD_SCREEN) MainHostHarness.kt:308 from MainRailBackTest.kt:203, "could not find any node (TestTag = child_screen)" | killed |

B3 caveat: the test presses Down then asserts a rail item is focused with assertRailItemFocused, which matches any focused node with Selected defined; it does not assert that the item is a non-selected one (History per the comment). The mutation is killed anyway, because Center switched the tab and the child screen disappeared, so the Center effect is observed.

Device, rail picks (screenshots viewed, none stored):

| Step | Steps | Observed | Result |
|------|-------|----------|--------|
| P1 Home | Cold start, Left, Down x2, Center | Rail expanded on Left, focus on "Сериалы", Center switched to the Сериалы tab, rail collapsed, first card focused in content | PASS |
| P2 Movies | Left, Up, Center (focus on Movies item; the rail entry item is the one nearest the card vertically, as before) | Movies tab opened, rail collapsed, first card focused | PASS |
| M1 | Movies root, Center on "Все фильмы", 3.5 s, Left, Back | Rail expanded over the grid; Back closed it, grid unchanged, first card focused, no toast | PASS |
| M4 | Back once more | Popped to the Movies root, "Все фильмы" focused, rail collapsed, app in foreground | PASS |

No Center on a continue-watching card, no resume prompt confirmed.

check.full (testDevDebugUnitTest, :app:detektAll, :app:lintDevDebug): BUILD SUCCESSFUL, also with --rerun on the three tasks (BUILD SUCCESSFUL in 33s).

## Round 7 (HEAD 73371c73, box 192.168.1.110:5555, auth-save first, lease released)

Build: dev debug, APK sha256 4151c7e32f25e588df1bbb2043acd01337f255da98f9494251c733eee780e8bd, install-preserve outcome installed (compatible_replace), on-device base.apk sha256 identical.

Instrumented at HEAD (make itest, one class per run), all BUILD SUCCESSFUL: MainRailBackTest (14 tests), MainTabPushRailFocusTest, MainSideMenuFocusTraversalTest, core.ui.uikit.component.drawer.InitialDrawerContentFocusTest.

### Mutations (MainScreenComponent.kt, one at a time, reverted by git checkout, tree clean)

| Mutation | Observed | Verdict |
|----------|----------|---------|
| C3: acceptsPickOf HandingOff branch -> false (Open still true) | 1 of 14 red: correctivePickDuringTabPickHandoff_switchesToTheSecondTab, ComposeTimeoutException at MainHostHarness.awaitRoot (MainHostHarness.kt:319 via awaitTag :316), called from MainRailBackTest.kt:223 | killed |
| C2/B3: acceptsPickOf body -> true | 1 of 14 red: centerOnCollapsedRail_duringHandoff_doesNotSwitchTab, assertExists child_screen failed (MainRailBackTest.kt:203) | killed |

### Device

| Step | Observed | Result |
|------|----------|--------|
| P1 Home: Left, Down x2, Center | Сериалы tab opened, rail collapsed, first card focused | PASS |
| P2 Movies: Left, Up x7 from Settings, Center | Movies opened, rail collapsed, first card focused | PASS |
| Quick double pick: Left, Up to a tab, Center, then Down, Center in one shell command (also as one multi-key input), twice | The first pick won each time (Movies or Сериалы). The handoff had already finished when Down arrived, so Down and Center acted on the arrived tab content and opened a details page (no playback). The corrective window exists only while the new tab is still loading; the tabs were cached, so it did not open | NOT CHECKED on device (instrumented test correctivePickDuringTabPickHandoff_switchesToTheSecondTab, killed by C3) |
| M1 Movies, Center on "Все фильмы", Left, Back | Rail expanded over the grid; Back closed it, grid unchanged, first card focused, no toast | PASS |

No Center on a continue-watching card, no resume prompt confirmed. Two unplanned Centers landed on catalogue cards (details pages only), backed out with Back.

check.full (testDevDebugUnitTest, :app:detektAll, :app:lintDevDebug, --rerun-tasks): BUILD SUCCESSFUL in 2m 36s.
