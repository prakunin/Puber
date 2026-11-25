# Device evidence: rail-handoff-nothing-focusable (round 1)

Date: 2026-11-25. Package: com.kino.puber.stage only. Device: TV box 192.168.1.110:5555 (model KM6), lease held for the whole run, released at the end.

## Build

- Worktree HEAD a746c00f (branch prakunin/rail-handoff-nothing-focusable), base c9e98129.
- Dev APK sha256 3c7aa7266edecd8579217e776b09c1efb1e270b40d1bba2ad93c02ee9ebe8d84 (host build and on-device base.apk agree).
- Install: tools/android-apk-install-preserve, outcome installed, classification compatible_replace, destructive_action false.
- D0: the app opened on Home (session live) on the build the previous itest had installed, so the master-build refresh was not needed; make DEVICE=192.168.1.110:5555 auth-save done before any further install.

## Red run R (production at base, commit dfca2744 detached in the worktree)

make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=...MainRailHandoffKindTest: 13 tests, 8 failed, 5 passed. git diff c9e98129 --stat -- app/src/main was empty.

| Test | Base result | Failing line (MainRailHandoffKindTest.kt) |
|---|---|---|
| backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff | red | 46 assertTrue(host.isFinishing) |
| failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt | red | 64 via failBackHandoffAtNeverFocusableRoot :243 assertDrawer(Closed), was Open |
| failedBackHandoffAtNeverFocusableRoot_expiredArmBackReopensRail | red | 73 via :243 assertDrawer(Closed), was Open |
| rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed | red | 91 assertDrawer(Closed), was Open |
| pickDuringInitialHandoff_switchesToPickedTab | red | 104 assertEquals(1, host.railPicks), was 0 |
| pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle | red | 125 assertEquals(1, host.railPicks), was 0 |
| coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus | red | 154 assertDrawer(Closed), was Open |
| rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed | red | 220 assertDrawer(Closed), was Open |
| backDuringDismissHandoffAfterArmExpired_isSwallowed, centerOnSelectedTabDuringInitialHandoff_switchesNothing, centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches, tabPickIntoNeverFocusableTab_failedHandoffReopensRail, rePickOfSelectedTab_closesRailOntoContent | guard | pass |

Every red row fails at the assertion the plan names, none in the fixture.

## Green run G (HEAD)

itest over MainRailHandoffKindTest (13), MainRailBackTest (14), MainTabPushRailFocusTest (5), MainSideMenuFocusTraversalTest (3), InitialDrawerContentFocusTest (3), ShowAllRailCloseFocusTest (8): 46 tests, 0 failures.

## Mutation runs M (each applied alone, reverted with git checkout HEAD -- file, git status clean after each)

| # | Result: tests that fail under the mutation |
|---|---|
| M1 | backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff (:46) |
| M3 | backDuringDismissHandoffAfterArmExpired_isSwallowed (:58) |
| M4 | instrumented: coldStart... (:154), rePickOfSelectedTabAtNeverFocusableRoot_... (:220), failedBackHandoff... both (:64, :73), rightFromRail... (:91), all expected Closed was Open; unit: failed dismiss and failed initial cases (DrawerStateTest :113, :123) |
| M5 | instrumented: tabPickIntoNeverFocusableTab_... (:193 expected Open was Closed), MainSideMenuFocusTraversalTest.selectingATabWhoseContentNeverTakesFocus_reopensTheRail (:125), pickDuringInitialHandoff_restartsAsTabPick_... (:129 timeout); unit: a failed tab pick handoff reopens the rail (:93), settling a superseded handoff is ignored (:79) |
| M6 | pickDuringInitialHandoff_switchesToPickedTab (:104), pickDuringInitialHandoff_restartsAsTabPick_... (:125) |
| M7 | centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches (:174), centerOnSelectedTabDuringInitialHandoff_switchesNothing (:141) |
| M8 | MainRailBackTest.centerOnCollapsedRail_duringHandoff_doesNotSwitchTab (:203) |
| M9 null (instrumented 3 runs) | pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle failed in 3 of 3 (:129 timeout); unit: 3 restart cases fail |
| M9 same id (instrumented 3 runs) | the instrumented test PASSED in 3 of 3 (mutation survives the instrumented row); unit: the three restart cases fail (DrawerStateTest :151, :166, :178, "not equal") |
| M10 | rePickOfSelectedTab_closesRailOntoContent (:206), rePickOfSelectedTabAtNeverFocusableRoot_... (:219) |
| M11 | rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed (:90) |
| M12 | failedBackHandoff... both (:64, :73), backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff (:46) |

