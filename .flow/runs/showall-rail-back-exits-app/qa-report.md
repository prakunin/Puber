# Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL)
DEVICE: 192.168.1.110:5555 (TV box), dev APK sha256 d0ddf76c...8428 at HEAD 8cc8daa0, lease released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (device) / FAIL (instrumented) | M1 on box. Instrumented railOpenOnPushedScreen_..._afterColdStart red in the harness (awaitDrawer in openRailFrom) |
| AC-2 | PASS (device) / FAIL (instrumented) | M2; ..._afterDetailsRoundTrip red, same step |
| AC-3 | PASS (device) / FAIL (instrumented) | M3; ..._afterTabSwitch red, same step |
| AC-4 | PASS | M4 device; backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory green |
| AC-5 | PASS (device) / FAIL (instrumented) | M5 (exit on press 5 only); fiveBacks... red at openRailFrom |
| AC-6 | PASS | M6 device; armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit green |
| AC-7 | PASS | M7 on Home, Movies, History; backAtTabRoot_opensRailThenArmsThenExits green |
| AC-8 | PASS on 3 of 4 paths (device), NOT CHECKED shelf tile and Left-during-Loading | pushIntoLoadingScreen, pushWithContentReady, pushAfterDetailsRoundTrip, leftFromPushedGrid green; leftWithNothingFocused_opensRailAndBackReturnsToContent FAIL (awaitDrawer(Open) after real Left, test:94) |
| AC-9 | PASS | M9 panel and menu; ShowAllRailCloseFocusTest green |
| AC-10 | PASS | M10 Details Back; backOnRootPushedDetails_popsToMain green; player Back reached only by an unplanned playback start |
| AC-11 | FAIL | check.full green; guards TabFlowLifecycle, ShowAllRailCloseFocus, InitialDrawerContentFocus, MainSideMenuFocusTraversal green; the new MainRailBackTest (5 of 10 red) and MainTabPushRailFocusTest (1 of 5 red) are not |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md
DEFECTS:
1. MainRailBackTest at HEAD: railOpenOnPushedScreen_* (x3) and fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth time out in openRailFrom (MainHostHarness.kt:255). Reproduced on three runs. Cause looks like a harness race: those tests call focus(CHILD_CARD_FIRST) right after the push; the throw-away edit awaitFocused(CHILD_CARD_FIRST) first made the three railOpen tests green (fiveBacks calls openRailFrom with no wait, so it needs the same wait). Reproduce: make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.main.component.MainRailBackTest
2. MainRailBackTest.backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail (test:172) red on all three runs, green at 5a152b4c (d1-d2.md). Back at tab B root with tab A holding a pushed child does not open the rail within 10 s; regression from f4ec56aa or flake in the harness, not diagnosed. The device did not exercise this history.
3. MainTabPushRailFocusTest.leftWithNothingFocused_opensRailAndBackReturnsToContent red (test:94): a real Left via sendKeyDownUpSync with nothing focused does not open the rail. On the box Center then Left 150 ms later on a cached Show all did expand the rail, so the device behaviour is unproven either way (R11).
UNAVAILABLE: none
MANUAL: Shelf "Показать все" tile entry (M8 path 4) and a Left during a real Loading state (M8 step 6) need a tab row with a short list or a slow network; M11 steps for Bookmarks, Settings, empty screens and dialogs were not run. Note: during M9 setup a Center on a Home continue-watching card started playback of an episode at 0:04 for about 3 s on the owner's account; Back closed it, no resume prompt was confirmed; a 0:04 progress record may have been written.

# Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at HEAD 6379bd8b; the final run reused up-to-date results from a run that executed the tasks)
DEVICE: 192.168.1.110:5555 (TV box), dev APK sha256 02e60fba...f648 at HEAD 6379bd8b (device base.apk identical), lease released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (device) / PASS (instrumented) | M1 on box (Left from first card, Back: rail closed, grid and card stay); railOpenOnPushedScreen_* green in MainRailBackTest x2 |
| AC-2 | PASS (instrumented), device not re-run | ..._afterDetailsRoundTrip green x2; round 1 M2 on box at 8cc8daa0 |
| AC-3 | PASS (instrumented), device not re-run | ..._afterTabSwitch green x2; round 1 M3 on box |
| AC-4 | PASS | M4 on box; backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory green |
| AC-5 | PASS (instrumented), device not re-run | fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth green x2; round 1 M5 |
| AC-6 | PASS (instrumented), device not re-run | armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit green x2; round 1 M6 |
| AC-7 | PASS | M7 on Home (4 gap values) and Movies exits on the third Back, toast after the second; backAtTabRoot_opensRailThenArmsThenExits green. See the observation under DEFECTS |
| AC-8 | PASS on 3 of 4 paths, NOT CHECKED Left-during-Loading and the shelf tile | cold entry shows spinner then grid with the rail collapsed; pushIntoLoadingScreen, pushWithContentReady, pushAfterDetailsRoundTrip, leftFromPushedGrid green; leftWithNothingFocused_opensRailAndBackReturnsToContent still FAIL (MainTabPushRailFocusTest.kt:94) |
| AC-9 | PASS | M9 panel and long-press menu on box; ShowAllRailCloseFocusTest green |
| AC-10 | PASS (instrumented), device not re-run | backOnRootPushedDetails_popsToMain green; round 1 M10 |
| AC-11 | FAIL | check.full green; MainRailBackTest 10/10 x2, TabFlowLifecycleTest, ShowAllRailCloseFocusTest, InitialDrawerContentFocusTest, MainSideMenuFocusTraversalTest green; MainTabPushRailFocusTest 4 of 5 (defect 3 open) |
| R3/R7 Home D-pad | PASS | Left from a card opens the rail at the card's row, Right returns to the same card; MainSideMenuFocusTraversalTest green |
| R11 step 5 | NOT CHECKED | no nothing-focusable screen found on the account |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md (committed 8d9d19a7, audit passed)
DEFECTS:
1 (round 1, fixed): MainRailBackTest railOpen x3 and fiveBacks now green on two runs.
2 (round 1, fixed): backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail now green on two runs.
3 (open): MainTabPushRailFocusTest.leftWithNothingFocused_opensRailAndBackReturnsToContent still red at MainTabPushRailFocusTest.kt:94 (awaitDrawer(Open) after sendLeftThroughInputPipeline, ComposeTimeout 10 s), two runs after 1fd30c7a. Reproduce: make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.main.component.MainTabPushRailFocusTest. The device cannot settle it: the grid was cached on every attempt, so Left never met a real Loading state.
Observation (not a defect, unexplained): on Home, with a screencap between the Back presses, the third Back reopened the rail instead of leaving (3 runs); with no capture between presses it left at gaps 0.45 to 2.2 s (4 runs). Armed window is 3 s (ExitConfirmation.kt:12). Worth a look if a slow display could produce the same on a real TV.
UNAVAILABLE: none
MANUAL: Left during a real Loading state (needs a cold cache or slow network on a Show all screen) and a screen with nothing focusable (M11 step 5); shelf "Показать все" tile entry. Left on the unchanged build vs HEAD for the nothing-focused test would tell whether the test or the guard is wrong.

# Round 3

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at HEAD 1ed3df50, tasks executed)
DEVICE: 192.168.1.110:5555 (TV box), auth-save first, lease released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (device) / PASS (instrumented) | round 2 M1; railOpenOnPushedScreen_* green, MainRailBackTest 10/10 at HEAD |
| AC-2 | PASS (instrumented), device not re-run | ..._afterDetailsRoundTrip green; round 1 M2 |
| AC-3 | PASS (instrumented), device not re-run | ..._afterTabSwitch green; round 1 M3 |
| AC-4 | PASS | round 2 M4; backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory green |
| AC-5 | PASS (instrumented), device not re-run | fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth green; round 1 M5 |
| AC-6 | PASS (instrumented), device not re-run | armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit green; round 1 M6 |
| AC-7 | PASS | round 2 M7 Home and Movies; backAtTabRoot_opensRailThenArmsThenExits green |
| AC-8 | PASS on 3 of 4 paths, NOT CHECKED Left-during-Loading on device and the shelf tile | MainTabPushRailFocusTest 5/5 x2 at HEAD, incl. leftWithNothingFocused_opensRailAndBackReturnsToContent |
| AC-9 | PASS | round 2 M9; ShowAllRailCloseFocusTest green (round 2) |
| AC-10 | PASS (instrumented), device not re-run | backOnRootPushedDetails_popsToMain green; round 1 M10 |
| AC-11 | PASS | check.full green; MainRailBackTest 10/10 and MainTabPushRailFocusTest 5/5 x2 at HEAD; TabFlowLifecycle, ShowAllRailClose, InitialDrawerContentFocus, MainSideMenuFocusTraversal green in round 2 (only test code and a comment changed since) |
| R3/R7 Home D-pad | PASS | round 2 |
| R11 step 5 | NOT CHECKED | no nothing-focusable screen found on the account |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md (audit: see commit)
DEFECTS: none open (round 2 defect 3 fixed by 2bf56b89 plus 1ed3df50). Round 2 observation (third Back reopens the rail when a screencap sits between presses on Home) stays unexplained.
UNAVAILABLE: none
MANUAL: Left during a real Loading state, a screen with nothing focusable (M11 step 5), shelf "Показать все" tile entry.

