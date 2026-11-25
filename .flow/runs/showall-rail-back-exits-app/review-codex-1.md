CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainRailBackTest.kt:181-191 - `assertRailBackKeepsChild` never checks that closing the rail over a pushed screen leaves the exit unarmed and shows no toast. A wrongly armed exit (`RailOpenBackHandler` skipping the `!tabStack.canPop` guard) would pass every test. The next Back pops the child, which changes `canPop` and runs `ExitDisarmOnTabPush`. `fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth` has the same blind spot. Scenario: the guard regresses, so Back over Show all shows "press back again to exit" while the grid stays. AC-2 forbids this toast, but nothing fails. Fix: assert `exitConfirmation.isArmed` is false (or no toast) right after `awaitDrawer(Closed)`, before the pop. Production code is correct as written.

REJECTED:
- Back can pop Show all in the one frame between a Left reveal and recomposition (Codex "high") - unproven. Handler `enabled` follows `drawerState.currentValue`, so the stale window is at most one frame after Left. No evidence a real D-pad Back lands inside it; not reproduced. The old order-dependent code had the same window.
- Back from a rail opened by Left over a Loading screen that outlasts the 5 s handoff timeout reopens the rail (Codex "medium") - the timeout in `ContentFocusHandoff.kt:24,120` is code this branch did not touch; on base the rail was already stuck Open in that state. Documented in the test comment.
- The loading test bypasses the timeout - same claim, same grounds.
- Revert 1fd30c7a (the focus-observing Box) - not decided. No concrete defect either way. Keep until leftWithNothingFocused and the D-pad guards pass on the box without it.

VERDICT: changes needed
