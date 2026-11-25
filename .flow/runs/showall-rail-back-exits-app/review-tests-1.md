LENS: tests  ROUND: 1  BASE: f88f3079

check.full passes at c6b25f49 (up-to-date reuse; only evidence markdown changed since qa round 3 at 1ed3df50). Red runs on base recorded for every P0 red row in d1-d2.md; unit red runs in the plan.

### F1 [major] No test fails if the unhandled-Left listener or its guard breaks, and commit 1fd30c7a has no defect behind it
- Where: ModalNavigationDrawer.kt:331-347 (RevealOnUnhandledLeft) and :229-231 (wrapper Box with onFocusChanged). Test: MainTabPushRailFocusTest.kt:78-104
- Problem: leftWithNothingFocused_opensRailAndBackReturnsToContent is the only P0 test for the listener; on base it was red only through its precondition (fixed by the onEnter gate, not the listener). Removing the listener or inverting its guard was never run. The test file did not change between 13e46676 and 2bf56b89, so line 90 (awaitDrawer(Open) after the real Left) passed with the original !v.hasFocus() guard (round 1) and with the Compose-focus guard (round 2); b3933ff8 agrees 1fd30c7a was not needed. assertNothingFocused checks Compose semantics only; if the view keeps focus, Left may reach Compose's own focus move and enter the rail as FocusDirection.Left (allowed by the gate), so the listener may never run in this test.
- Failure scenario: delete RevealOnUnhandledLeft or swap the guard back; nothing shows the suite would go red. A later regression in Left-while-Loading (M8 step 6 / R11 step 5, both NOT CHECKED on device) would ship green.
- Fix: (1) revert 1fd30c7a (wrapper Box, contentHasFocus) back to !v.hasFocus(); (2) on the box run two mutations (remove the listener; invert the guard), expect failure at awaitDrawer(Open), record both; (3) if the test stays green without the listener, make it reach the unhandled-key path, or record the listener as unproven and keep R11 manual.

### F2 [minor] The "no exit arm over a pushed screen" rule has no test that can fail, though the test design says AC-2 covers it
- Where: MainScreenComponent.kt:216-219 (if (!tabStack.canPop) arm()); MainRailBackTest.kt:38-52; test-design AC-2 row
- Problem: ExitDisarmOnTabPush clears the arm whenever a pop changes canPop; a wrong arm on a pushed screen is cleared by the next pop, so AC-2/AC-5 tests stay green; only the toast shows it.
- Failure scenario: drop if (!tabStack.canPop); AC-2 and AC-5 stay green, the user sees the exit toast over Show all.
- Fix: correct the AC-2 row; name M1/M2 (toast absent) as the guard; or a seam exposing exitConfirmation.isArmed.

### F3 [minor] backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail cannot catch the R2 defect it is listed for
- Where: MainRailBackTest.kt:166-178; test-design R2 row
- Problem: tab B is at its root, so a cleared attachment gives canPop = false, same as B's root; the test only catches a stale A attachment that wins.
- Failure scenario: make detach ignore the owner; this test stays green; only TabStackStateTest.detach_byPreviousOwner_keepsNewAttachment fails.
- Fix: correct the R2 row (unit test is the guard), or push a child in tab B before Back and assert only one handler acts.

### F4 [minor] AC-6 test relies on finishing inside the 3 s exit window
- Where: MainRailBackTest.kt:107-123; ExitConfirmation.kt:12
- Problem: if push/focus/Back/awaitRoot/Back exceed 3 s, the arm expires by itself and the test passes without the disarm it proves.
- Failure scenario: on a slow/loaded box, removing the disarm still gives a green run.
- Fix: inject a fake clock through the harness, or assert arm-to-final-Back is under 3 s.

### Unproven
- ModalNavigationDrawer.kt:338: with the Compose-focus guard, Left on a same-window overlay outside Main's content (UpdatePromptOverlay, App.kt:148) could reveal the rail behind it; needs evidence the drawer is Closed (not HandingOff) while the prompt is up.
- ModalNavigationDrawer.kt:331: the unhandled-key path below API 28 (KeyEventDispatcher) is untested.
