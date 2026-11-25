LENS: edge  ROUND: 3  BASE: f88f3079

### F1 [minor] The B3 test does not check that Down reached a different tab, so it may pass even with the guard removed
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainRailBackTest.kt:197-205
- Problem: after press(Key.DirectionDown) the test only calls assertRailItemFocused(), which matches any rail item, including Movies. The plan shows no red run of this test without the guard.
- Failure scenario: if Down does not move focus on the collapsed rail, Center lands on Movies (selected). With the guard removed, ItemSelected(Movies) → openTab(tabFirst) on the current tab; beginHandoff returns null in HandingOff; CHILD_SCREEN stays, root "other" absent, drawer Closed: the test passes green.
- Fix: after Down, assert the focused rail item is History (e.g. hasAnyDescendant(hasText(<history title>), useUnmergedTree) while the label is composed, or a test tag per item). Record one red run with the guard removed (qa round 6 mutation).

Production change checked, nothing wrong: onClick reads drawerState.currentValue at run time; Left into the rail reveals (Open) before any Center; a rail reopened by failHandoff(reopen = true) is Open so Center still picks; during the initial handoff and in Closed before the clear Center is ignored as intended; a second Center during a tab-pick handoff no longer sends a duplicate ItemSelected.

### Unproven
- MainRailBackTest.kt:168: the fixed 2.5 s sleep against the 5 s handoff budget could flake at assertDrawer(HandingOff) on a slow device; real only if a qa run shows timing-dependent red.
