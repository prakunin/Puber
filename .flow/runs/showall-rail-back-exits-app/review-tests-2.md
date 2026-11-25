LENS: tests  ROUND: 2  BASE: c6b25f49

### F1 [major] No red run and no device run for the two new P0 instrumented tests
- Where: docs/superpowers/plans/2026-11-25-showall-rail-back-exits-app.md:284-285
- Problem: MainRailBackTest.backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff (AC-1, R13) and MainRailBackTest.armedAtRoot_thenTabPick_nextBackOpensRailNotExit (AC-6, R14) are P0 red, but the record only says "expected red ... not run". They never ran, old code or HEAD. qa-report still counts MainRailBackTest 10/10; the class now has 12. Instrumented tests are not in check.full, so A5, A6 and the new clearFocus branch in DrawerSheet have no recorded failing or passing run; DrawerStateTest covers failHandoff(reopen = false) only, not the ContentFocusHandoff.fail mapping, the tabStack.canPop wiring or the disarm on a rail pick.
- Failure scenario: a harness step (assertDrawer(HandingOff) after back(), or pickRailTabBelow not reaching History) fails on the old code for a harness reason and looks like a valid red; at HEAD the test could fail or pass without exercising the fix.
- Fix: qa-device run of MainRailBackTest at 00be3dcb (or c6b25f49) recording the failing line, then at HEAD green; write both into the A5 and A6 rows.

### F2 [minor] The M11 step 5 expectation contradicts A5
- Where: spec :292 (and the plan's manual checks)
- Problem: step 5 falls back to Show all during Loading (a pushed screen) but still expects "the rail reopens after 5 s"; since A5 a failed handoff over a pushed screen settles Closed (spec :153, :167).
- Failure scenario: qa runs step 5 on Show all Loading; the rail stays collapsed (correct) but the check expects a reopen.
- Fix: "reopens after 5 s at a tab root; settles collapsed over a pushed screen, and the next Back pops (A5)".

### Unproven
- MainScreenComponent.kt:106: no test covers a first-launch or dialog-dismiss handoff failing over a pushed screen (now ends Closed with focus cleared). Real if a dialog can be dismissed over a pushed screen whose content never takes focus (e.g. Show all Error).
- MainScreenComponent.kt:113: no test covers the disarm when the user picks the already active tab.

Suite state: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*DrawerStateTest*' at 773b60f2 passed. Instrumented tests not run by this lens.
