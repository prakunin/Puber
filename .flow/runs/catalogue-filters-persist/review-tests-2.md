LENS: tests  ROUND: 2  BASE: ef5987ec

### F1 [minor] The two new instrumented tests have not yet been run red without the fix
- Where: plan:197 (Review round 1 fixes, item 1); ShowAllFilterFocusTest.kt:140-170
- Problem: `coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset` and `panelApplyThatPagesEmpty_keepsFocusOnFiltersNotReset` are "Compiled only". In round 1 a test in this file passed with its fix reverted. By reading, the cold-open test fails without the LaunchedEffect (awaitAnyFocus times out) and the panel test fails without `!headerHoldsFocus.value`; that is a reading, not evidence.
- Fix: qa-device runs both on the box with the LaunchedEffect removed (cold-open test red) and with `&& !headerHoldsFocus.value` removed (panel test red), plus the green class run.

### F2 [nit] The panel test's name says "apply", but the test closes the panel with Back
- Where: ShowAllFilterFocusTest.kt:155
- Fix: rename to e.g. `panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset`, or apply through the panel.

New unit tests can fail and are accurate; ShowAllVMTest BUILD SUCCESSFUL (UP-TO-DATE). Instrumented tests not run.

### Unproven
- ShowAllScreenContent.kt:190-195 the effect checks only `headerHoldsFocus`; if the side rail has focus when a filtered Empty mounts on a cold open, «Сброс» takes it. Untested; real only with device evidence that the rail holds focus during Loading and that taking it is unwanted.