# Round 4

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at HEAD 773b60f2)
DEVICE: 192.168.1.110:5555 (TV box), auth-save first, dev APK sha256 d1a31193...18f15e at HEAD 773b60f2 (device base.apk identical), lease released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (device) / PASS (instrumented) | M1 on box (Show all on Док. Сериалы: Left opens rail, Back closes it, grid and card stay, no toast); railOpenOnPushedScreen_*_afterColdStart green, MainRailBackTest 12/12 |
| AC-2 | PASS (device, no toast) / PASS (instrumented) | M1 no toast; ..._afterDetailsRoundTrip green; A2 mutation (arm unconditionally) turns 4 of 12 red |
| AC-3 | PASS (instrumented), device not re-run | ..._afterTabSwitch green; round 1 M3 |
| AC-4 | PASS | M4 on box (pop to root, button focused, rail collapsed); backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory green |
| AC-5 | PASS (instrumented), device not re-run | fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth green; round 1 M5 |
| AC-6 | PASS | M12 on box (arm at root, other tab picked, Back opens rail, app stays); armedAtRoot_thenPushAndPop and armedAtRoot_thenTabPick green; A4 mutation 1 red, A6 mutation 1 red |
| AC-7 | PASS | M7 on Movies root: Back x3 without a capture between, toast after press 2, exit on press 3; backAtTabRoot_opensRailThenArmsThenExits green |
| AC-8 | PASS on 3 of 4 paths, NOT CHECKED Left-during-Loading on device and the shelf tile | MainTabPushRailFocusTest green at HEAD (BUILD SUCCESSFUL, 5 tests); cold entry to Show all showed rail collapsed, first card focused. A1a and A1b both turn leftWithNothingFocused_opensRailAndBackReturnsToContent red at awaitDrawer(Open) (MainTabPushRailFocusTest.kt:90) |
| AC-9 | PASS (instrumented), device not re-run | ShowAllRailCloseFocusTest, ShowAllFilterFocusTest, ShowAllScreenContentFocusTest green; round 2 M9 |
| AC-10 | PASS (instrumented), device not re-run | backOnRootPushedDetails_popsToMain green; round 1 M10 |
| AC-11 | PASS | check.full green; MainRailBackTest 12/12, MainTabPushRailFocusTest, InitialDrawerContentFocusTest, MainSideMenuFocusTraversalTest, ShowAllRailCloseFocusTest, ShowAllFilterFocusTest, ShowAllScreenContentFocusTest, TabFlowLifecycleTest all BUILD SUCCESSFUL |
| R13 | PASS (instrumented) | backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff green |
| R11 step 5 | NOT CHECKED on device | no nothing-focusable screen on the account |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md (committed 4b0b1a4a, audit passed)
Mutations (temporary, reverted, tree clean): A1a (RevealOnUnhandledLeft call removed) and A1b (guard inverted) each make leftWithNothingFocused red at awaitDrawer(Open), MainTabPushRailFocusTest.kt:90, 1 of 5 red. A2 4 of 12 red, A4 1 of 12 red, A6 1 of 12 red in MainRailBackTest (failing test names not captured; the result XML is overwritten by the next run).
DEFECTS: none
UNAVAILABLE: none
MANUAL: Left during a real Loading state, a nothing-focusable screen (M11 step 5) and the shelf "Показать все" tile entry. Setup note: my first M12 attempt began with the rail already open and exited the app; a setup slip, not a defect. A screencap between Back presses 2 and 3 of M7 lets the 3 s arm lapse (same as round 2).


