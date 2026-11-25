LENS: tests  ROUND: 4  BASE: ef5987ec (incremental b0cee092..d6ec6134)

### F1 [blocker] Both new tests check the header «Сброс», not the one in the empty state, so both fail on the fixed code and neither checks the guard
- Where: ShowAllRailCloseFocusTest.kt:220, :228, :246
- Problem: with an active filter two nodes carry «Сброс»; the header's (ShowAllHeader.kt:86-102) comes first in tree order; the guard controls only the `ShowAllEmpty` one. `onAllNodesWithText(reset)[0]` picks the header. ShowAllFilterFocusTest.kt:152-153, :243-249 assert 2 nodes and use `[1]`.
- Failure scenario: outgoing test: `[0].assertIsFocused()` at :220 fails on correct code; `[0].assertIsNotFocused()` at :228 holds with or without the guard. Arriving test: waitUntil at :245 times out on correct code. The suite goes red on correct production code and tests neither branch.
- Fix: use `[1]` in all three places after asserting 2 nodes, or a test tag.

### F2 [major] The arriving-tab test starts no handoff, so it passes with or without the fix
- Where: ShowAllRailCloseFocusTest.kt:238-240; ModalNavigationDrawer.kt:158-165
- Problem: `beginHandoff` returns null from Closed; the test is Closed (the initial handoff has settled); the return value is ignored. The plan (line 265) states the false assumption.
- Fix: Left, wait Open; `val id = beginHandoff(TabPick)`, assert non-null and HandingOff; swap to Empty; wait for `[1]` focused. Then only ShowAllEmpty's own request can move focus; red on the round-2 code.

### F3 [minor] The plan records no red run for either test
- Where: plan:262-265
- Fix: after F1/F2, record red under each mutation (outgoing: delete `!outgoingDuringTabPick`; arriving: restore the round-2 condition) and green on the fix.

No tests run (instrumented; device lease is qa-device's).

### Unproven
- ShowAllRailCloseFocusTest.kt:221 Left from the centred empty-state «Сброс» is assumed to reach the rail; a 2D search could pick «Фильтры»; a first run on the box settles it.
