LENS: tests  ROUND: 3  BASE: f88f3079

### F1 [major] The B3 regression test does not check that Down reached the History item, so it may pass on the old code too
- Where: MainRailBackTest.kt:197-199 (centerOnCollapsedRail_duringHandoff_doesNotSwitchTab)
- Problem: after press(Key.DirectionDown) only assertRailItemFocused() (isFocused() and isSelectable(), matches any rail item incl. Movies). If Center lands on Movies, the old onClick sends ItemSelected(Movies) → openTab(tabFirst) → TabComponent.kt:153-159 only sets navigator.current to the current tab; the child stays, root "other" never appears: passes on 15f0f3a5^.
- Failure scenario: Down on the collapsed rail during HandingOff does not move focus (never checked); the test passes with or without the isOpen guard (MainScreenComponent.kt:315).
- Fix: after Down assert the focused rail item is the unselected one, e.g. rule.onNode(isFocused() and isSelectable() and isNotSelected()).assertExists(); record a real red run on 15f0f3a5^.

### F2 [major] The new and changed P0 instrumented assertions have never run, red or green
- Where: plan :301-302; MainRailBackTest.kt:167-170, 185-206; MainHostHarness.kt:326-334
- Problem: B2 (expected red under mutation (b)) and B3 (expected red on 15f0f3a5^) have no red run and no green run at HEAD. B2 relies on an unverified tv-material 1.1.0 claim (rail item carries selected semantics, matched by isSelectable() in the merged tree); all 12 awaitRailItemFocused waits now use the new matcher. check.full does not run androidTest.
- Failure scenario: isSelectable() finds no node → 10+ MainRailBackTest tests red at awaitRailItemFocused; or the B3 test passes on old code and is recorded as the regression test.
- Fix: qa round 6: MainRailBackTest at HEAD, with mutation (b), and with the isOpen guard removed; record all three.

### Unproven
- MainRailBackTest.kt:168 Thread.sleep(BEFORE_HANDOFF_TIMEOUT_MILLIS): if the compose test clock drives the handoff withTimeoutOrNull(5_000), a real-time sleep does not advance it; the 2.5 s check would be a no-op wait.