## Round 5 (mutations)

Build: worktree HEAD 4b0b1a4a, dev debug, TV box 192.168.1.110:5555 (lease held, auth-save taken first, released after). Test: `MainRailBackTest` (12 tests), `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.main.component.MainRailBackTest`, AGENT_GRADLE_SERIAL=1. Each edit was applied alone, run, then `git checkout -- <file>`; `git status --short` was empty after each and at the end. Failing names read from the result XML before the next run.

| Mutation | Expected red | Observed | Verdict |
|----------|--------------|----------|---------|
| (a) MainScreenComponent.kt: `closeOnFail = { tabStack.canPop }` -> `{ false }` | backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff | 1 of 12 failed: backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff, ComposeTimeoutException (10000 ms) at MainHostHarness.awaitDrawer (MainHostHarness.kt:300) called from MainRailBackTest.kt:166 (`host.awaitDrawer(DrawerValue.Closed)`, the rail reopened instead) | killed |
| (b) ModalNavigationDrawer.kt: remove `focusManager.clearFocus(force = true)` (Closed branch of DrawerSheet) | same test, red at assertNoRailItemFocused (MainRailBackTest.kt:169) | 12 of 12 passed, no failure. Edit compiled and was in the diff (1 deletion). | SURVIVED (finding) |
| (c) MainScreenComponent.kt: `onAction = onRailAction` -> `onAction = onAction` | armedAtRoot_thenTabPick_nextBackOpensRailNotExit | 1 of 12 failed: armedAtRoot_thenTabPick_nextBackOpensRailNotExit, java.lang.AssertionError at MainRailBackTest.kt:141 | killed |

Finding F1 (b): the focus-clear line is not covered by the test. With it removed, `assertNoRailItemFocused` (MainHostHarness.kt:338, `railItemFocused().assertDoesNotExist()`) still passes after the failed handoff, so something else drops focus from the rail item in this harness, or the assertion runs after focus has left for another reason (the test never proves the focus was in the rail before the close, and `awaitDrawer(Closed)` returns before the LaunchedEffect would have mattered). Whether the production line is dead or only the test is blind is not decided here. A test that would kill it needs to assert the item is focused at the moment of Closed (or check that Center in the collapsed rail picks nothing) so that it fails without the line. No production or test file was changed; no evidence file changed, so no audit or commit.

Lease released (exit 0).

## Round 6

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (also with --rerun on the three tasks, BUILD SUCCESSFUL in 33s)
DEVICE: 192.168.1.110:5555 (lease held and released, auth-save first). APK sha256 616edb279a4cdce992473ca941f88adde44f0a85fa534ccc824714f1a0d3775f, install-preserve installed, on-device sha identical, HEAD 3a536877.
Instrumented at HEAD, all BUILD SUCCESSFUL: MainRailBackTest (13 tests in the result XML; the brief said 14), MainTabPushRailFocusTest, MainSideMenuFocusTraversalTest, InitialDrawerContentFocusTest.

Mutations (alone, reverted, tree clean after each):
- B2 (delete focusManager.clearFocus(force = true)): KILLED. 1 of 13 red: backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff, assertNoRailItemFocused MainHostHarness.kt:337 from MainRailBackTest.kt:174 (found 1 focused node with Selected defined). Run twice, same.
- B3 (MainScreenComponent.kt:315 -> if (true)): KILLED. 1 of 13 red: centerOnCollapsedRail_duringHandoff_doesNotSwitchTab, assertShown(CHILD_SCREEN) MainHostHarness.kt:308 from MainRailBackTest.kt:203. Caveat confirmed: the test's Down is followed by assertRailItemFocused, which accepts any focused selected-defined rail node, so it does not prove the focused item is a non-selected one; the kill comes from the Center effect (child screen gone), which is the behaviour under test.

