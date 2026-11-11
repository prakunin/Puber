LENS: tests  ROUND: 2  BASE: 97be2417 (incremental 97be2417..e9595021)

### F1 [major] T-I4 `showAllRecompositionAfterSettleDoesNotRefocus` cannot fail on a re-fire
- Where: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt`, the test body and `flipContentActive`
- Problem: The test moves focus with RIGHT, so the grid's remembered card is the moved card. It then flips `forcedInactive` and back. Suppose the handoff attempt re-fired after that flip. The grid is focused, so the attempt either does nothing (`inGrid` guard) or restores the remembered card, which is `moved`. Either way `assertEquals(moved, focusedTitle())` passes. T-I4 is meant to catch a once-per-id latch that stops holding, and only an attempt aimed at a stale card would show that.
- Failure scenario: Remove the single-fire latch (the `remember`ed `pendingRequestId`) or the `inGrid` check. The flip re-runs the attempt. It requests `moved` again, which already has focus, and the test stays green. Test-design.md says this latch is covered only by T-I1 and T-I4, so T-I4 is the sole instrumented guard for a P1 AC-5 row.
- Fix: Make the stale target differ from the focused card. After the handoff settles, move focus with RIGHT and then flip the content state. For the assertion to be meaningful, the remembered card must not follow focus. Either flip while the grid does not hold focus and the remembered card is the old one, or have the test count `requestRestore` / focus-gain events on the old card across the flip. If neither is feasible in this harness, state in the test header that it is a smoke check. Then cover the latch with a unit test (T-U4) and the D5 device check.

### F2 [minor] T-I2 `showAllRailCloseWithoutMemoryBehavesAsBase` is not labelled as a guard
- Where: `ShowAllRailCloseFocusTest.kt`, `showAllRailCloseWithoutMemoryBehavesAsBase`
- Problem: It asserts the first card is focused, which is also base behaviour, so it passes on base. Triage #3 asked for a guard label on the top-row test. This test has the same property and no comment, and the Implementation record needs a "guard" mark for a test that is green on base.
- Failure scenario: A reviewer or the gate reads it as a P0 behaviour test and expects a red run on base that does not exist.
- Fix: Add a one-line comment above it: `// Guard: also passes on base.` The test still fails if a null-memory handoff steals focus away from the first card, so it keeps its value.

No other findings. The other round-2 changes hold up:
- T-I1b now covers the top row and a scrolled position (triage #2 and #3).
- T-I3 would fail on a steal, because the popup holds focus outside the grid while the content turns inactive and active again.
- The class run that qa is doing will confirm whether the scrolled case really lands in a lower visible row.

### Unproven
- `ShowAllRailCloseFocusTest.kt` T-I1b: after 7 DOWN and 1 UP the focused card may not be in a lower visible row, so the scrolled case may be less discriminating than intended. Evidence that would settle it: the qa device run showing the card's row.
