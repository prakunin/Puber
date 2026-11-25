LENS: tests  ROUND: 4  BASE: f88f3079
### F1 [major] The !tab.isSelected half of the new HandingOff guard has no test that would fail, and the probe cannot express it
- Where: MainScreenComponent.kt:306; MainHostHarness.kt:399-414
- Problem: ProbeMainHostScreen passes a fixed MainViewState (Movies selected); onAction only calls tabRouter.openTab and never updates the selection. correctivePickDuringTabPickHandoff_switchesToTheSecondTab and the B3 test pass with or without !tab.isSelected. The plan names the refusal of a re-pick of the selected tab during HandingOff as intended; nothing tests it.
- Failure scenario: guard changed to HandingOff -> handoffExpectsNewContent; user picks History (loading), focus stays on History, Center again: a second ItemSelected and MainVM.onTabSelected catch-up refresh run again. Every test green.
- Fix: probe onAction keeps the selected tab in mutableStateOf; test: History loading, pick History, Center again during HandingOff, assert ItemSelected forwarded once. Or remove the clause and record the refusal as a manual check.

### F2 [major] The C2 and C3 test changes have never run
- Where: plan :308-309; MainRailBackTest.kt:199, :208-228
- Problem: not run at 73371c73: the C3 mutation, the isNotSelected() matcher on the collapsed rail, the third tab and the loadingRoot early return.
- Fix: qa round 7 runs MainRailBackTest at HEAD and the C3 mutation (red at awaitRoot("third")).

### F3 [minor] The corrective test proves less after Down than the B3 test does
- Where: MainRailBackTest.kt:217
- Problem: after Down only assertRailItemFocused(); if Down does not move, Center lands on History (accepted by the static probe) and the test fails later at awaitRoot("third") with an unclear timeout.
- Fix: assert the focused node has the Series label (unmerged tree), or assertUnselectedRailItemFocused() once F1 makes the probe selection real.

### F4 [nit] The evidence file keeps a stale caveat
- Where: evidence Round 6 "B3 caveat" and "13 tests" (now 14).
- Fix: note in qa round 7 that C2 fixed it; new count.

Suite state: nothing run by this lens; last recorded check.full green at 6c44c4e9.

### Unproven
- MainRailBackTest.kt:224 awaitFocused(ROOT_CARD_FIRST) could match a hidden first root's card if tab roots stay composed after openTab.