Device: P1 Home (Left, Down x2, Center: Сериалы tab opened, rail collapsed, first card focused) PASS; P2 Movies (Left, Up x1 / Up x7 from Settings, Center: Movies opened, focus in content) PASS; M1 (Movies, Show all, Left, Back: rail closes, grid and first card stay, no toast) PASS; M4 (Back: Movies root, "Все фильмы" focused, rail collapsed) PASS.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1, AC-2 | PASS | M1 on device at HEAD; MainRailBackTest green |
| AC-3 | PASS | round 1 M3 on device; MainRailBackTest green at HEAD (not re-run on device) |
| AC-4 | PASS | M4 on device at HEAD |
| AC-5 | PASS (instrumented) | MainRailBackTest green at HEAD; device not re-run |
| AC-6 | PASS | MainRailBackTest green at HEAD (armedAtRoot tests); device M12 in round 3, not re-run |
| AC-7 | PASS (instrumented) | MainRailBackTest green at HEAD; device M7 rounds 2 and 3 |
| AC-8 | PASS on 3 of 4 paths | MainTabPushRailFocusTest green at HEAD; Left-during-Loading and the shelf tile NOT CHECKED on device |
| AC-9, AC-10 | PASS (earlier rounds) | not re-run this round; no production change to those paths in the 15f0f3a5 click guard besides the rail |
| AC-11 | PASS | check.full green |
| R13 | PASS | B2 and B3 mutation kills; backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff green |
| Rail picks (click guard 15f0f3a5) | PASS | P1, P2 on device; MainSideMenuFocusTraversalTest, InitialDrawerContentFocusTest green |
| R11 step 5 | NOT CHECKED on device | no nothing-focusable screen on the account |

TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md (committed, audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: Left during a real Loading state, a nothing-focusable screen, the shelf "Показать все" tile entry.
Note: entering the rail with Left puts focus on the item nearest the focused content vertically (Settings from a bottom card), not on the selected tab; I saw this on every pick and did not compare it to the earlier rounds.

## Round 7

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug (--rerun-tasks) -> pass (BUILD SUCCESSFUL in 2m 36s)
DEVICE: 192.168.1.110:5555 (lease held and released, auth-save first). HEAD 73371c73, APK sha256 4151c7e32f25e588df1bbb2043acd01337f255da98f9494251c733eee780e8bd, install-preserve installed, on-device sha identical.
Instrumented at HEAD, all BUILD SUCCESSFUL: MainRailBackTest (14 tests), MainTabPushRailFocusTest, MainSideMenuFocusTraversalTest, InitialDrawerContentFocusTest.

Mutations (alone, reverted, tree clean):
- C3 (acceptsPickOf HandingOff -> false): KILLED, 1 of 14: correctivePickDuringTabPickHandoff_switchesToTheSecondTab, ComposeTimeoutException at MainHostHarness.awaitRoot (MainHostHarness.kt:319), MainRailBackTest.kt:223.
- C2/B3 (acceptsPickOf body -> true): KILLED, 1 of 14: centerOnCollapsedRail_duringHandoff_doesNotSwitchTab, assertExists child_screen at MainRailBackTest.kt:203.

Device: P1 Home PASS; P2 Movies PASS; M1 PASS (Show all, Left, Back: rail closed, grid and first card stay, no toast). Quick double pick: NOT CHECKED on device. Four tries (separate and single-command keys): the first pick always won, because the cached tab finished its handoff before the second Down arrived, so Down and Center acted on the new tab's content (opened a details page, no playback; backed out). The corrective window needs a still-loading tab, which this box did not give.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1, AC-2 | PASS | M1 on device at HEAD; MainRailBackTest green |
| AC-3, AC-5, AC-6, AC-7 | PASS (instrumented) | MainRailBackTest 14/14 at HEAD; device M3, M5, M12, M7 in earlier rounds |
| AC-4 | PASS | round 6 M4 on device; MainRailBackTest green |
| AC-8 | PASS on 3 of 4 paths | MainTabPushRailFocusTest green; Left-during-Loading and shelf tile NOT CHECKED on device |
| AC-9, AC-10 | PASS (earlier rounds) | not re-run |
| AC-11 | PASS | check.full green |
| R13 | PASS | MainRailBackTest green |
| Rail picks (click guard) | PASS | P1, P2 on device; MainSideMenuFocusTraversalTest, InitialDrawerContentFocusTest green; C2/B3 kill |
| Corrective pick during handoff | PASS (instrumented), NOT CHECKED on device | correctivePickDuringTabPickHandoff_switchesToTheSecondTab green and killed by C3 |
| R11 step 5 | NOT CHECKED on device | no nothing-focusable screen |

TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md (committed, audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: quick double pick while the target tab is still loading (cold start, then Left, Down, Center, Down, Center), Left during a real Loading state, a nothing-focusable screen, the shelf "Показать все" tile.