DrawerStateTest at HEAD with --rerun: 25 tests, 0 failures.

## Manual checks on the worktree dev APK (screenshots stay local, not committed)

### D1 / AC-10: Back, Back, Back about 0.5 s apart, one adb shell line (input keyevent 4; sleep 0.5; ...)

- Steps one at a time first (Home): Back 1 shows the rail open on the selected item; Back 2 (within 3 s) shows the rail collapsing with the toast "Press Back again to exit" (Russian text on screen). Observed. A separate try where the third Back came more than 3 s after the second (screenshot latency) reopened the rail, as designed once the arm expired.
- Home, cold start, three Backs in one line: foreground activity after 2.5 s is com.google.android.tvlauncher (exit). 2 of 2 runs.
- History (picked from the rail, content focused on its first card), three Backs: launcher. 1 of 1.
- Movies (picked from the rail, Movies card focused), three Backs: launcher. 1 of 1.
- Result: PASS on Home, Movies, History. Not seen: rail reopening by itself, an ignored Back, exit on the first or second Back.

### D2 / AC-11

- Cold start, Home focused. DPAD_LEFT opened the rail (its focus lands on the rail item level with the focused card, not always Home). DPAD_DOWN to Movies (screenshot: Movies item focused), DPAD_CENTER: Movies shown, a Movies card focused, rail collapsed, no further change. A pick of Concerts and of Documentary Movies and Series behaved the same.
- With the first Movies card focused: DPAD_LEFT opened the rail; BACK: rail collapsed, focus back on the same card, rail still collapsed and card still focused 6 s later. PASS.
- Note: DPAD_LEFT from a card that is not the leftmost moves focus to the neighbouring card instead of opening the rail; Back there opens the rail (root Back with the rail closed), as before.

### D3 / AC-1 on the real app

not run: Favourites not empty. The heart tab (labelled "Я смотрю") was opened by navigation only and shows a populated list; nothing was added, removed or long-pressed.

## Safety

No uninstall, data clear, downgrade or signer change. Only com.kino.puber.stage was launched or driven; no Center on a continue-watching card, no resume prompt confirmed.

## Round 2 (test-only changes, triage-1 E3)

Device: 192.168.1.110:5555 (TV box), lease held, auth-saved first, com.kino.puber.stage only. HEAD 8f119fa2, run through `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest` with AGENT_GRADLE_SERIAL=1.

| Step | Result |
|------|--------|
| 1. MainRailHandoffKindTest (13) + MainTabPushRailFocusTest (5) at HEAD | 18 tests, 0 failures, 0 errors, 0 skipped: PASS |
| 2. M9 same id (ModalNavigationDrawer.kt:163, exact plan edit `{ handoffKind = kind; pendingHandoffId }`), `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`, 3 runs | run 1 RED, run 2 RED, run 3 RED. Each fails at `MainRailHandoffKindTest.kt:130` `assertDrawer(HandingOff)`: expected HandingOff but was Closed (as the plan predicted). The mutation compiled and the build ran. |
| Revert | `git checkout HEAD -- ModalNavigationDrawer.kt`; `git status --short` empty |
| 3. MainRailHandoffKindTest after revert | 13 tests, 0 failures: PASS |

Verdict round 2: the M9-same-id mutation is killed 3 of 3; no defects.
